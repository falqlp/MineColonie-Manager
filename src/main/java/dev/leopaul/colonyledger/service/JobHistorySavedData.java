package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class JobHistorySavedData extends SavedData {
    private record Key(int colony, int citizen) {}
    private record Observation(long tick, ObservedJobStatus status) {}
    private static final Factory<JobHistorySavedData> FACTORY =
            new Factory<>(JobHistorySavedData::new, JobHistorySavedData::load);
    private final Map<Key, JobHistory> histories = new HashMap<>();
    // Not persisted: reopening a world must not extrapolate across downtime.
    private final Map<Key, Observation> observations = new HashMap<>();

    public static JobHistorySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "colonyresourceledger_job_history");
    }

    public void observe(int colony, int citizen, long tick, ObservedJobStatus status) {
        Key key = new Key(colony, citizen);
        Observation previous = observations.put(key, new Observation(tick, status));
        if (previous != null && tick - previous.tick() == JobHistory.SAMPLE_TICKS) {
            // Attribute the preceding interval to its previous sampled status.
            histories.computeIfAbsent(key, ignored -> new JobHistory())
                    .record(previous.tick() / JobHistory.SAMPLE_TICKS, previous.status());
            setDirty();
        }
    }

    public JobDurations durations(int colony, int citizen, long now, JobPeriod period) {
        JobHistory history = histories.get(new Key(colony, citizen));
        return history == null ? JobDurations.EMPTY : history.durations(now, period.ticks());
    }

    static JobHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        JobHistorySavedData data = new JobHistorySavedData();
        ListTag list = tag.getList("citizens", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            data.histories.put(new Key(row.getInt("colony"), row.getInt("citizen")),
                    new JobHistory(row.getByteArray("samples"), row.getLong("lastBucket"), row.getLongArray("lifetime")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        histories.forEach((key, history) -> {
            CompoundTag row = new CompoundTag();
            row.putInt("colony", key.colony());
            row.putInt("citizen", key.citizen());
            row.putByteArray("samples", history.samples());
            row.putLong("lastBucket", history.lastBucket());
            row.putLongArray("lifetime", history.lifetime());
            list.add(row);
        });
        tag.put("citizens", list);
        return tag;
    }
}
