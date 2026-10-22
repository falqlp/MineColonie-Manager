package dev.leopaul.colonyledger.service;

import dev.leopaul.colonyledger.model.ObservedJobStatus;

/** Read-only activity estimate. In particular, JobStatus.IDLE is not evidence that an AI is idle. */
public final class JobActivityClassifier {
    public enum JobKind { BUILDER, COURIER, OTHER }
    public enum CitizenPhase { WORK, NOT_WORK, UNKNOWN }
    public enum WorkerPhase { UNKNOWN, INITIALIZING, IDLE, STARTING, ACTIVE, NEEDS_ITEM, PAUSED }
    public record Signals(boolean hasJob, boolean paused, boolean asleep, CitizenPhase citizenPhase,
            WorkerPhase workerPhase, ObservedJobStatus nativeStatus, JobKind kind,
            boolean assignedTask, boolean blockingRequest, boolean completedRequest) {}

    private JobActivityClassifier() {}

    public static ObservedJobStatus classify(Signals s) {
        // The work AI retains its last state while the citizen is sleeping, eating, etc.
        if (!s.hasJob() || s.paused() || s.asleep() || s.citizenPhase() == CitizenPhase.NOT_WORK
                || s.workerPhase() == WorkerPhase.PAUSED || s.workerPhase() == WorkerPhase.INITIALIZING)
            return ObservedJobStatus.IDLE;

        // A builder without a work order cycles between IDLE and START_WORKING forever.
        if (s.kind() == JobKind.BUILDER && !s.assignedTask()) return ObservedJobStatus.IDLE;

        // A completed delivery can still be collected in NEEDS_ITEM; that walk is work, not a blockage.
        if (s.workerPhase() == WorkerPhase.NEEDS_ITEM) {
            if (s.completedRequest()) return ObservedJobStatus.WORKING;
            if (s.blockingRequest()) return ObservedJobStatus.STUCK;
        }

        // Couriers also cycle through START_WORKING with no tasks, even with isWorking() == true.
        // ACTIVE includes unloading after a completed task, so an empty queue alone is not sufficient.
        if (s.kind() == JobKind.COURIER && !s.assignedTask() && s.workerPhase() != WorkerPhase.ACTIVE)
            return ObservedJobStatus.IDLE;

        if (s.workerPhase() == WorkerPhase.IDLE && s.kind() == JobKind.OTHER) return ObservedJobStatus.IDLE;

        if (s.nativeStatus() == ObservedJobStatus.STUCK) return ObservedJobStatus.STUCK;
        if (s.workerPhase() == WorkerPhase.ACTIVE) return ObservedJobStatus.WORKING;
        if ((s.kind() == JobKind.BUILDER || s.kind() == JobKind.COURIER) && s.assignedTask()
                && (s.workerPhase() == WorkerPhase.STARTING || s.workerPhase() == WorkerPhase.IDLE))
            return ObservedJobStatus.WORKING;
        // Unknown/custom states retain native semantics instead of guessing from movement or animation.
        return s.nativeStatus();
    }
}
