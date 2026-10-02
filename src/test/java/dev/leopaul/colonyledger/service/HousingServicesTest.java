package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.*;
import java.util.*;

public final class HousingServicesTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static final HousingPosition A = new HousingPosition(0, 64, 0), B = new HousingPosition(300, 80, 0);
    private static CitizenHousingInfo citizen(int id, HousingPosition home, HousingPosition work, boolean child, boolean locked, boolean excluded) {
        double distance = home == null || work == null ? 0 : home.horizontalDistance(work);
        return new CitizenHousingInfo(id, "Citizen " + id, child, "job.builder", 20, "Work", work,
                "Home", home, 3, 3, 1, 40, distance, home == null ? HousingStatus.NO_HOME : work == null ? HousingStatus.NO_WORK
                : HousingStatus.of(distance, 50, 150, 300), locked, null, excluded);
    }
    private static ResidenceInfo home(HousingPosition position, int cap, int skillCap, boolean assignable, boolean locked, Integer... occupants) {
        return new ResidenceInfo(position, "Home", 3, skillCap, cap, List.of(occupants), assignable, locked);
    }
    public static void main(String[] args) {
        check(A.horizontalDistance(new HousingPosition(3, -100, 4)) == 5, "Euclidean X/Z; ignore Y");
        check(new HousingPosition(Integer.MIN_VALUE, 0, 0).horizontalDistance(new HousingPosition(Integer.MAX_VALUE, 0, 0)) == 4294967295.0, "No int overflow");
        check(HousingStatus.of(50, 50, 150, 300) == HousingStatus.GOOD, "Good inclusive");
        check(HousingStatus.of(50.01, 50, 150, 300) == HousingStatus.ACCEPTABLE, "Above good");
        check(HousingStatus.of(150, 50, 150, 300) == HousingStatus.ACCEPTABLE, "Acceptable inclusive");
        check(HousingStatus.of(150.01, 50, 150, 300) == HousingStatus.FAR, "Above acceptable");
        check(HousingStatus.of(300, 50, 150, 300) == HousingStatus.FAR, "Far inclusive");
        check(HousingStatus.of(300.01, 50, 150, 300) == HousingStatus.VERY_FAR, "Above far");
        check(HousingStatus.of(10, 5, 10, 20) == HousingStatus.ACCEPTABLE, "Custom thresholds");
        CitizenHousingInfo a = citizen(1, A, new HousingPosition(290, 64, 0), false, false, false);
        CitizenHousingInfo b = citizen(2, B, new HousingPosition(10, 64, 0), false, false, false);
        var homes = Map.of(A, home(A, 1, 40, true, false, 1), B, home(B, 1, 40, true, false, 2));
        HousingSuggestion swap = HousingOptimizationService.swap(a, b, homes, 10).orElseThrow();
        check(swap.before() == 580 && swap.after() == 20 && swap.gain() == 560, "Pair gain");
        check(HousingOptimizationService.swap(a, b, homes, 561).isEmpty(), "Minimum gain");
        check(HousingOptimizationService.swap(a, a, homes, 1).isEmpty(), "Same citizen");
        check(HousingOptimizationService.swap(a, citizen(2, A, A, false, false, false), homes, 1).isEmpty(), "Same residence");
        check(HousingOptimizationService.swap(a, citizen(2, B, null, false, false, false), homes, 1).isEmpty(), "No work");
        check(HousingOptimizationService.swap(a, citizen(2, null, A, false, false, false), homes, 1).isEmpty(), "No home");
        check(HousingOptimizationService.swap(a, citizen(2, B, A, true, false, false), homes, 1).isEmpty(), "Child excluded");
        check(HousingOptimizationService.swap(a, citizen(2, B, A, false, true, false), homes, 1).isEmpty(), "Citizen lock extension");
        check(HousingOptimizationService.swap(a, citizen(2, B, A, false, false, true), homes, 1).isEmpty(), "Special job excluded");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 1, 30, true, false, 2)), 1).isEmpty(), "No skill cap downgrade");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 1, 40, false, false, 2)), 1).isEmpty(), "Unbuilt/special home");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 1, 40, true, true, 2)), 1).isEmpty(), "Residence lock extension");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 1, 40, true, false, 2, 3)), 1).isEmpty(), "Over capacity");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 0, 40, true, false, 2)), 1).isEmpty(), "No slots");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A), B, home(B, 1, 40, true, false, 3)), 1).isEmpty(), "Membership mismatch");
        check(HousingOptimizationService.swap(a, b, Map.of(A, homes.get(A)), 1).isEmpty(), "Destroyed residence");
        check(HousingOptimizationService.swap(a, citizen(2, B, B, false, false, false), homes, 1).isEmpty(), "No worsening other citizen");
        List<CitizenHousingInfo> rows = List.of(a, b, citizen(3, B, A, false, false, false));
        List<ResidenceInfo> multiHomes = List.of(homes.get(A), home(B, 3, 40, true, false, 2, 3));
        List<HousingSuggestion> proposals = HousingOptimizationService.suggest(rows, multiHomes, 1, 20);
        check(proposals.size() == 1, "Independent proposals, not duplicate citizens");
        HousingSummary summary = HousingAnalysisService.analyze(1, "Colony", true, true, multiHomes, rows, 1, 500, 20, "");
        check(summary.commuters() == 3 && summary.totalDistance() == 880 && summary.freePlaces() == 1, "Summary denominators and capacity");
        check(HousingAnalysisService.analyze(1, "Colony", true, true, multiHomes, rows, 1, 2, 20, "").analysisLimited(), "Large colony guard");
        check(HousingAnalysisService.analyze(1, "Colony", false, true, multiHomes, rows, 1, 500, 20, "").suggestions().isEmpty(), "No manage permission, no optimization");
        check(!HousingAnalysisService.analyze(1, "Colony", true, false, multiHomes, rows, 1, 500, 20, "").suggestions().isEmpty(), "Suggestions-only still analyzes");
        check(!home(A, 3, 40, true, false, 1).overloaded() && home(A, 3, 40, true, false, 1).freePlaces() == 2, "Underuse");
        check(home(A, 0, 40, false, false).freePlaces() == 0, "Unbuilt not counted as free");
        check(HousingSummary.empty("none").averageDistance() == 0, "Empty summary not NaN");
        CitizenHousingInfo capped = new CitizenHousingInfo(9, "Capped", false, "job", 40, "Work", B, "Home", A, 3, 3, 1, 40, 300, HousingStatus.FAR, false, null, false);
        check(capped.skillCapped(), "Housing progression warning");
        CitizenHousingInfo absoluteCap = new CitizenHousingInfo(9, "Max", false, "job", 99, "Work", B, "Home", A, 5, 5, 1, 99, 300, HousingStatus.FAR, false, null, false);
        check(!absoluteCap.skillCapped(), "Absolute skill maximum not a housing restriction");
        transactions();
        System.out.println("HousingServices: " + checks + " checks passed.");
    }
    private static void transactions() {
        class Port implements HousingAssignmentService.Assignments<Integer, String> {
            final Map<String, Set<Integer>> rooms = new HashMap<>(Map.of("A", new HashSet<>(Set.of(1)), "B", new HashSet<>(Set.of(2))));
            int mutations; int failAt = -1; boolean throwFailure;
            public boolean contains(String home, Integer citizen) { return rooms.get(home).contains(citizen); }
            public boolean remove(String home, Integer citizen) { mutations++; return rooms.get(home).remove(citizen); }
            public boolean assign(String home, Integer citizen) {
                if (++mutations == failAt) { if (throwFailure) throw new IllegalStateException(); return false; }
                if (!rooms.get(home).isEmpty()) return false;
                return rooms.get(home).add(citizen);
            }
        }
        Port success = new Port();
        check(HousingAssignmentService.swap(success, 1, 2, "A", "B") == HousingAssignmentService.Result.SUCCESS, "Swap full residences");
        check(success.contains("B", 1) && success.contains("A", 2), "Assignments updated");
        for (int step : new int[]{3, 4}) for (boolean throwing : new boolean[]{false, true}) {
            Port failing = new Port(); failing.failAt = step; failing.throwFailure = throwing;
            check(HousingAssignmentService.swap(failing, 1, 2, "A", "B") == HousingAssignmentService.Result.ROLLED_BACK, "Rejected/thrown assignment rolled back");
            check(failing.contains("A", 1) && failing.contains("B", 2) && !failing.contains("B", 1) && !failing.contains("A", 2), "No lost or duplicated citizen");
        }
    }
}
