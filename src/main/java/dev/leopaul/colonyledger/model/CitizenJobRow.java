package dev.leopaul.colonyledger.model;

public record CitizenJobRow(int citizenId, String name, String jobTranslationKey,
        ObservedJobStatus currentStatus, boolean observedNow, JobDurations durations) {}
