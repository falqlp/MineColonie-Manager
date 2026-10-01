package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import dev.leopaul.colonyledger.model.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record LedgerDataPayload(ColonyResourceSummary summary) implements CustomPacketPayload {
    public static final Type<LedgerDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "ledger_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LedgerDataPayload> STREAM_CODEC =
            StreamCodec.ofMember(LedgerDataPayload::write, LedgerDataPayload::new);

    private LedgerDataPayload(RegistryFriendlyByteBuf buf) {
        this(readSummary(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(summary.colonyId());
        buf.writeUtf(summary.colonyName());
        buf.writeVarInt(summary.activeBuilderCount());
        buf.writeVarInt(summary.activeConstructionCount());
        buf.writeLong(summary.generatedAt());
        buf.writeCollection(summary.resources(), LedgerDataPayload::writeResource);
        buf.writeCollection(summary.supplyRequirements(), LedgerDataPayload::writeSupply);
        buf.writeBoolean(summary.supplyPlanLimited());
    }

    private static ColonyResourceSummary readSummary(RegistryFriendlyByteBuf buf) {
        int colonyId = buf.readVarInt();
        String name = buf.readUtf();
        int builders = buf.readVarInt();
        int constructions = buf.readVarInt();
        long generatedAt = buf.readLong();
        List<ResourceRequirement> rows = buf.readList(LedgerDataPayload::readResource);
        List<SupplyRequirement> supplies = buf.readList(LedgerDataPayload::readSupply);
        boolean limited = buf.readBoolean();
        return new ColonyResourceSummary(colonyId, name, builders, constructions, generatedAt, rows, supplies, limited);
    }

    private static void writeSupply(FriendlyByteBuf buf, SupplyRequirement row) {
        buf.writeUtf(row.itemId());
        buf.writeUtf(row.displayName());
        buf.writeVarInt(row.requiredTotal());
        buf.writeVarInt(row.allocatedStock());
        buf.writeVarInt(row.missing());
        buf.writeCollection(row.usedBy(), (buffer, source) -> buffer.writeUtf(source));
    }

    private static SupplyRequirement readSupply(FriendlyByteBuf buf) {
        return new SupplyRequirement(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readList(buffer -> buffer.readUtf()));
    }

    private static void writeResource(FriendlyByteBuf buf, ResourceRequirement row) {
        buf.writeUtf(row.itemId());
        buf.writeUtf(row.displayName());
        buf.writeVarInt(row.requiredTotal());
        buf.writeVarInt(row.builderInventory());
        buf.writeVarInt(row.colonyAvailable());
        buf.writeVarInt(row.inTransit());
        buf.writeVarInt(row.missing());
        buf.writeEnum(row.status());
        buf.writeCollection(row.requestSources(), LedgerDataPayload::writeSource);
    }

    private static ResourceRequirement readResource(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        int required = buf.readVarInt();
        int atBuilder = buf.readVarInt();
        int available = buf.readVarInt();
        int transit = buf.readVarInt();
        int missing = buf.readVarInt();
        ResourceStatus status = buf.readEnum(ResourceStatus.class);
        List<RequestSource> sources = buf.readList(LedgerDataPayload::readSource);
        return new ResourceRequirement(id, name, required, atBuilder, available, transit, missing, status, sources);
    }

    private static void writeSource(FriendlyByteBuf buf, RequestSource source) {
        buf.writeUtf(source.builderName());
        buf.writeUtf(source.buildingName());
        buf.writeVarInt(source.buildingLevel());
        buf.writeLong(source.buildingPosition());
        buf.writeVarInt(source.requiredQuantity());
    }

    private static RequestSource readSource(FriendlyByteBuf buf) {
        return new RequestSource(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readLong(), buf.readVarInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
