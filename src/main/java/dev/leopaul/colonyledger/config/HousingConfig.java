package dev.leopaul.colonyledger.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned settings; automatic reassignment is deliberately not implemented in the MVP. */
public final class HousingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, ALLOW_MANUAL_SWAPS;
    public static final ModConfigSpec.DoubleValue GOOD, ACCEPTABLE, FAR, MINIMUM_GAIN;
    public static final ModConfigSpec.IntValue MAX_ANALYSIS_CITIZENS, MAX_SUGGESTIONS;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("housing");
        ENABLED = b.comment("Enable housing inspection and suggestions.").define("enabled", true);
        ALLOW_MANUAL_SWAPS = b.comment("False = suggestions only. Never performs automatic moves.").define("allowManualSwaps", true);
        GOOD = b.defineInRange("goodDistance", 50.0, 0.0, 1000000.0);
        ACCEPTABLE = b.defineInRange("acceptableDistance", 150.0, 0.0, 1000000.0);
        FAR = b.defineInRange("farDistance", 300.0, 0.0, 1000000.0);
        MINIMUM_GAIN = b.comment("Minimum combined X/Z distance saved by a pair swap, in blocks.").defineInRange("minimumSwapGain", 10.0, 0.01, 1000000.0);
        MAX_ANALYSIS_CITIZENS = b.comment("Above this limit, still show everyone but skip quadratic pair analysis.").defineInRange("maxAnalysisCitizens", 500, 2, 1000);
        MAX_SUGGESTIONS = b.defineInRange("maxSuggestions", 20, 1, 100);
        b.pop(); SPEC = b.build();
    }
    private HousingConfig() {}
    public static double acceptable() { return Math.max(GOOD.get(), ACCEPTABLE.get()); }
    public static double far() { return Math.max(acceptable(), FAR.get()); }
}
