package dev.leopaul.colonyledger.service;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Read-only, deterministic ingredient forecast. No game or inventory mutations. */
public final class SupplyPlanner<K> {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_EXPANSIONS = 4096;

    public record Demand<K>(K item, int count, String source) {}
    public record Ingredient<K>(K item, int count) {}
    public record Recipe<K>(int outputCount, List<Ingredient<K>> ingredients, String workshop) {
        public Recipe {
            if (outputCount <= 0) throw new IllegalArgumentException("Recipe output must be positive");
            ingredients = List.copyOf(ingredients);
        }
    }
    public record Material<K>(K item, int required, int allocatedStock, int missing, List<String> usedBy) {}
    public record Result<K>(List<Material<K>> materials, boolean limited) {}

    private final ToIntFunction<K> stockLookup;
    private final Function<K, Optional<Recipe<K>>> recipeLookup;
    private final Map<K, Integer> stock = new HashMap<>();
    private final Map<K, Integer> surplus = new HashMap<>();
    private final Map<K, Optional<Recipe<K>>> recipes = new HashMap<>();
    private final Map<K, MutableMaterial> materials = new LinkedHashMap<>();
    private int expansions;
    private boolean limited;
    private boolean planned;

    public SupplyPlanner(ToIntFunction<K> stockLookup, Function<K, Optional<Recipe<K>>> recipeLookup) {
        this.stockLookup = stockLookup;
        this.recipeLookup = recipeLookup;
    }

    public Result<K> plan(List<Demand<K>> builderDemands) {
        if (planned) throw new IllegalStateException("Use a new planner for each snapshot");
        planned = true;
        // Reserve finished blocks for ALL builders before allocating stock to recipes.
        // A block requested directly must not also be counted as a free ingredient.
        List<Demand<K>> remaining = new ArrayList<>();
        for (Demand<K> demand : builderDemands) {
            int count = Math.max(0, demand.count());
            int used = takeStock(demand.item(), count);
            if (count > used) remaining.add(new Demand<>(demand.item(), count - used, demand.source()));
        }
        for (Demand<K> demand : remaining) {
            require(demand.item(), demand.count(), demand.source(), new HashSet<>(), 0);
        }
        List<Material<K>> result = new ArrayList<>();
        materials.forEach((item, row) -> result.add(new Material<>(item, row.required, row.stock,
                row.missing, List.copyOf(row.sources))));
        return new Result<>(List.copyOf(result), limited);
    }

    private void require(K item, int count, String source, Set<K> path, int depth) {
        if (count <= 0) return;
        int fromStock = takeStock(item, count);
        int fromSurplus = Math.min(count - fromStock, surplus.getOrDefault(item, 0));
        surplus.put(item, surplus.getOrDefault(item, 0) - fromSurplus);
        int deficit = count - fromStock - fromSurplus;
        if (deficit == 0) {
            // Planned batch surplus is not a new raw-material requirement.
            addMaterial(item, fromStock, fromStock, 0, source);
            return;
        }
        if (depth >= MAX_DEPTH || expansions++ >= MAX_EXPANSIONS || path.contains(item)) {
            limited = true;
            addMaterial(item, fromStock + deficit, fromStock, deficit, source);
            return;
        }
        Optional<Recipe<K>> known = recipes.computeIfAbsent(item, recipeLookup);
        if (known.isEmpty()) {
            addMaterial(item, fromStock + deficit, fromStock, deficit, source);
            return;
        }
        // Keep existing intermediate items visible, but expand only the missing part.
        addMaterial(item, fromStock, fromStock, 0, source);
        Recipe<K> recipe = known.get();
        int batches = (int) (((long) deficit + recipe.outputCount() - 1) / recipe.outputCount());
        path.add(item);
        try {
            for (Ingredient<K> ingredient : recipe.ingredients()) {
                int needed = bounded((long) Math.max(0, ingredient.count()) * batches);
                require(ingredient.item(), needed, recipe.workshop(), path, depth + 1);
            }
        } finally {
            path.remove(item);
        }
        // Credit the unused output only after expanding inputs (prevents circular credit).
        int extra = bounded((long) batches * recipe.outputCount() - deficit);
        surplus.put(item, bounded((long) surplus.getOrDefault(item, 0) + extra));
    }

    private int takeStock(K item, int count) {
        int available = stock.computeIfAbsent(item, key -> Math.max(0, stockLookup.applyAsInt(key)));
        int used = Math.min(count, available);
        stock.put(item, available - used);
        return used;
    }

    private void addMaterial(K item, int count, int allocated, int missing, String source) {
        if (count <= 0) return;
        MutableMaterial row = materials.computeIfAbsent(item, ignored -> new MutableMaterial());
        row.required = bounded((long) row.required + count);
        row.stock = bounded((long) row.stock + allocated);
        row.missing = bounded((long) row.missing + missing);
        if (row.sources.size() < 16) row.sources.add(source);
    }

    private int bounded(long value) {
        if (value > Integer.MAX_VALUE) limited = true;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static final class MutableMaterial {
        private int required;
        private int stock;
        private int missing;
        private final Set<String> sources = new LinkedHashSet<>();
    }
}
