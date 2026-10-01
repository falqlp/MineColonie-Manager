package dev.leopaul.colonyledger.service;

public final class ResourceMath {
    private ResourceMath() {}
    public static int missing(int required, int atBuilder, int available, int inTransit) {
        return Math.max(0, required - Math.max(0, atBuilder) - Math.max(0, available) - Math.max(0, inTransit));
    }
}
