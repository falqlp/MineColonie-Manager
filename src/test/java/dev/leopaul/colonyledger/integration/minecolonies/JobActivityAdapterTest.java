package dev.leopaul.colonyledger.integration.minecolonies;

import com.minecolonies.api.entity.ai.statemachine.states.*;
import dev.leopaul.colonyledger.service.JobActivityClassifier.WorkerPhase;
import dev.leopaul.colonyledger.service.JobActivityClassifierTest;

/** Tests the actual installed MineColonies enum mapping without loading a colony or ticking its AI. */
public final class JobActivityAdapterTest {
    private static int checks;
    private static void check(WorkerPhase expected, IState state) {
        checks++;
        if (JobActivityAdapter.workerPhase(state) != expected) throw new AssertionError("Unexpected mapping for " + state);
    }
    public static void main(String[] args) {
        JobActivityClassifierTest.main(args);
        for (AIWorkerState state : new AIWorkerState[]{AIWorkerState.BUILDING_STEP, AIWorkerState.START_BUILDING,
                AIWorkerState.LOAD_STRUCTURE, AIWorkerState.MINE_BLOCK, AIWorkerState.COMPLETE_BUILD, AIWorkerState.PICK_UP,
                AIWorkerState.GATHERING_REQUIRED_MATERIALS, AIWorkerState.INVENTORY_FULL, AIWorkerState.PREPARE_DELIVERY,
                AIWorkerState.DELIVERY, AIWorkerState.PICKUP, AIWorkerState.DUMPING}) check(WorkerPhase.ACTIVE, state);
        check(WorkerPhase.STARTING, AIWorkerState.START_WORKING);
        check(WorkerPhase.STARTING, AIWorkerState.DECIDE);
        check(WorkerPhase.INITIALIZING, AIWorkerState.INIT);
        check(WorkerPhase.IDLE, AIWorkerState.IDLE);
        check(WorkerPhase.IDLE, AIWorkerState.WANDER);
        check(WorkerPhase.PAUSED, AIWorkerState.PAUSED);
        check(WorkerPhase.PAUSED, AIWorkerState.GUARD_SLEEP);
        check(WorkerPhase.NEEDS_ITEM, AIWorkerState.NEEDS_ITEM);
        check(WorkerPhase.UNKNOWN, null);
        check(WorkerPhase.UNKNOWN, new IState() {});
        System.out.println("JobActivityAdapter: " + checks + " mappings passed.");
    }
}
