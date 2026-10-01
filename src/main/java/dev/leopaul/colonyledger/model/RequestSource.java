package dev.leopaul.colonyledger.model;

public record RequestSource(String builderName, String buildingName, int buildingLevel,
                            long buildingPosition, int requiredQuantity) {}
