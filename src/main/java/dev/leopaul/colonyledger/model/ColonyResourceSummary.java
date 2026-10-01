package dev.leopaul.colonyledger.model;

import java.util.List;

public record ColonyResourceSummary(int colonyId, String colonyName, int activeBuilderCount,
        int activeConstructionCount, long generatedAt, List<ResourceRequirement> resources) {
    public static ColonyResourceSummary empty(String message) {
        return new ColonyResourceSummary(-1, message, 0, 0, System.currentTimeMillis(), List.of());
    }
}
