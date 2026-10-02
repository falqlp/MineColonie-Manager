package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import dev.leopaul.colonyledger.model.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JobMonitorPayload(JobMonitorSummary summary, boolean openScreen) implements CustomPacketPayload {
    public static final Type<JobMonitorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "job_monitor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobMonitorPayload> STREAM_CODEC =
            StreamCodec.ofMember(JobMonitorPayload::write, JobMonitorPayload::new);
    private JobMonitorPayload(RegistryFriendlyByteBuf buf) {
        this(new JobMonitorSummary(buf.readVarInt(), buf.readUtf(), buf.readEnum(JobPeriod.class), buf.readList(JobMonitorPayload::readRow)), buf.readBoolean());
    }
    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(summary.colonyId());
        buf.writeUtf(summary.colonyName());
        buf.writeEnum(summary.period());
        buf.writeCollection(summary.citizens(), JobMonitorPayload::writeRow);
        buf.writeBoolean(openScreen);
    }
    private static void writeRow(FriendlyByteBuf buf, CitizenJobRow row) {
        buf.writeVarInt(row.citizenId()); buf.writeUtf(row.name()); buf.writeUtf(row.jobTranslationKey());
        buf.writeEnum(row.currentStatus()); buf.writeBoolean(row.observedNow());
        buf.writeVarLong(row.durations().idle()); buf.writeVarLong(row.durations().working()); buf.writeVarLong(row.durations().stuck());
    }
    private static CitizenJobRow readRow(FriendlyByteBuf buf) {
        return new CitizenJobRow(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readEnum(ObservedJobStatus.class),
                buf.readBoolean(), new JobDurations(buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
