package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class JobHistoryTest {
    private static int checks;
    public static void main(String[] args) {
        minecraftPeriods();
        percentagesAndWindows();
        unobservedGapsAndDuplicateSamples();
        retentionAndLifetime();
        persistenceDoesNotExtrapolate();
        System.out.println("JobHistory: " + checks + " regression checks passed.");
    }
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void minecraftPeriods() {
        equal(24_000L, JobPeriod.ONE_DAY.ticks());
        equal(72_000L, JobPeriod.THREE_DAYS.ticks());
        equal(168_000L, JobPeriod.SEVEN_DAYS.ticks());
        equal(720_000L, JobPeriod.THIRTY_DAYS.ticks());
        equal(0L, JobPeriod.ALL.ticks());
    }
    private static void percentagesAndWindows() {
        JobHistory history = new JobHistory();
        for (int i = 0; i < 1200; i++) history.record(i,
                i < 600 ? ObservedJobStatus.IDLE : i < 900 ? ObservedJobStatus.WORKING : ObservedJobStatus.STUCK);
        JobDurations day = history.durations(24_000, 24_000);
        equal(new JobDurations(12_000, 6000, 6000), day);
        equal("[500, 250, 250]", Arrays.toString(day.percentages()));
        equal(24_000L, history.durations(24_000, 72_000).total());
        equal(6000L, history.durations(24_000, 6000).stuck());
        equal(23_990L, history.durations(24_010, 24_000).total());
        equal(1000, Arrays.stream(new JobDurations(1, 1, 1).percentages()).sum());
        equal(0, Arrays.stream(JobDurations.EMPTY.percentages()).sum());
    }
    private static void unobservedGapsAndDuplicateSamples() {
        JobHistory history = new JobHistory();
        equal(0L, history.durations(0, 24_000).total());
        history.record(0, ObservedJobStatus.IDLE);
        history.record(0, ObservedJobStatus.STUCK);
        history.record(10, ObservedJobStatus.WORKING);
        equal(new JobDurations(20, 20, 0), history.durations(220, 24_000));
        equal(40L, history.durations(220, 0).total());
        equal(0L, history.durations(50_000, 24_000).total());
        JobHistory restored = new JobHistory(history.samples(), history.lastBucket(), history.lifetime());
        equal(history.durations(220, 24_000), restored.durations(220, 24_000));
    }
    private static void retentionAndLifetime() {
        JobHistory history = new JobHistory();
        int count = JobHistory.CAPACITY + 1000;
        for (int i = 0; i < count; i++) history.record(i, ObservedJobStatus.WORKING);
        equal(720_000L, history.durations((long) count * 20, JobPeriod.THIRTY_DAYS.ticks()).total());
        equal((long) count * 20, history.durations((long) count * 20, 0).total());
        history.record(count + JobHistory.CAPACITY, ObservedJobStatus.STUCK);
        equal(20L, history.durations((long) (count + JobHistory.CAPACITY + 1) * 20, 720_000).total());
    }
    private static void persistenceDoesNotExtrapolate() {
        JobHistorySavedData data = new JobHistorySavedData();
        data.observe(1, 5, 20, ObservedJobStatus.WORKING);
        equal(0L, data.durations(1, 5, 20, JobPeriod.ALL).total());
        data.observe(1, 5, 40, ObservedJobStatus.STUCK);
        equal(new JobDurations(0, 20, 0), data.durations(1, 5, 40, JobPeriod.ALL));
        data.observe(1, 5, 60, ObservedJobStatus.IDLE);
        data.observe(1, 5, 200, ObservedJobStatus.WORKING);
        equal(40L, data.durations(1, 5, 200, JobPeriod.ALL).total());
        data.observe(2, 5, 20, ObservedJobStatus.IDLE);
        data.observe(2, 5, 40, ObservedJobStatus.IDLE);
        equal(new JobDurations(20, 0, 0), data.durations(2, 5, 40, JobPeriod.ALL));
        CompoundTag encoded = data.save(new CompoundTag(), null);
        JobHistorySavedData loaded = JobHistorySavedData.load(encoded, null);
        equal(data.durations(1, 5, 200, JobPeriod.ALL), loaded.durations(1, 5, 200, JobPeriod.ALL));
        loaded.observe(1, 5, 10_000, ObservedJobStatus.IDLE);
        equal(40L, loaded.durations(1, 5, 10_000, JobPeriod.ALL).total());
        loaded.observe(1, 5, 10_020, ObservedJobStatus.STUCK);
        equal(new JobDurations(20, 20, 20), loaded.durations(1, 5, 10_020, JobPeriod.ALL));
    }
}
