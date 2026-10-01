package dev.leopaul.colonyledger.cache;

import dev.leopaul.colonyledger.model.ColonyResourceSummary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ColonySummaryCache {
    private static final long TTL_MS = 5_000L;
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private ColonySummaryCache() {}

    public static ColonyResourceSummary get(String key, Supplier<ColonyResourceSummary> loader) {
        long now = System.currentTimeMillis();
        Entry current = ENTRIES.get(key);
        if (current != null && now - current.createdAt < TTL_MS) return current.summary;
        ColonyResourceSummary loaded = loader.get();
        ENTRIES.put(key, new Entry(now, loaded));
        return loaded;
    }

    private record Entry(long createdAt, ColonyResourceSummary summary) {}
}
