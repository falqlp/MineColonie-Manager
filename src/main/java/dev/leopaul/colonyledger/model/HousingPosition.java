package dev.leopaul.colonyledger.model;

public record HousingPosition(int x, int y, int z) {
    public double horizontalDistance(HousingPosition other) {
        return Math.hypot((double) x - other.x, (double) z - other.z);
    }
    public String coordinates() { return x + ", " + y + ", " + z; }
}
