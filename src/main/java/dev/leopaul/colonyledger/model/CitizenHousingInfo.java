package dev.leopaul.colonyledger.model;

/** workerLevel is the highest current skill, not the work hut level or a native job level. */
public record CitizenHousingInfo(int citizenId, String citizenName, boolean child, String jobName,
        int workerLevel, String workplaceName, HousingPosition workplacePosition,
        String residenceName, HousingPosition residencePosition, int residenceLevel,
        int residenceCapacity, int residenceOccupancy, int skillCap, double homeToWorkDistance,
        HousingStatus status, boolean housingLocked, HousingPosition preferredResidence,
        boolean excludedFromOptimization) {
    public boolean hasCommute() { return residencePosition != null && workplacePosition != null; }
    // A level-5 home normally has the game's absolute cap (99), not a housing-specific restriction.
    public boolean skillCapped() { return residencePosition != null && skillCap < 99 && workerLevel >= skillCap; }
}
