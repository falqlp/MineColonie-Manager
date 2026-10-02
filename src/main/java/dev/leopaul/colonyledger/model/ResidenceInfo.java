package dev.leopaul.colonyledger.model;

import java.util.List;

public record ResidenceInfo(HousingPosition position, String name, int level, int skillCap,
        int capacity, List<Integer> occupants, boolean assignable, boolean housingLocked) {
    public ResidenceInfo { occupants = List.copyOf(occupants); }
    public int freePlaces() { return assignable ? Math.max(0, capacity - occupants.size()) : 0; }
    public boolean overloaded() { return occupants.size() > capacity; }
}
