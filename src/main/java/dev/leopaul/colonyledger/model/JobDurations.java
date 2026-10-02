package dev.leopaul.colonyledger.model;

/** All durations are observed server ticks, not elapsed real-world time. */
public record JobDurations(long idle, long working, long stuck) {
    public static final JobDurations EMPTY = new JobDurations(0, 0, 0);
    public long total() { return idle + working + stuck; }
    public JobDurations plus(JobDurations other) {
        return new JobDurations(idle + other.idle, working + other.working, stuck + other.stuck);
    }

    /** Tenths of a percent; largest-remainder rounding makes the sum exactly 100.0%. */
    public int[] percentages() {
        long total = total();
        int[] result = new int[3];
        if (total == 0) return result;
        long[] values = {idle, working, stuck};
        double[] remainder = new double[3];
        int assigned = 0;
        for (int i = 0; i < 3; i++) {
            double exact = values[i] * 1000.0 / total;
            result[i] = (int) exact;
            remainder[i] = exact - result[i];
            assigned += result[i];
        }
        while (assigned++ < 1000) {
            int best = 0;
            for (int i = 1; i < 3; i++) if (remainder[i] > remainder[best]) best = i;
            result[best]++;
            remainder[best] = -1;
        }
        return result;
    }
}
