package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.model.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;

public final class LedgerDataPayloadTest {
    public static void main(String[] args) {
        roundTrip(ColonyResourceSummary.empty("Aucune colonie accessible"));
        roundTrip(new ColonyResourceSummary(5, "Colonie de test", 2, 3, 123456L,
                List.of(new ResourceRequirement("minecraft:oak_stairs", "Escaliers en chêne", 8,
                        0, 0, 0, 8, ResourceStatus.MISSING,
                        List.of(new RequestSource("Alex", "Maison", 2, 42L, 8)))),
                List.of(new SupplyRequirement("minecraft:oak_log", "Bûche de chêne", 3, 1, 2,
                        List.of("Scierie (1, 2, 3) → Planches en chêne"))), true));
        System.out.println("LedgerDataPayload: empty and populated round trips passed.");
    }

    private static void roundTrip(ColonyResourceSummary summary) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LedgerDataPayload.STREAM_CODEC.encode(buffer, new LedgerDataPayload(summary));
            ColonyResourceSummary decoded = LedgerDataPayload.STREAM_CODEC.decode(buffer).summary();
            if (!summary.equals(decoded) || buffer.readableBytes() != 0) {
                throw new AssertionError("Ledger packet round trip mismatch");
            }
        } finally {
            buffer.release();
        }
    }
}
