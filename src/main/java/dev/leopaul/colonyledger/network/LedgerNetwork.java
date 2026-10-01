package dev.leopaul.colonyledger.network;

import dev.leopaul.colonyledger.client.ClientPayloadHandler;
import dev.leopaul.colonyledger.integration.minecolonies.MineColoniesGateway;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LedgerNetwork {
    private LedgerNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(RequestLedgerPayload.TYPE, RequestLedgerPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PacketDistributor.sendToPlayer(player,
                                new LedgerDataPayload(MineColoniesGateway.snapshot(player, payload.colonyId())));
                    }
                }));
        registrar.playToClient(LedgerDataPayload.TYPE, LedgerDataPayload.STREAM_CODEC, ClientPayloadHandler::handle);
    }
}
