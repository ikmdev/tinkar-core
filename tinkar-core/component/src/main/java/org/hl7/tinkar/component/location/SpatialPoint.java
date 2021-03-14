package org.hl7.tinkar.component.location;

import java.util.Objects;

public class SpatialPoint {
    public final int x;
    public final int y;
    public final int z;

    public SpatialPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpatialPoint)) return false;
        SpatialPoint that = (SpatialPoint) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
