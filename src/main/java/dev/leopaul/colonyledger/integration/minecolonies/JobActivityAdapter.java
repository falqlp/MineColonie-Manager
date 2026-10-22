package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.ai.statemachine.states.*;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.leopaul.colonyledger.model.ObservedJobStatus;
import dev.leopaul.colonyledger.service.JobActivityClassifier;
import dev.leopaul.colonyledger.service.JobActivityClassifier.*;

/** Version-specific read-only boundary for MineColonies 1.1.1403. See docs/job-activity.md. */
public final class JobActivityAdapter {
    private JobActivityAdapter() {}

    public static ObservedJobStatus status(ICitizenData citizen) {
        var job = citizen.getJob();
        if (job == null) return ObservedJobStatus.IDLE;
        ObservedJobStatus nativeStatus = citizen.getJobStatus() == null ? ObservedJobStatus.IDLE
                : ObservedJobStatus.valueOf(citizen.getJobStatus().name());
        var entity = citizen.getEntity().orElse(null);
        if (entity == null) return nativeStatus;
        var ai = job.getWorkerAI();
        WorkerPhase phase = workerPhase(ai == null ? null : ai.getState());
        JobKind kind = job instanceof JobBuilder ? JobKind.BUILDER : job instanceof JobDeliveryman ? JobKind.COURIER : JobKind.OTHER;
        var building = citizen.getWorkBuilding();
        boolean assigned = kind == JobKind.BUILDER
                ? building instanceof AbstractBuildingStructureBuilder builder && builder.getWorkOrder() != null
                : job instanceof JobDeliveryman courier && !courier.getTaskQueue().isEmpty();
        boolean waiting = phase == WorkerPhase.NEEDS_ITEM && building != null;
        return JobActivityClassifier.classify(new Signals(true, citizen.isPaused(), citizen.isAsleep(),
                citizenPhase(entity, citizen.getStatus()), phase, nativeStatus, kind, assigned,
                waiting && building.hasOpenSyncRequest(citizen),
                waiting && building.hasCitizenCompletedRequests(citizen)));
    }

    private static CitizenPhase citizenPhase(AbstractEntityCitizen entity, VisibleCitizenStatus visible) {
        // Internal API: the high-level state tells whether the worker AI is actually being ticked.
        if (entity instanceof EntityCitizen nativeEntity) {
            IState state = nativeEntity.getCitizenAI().getState();
            if (state instanceof CitizenAIState citizenState)
                return citizenState == CitizenAIState.WORK || citizenState == CitizenAIState.WORKING
                        ? CitizenPhase.WORK : CitizenPhase.NOT_WORK;
        }
        if (visible == VisibleCitizenStatus.SLEEP || visible == VisibleCitizenStatus.EAT
                || visible == VisibleCitizenStatus.SICK || visible == VisibleCitizenStatus.HOUSE
                || visible == VisibleCitizenStatus.MOURNING || visible == VisibleCitizenStatus.RAIDED
                || visible == VisibleCitizenStatus.BAD_WEATHER) return CitizenPhase.NOT_WORK;
        return visible == VisibleCitizenStatus.WORKING ? CitizenPhase.WORK : CitizenPhase.UNKNOWN;
    }

    static WorkerPhase workerPhase(IState state) {
        if (!(state instanceof AIWorkerState workerState)) return WorkerPhase.UNKNOWN;
        return switch (workerState) {
            case INIT -> WorkerPhase.INITIALIZING;
            case IDLE, WANDER, RECESS, LUMBERJACK_NO_TREES_FOUND -> WorkerPhase.IDLE;
            case PAUSED, GUARD_SLEEP, GUARD_REGEN -> WorkerPhase.PAUSED;
            case START_WORKING, DECIDE -> WorkerPhase.STARTING;
            case NEEDS_ITEM -> WorkerPhase.NEEDS_ITEM;
            default -> WorkerPhase.ACTIVE;
        };
    }
}
