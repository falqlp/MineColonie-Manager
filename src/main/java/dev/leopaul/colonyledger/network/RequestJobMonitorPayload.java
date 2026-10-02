package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import dev.leopaul.colonyledger.model.JobPeriod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestJobMonitorPayload(int colonyId, JobPeriod period, boolean openScreen) implements CustomPacketPayload {
    public static final Type<RequestJobMonitorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "request_job_monitor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestJobMonitorPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestJobMonitorPayload::write, RequestJobMonitorPayload::new);
    private RequestJobMonitorPayload(RegistryFriendlyByteBuf buf) { this(buf.readVarInt(), buf.readEnum(JobPeriod.class), buf.readBoolean()); }
    private void write(RegistryFriendlyByteBuf buf) { buf.writeVarInt(colonyId); buf.writeEnum(period); buf.writeBoolean(openScreen); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
