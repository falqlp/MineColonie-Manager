package dev.leopaul.colonyledger.model;

/** A capacity-neutral pair swap. The player must confirm the server-generated proposal. */
public record HousingSuggestion(int firstCitizenId, int secondCitizenId,
        HousingPosition firstHome, HousingPosition secondHome,
        double firstBefore, double secondBefore, double firstAfter, double secondAfter) {
    public double before() { return firstBefore + secondBefore; }
    public double after() { return firstAfter + secondAfter; }
    public double gain() { return before() - after(); }
}
