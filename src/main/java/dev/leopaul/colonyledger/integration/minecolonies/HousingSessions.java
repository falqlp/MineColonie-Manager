package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import dev.leopaul.colonyledger.config.HousingConfig;
import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.network.*;
import dev.leopaul.colonyledger.service.HousingAssignmentService;
import dev.leopaul.colonyledger.service.HousingOptimizationService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.*;

/** Access exclusively on the server thread. One outstanding, player-bound proposal per player. */
public final class HousingSessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private record Session(UUID id, ResourceKey<Level> dimension, int createdTick, HousingSummary snapshot) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> REQUESTS = new WeakHashMap<>();
    private HousingSessions() {}
    public static boolean acceptRequest(ServerPlayer player) {
        Integer previous = REQUESTS.get(player);
        if (previous != null && player.tickCount - previous >= 0 && player.tickCount - previous < 20) return false;
        REQUESTS.put(player, player.tickCount); return true;
    }
    public static HousingDataPayload request(ServerPlayer player, int colonyId, boolean open, String message) {
        HousingSummary snapshot = ColonyHousingAdapter.snapshot(player, colonyId, message);
        UUID id = UUID.randomUUID();
        SESSIONS.put(player, new Session(id, player.level().dimension(), player.tickCount, snapshot));
        return new HousingDataPayload(id, snapshot, open);
    }
    public static HousingDataPayload apply(ServerPlayer player, ApplyHousingSwapPayload packet) {
        Session session = SESSIONS.get(player);
        // A forged token cannot invalidate a real proposal or acquire a different colony's data.
        if (session == null || !session.id().equals(packet.proposalId()))
            return new HousingDataPayload(new UUID(0, 0), HousingSummary.empty(key("stale")), false);
        SESSIONS.remove(player); // consume once, even on rejection; replay never applies a second swap
        int colonyId = session.snapshot().colonyId();
        int age = player.tickCount - session.createdTick();
        if (!session.dimension().equals(player.level().dimension()) || age < 0 || age > 1200
                || packet.suggestionIndex() < 0 || packet.suggestionIndex() >= session.snapshot().suggestions().size())
            return request(player, colonyId, false, key("stale"));
        IColony colony = ColonyHousingAdapter.find(player, colonyId);
        if (colony == null || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)
                || !HousingConfig.ALLOW_MANUAL_SWAPS.get()) return request(player, colonyId, false, key("denied"));
        HousingSuggestion wanted = session.snapshot().suggestions().get(packet.suggestionIndex());
        HousingSummary current = ColonyHousingAdapter.snapshot(player, colony, "");
        CitizenHousingInfo a = citizen(current, wanted.firstCitizenId()), b = citizen(current, wanted.secondCitizenId());
        Map<HousingPosition, ResidenceInfo> homes = new HashMap<>();
        current.residences().forEach(h -> homes.put(h.position(), h));
        boolean unchanged = a != null && b != null && a.equals(citizen(session.snapshot(), a.citizenId()))
                && b.equals(citizen(session.snapshot(), b.citizenId()))
                && Objects.equals(homes.get(wanted.firstHome()), home(session.snapshot(), wanted.firstHome()))
                && Objects.equals(homes.get(wanted.secondHome()), home(session.snapshot(), wanted.secondHome()));
        if (!unchanged || HousingOptimizationService.swap(a, b, homes, HousingConfig.MINIMUM_GAIN.get()).filter(wanted::equals).isEmpty())
            return request(player, colonyId, false, key("stale"));
        ICitizenData first = colony.getCitizenManager().getCivilian(a.citizenId());
        ICitizenData second = colony.getCitizenManager().getCivilian(b.citizenId());
        IBuilding firstBuilding = colony.getServerBuildingManager().getBuilding(ColonyHousingAdapter.blockPos(wanted.firstHome()));
        IBuilding secondBuilding = colony.getServerBuildingManager().getBuilding(ColonyHousingAdapter.blockPos(wanted.secondHome()));
        LivingBuildingModule firstModule = ColonyHousingAdapter.living(firstBuilding), secondModule = ColonyHousingAdapter.living(secondBuilding);
        if (first == null || second == null || firstModule == null || secondModule == null
                || !firstModule.hasAssignedCitizen(first) || !secondModule.hasAssignedCitizen(second))
            return request(player, colonyId, false, key("stale"));
        HousingAssignmentService.Result result = HousingAssignmentService.swap(new HousingAssignmentService.Assignments<ICitizenData, LivingBuildingModule>() {
            public boolean remove(LivingBuildingModule h, ICitizenData c) { return h.removeCitizen(c); }
            public boolean assign(LivingBuildingModule h, ICitizenData c) { return h.assignCitizen(c); }
            public boolean contains(LivingBuildingModule h, ICitizenData c) { return h.hasAssignedCitizen(c); }
        }, first, second, firstModule, secondModule);
        if (result != HousingAssignmentService.Result.SUCCESS) LOGGER.error("Housing swap {} / {} in colony {} failed: {}", a.citizenId(), b.citizenId(), colonyId, result);
        return request(player, colonyId, false, key(result == HousingAssignmentService.Result.SUCCESS ? "applied"
                : result == HousingAssignmentService.Result.ROLLED_BACK ? "rolled_back" : "rollback_failed"));
    }
    private static CitizenHousingInfo citizen(HousingSummary s, int id) { return s.citizens().stream().filter(c -> c.citizenId() == id).findFirst().orElse(null); }
    private static ResidenceInfo home(HousingSummary s, HousingPosition p) { return s.residences().stream().filter(h -> h.position().equals(p)).findFirst().orElse(null); }
    private static String key(String value) { return "screen.colonyresourceledger.housing." + value; }
}
