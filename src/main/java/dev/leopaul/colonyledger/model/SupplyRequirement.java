package dev.leopaul.colonyledger.model;

import java.util.List;

/** A terminal ingredient (or an uncraftable block) in the estimated supply plan. */
public record SupplyRequirement(String itemId, String displayName, int requiredTotal,
        int allocatedStock, int missing, List<String> usedBy) {
    /** Keep stock allocation in the forecast, but only display actionable shortages. */
    public boolean needsSupply() { return missing > 0; }
}
