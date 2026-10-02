package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.service.JobHistory;
import dev.leopaul.colonyledger.service.JobHistorySavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

public final class JobStatusTracker {
    private JobStatusTracker() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        if (tick % JobHistory.SAMPLE_TICKS != 0) return;
        var colonies = IMinecoloniesAPI.getInstance().getColonyManager().getColonies(level);
        if (colonies.isEmpty()) return;
        JobHistorySavedData data = JobHistorySavedData.get(level);
        for (IColony colony : colonies) {
            if (!colony.isActive()) continue;
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                if (observed(colony, citizen)) {
                    data.observe(colony.getID(), citizen.getId(), tick, status(citizen));
                }
            }
        }
    }

    private static boolean observed(IColony colony, ICitizenData citizen) {
        return colony.isActive() && citizen.getEntity().filter(entity -> entity.isAlive() && !entity.isRemoved()).isPresent();
    }
    private static ObservedJobStatus status(ICitizenData citizen) {
        return ObservedJobStatus.valueOf(citizen.getJobStatus().name());
    }

    public static JobMonitorSummary snapshot(ServerPlayer player, int requestedColony, JobPeriod period) {
        IColony colony = MineColoniesGateway.findAccessibleColony(player, requestedColony);
        if (colony == null) return JobMonitorSummary.empty(period);
        ServerLevel level = player.serverLevel();
        JobHistorySavedData data = JobHistorySavedData.get(level);
        List<CitizenJobRow> rows = colony.getCitizenManager().getCitizens().stream().map(citizen ->
                new CitizenJobRow(citizen.getId(), citizen.getName(),
                        citizen.getJob() == null ? "screen.colonyresourceledger.jobs.unemployed"
                                : citizen.getJob().getJobRegistryEntry().getTranslationKey(),
                        status(citizen), observed(colony, citizen),
                        data.durations(colony.getID(), citizen.getId(), level.getGameTime(), period)))
                .sorted(Comparator.comparing(CitizenJobRow::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new JobMonitorSummary(colony.getID(), colony.getName(), period, rows);
    }
}
