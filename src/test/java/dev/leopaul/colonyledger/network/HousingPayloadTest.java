package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.model.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;

public final class HousingPayloadTest {
    public static void main(String[] args) {
        int checks = 0;
        var a = new HousingPosition(-10, 72, 50); var b = new HousingPosition(200, -20, 30);
        List<CitizenHousingInfo> citizens = new ArrayList<>();
        for (HousingStatus status : HousingStatus.values()) citizens.add(new CitizenHousingInfo(status.ordinal(), "Léo " + status,
                status == HousingStatus.NO_WORK, "job.builder", 25, "Atelier", status == HousingStatus.NO_WORK ? null : b,
                "Résidence", status == HousingStatus.NO_HOME ? null : a, 3, 3, 2, 40, 210.95, status, true, b, false));
        HousingSummary full = new HousingSummary(8, "Colonie", true, false,
                List.of(new ResidenceInfo(a, "Maison", 3, 40, 3, List.of(1, 2), true, false)), citizens,
                List.of(new HousingSuggestion(1, 2, a, b, 300, 280, 60, 55)), true, "message.key");
        for (HousingSummary summary : List.of(full, HousingSummary.empty("no_access"))) for (boolean open : new boolean[]{true, false}) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                UUID token = UUID.randomUUID();
                HousingDataPayload data = new HousingDataPayload(token, summary, open);
                HousingDataPayload.STREAM_CODEC.encode(buffer, data);
                if (!data.equals(HousingDataPayload.STREAM_CODEC.decode(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Housing round trip");
                checks++;
                RequestHousingDataPayload request = new RequestHousingDataPayload(-1, open);
                RequestHousingDataPayload.STREAM_CODEC.encode(buffer, request);
                if (!request.equals(RequestHousingDataPayload.STREAM_CODEC.decode(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Request round trip");
                checks++;
                ApplyHousingSwapPayload apply = new ApplyHousingSwapPayload(token, 3);
                ApplyHousingSwapPayload.STREAM_CODEC.encode(buffer, apply);
                if (!apply.equals(ApplyHousingSwapPayload.STREAM_CODEC.decode(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Apply round trip");
                checks++;
            } finally { buffer.release(); }
        }
        System.out.println("HousingPayload: " + checks + " round trips passed.");
    }
}
