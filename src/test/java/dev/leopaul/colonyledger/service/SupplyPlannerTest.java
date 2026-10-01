package dev.leopaul.colonyledger.service;

import java.util.*;
import dev.leopaul.colonyledger.service.SupplyPlanner.*;

/** Dependency-free regression checks, also executable with Java 21 directly. */
public final class SupplyPlannerTest {
    private static int checks;

    public static void main(String[] args) {
        unknownRecipeKeepsOriginalBlock();
        batchesRoundUpAndDeductStock();
        followsWorkshopChains();
        reservesFinishedBlocksBeforeIngredients();
        sharesStockAndBatchSurplus();
        noMissingBlocksNeedsNoMaterials();
        usesIntermediateStockBeforeCrafting();
        cyclesAndDeepChainsAreBounded();
        largeQuantitiesDoNotOverflow();
        System.out.println("SupplyPlanner: " + checks + " regression checks passed.");
    }

    @SafeVarargs
    private static Result<String> plan(Map<String, Integer> stocks, Map<String, Recipe<String>> recipes,
            Demand<String>... demands) {
        return new SupplyPlanner<String>(item -> stocks.getOrDefault(item, 0),
                item -> Optional.ofNullable(recipes.get(item))).plan(List.of(demands));
    }

    private static Recipe<String> recipe(int output, String input, int count) {
        return new Recipe<>(output, List.of(new Ingredient<>(input, count)), "Workshop -> " + input);
    }

    private static Demand<String> demand(String item, int count) {
        return new Demand<>(item, count, "Builder");
    }

    private static Material<String> row(Result<String> result, String item) {
        return result.materials().stream().filter(r -> r.item().equals(item)).findFirst().orElseThrow();
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void unknownRecipeKeepsOriginalBlock() {
        Result<String> result = plan(Map.of("dirt", 4), Map.of(), demand("dirt", 10));
        equal(6, row(result, "dirt").missing());
        equal(6, row(result, "dirt").required());
        equal(false, result.limited());
    }

    private static void batchesRoundUpAndDeductStock() {
        // Seven stairs; two batches of four stairs from six planks per batch.
        Result<String> result = plan(Map.of("planks", 5), Map.of("stairs", recipe(4, "planks", 6)),
                demand("stairs", 7));
        equal(12, row(result, "planks").required());
        equal(5, row(result, "planks").allocatedStock());
        equal(7, row(result, "planks").missing());
        equal(1, result.materials().size());
    }

    private static void followsWorkshopChains() {
        Result<String> result = plan(Map.of(), Map.of("stairs", recipe(4, "planks", 6),
                "planks", recipe(4, "log", 1)), demand("stairs", 8));
        equal(3, row(result, "log").missing());
        equal(1, result.materials().size());
    }

    private static void reservesFinishedBlocksBeforeIngredients() {
        // These four planks belong to a direct builder requirement, not the stairs.
        Map<String, Recipe<String>> recipes = Map.of("stairs", recipe(4, "planks", 6),
                "planks", recipe(4, "log", 1));
        Result<String> result = plan(Map.of("planks", 4), recipes, demand("stairs", 4), demand("planks", 4));
        Result<String> reversed = plan(Map.of("planks", 4), recipes, demand("planks", 4), demand("stairs", 4));
        equal(2, row(result, "log").missing());
        equal(result.materials(), reversed.materials());
    }

    private static void sharesStockAndBatchSurplus() {
        // Two one-plank jobs share a single four-plank batch (and the same stock).
        Map<String, Recipe<String>> recipes = Map.of("a", recipe(1, "planks", 1),
                "b", recipe(1, "planks", 1), "planks", recipe(4, "log", 1));
        Result<String> result = plan(Map.of(), recipes, demand("a", 1), demand("b", 1));
        equal(1, row(result, "log").missing());
        Result<String> available = plan(Map.of("planks", 1),
                Map.of("a", recipe(1, "planks", 1), "b", recipe(1, "planks", 1)),
                demand("a", 1), demand("b", 1));
        equal(2, row(available, "planks").required());
        equal(1, row(available, "planks").allocatedStock());
        equal(1, row(available, "planks").missing());
    }

    private static void noMissingBlocksNeedsNoMaterials() {
        Result<String> result = plan(Map.of("stairs", 8), Map.of("stairs", recipe(4, "planks", 6)),
                demand("stairs", 8));
        equal(0, result.materials().size());
        equal(0, plan(Map.of(), Map.of(), demand("stairs", 0)).materials().size());
    }

    private static void usesIntermediateStockBeforeCrafting() {
        Result<String> result = plan(Map.of("planks", 2), Map.of("stairs", recipe(4, "planks", 6),
                "planks", recipe(4, "log", 1)), demand("stairs", 4));
        equal(2, row(result, "planks").allocatedStock());
        equal(0, row(result, "planks").missing());
        equal(1, row(result, "log").missing());
    }

    private static void cyclesAndDeepChainsAreBounded() {
        Result<String> cyclic = plan(Map.of(), Map.of("a", recipe(1, "b", 1),
                "b", recipe(1, "a", 1)), demand("a", 2));
        equal(true, cyclic.limited());
        equal(2, row(cyclic, "a").missing());
        Map<String, Recipe<String>> deep = new HashMap<>();
        for (int i = 0; i < 25; i++) deep.put("item" + i, recipe(1, "item" + (i + 1), 1));
        Result<String> limited = plan(Map.of(), deep, demand("item0", 1));
        equal(true, limited.limited());
        equal(1, limited.materials().size());
    }

    private static void largeQuantitiesDoNotOverflow() {
        Result<String> result = plan(Map.of(), Map.of("a", recipe(1, "stone", 8)),
                demand("a", Integer.MAX_VALUE));
        equal(Integer.MAX_VALUE, row(result, "stone").missing());
        equal(true, result.limited());
    }
}
