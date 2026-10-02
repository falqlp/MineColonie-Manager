package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.HomeBuildingModule;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkAtHomeBuildingModule;
import dev.leopaul.colonyledger.config.HousingConfig;
import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.service.HousingAnalysisService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static com.minecolonies.api.util.constant.CitizenConstants.MAX_CITIZEN_LEVEL;
import static com.minecolonies.api.util.constant.Constants.MAX_BUILDING_LEVEL;

/** Internal Living/Home/WorkAtHome modules are isolated here; see docs/housing-api.md. */
public final class ColonyHousingAdapter {
    private ColonyHousingAdapter() {}
    public static IColony find(ServerPlayer player, int requestedId) {
        if (!HousingConfig.ENABLED.get()) return null;
        return IMinecoloniesAPI.getInstance().getColonyManager().getColonies(player.level()).stream()
                .filter(c -> requestedId < 0 || c.getID() == requestedId)
                .filter(c -> c.getPermissions().isColonyMember(player)
                        && c.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
                .min(Comparator.comparingLong(c -> c.getDistanceSquared(player.blockPosition()))).orElse(null);
    }
    public static HousingSummary snapshot(ServerPlayer player, int requestedId, String message) {
        IColony colony = find(player, requestedId);
        return colony == null ? HousingSummary.empty(HousingConfig.ENABLED.get()
                ? "screen.colonyresourceledger.housing.no_colony" : "screen.colonyresourceledger.housing.disabled")
                : snapshot(player, colony, message);
    }
    public static HousingSummary snapshot(ServerPlayer player, IColony colony, String message) {
        List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        citizens.sort(Comparator.comparingInt(ICitizenData::getId));
        List<ResidenceInfo> homes = new ArrayList<>();
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (building.hasModule(HomeBuildingModule.class) || building.hasModule(LivingBuildingModule.class)
                    || building.hasModule(WorkAtHomeBuildingModule.class)) homes.add(residence(building, citizens));
        }
        homes.sort(Comparator.comparingInt((ResidenceInfo h) -> h.position().x())
                .thenComparingInt(h -> h.position().z()).thenComparingInt(h -> h.position().y()));
        Map<HousingPosition, ResidenceInfo> byPosition = new HashMap<>();
        homes.forEach(h -> byPosition.put(h.position(), h));
        List<CitizenHousingInfo> rows = new ArrayList<>();
        for (ICitizenData citizen : citizens) {
            IBuilding home = citizen.getHomeBuilding(), work = citizen.getWorkBuilding();
            HousingPosition homePos = home == null ? null : position(home.getID());
            HousingPosition workPos = work == null ? null : position(work.getID());
            ResidenceInfo residence = home == null ? null : byPosition.get(homePos);
            if (home != null && residence == null) residence = residence(home, citizens);
            double distance = home == null || work == null ? 0 : homePos.horizontalDistance(workPos);
            HousingStatus status = home == null ? HousingStatus.NO_HOME : work == null ? HousingStatus.NO_WORK
                    : HousingStatus.of(distance, HousingConfig.GOOD.get(), HousingConfig.acceptable(), HousingConfig.far());
            int maxSkill = Arrays.stream(Skill.values()).mapToInt(citizen.getCitizenSkillHandler()::getLevel).max().orElse(0);
            boolean mandatoryHome = work != null && work.hasModule(WorkAtHomeBuildingModule.class);
            rows.add(new CitizenHousingInfo(citizen.getId(), citizen.getName(), citizen.isChild(),
                    citizen.getJob() == null ? "screen.colonyresourceledger.jobs.unemployed"
                            : citizen.getJob().getJobRegistryEntry().getTranslationKey(), maxSkill,
                    work == null ? "" : work.getBuildingDisplayName(), workPos,
                    home == null ? "" : home.getBuildingDisplayName(), homePos,
                    home == null ? 0 : home.getBuildingLevel(), residence == null ? 0 : residence.capacity(),
                    residence == null ? 0 : residence.occupants().size(), home == null ? 10 : skillCap(home),
                    distance, status, false, null, citizen.isChild() || mandatoryHome || residence == null || !residence.assignable()));
        }
        return HousingAnalysisService.analyze(colony.getID(), colony.getName(),
                colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS), HousingConfig.ALLOW_MANUAL_SWAPS.get(),
                homes, rows, HousingConfig.MINIMUM_GAIN.get(), HousingConfig.MAX_ANALYSIS_CITIZENS.get(),
                HousingConfig.MAX_SUGGESTIONS.get(), message);
    }
    public static LivingBuildingModule living(IBuilding building) {
        // Only ordinary residential modules, never worker assignment modules or special subclasses.
        if (building == null || !building.hasModule(HomeBuildingModule.class)
                || building.hasModule(WorkAtHomeBuildingModule.class)) return null;
        return building.getModules(LivingBuildingModule.class).stream()
                .filter(m -> m.getClass() == LivingBuildingModule.class).findFirst().orElse(null);
    }
    private static ResidenceInfo residence(IBuilding building, List<ICitizenData> citizens) {
        LivingBuildingModule normal = living(building);
        List<Integer> occupants = citizens.stream().filter(c -> c.getHomeBuilding() != null
                && c.getHomeBuilding().getID().equals(building.getID())).map(ICitizenData::getId).sorted().toList();
        // Guard job modules share one building-wide limit (GuardBuildingModule.isFull),
        // so summing knight/ranger/druid capacities would invent extra housing slots.
        var workAtHome = building.getModules(WorkAtHomeBuildingModule.class);
        int capacity = normal != null ? normal.getModuleMax() : building.hasModule(GuardBuildingModule.class)
                ? workAtHome.stream().mapToInt(WorkAtHomeBuildingModule::getModuleMax).max().orElse(0)
                : workAtHome.stream().mapToInt(WorkAtHomeBuildingModule::getModuleMax).sum();
        if (normal == null && capacity == 0) capacity = building.getModules(LivingBuildingModule.class).stream()
                .mapToInt(LivingBuildingModule::getModuleMax).sum();
        // A mismatch between citizen references and module membership is unsafe to mutate.
        boolean consistent = normal != null && normal.getAssignedCitizen().stream().map(ICitizenData::getId).sorted().toList().equals(occupants);
        return new ResidenceInfo(position(building.getID()), building.getBuildingDisplayName(), building.getBuildingLevel(),
                skillCap(building), capacity, occupants, consistent && building.canAssignCitizens(), false);
    }
    private static int skillCap(IBuilding home) {
        int equivalent = home.getBuildingLevelEquivalent(), max = home.getMaxBuildingLevel();
        // Mirrors CitizenSkillHandler.addXpToSkill in the installed version, not hut level == worker skill.
        return equivalent < max || max < MAX_BUILDING_LEVEL ? Math.min(MAX_CITIZEN_LEVEL, (equivalent + 1) * 10) : MAX_CITIZEN_LEVEL;
    }
    public static HousingPosition position(BlockPos p) { return new HousingPosition(p.getX(), p.getY(), p.getZ()); }
    public static BlockPos blockPos(HousingPosition p) { return new BlockPos(p.x(), p.y(), p.z()); }
}
