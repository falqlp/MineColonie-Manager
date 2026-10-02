package dev.leopaul.colonyledger.client;

import dev.leopaul.colonyledger.client.gui.LedgerScreen;
import dev.leopaul.colonyledger.client.gui.JobMonitorScreen;
import dev.leopaul.colonyledger.network.LedgerDataPayload;
import dev.leopaul.colonyledger.network.JobMonitorPayload;
import dev.leopaul.colonyledger.network.HousingDataPayload;
import dev.leopaul.colonyledger.client.gui.HousingManagerScreen;
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
    public static void handleJobMonitor(JobMonitorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.openScreen()) minecraft.setScreen(new JobMonitorScreen(payload.summary()));
            else if (minecraft.screen instanceof JobMonitorScreen screen) screen.update(payload.summary());
        });
    }
    public static void handleHousing(HousingDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (payload.openScreen()) minecraft.setScreen(new HousingManagerScreen(payload));
            else if (minecraft.screen instanceof HousingManagerScreen screen) screen.update(payload);
        });
    }
}
