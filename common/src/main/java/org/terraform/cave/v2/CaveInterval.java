package org.terraform.cave.v2;

public record CaveInterval(short ceilingAirY, short floorSolidY) {
    public int height() {
        return ceilingAirY - floorSolidY;
    }
}
