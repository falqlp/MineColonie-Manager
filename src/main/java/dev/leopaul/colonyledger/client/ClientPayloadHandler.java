package dev.leopaul.colonyledger.client;

import dev.leopaul.colonyledger.client.gui.LedgerScreen;
import dev.leopaul.colonyledger.network.LedgerDataPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}
    public static void handle(LedgerDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof LedgerScreen screen) screen.update(payload.summary());
            else minecraft.setScreen(new LedgerScreen(payload.summary()));
        });
    }
}
