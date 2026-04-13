package org.terraform.cave.v3.generation;

@FunctionalInterface
public interface CompositeCaveColumnSampler {
    boolean canCarve(int y);
}
