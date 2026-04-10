package org.terraform.cave.v3.generation;

@FunctionalInterface
public interface CaveFieldSampler {
    float sampleDensity(int rawX, int y, int rawZ, double surfaceHeight);
}
