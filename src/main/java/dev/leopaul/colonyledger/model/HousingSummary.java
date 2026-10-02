package dev.leopaul.colonyledger.model;

import java.util.List;

public record HousingSummary(int colonyId, String colonyName, boolean canManage, boolean allowManualSwaps,
        List<ResidenceInfo> residences, List<CitizenHousingInfo> citizens,
        List<HousingSuggestion> suggestions, boolean analysisLimited, String messageKey) {
    public HousingSummary {
        residences = List.copyOf(residences); citizens = List.copyOf(citizens); suggestions = List.copyOf(suggestions);
    }
    public static HousingSummary empty(String message) {
        return new HousingSummary(-1, "", false, false, List.of(), List.of(), List.of(), false, message);
    }
    public long commuters() { return citizens.stream().filter(CitizenHousingInfo::hasCommute).count(); }
    public double totalDistance() { return citizens.stream().filter(CitizenHousingInfo::hasCommute).mapToDouble(CitizenHousingInfo::homeToWorkDistance).sum(); }
    public double averageDistance() { return commuters() == 0 ? 0 : totalDistance() / commuters(); }
    public int freePlaces() { return residences.stream().mapToInt(ResidenceInfo::freePlaces).sum(); }
}
