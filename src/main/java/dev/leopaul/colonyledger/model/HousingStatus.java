package dev.leopaul.colonyledger.model;

public enum HousingStatus {
    GOOD, ACCEPTABLE, FAR, VERY_FAR, NO_HOME, NO_WORK;
    public String translationKey() { return "screen.colonyresourceledger.housing.status." + name().toLowerCase(java.util.Locale.ROOT); }
    public static HousingStatus of(double distance, double good, double acceptable, double far) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        return distance <= good ? GOOD : distance <= acceptable ? ACCEPTABLE : distance <= far ? FAR : VERY_FAR;
    }
}
