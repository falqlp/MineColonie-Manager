package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestHousingDataPayload(int colonyId, boolean openScreen) implements CustomPacketPayload {
    public static final Type<RequestHousingDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "request_housing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHousingDataPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestHousingDataPayload::write, RequestHousingDataPayload::new);
    private RequestHousingDataPayload(RegistryFriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean()); }
    private void write(RegistryFriendlyByteBuf b) { b.writeVarInt(colonyId); b.writeBoolean(openScreen); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
