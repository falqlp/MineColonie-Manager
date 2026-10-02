package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import dev.leopaul.colonyledger.cache.ColonySummaryCache;
import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.service.ResourceMath;
import dev.leopaul.colonyledger.service.SupplyPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/** Boundary for MineColonies. Any use of its internal API is documented here. */
public final class MineColoniesGateway {
    private MineColoniesGateway() {}

    public static ColonyResourceSummary snapshot(ServerPlayer player, int requestedColonyId) {
        IColony colony = findAccessibleColony(player, requestedColonyId);
        if (colony == null) return ColonyResourceSummary.empty("Aucune colonie accessible");
        String cacheKey = player.level().dimension().location() + ":" + colony.getID();
        return ColonySummaryCache.get(cacheKey, () -> computeSnapshot(colony));
    }

    private static ColonyResourceSummary computeSnapshot(IColony colony) {

        List<IBuilding> buildings = colony.getServerBuildingManager().getBuildings().values().stream()
                .sorted(Comparator.comparingLong(b -> b.getPosition().asLong())).toList();
        List<IBuilding> warehouses = buildings.stream()
                .filter(MineColoniesGateway::isWarehouse).toList();
        Map<ItemStorage, MutableRequirement> rows = new LinkedHashMap<>();
        int activeBuilders = 0;

        for (IBuilding building : buildings) {
            if (!isBuilder(building)) continue;
            BuildingResourcesModule module = building.getModule(BuildingResourcesModule.class);
            if (module == null || module.getNeededResources().isEmpty()) continue;

            String builderName = building.getAllAssignedCitizen().stream()
                    .findFirst().map(citizen -> citizen.getName()).orElse("Builder");
            String constructionName = building.getBuildingDisplayName();
            if (building instanceof AbstractBuildingStructureBuilder structure && structure.getWorkOrder() != null) {
                constructionName = structure.getWorkOrder().getDisplayName().getString().replace('\n', ' ');
            }

            for (BuildingBuilderResource wanted : module.getNeededResources().values()) {
                ItemStack stack = wanted.getItemStack();
                ItemStorage key = new ItemStorage(stack, false, false);
                MutableRequirement row = rows.computeIfAbsent(key, ignored -> new MutableRequirement(stack));
                row.required += wanted.getAmount();
                row.sources.add(new RequestSource(builderName, constructionName,
                        building.getBuildingLevel(), building.getPosition().asLong(), wanted.getAmount()));
            }
            activeBuilders++;
        }

        List<ResourceRequirement> resources = new ArrayList<>();
        List<SupplyPlanner.Demand<ItemStorage>> demands = new ArrayList<>();
        for (Map.Entry<ItemStorage, MutableRequirement> entry : rows.entrySet()) {
            MutableRequirement row = entry.getValue();
            Predicate<ItemStack> matches = candidate -> entry.getKey().equals(new ItemStorage(candidate, false, false));
            int atBuilders = row.sources.stream().mapToLong(RequestSource::buildingPosition).distinct()
                    .mapToObj(p -> colony.getServerBuildingManager().getBuilding(BlockPos.of(p)))
                    .filter(Objects::nonNull).mapToInt(b -> countAtBuilder(b, matches)).sum();
            int available = warehouses.stream().mapToInt(w -> InventoryUtils.getCountFromBuilding(w, matches)).sum();
            int missing = ResourceMath.missing(row.required, atBuilders, available, row.inTransit);
            ResourceStatus status = missing > 0 ? ResourceStatus.MISSING
                    : row.inTransit > 0 ? ResourceStatus.IN_DELIVERY : ResourceStatus.AVAILABLE;
            resources.add(new ResourceRequirement(BuiltInRegistries.ITEM.getKey(row.stack.getItem()).toString(),
                    row.stack.getHoverName().getString(), row.required, atBuilders, available, row.inTransit,
                    missing, status, List.copyOf(row.sources)));
            demands.add(new SupplyPlanner.Demand<>(entry.getKey(), Math.max(0, row.required - atBuilders),
                    row.sources.stream().map(RequestSource::buildingName).distinct()
                            .reduce((a, b) -> a + ", " + b).orElse(row.stack.getHoverName().getString())));
        }
        resources.sort(Comparator.comparingInt(ResourceRequirement::missing).reversed()
                .thenComparing(ResourceRequirement::displayName, String.CASE_INSENSITIVE_ORDER));
        List<IBuilding> workshops = buildings.stream()
                .filter(b -> !isBuilder(b) && !isWarehouse(b) && b.getBuildingLevel() > 0)
                .filter(b -> !b.getModules(ICraftingBuildingModule.class).isEmpty()).toList();
        List<KnownRecipe> knownRecipes = collectRecipes(workshops);
        SupplyPlanner<ItemStorage> planner = new SupplyPlanner<>(
                item -> countSupplyStock(item, warehouses, workshops), item -> findRecipe(item, knownRecipes));
        SupplyPlanner.Result<ItemStorage> plan = planner.plan(demands);
        List<SupplyRequirement> supplies = plan.materials().stream().map(material -> {
            ItemStack stack = material.item().getItemStack();
            return new SupplyRequirement(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.getHoverName().getString(), material.required(), material.allocatedStock(),
                    material.missing(), material.usedBy());
        }).sorted(Comparator.comparingInt(SupplyRequirement::missing).reversed()
                .thenComparing(SupplyRequirement::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
        return new ColonyResourceSummary(colony.getID(), colony.getName(), activeBuilders,
                colony.getWorkManager().getWorkOrders().size(),
                System.currentTimeMillis(), List.copyOf(resources), supplies, plan.limited());
    }

    private record KnownRecipe(IBuilding workshop, IRecipeStorage recipe) {}

    private static List<KnownRecipe> collectRecipes(List<IBuilding> workshops) {
        List<KnownRecipe> recipes = new ArrayList<>();
        var manager = IMinecoloniesAPI.getInstance().getColonyManager().getRecipeManager();
        for (IBuilding workshop : workshops) {
            for (ICraftingBuildingModule module : workshop.getModules(ICraftingBuildingModule.class)) {
                for (var token : module.getRecipes()) {
                    if (module.isDisabled(token)) continue;
                    IRecipeStorage recipe = manager.getRecipe(token);
                    if (recipe != null) recipes.add(new KnownRecipe(workshop, recipe));
                }
            }
        }
        return recipes;
    }

    private static Optional<SupplyPlanner.Recipe<ItemStorage>> findRecipe(ItemStorage target,
            List<KnownRecipe> knownRecipes) {
        Predicate<ItemStack> matches = stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, target.getItemStack());
        // Stable workshop order, then learned-recipe order. This is a forecast, not
        // a claim that the request system will choose this exact production route.
        for (KnownRecipe known : knownRecipes) {
            IRecipeStorage recipe = known.recipe();
            if (!matches.test(recipe.getPrimaryOutput())) {
                if (recipe.getAlternateOutputs().stream().noneMatch(matches)) continue;
                // Create a local copy for the desired Domum/multi-output variant.
                // Do not call getFirstRecipe: it can register recipes globally.
                recipe = recipe.getClassicForMultiOutput(matches);
            }
            if (recipe == null || recipe.getPrimaryOutput().getCount() <= 0) continue;
            List<SupplyPlanner.Ingredient<ItemStorage>> inputs = new ArrayList<>();
            for (ItemStorage input : recipe.getCleanedInput()) {
                if (input.isEmpty()) continue;
                ItemStack stack = input.getItemStack();
                boolean reusable = recipe.getCraftingTools().stream().anyMatch(tool -> tool.is(stack.getItem()));
                ItemStack remainder = stack.getCraftingRemainingItem();
                if (reusable || (!remainder.isEmpty() && remainder.is(stack.getItem()))) continue;
                inputs.add(new SupplyPlanner.Ingredient<>(new ItemStorage(stack.copyWithCount(1), false, false),
                        input.getAmount()));
            }
            String source = known.workshop().getBuildingDisplayName() + " ("
                    + known.workshop().getPosition().toShortString() + ") → "
                    + target.getItemStack().getHoverName().getString();
            return Optional.of(new SupplyPlanner.Recipe<>(recipe.getPrimaryOutput().getCount(), inputs, source));
        }
        return Optional.empty();
    }

    private static int countSupplyStock(ItemStorage item, List<IBuilding> warehouses, List<IBuilding> workshops) {
        Predicate<ItemStack> matches = stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, item.getItemStack());
        long count = 0;
        for (IBuilding warehouse : warehouses) count += InventoryUtils.getCountFromBuilding(warehouse, matches);
        for (IBuilding workshop : workshops) {
            // getCountFromBuilding includes the hut and its racks; don't add the hut twice.
            count += InventoryUtils.getCountFromBuilding(workshop, matches);
            for (var citizen : workshop.getAllAssignedCitizen()) {
                count += InventoryUtils.getItemCountInItemHandler(citizen.getInventory(), matches);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    static IColony findAccessibleColony(ServerPlayer player, int requestedId) {
        return IMinecoloniesAPI.getInstance().getColonyManager().getColonies(player.level()).stream()
                .filter(c -> requestedId < 0 || c.getID() == requestedId)
                .filter(c -> c.getPermissions().isColonyMember(player))
                .min(Comparator.comparingLong(c -> c.getDistanceSquared(player.blockPosition()))).orElse(null);
    }

    private static boolean isBuilder(IBuilding building) {
        return "builder".equals(building.getBuildingType().getRegistryName().getPath());
    }

    private static boolean isWarehouse(IBuilding building) {
        return "warehouse".equals(building.getBuildingType().getRegistryName().getPath());
    }

    private static int countAtBuilder(IBuilding building, Predicate<ItemStack> matches) {
        int count = InventoryUtils.getCountFromBuilding(building, matches);
        for (var citizen : building.getAllAssignedCitizen()) {
            count += InventoryUtils.getItemCountInItemHandler(citizen.getInventory(), matches);
        }
        return count;
    }

    private static final class MutableRequirement {
        private final ItemStack stack;
        private int required;
        private int inTransit;
        private final List<RequestSource> sources = new ArrayList<>();
        private MutableRequirement(ItemStack stack) { this.stack = stack.copyWithCount(1); }
    }
}
