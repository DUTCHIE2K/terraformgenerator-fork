package org.terraform.cave.v2.generation;

@FunctionalInterface
public interface CaveDensitySampler {
    float sampleDensity(int rawX, int y, int rawZ, double surfaceHeight);
}
