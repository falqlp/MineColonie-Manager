package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.*;
import java.util.List;

public final class HousingAnalysisService {
    private HousingAnalysisService() {}
    public static HousingSummary analyze(int colonyId, String colonyName, boolean canManage, boolean allowManualSwaps,
            List<ResidenceInfo> homes, List<CitizenHousingInfo> citizens, double minimumGain,
            int maxCitizens, int maxSuggestions, String messageKey) {
        boolean limited = citizens.size() > maxCitizens;
        return new HousingSummary(colonyId, colonyName, canManage, allowManualSwaps, homes, citizens,
                canManage && !limited ? HousingOptimizationService.suggest(citizens, homes, minimumGain, maxSuggestions) : List.of(),
                limited, messageKey);
    }
}
