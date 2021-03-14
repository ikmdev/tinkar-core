package org.hl7.tinkar.component.location;

import java.util.Objects;

public class PlanarPoint {
    public final int x;
    public final int y;

    public PlanarPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanarPoint)) return false;
        PlanarPoint that = (PlanarPoint) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
