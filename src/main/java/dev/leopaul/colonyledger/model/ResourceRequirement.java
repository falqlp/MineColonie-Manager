package dev.leopaul.colonyledger.model;

import java.util.List;

public record ResourceRequirement(String itemId, String displayName, int requiredTotal,
        int builderInventory, int colonyAvailable, int inTransit, int missing,
        ResourceStatus status, List<RequestSource> requestSources) {}
