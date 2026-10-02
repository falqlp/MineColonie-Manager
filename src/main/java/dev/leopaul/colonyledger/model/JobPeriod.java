package dev.leopaul.colonyledger.model;

public enum JobPeriod {
    ONE_DAY(24_000L, "one"), THREE_DAYS(72_000L, "three"),
    SEVEN_DAYS(168_000L, "seven"), THIRTY_DAYS(720_000L, "thirty"), ALL(0, "all");

    private final long ticks;
    private final String key;
    JobPeriod(long ticks, String key) { this.ticks = ticks; this.key = key; }
    public long ticks() { return ticks; }
    public String translationKey() { return "screen.colonyresourceledger.jobs.period." + key; }
}
