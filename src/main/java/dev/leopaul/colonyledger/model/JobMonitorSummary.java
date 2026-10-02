package dev.leopaul.colonyledger.model;

import java.util.List;

public record JobMonitorSummary(int colonyId, String colonyName, JobPeriod period, List<CitizenJobRow> citizens) {
    public static JobMonitorSummary empty(JobPeriod period) {
        return new JobMonitorSummary(-1, "", period, List.of());
    }
    public JobDurations totalDurations() {
        return citizens.stream().map(CitizenJobRow::durations).reduce(JobDurations.EMPTY, JobDurations::plus);
    }
}
