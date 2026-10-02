package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.client.ClientPayloadHandler;
import dev.leopaul.colonyledger.integration.minecolonies.MineColoniesGateway;
import dev.leopaul.colonyledger.integration.minecolonies.JobStatusTracker;
import dev.leopaul.colonyledger.integration.minecolonies.HousingSessions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Map;
import java.util.WeakHashMap;

public final class LedgerNetwork {
    private static final Map<ServerPlayer, Integer> JOB_REQUEST_TICKS = new WeakHashMap<>();
    private LedgerNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4");
        registrar.playToServer(RequestLedgerPayload.TYPE, RequestLedgerPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PacketDistributor.sendToPlayer(player,
                                new LedgerDataPayload(MineColoniesGateway.snapshot(player, payload.colonyId())));
                    }
                }));
        registrar.playToClient(LedgerDataPayload.TYPE, LedgerDataPayload.STREAM_CODEC, ClientPayloadHandler::handle);
        registrar.playToServer(RequestJobMonitorPayload.TYPE, RequestJobMonitorPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Integer previous = JOB_REQUEST_TICKS.get(player);
                        if (previous != null && player.tickCount - previous >= 0 && player.tickCount - previous < 10) return;
                        JOB_REQUEST_TICKS.put(player, player.tickCount);
                        PacketDistributor.sendToPlayer(player,
                                new JobMonitorPayload(JobStatusTracker.snapshot(player, payload.colonyId(), payload.period()), payload.openScreen()));
                    }
                }));
        registrar.playToClient(JobMonitorPayload.TYPE, JobMonitorPayload.STREAM_CODEC, ClientPayloadHandler::handleJobMonitor);
        registrar.playToServer(RequestHousingDataPayload.TYPE, RequestHousingDataPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && HousingSessions.acceptRequest(player))
                        PacketDistributor.sendToPlayer(player, HousingSessions.request(player, payload.colonyId(), payload.openScreen(), ""));
                }));
        registrar.playToServer(ApplyHousingSwapPayload.TYPE, ApplyHousingSwapPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    // Applying an issued, single-use token is already bounded by the snapshot request rate.
                    // Do not silently drop a quick confirmation made just after opening the screen.
                    if (context.player() instanceof ServerPlayer player)
                        PacketDistributor.sendToPlayer(player, HousingSessions.apply(player, payload));
                }));
        registrar.playToClient(HousingDataPayload.TYPE, HousingDataPayload.STREAM_CODEC, ClientPayloadHandler::handleHousing);
    }
}
