package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestLedgerPayload(int colonyId) implements CustomPacketPayload {
    public static final Type<RequestLedgerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "request_ledger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLedgerPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestLedgerPayload::write, RequestLedgerPayload::new);

    private RequestLedgerPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readVarInt()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(colonyId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
