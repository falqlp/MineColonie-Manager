package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import dev.leopaul.colonyledger.cache.ColonySummaryCache;
import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.service.ResourceMath;
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

        List<IBuilding> warehouses = colony.getServerBuildingManager().getBuildings().values().stream()
                .filter(MineColoniesGateway::isWarehouse).toList();
        Map<ItemStorage, MutableRequirement> rows = new LinkedHashMap<>();
        int activeBuilders = 0;

        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
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
        }
        resources.sort(Comparator.comparingInt(ResourceRequirement::missing).reversed()
                .thenComparing(ResourceRequirement::displayName, String.CASE_INSENSITIVE_ORDER));
        return new ColonyResourceSummary(colony.getID(), colony.getName(), activeBuilders,
                colony.getWorkManager().getWorkOrders().size(),
                System.currentTimeMillis(), List.copyOf(resources));
    }

    private static IColony findAccessibleColony(ServerPlayer player, int requestedId) {
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
        int count = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), matches);
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
