package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.*;
import java.util.*;

/** MVP: polynomial pair analysis, not a global minimum-cost assignment solver. */
public final class HousingOptimizationService {
    private HousingOptimizationService() {}
    public static Optional<HousingSuggestion> swap(CitizenHousingInfo a, CitizenHousingInfo b,
            Map<HousingPosition, ResidenceInfo> homes, double minimumGain) {
        if (!a.hasCommute() || !b.hasCommute() || a.citizenId() == b.citizenId()
                || a.child() || b.child() || a.housingLocked() || b.housingLocked()
                || a.excludedFromOptimization() || b.excludedFromOptimization()
                || a.residencePosition().equals(b.residencePosition())) return Optional.empty();
        ResidenceInfo ah = homes.get(a.residencePosition()), bh = homes.get(b.residencePosition());
        if (!compatible(a, ah, bh) || !compatible(b, bh, ah)) return Optional.empty();
        double afterA = bh.position().horizontalDistance(a.workplacePosition());
        double afterB = ah.position().horizontalDistance(b.workplacePosition());
        // Do not sacrifice one citizen's commute just to benefit the other.
        if (afterA > a.homeToWorkDistance() + 1e-6 || afterB > b.homeToWorkDistance() + 1e-6) return Optional.empty();
        var suggestion = new HousingSuggestion(a.citizenId(), b.citizenId(), ah.position(), bh.position(),
                a.homeToWorkDistance(), b.homeToWorkDistance(), afterA, afterB);
        return suggestion.gain() + 1e-6 >= minimumGain ? Optional.of(suggestion) : Optional.empty();
    }
    private static boolean compatible(CitizenHousingInfo citizen, ResidenceInfo from, ResidenceInfo to) {
        return from != null && to != null && from.assignable() && to.assignable()
                && !from.housingLocked() && !to.housingLocked() && !from.overloaded() && !to.overloaded()
                && from.occupants().contains(citizen.citizenId()) && to.capacity() > 0
                && to.skillCap() >= from.skillCap()
                && (citizen.preferredResidence() == null || citizen.preferredResidence().equals(to.position()));
    }
    public static List<HousingSuggestion> suggest(List<CitizenHousingInfo> citizens, List<ResidenceInfo> homes,
            double minimumGain, int limit) {
        Map<HousingPosition, ResidenceInfo> byPosition = new HashMap<>();
        homes.forEach(home -> byPosition.put(home.position(), home));
        List<HousingSuggestion> candidates = new ArrayList<>();
        for (int i = 0; i < citizens.size(); i++) for (int j = i + 1; j < citizens.size(); j++)
            swap(citizens.get(i), citizens.get(j), byPosition, minimumGain).ifPresent(candidates::add);
        candidates.sort(Comparator.comparingDouble(HousingSuggestion::gain).reversed()
                .thenComparingInt(HousingSuggestion::firstCitizenId).thenComparingInt(HousingSuggestion::secondCitizenId));
        Set<Integer> used = new HashSet<>(); List<HousingSuggestion> result = new ArrayList<>();
        for (var candidate : candidates) {
            if (used.contains(candidate.firstCitizenId()) || used.contains(candidate.secondCitizenId())) continue;
            result.add(candidate); used.add(candidate.firstCitizenId()); used.add(candidate.secondCitizenId());
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }
}
