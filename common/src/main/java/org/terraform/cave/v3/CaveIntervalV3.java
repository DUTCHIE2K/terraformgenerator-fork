package org.terraform.cave.v3;

public record CaveIntervalV3(short ceilingAirY, short floorSolidY, CaveIntervalMetadata metadata) {
    public int height() {
        return ceilingAirY - floorSolidY;
    }
}
