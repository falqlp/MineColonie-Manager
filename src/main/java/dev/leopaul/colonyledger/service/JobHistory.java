package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.JobDurations;
import dev.leopaul.colonyledger.model.ObservedJobStatus;
import java.util.Arrays;

/** One byte per observed second; bounded rolling history of 30 Minecraft days. */
public final class JobHistory {
    public static final int SAMPLE_TICKS = 20;
    public static final int CAPACITY = 720_000 / SAMPLE_TICKS;
    private final byte[] samples;
    private long lastBucket;
    private final long[] lifetime;

    public JobHistory() {
        samples = new byte[CAPACITY];
        lastBucket = -1;
        lifetime = new long[3];
    }
    public JobHistory(byte[] samples, long lastBucket, long[] lifetime) {
        this.samples = Arrays.copyOf(samples, CAPACITY);
        this.lastBucket = lastBucket;
        this.lifetime = Arrays.copyOf(lifetime, 3);
    }

    /** Bucket b represents [b*20, (b+1)*20); skipped buckets remain unobserved. */
    public void record(long bucket, ObservedJobStatus status) {
        if (bucket < 0 || bucket <= lastBucket) return;
        if (lastBucket < 0 || bucket - lastBucket >= CAPACITY) {
            Arrays.fill(samples, (byte) 0);
        } else {
            for (long b = lastBucket + 1; b <= bucket; b++) samples[index(b)] = 0;
        }
        samples[index(bucket)] = (byte) (status.ordinal() + 1);
        lifetime[status.ordinal()] += SAMPLE_TICKS;
        lastBucket = bucket;
    }

    public JobDurations durations(long now, long windowTicks) {
        if (windowTicks == 0) return new JobDurations(lifetime[0], lifetime[1], lifetime[2]);
        long cutoff = Math.max(0, now - windowTicks);
        long first = Math.max(cutoff / SAMPLE_TICKS, Math.max(0, lastBucket - CAPACITY + 1));
        long last = Math.min(lastBucket, (now - 1) / SAMPLE_TICKS);
        long[] counts = new long[3];
        for (long b = first; b <= last; b++) {
            int state = samples[index(b)] - 1;
            if (state < 0 || state >= 3) continue;
            long overlap = Math.min(now, (b + 1) * SAMPLE_TICKS) - Math.max(cutoff, b * SAMPLE_TICKS);
            if (overlap > 0) counts[state] += overlap;
        }
        return new JobDurations(counts[0], counts[1], counts[2]);
    }

    private static int index(long bucket) { return (int) (bucket % CAPACITY); }
    public byte[] samples() { return samples.clone(); }
    public long lastBucket() { return lastBucket; }
    public long[] lifetime() { return lifetime.clone(); }
}
