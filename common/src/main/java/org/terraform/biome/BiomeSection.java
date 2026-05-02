package org.terraform.biome;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.data.SimpleLocation;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.GenUtils;
import org.terraform.utils.HashUtils;
import org.terraform.utils.noise.FastNoise;
import org.terraform.utils.noise.FastNoise.NoiseType;

import java.util.*;

public class BiomeSection {
    // A BiomeSection is 128 blocks wide (Default of bitshift 7).
    public static final int bitshifts = TConfig.c.BIOME_SECTION_BITSHIFTS;
    public static final int sectionWidth = 1 << bitshifts;
    public static final int minSize = sectionWidth;
    public static final int dominanceThreshold = (int) (0.35 * sectionWidth);
    public static final int dominanceThresholdSquared = dominanceThreshold*dominanceThreshold;
    private static final long DITHER_BLOCK_X_MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long DITHER_BLOCK_Z_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;
    private static final long DITHER_SECTION_X_MULTIPLIER = 0x165667B19E3779F9L;
    private static final long DITHER_SECTION_Z_MULTIPLIER = 0x85EBCA77C2B2AE63L;
    private final int x;
    private final int z;
    private final TerraformWorld tw;
    private final int centerX;
    private final int centerZ;
    private final @NotNull SimpleLocation center;
    private final @NotNull SimpleLocation lowerBounds;
    private final @NotNull SimpleLocation upperBounds;
    private final int sectionSeed;
    private final int hash;
    private float temperature;
    private float moisture;
    private int radius;
    private double inverseRadiusSquared;
    private double oceanLevel;
    private double mountainLevel;
    private @Nullable BiomeClimate climate;
    private @Nullable BiomeBank biome;
    private FastNoise shapeNoise;

    /**
     * Block x and z
     */
    protected BiomeSection(TerraformWorld tw, int x, int z) {
        this(tw, x >> bitshifts, z >> bitshifts, true);
    }

    protected BiomeSection(TerraformWorld tw, int x, int z, boolean useSectionCoords) {
        this.x = useSectionCoords ? x : x >> bitshifts;
        this.z = useSectionCoords ? z : z >> bitshifts;
        this.tw = tw;
        this.centerX = (this.x << bitshifts) + sectionWidth / 2;
        this.centerZ = (this.z << bitshifts) + sectionWidth / 2;
        this.center = new SimpleLocation(centerX, 0, centerZ);
        this.lowerBounds = new SimpleLocation(this.x << bitshifts, 0, this.z << bitshifts);
        this.upperBounds = new SimpleLocation((this.x << bitshifts) + sectionWidth, 0, (this.z << bitshifts) + sectionWidth);
        this.sectionSeed = HashUtils.hashSeed(tw.getSeed(), this.x, this.z);
        int hash = 5;
        hash = 13 * hash + this.x;
        hash = 13 * hash + this.z;
        hash = 13 * hash + tw.getName().hashCode();
        this.hash = hash;
    }

    /**
     * @return the width * width closest biome sections to this block point.
     */
    public static @NotNull Collection<BiomeSection> getSurroundingSections(TerraformWorld tw,
                                                                           int width,
                                                                           int blockX,
                                                                           int blockZ)
    {
        BiomeSection homeSection = BiomeBank.getBiomeSectionFromBlockCoords(tw, blockX, blockZ);
        Collection<BiomeSection> sections = new ArrayList<>(width * width);

        int startX, startZ;
        if (width % 2 == 1) {
            startX = startZ = -width / 2;
        }
        else {
            startX = blockX >= homeSection.centerX ? -width / 2 - 1 : -width / 2;
            startZ = blockZ >= homeSection.centerZ ? -width / 2 - 1 : -width / 2;
        }

        for (int rx = startX; rx < startX + width; rx++) {
            for (int rz = startZ; rz < startZ + width; rz++) {
                sections.add(homeSection.getRelative(rx, rz));
            }
        }

        if (sections.size() != width * width) {
            TerraformGeneratorPlugin.logger.error("Section size was not " + (width * width) + ".");
        }

        return sections;
    }

    /**
     * @return the four closest biome sections to this block point
     */
    public static @NotNull Collection<BiomeSection> getSurroundingSections(TerraformWorld tw, int blockX, int blockZ) {
        Collection<BiomeSection> sections = new ArrayList<>(4);

        BiomeSection homeBiome = BiomeBank.getBiomeSectionFromBlockCoords(tw, blockX, blockZ);
        sections.add(homeBiome);
        int relativeX = blockX >= homeBiome.centerX ? 1 : -1;
        int relativeZ = blockZ >= homeBiome.centerZ ? 1 : -1;
        sections.add(homeBiome.getRelative(relativeX, 0));
        sections.add(homeBiome.getRelative(relativeX, relativeZ));
        sections.add(homeBiome.getRelative(0, relativeZ));
        return sections;
    }

    public static @NotNull BiomeSection getMostDominantSection(@NotNull TerraformWorld tw, int x, int z) {
        double dither = TConfig.c.BIOME_DITHER;
        BiomeSection homeSection = BiomeBank.getBiomeSectionFromBlockCoords(tw, x, z);
        int xOffset = homeSection.centerX - x;
        int zOffset = homeSection.centerZ - z;

        // Don't calculate if distance is very close to center
        if (xOffset * xOffset + zOffset * zOffset <= dominanceThresholdSquared) {
            return homeSection;
        }

        BiomeSection mostDominant = homeSection;
        float mostDominantScore = getDominanceScore(homeSection, tw.getSeed(), x, z, dither);
        int relativeX = x >= homeSection.centerX ? 1 : -1;
        int relativeZ = z >= homeSection.centerZ ? 1 : -1;

        BiomeSection candidate = homeSection.getRelative(relativeX, 0);
        float candidateScore = getDominanceScore(candidate, tw.getSeed(), x, z, dither);
        if (candidateScore > mostDominantScore) {
            mostDominant = candidate;
            mostDominantScore = candidateScore;
        }

        candidate = homeSection.getRelative(relativeX, relativeZ);
        candidateScore = getDominanceScore(candidate, tw.getSeed(), x, z, dither);
        if (candidateScore > mostDominantScore) {
            mostDominant = candidate;
            mostDominantScore = candidateScore;
        }

        candidate = homeSection.getRelative(0, relativeZ);
        candidateScore = getDominanceScore(candidate, tw.getSeed(), x, z, dither);
        if (candidateScore > mostDominantScore) {
            mostDominant = candidate;
        }

        return mostDominant;
    }

    protected void doCalculations() {
        this.radius = GenUtils.randInt(getSectionRandom(), minSize / 2, 5 * minSize / 4);
        this.inverseRadiusSquared = 1.0 / ((double) radius * radius);
        this.shapeNoise = new FastNoise(sectionSeed);
        shapeNoise.SetNoiseType(NoiseType.SimplexFractal);
        shapeNoise.SetFractalOctaves(3);
        shapeNoise.SetFrequency(0.01f);
        this.oceanLevel = tw.getOceanicNoise().GetNoise(x, z) * 50.0;
        this.mountainLevel = tw.getMountainousNoise().GetNoise(x, z) * 50.0;
        this.biome = this.parseBiomeBank();
    }

    public @NotNull Random getSectionRandom() {
        return new Random(sectionSeed);
    }

    public @NotNull Random getSectionRandom(int multiplier) {
        return new Random((long) multiplier * sectionSeed);
    }

    public @NotNull BiomeSection getRelative(int x, int z) {
        return BiomeBank.getBiomeSectionFromSectionCoords(this.tw, this.x + x, this.z + z, true);
    }

    public @NotNull BiomeSection getRelative(BiomeSubSection subSect) {
        return getRelative(subSect.relX, subSect.relZ);
    }

    public @NotNull BiomeBank getBiomeBank() {
        assert biome != null;
        return biome;
    }

    private @NotNull BiomeBank parseBiomeBank() {
        temperature = 3f * 2.5f * tw.getTemperatureOctave().GetNoise(this.x, this.z);
        moisture = 3f * 2.5f * tw.getMoistureOctave().GetNoise(this.x, this.z);
        climate = BiomeClimate.selectClimate(temperature, moisture);

        return BiomeBank.selectBiome(this, climate);
    }

    /**
     * Will be used to calculate which biome section has dominance in a certain
     * block
     */
    public float getDominance(@NotNull SimpleLocation target) {
        return getDominanceBasedOnRadius(target.getX(), target.getZ());
    }

    public float getDominanceBasedOnRadius(int blockX, int blockZ) {
        int xOffset = centerX - blockX;
        int zOffset = centerZ - blockZ;
        double equationResult = ((double) xOffset * xOffset) * inverseRadiusSquared
                                + ((double) zOffset * zOffset) * inverseRadiusSquared
                                + 0.7 * shapeNoise.GetNoise(xOffset, zOffset);

        return (float) (1 - 1 * (equationResult));

    }

    public @NotNull SimpleLocation getCenter() {
        return center;
    }

    /**
     * @return Block coords of lowest coord pair in the section's square
     */
    public @NotNull SimpleLocation getLowerBounds() {
        return lowerBounds;
    }

    /**
     * @return Block coords of highest coord pair in the section's square
     */
    public @NotNull SimpleLocation getUpperBounds() {
        return upperBounds;
    }

    /**
     * @param radius in biomesection coords
     * @return surrounding biome sections at radius distance away
     */
    public @NotNull Collection<BiomeSection> getRelativeSurroundingSections(int radius) {
        if (radius == 0) {
            BiomeSection target = this;
            return List.of(target);
        }
        //     xxxxx
        // xxx  x   x
        // xox  x o x
        // xxx  x   x
        //     xxxxx
        ArrayList<BiomeSection> candidates = new ArrayList<>(radius * 8);

        // Lock rX, iterate rZ
        for (int rz = -radius; rz <= radius; rz++) {
            candidates.add(this.getRelative(-radius, rz));
            candidates.add(this.getRelative(radius, rz));
        }

        // Lock rZ, iterate rX
        for (int rx = 1 - radius; rx <= radius - 1; rx++) {
            candidates.add(this.getRelative(rx, -radius));
            candidates.add(this.getRelative(rx, radius));
        }

        return candidates;
    }

    /**
     * @return the subsection within this biome section that the coordinates belong in.
     * Works even if the coords are outside the biome section.
     *
     * 12/6/2025 WHAT THE FUCK IS THIS
     */
    public @NotNull BiomeSubSection getSubSection(int rawX, int rawZ) {
        // if(new BiomeSection(tw, rawX, rawZ).equals(this)) {
        int relXFromCenter = rawX - centerX;
        int relZFromCenter = rawZ - centerZ;

        if (relXFromCenter > 0) {
            if (relXFromCenter >= Math.abs(relZFromCenter)) {
                return BiomeSubSection.POSITIVE_X;
            }
        }

        if (relXFromCenter <= 0) {
            if (Math.abs(relXFromCenter) >= Math.abs(relZFromCenter)) {
                return BiomeSubSection.NEGATIVE_X;
            }
        }

        if (relZFromCenter > 0) {
            if (relZFromCenter >= Math.abs(relXFromCenter)) {
                return BiomeSubSection.POSITIVE_Z;
            }
        }

        if (relZFromCenter <= 0) {
            if (Math.abs(relZFromCenter) >= Math.abs(relXFromCenter)) {
                return BiomeSubSection.NEGATIVE_Z;
            }
        }

        return BiomeSubSection.NONE;

        // }

    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BiomeSection other) {
            return this.tw.getName().equals(other.tw.getName())
                   && this.x == other.x
                   && this.z == other.z;
        }
        return false;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public @NotNull String toString() {
        return "(" + x + "," + z + ")";
    }

    public @NotNull BiomeClimate getClimate() {
        assert climate != null;
        return climate;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getMoisture() {
        return moisture;
    }

    public TerraformWorld getTw() {
        return tw;
    }

    public double getOceanLevel() {
        return oceanLevel;
    }

    public double getMountainLevel() {
        return mountainLevel;
    }

    private static float getDominanceScore(@NotNull BiomeSection section,
                                           long worldSeed,
                                           int blockX,
                                           int blockZ,
                                           double dither)
    {
        float score = section.getDominanceBasedOnRadius(blockX, blockZ);
        if (dither == 0) {
            return score;
        }

        return (float) (score + dither * getSectionDither(worldSeed, blockX, blockZ, section.x, section.z));
    }

    private static double getSectionDither(long worldSeed, int blockX, int blockZ, int sectionX, int sectionZ) {
        long mixed = worldSeed;
        mixed ^= DITHER_BLOCK_X_MULTIPLIER * blockX;
        mixed ^= DITHER_BLOCK_Z_MULTIPLIER * blockZ;
        mixed ^= DITHER_SECTION_X_MULTIPLIER * sectionX;
        mixed ^= DITHER_SECTION_Z_MULTIPLIER * sectionZ;
        return HashUtils.signedUnitDouble(HashUtils.mix64(mixed));
    }
}
