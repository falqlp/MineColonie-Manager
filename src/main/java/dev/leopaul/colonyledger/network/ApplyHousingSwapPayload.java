package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Only references a server-generated proposal; the client cannot submit arbitrary assignments. */
public record ApplyHousingSwapPayload(UUID proposalId, int suggestionIndex) implements CustomPacketPayload {
    public static final Type<ApplyHousingSwapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "apply_housing_swap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyHousingSwapPayload> STREAM_CODEC =
            StreamCodec.ofMember(ApplyHousingSwapPayload::write, ApplyHousingSwapPayload::new);
    private ApplyHousingSwapPayload(RegistryFriendlyByteBuf b) { this(b.readUUID(), b.readVarInt()); }
    private void write(RegistryFriendlyByteBuf b) { b.writeUUID(proposalId); b.writeVarInt(suggestionIndex); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
