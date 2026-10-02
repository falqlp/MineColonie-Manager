package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.model.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;

public final class JobMonitorPayloadTest {
    public static void main(String[] args) {
        for (JobPeriod period : JobPeriod.values()) {
            for (boolean open : new boolean[]{true, false}) {
                JobMonitorSummary summary = new JobMonitorSummary(2, "Colonie de test", period,
                        List.of(new CitizenJobRow(7, "Léo", "com.minecolonies.job.stonemason",
                                ObservedJobStatus.STUCK, true, new JobDurations(24_000, 72_000, 168_000)),
                                new CitizenJobRow(8, "Alex", "screen.colonyresourceledger.jobs.unemployed",
                                        ObservedJobStatus.IDLE, false, JobDurations.EMPTY)));
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                try {
                    JobMonitorPayload packet = new JobMonitorPayload(summary, open);
                    JobMonitorPayload.STREAM_CODEC.encode(buffer, packet);
                    if (!packet.equals(JobMonitorPayload.STREAM_CODEC.decode(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Job data mismatch");
                    RequestJobMonitorPayload request = new RequestJobMonitorPayload(-1, period, open);
                    RequestJobMonitorPayload.STREAM_CODEC.encode(buffer, request);
                    if (!request.equals(RequestJobMonitorPayload.STREAM_CODEC.decode(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Job request mismatch");
                } finally { buffer.release(); }
            }
        }
        System.out.println("JobMonitorPayload: 20 request/data round trips passed.");
    }
}
