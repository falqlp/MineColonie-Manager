package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.ObservedJobStatus;
import dev.leopaul.colonyledger.service.JobActivityClassifier.*;
import static dev.leopaul.colonyledger.model.ObservedJobStatus.*;

public final class JobActivityClassifierTest {
    private static int checks;
    private static Signals worker(JobKind kind, WorkerPhase phase, boolean assigned) {
        return new Signals(true, false, false, CitizenPhase.WORK, phase, IDLE, kind, assigned, false, false);
    }
    private static void check(String description, ObservedJobStatus expected, Signals signals) {
        checks++;
        ObservedJobStatus actual = JobActivityClassifier.classify(signals);
        if (actual != expected) throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        for (WorkerPhase phase : new WorkerPhase[]{WorkerPhase.ACTIVE, WorkerPhase.STARTING, WorkerPhase.IDLE}) {
            check("Builder with order and stale native IDLE", WORKING, worker(JobKind.BUILDER, phase, true));
            check("Builder without order", IDLE, worker(JobKind.BUILDER, phase, false));
        }
        check("Builder without order even with stale native WORKING", IDLE,
                new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.ACTIVE, WORKING, JobKind.BUILDER, false, false, false));
        for (WorkerPhase phase : new WorkerPhase[]{WorkerPhase.IDLE, WorkerPhase.STARTING, WorkerPhase.UNKNOWN})
            check("Courier queue empty despite work routine running", IDLE, worker(JobKind.COURIER, phase, false));
        check("Courier queued task", WORKING, worker(JobKind.COURIER, WorkerPhase.STARTING, true));
        check("Courier delivering", WORKING, worker(JobKind.COURIER, WorkerPhase.ACTIVE, true));
        check("Courier unloading after task left queue", WORKING, worker(JobKind.COURIER, WorkerPhase.ACTIVE, false));
        check("Courier without tasks despite native WORKING", IDLE,
                new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.STARTING, WORKING, JobKind.COURIER, false, false, false));
        for (JobKind kind : JobKind.values()) {
            for (ObservedJobStatus nativeStatus : ObservedJobStatus.values()) {
                check("No job", IDLE, new Signals(false, false, false, CitizenPhase.WORK, WorkerPhase.ACTIVE, nativeStatus, kind, true, false, false));
                check("Paused citizen", IDLE, new Signals(true, true, false, CitizenPhase.WORK, WorkerPhase.ACTIVE, nativeStatus, kind, true, false, false));
                check("Asleep citizen", IDLE, new Signals(true, false, true, CitizenPhase.WORK, WorkerPhase.ACTIVE, nativeStatus, kind, true, false, false));
                check("Eating/sleeping/fleeing high-level AI", IDLE, new Signals(true, false, false, CitizenPhase.NOT_WORK, WorkerPhase.ACTIVE, nativeStatus, kind, true, true, false));
                check("Paused worker AI", IDLE, new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.PAUSED, nativeStatus, kind, true, false, false));
                check("AI initializing", IDLE, new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.INITIALIZING, nativeStatus, kind, true, false, false));
            }
            check("Pending blocking request", STUCK,
                    new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.NEEDS_ITEM, IDLE, kind, true, true, false));
            check("Walk to collect completed request", WORKING,
                    new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.NEEDS_ITEM, STUCK, kind, true, true, true));
            check("Preserve explicit native blockage", STUCK,
                    new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.ACTIVE, STUCK, kind, true, false, false));
            check("Unavailable custom worker state falls back to native", WORKING,
                    new Signals(true, false, false, CitizenPhase.UNKNOWN, WorkerPhase.UNKNOWN, WORKING, kind, true, false, false));
        }
        check("Other worker executing task with stale IDLE", WORKING, worker(JobKind.OTHER, WorkerPhase.ACTIVE, false));
        check("Idle other worker overrides stale native WORKING", IDLE,
                new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.IDLE, WORKING, JobKind.OTHER, false, false, false));
        check("Idle other worker overrides stale native STUCK", IDLE,
                new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.IDLE, STUCK, JobKind.OTHER, false, false, false));
        check("Cancelled builder order does not stay blocked on stale requests", IDLE,
                new Signals(true, false, false, CitizenPhase.WORK, WorkerPhase.NEEDS_ITEM, STUCK, JobKind.BUILDER, false, true, false));
        check("Transition out of NEEDS_ITEM has no assumed blockage", IDLE, worker(JobKind.OTHER, WorkerPhase.NEEDS_ITEM, false));

        JobHistory history = new JobHistory();
        history.record(0, JobActivityClassifier.classify(worker(JobKind.BUILDER, WorkerPhase.ACTIVE, true)));
        history.record(1, JobActivityClassifier.classify(worker(JobKind.COURIER, WorkerPhase.STARTING, false)));
        checks++;
        var durations = history.durations(40, 0);
        if (durations.working() != 20 || durations.idle() != 20 || durations.stuck() != 0)
            throw new AssertionError("History uses inferred activity with unchanged tick units");
        System.out.println("JobActivityClassifier: " + checks + " checks passed.");
    }
}
