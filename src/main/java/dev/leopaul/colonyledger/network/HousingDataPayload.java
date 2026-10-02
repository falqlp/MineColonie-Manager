package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.ColonyResourceLedger;
import dev.leopaul.colonyledger.model.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record HousingDataPayload(UUID proposalId, HousingSummary summary, boolean openScreen) implements CustomPacketPayload {
    public static final Type<HousingDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyResourceLedger.MOD_ID, "housing_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HousingDataPayload> STREAM_CODEC =
            StreamCodec.ofMember(HousingDataPayload::write, HousingDataPayload::new);
    private HousingDataPayload(RegistryFriendlyByteBuf b) {
        this(b.readUUID(), new HousingSummary(b.readVarInt(), b.readUtf(), b.readBoolean(), b.readBoolean(),
                b.readList(HousingDataPayload::readHome), b.readList(HousingDataPayload::readCitizen),
                b.readList(HousingDataPayload::readSuggestion), b.readBoolean(), b.readUtf()), b.readBoolean());
    }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeUUID(proposalId); b.writeVarInt(summary.colonyId()); b.writeUtf(summary.colonyName());
        b.writeBoolean(summary.canManage()); b.writeBoolean(summary.allowManualSwaps());
        b.writeCollection(summary.residences(), HousingDataPayload::writeHome);
        b.writeCollection(summary.citizens(), HousingDataPayload::writeCitizen);
        b.writeCollection(summary.suggestions(), HousingDataPayload::writeSuggestion);
        b.writeBoolean(summary.analysisLimited()); b.writeUtf(summary.messageKey()); b.writeBoolean(openScreen);
    }
    private static void position(FriendlyByteBuf b, HousingPosition p) {
        b.writeBoolean(p != null); if (p != null) { b.writeInt(p.x()); b.writeInt(p.y()); b.writeInt(p.z()); }
    }
    private static HousingPosition position(FriendlyByteBuf b) { return b.readBoolean() ? new HousingPosition(b.readInt(), b.readInt(), b.readInt()) : null; }
    private static void writeHome(FriendlyByteBuf b, ResidenceInfo h) {
        position(b, h.position()); b.writeUtf(h.name()); b.writeVarInt(h.level()); b.writeVarInt(h.skillCap());
        b.writeVarInt(h.capacity()); b.writeCollection(h.occupants(), FriendlyByteBuf::writeVarInt);
        b.writeBoolean(h.assignable()); b.writeBoolean(h.housingLocked());
    }
    private static ResidenceInfo readHome(FriendlyByteBuf b) {
        return new ResidenceInfo(position(b), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readList(FriendlyByteBuf::readVarInt), b.readBoolean(), b.readBoolean());
    }
    private static void writeCitizen(FriendlyByteBuf b, CitizenHousingInfo c) {
        b.writeVarInt(c.citizenId()); b.writeUtf(c.citizenName()); b.writeBoolean(c.child()); b.writeUtf(c.jobName());
        b.writeVarInt(c.workerLevel()); b.writeUtf(c.workplaceName()); position(b, c.workplacePosition());
        b.writeUtf(c.residenceName()); position(b, c.residencePosition()); b.writeVarInt(c.residenceLevel());
        b.writeVarInt(c.residenceCapacity()); b.writeVarInt(c.residenceOccupancy()); b.writeVarInt(c.skillCap());
        b.writeDouble(c.homeToWorkDistance()); b.writeEnum(c.status()); b.writeBoolean(c.housingLocked());
        position(b, c.preferredResidence()); b.writeBoolean(c.excludedFromOptimization());
    }
    private static CitizenHousingInfo readCitizen(FriendlyByteBuf b) {
        return new CitizenHousingInfo(b.readVarInt(), b.readUtf(), b.readBoolean(), b.readUtf(), b.readVarInt(),
                b.readUtf(), position(b), b.readUtf(), position(b), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readDouble(), b.readEnum(HousingStatus.class), b.readBoolean(), position(b), b.readBoolean());
    }
    private static void writeSuggestion(FriendlyByteBuf b, HousingSuggestion s) {
        b.writeVarInt(s.firstCitizenId()); b.writeVarInt(s.secondCitizenId()); position(b, s.firstHome()); position(b, s.secondHome());
        b.writeDouble(s.firstBefore()); b.writeDouble(s.secondBefore()); b.writeDouble(s.firstAfter()); b.writeDouble(s.secondAfter());
    }
    private static HousingSuggestion readSuggestion(FriendlyByteBuf b) {
        return new HousingSuggestion(b.readVarInt(), b.readVarInt(), position(b), position(b), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
