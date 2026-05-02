package org.terraform.coregen;

import org.terraform.biome.BiomeSection;
import org.terraform.data.CoordPair;

import java.util.HashMap;

public record SectionBlurCache(BiomeSection sect, float[][] intermediate, float[][] blurred) {

    public void fillCache(){
        //this hashmap becomes a bottleneck when biomesection bitshifts is large.
        int lowerX = sect.getLowerBounds().getX();
        int lowerZ = sect.getLowerBounds().getZ();
        int upperX = sect.getUpperBounds().getX();
        int upperZ = sect.getUpperBounds().getZ();
        int startX = lowerX - HeightMap.MASK_RADIUS;
        int startZ = lowerZ - HeightMap.MASK_RADIUS;
        int dominantHeightCacheSize = (BiomeSection.sectionWidth + HeightMap.MASK_DIAMETER)
                                      * (BiomeSection.sectionWidth + HeightMap.MASK_DIAMETER);
        HashMap<CoordPair, Float> dominantBiomeHeights = new HashMap<>((int) (dominantHeightCacheSize / 0.75f) + 1);

        // Box blur across the biome section
        // For every point in the biome section, blur across the X axis.
        for (int relX = startX; relX <= upperX + HeightMap.MASK_RADIUS; relX++) {
            for (int relZ = startZ; relZ <= upperZ + HeightMap.MASK_RADIUS;
                 relZ++) {
                int arrIdX = relX - startX;
                int arrIdZ = relZ - startZ;
                float lineTotalHeight = 0;
                for (int offsetX = -HeightMap.MASK_RADIUS; offsetX <= HeightMap.MASK_RADIUS; offsetX++) {
                    lineTotalHeight += HeightMap.getDominantBiomeHeight(
                            sect.getTw(), relX + offsetX, relZ, dominantBiomeHeights);
                }

                // Temporarily cache these X-Blurred values into chunkcache.
                intermediate[arrIdX][arrIdZ] = lineTotalHeight;
            }
        }

        // For every point in the biome section, blur across the Z axis.
        for (int relX = lowerX; relX <= upperX; relX++) {
            for (int relZ = lowerZ; relZ <= upperZ; relZ++) {
                int arrIdX = relX - startX;
                int arrIdZ = relZ - startZ;

               float lineTotalHeight = 0;
                for (int offsetZ = -HeightMap.MASK_RADIUS; offsetZ <= HeightMap.MASK_RADIUS; offsetZ++) {
                    int querIdZ = relZ + offsetZ - startZ;
                    //wasted calculations get repeated on different sections at the boundary
                    // But not much we can do short of overhauling the system
                    lineTotalHeight += intermediate[arrIdX][querIdZ];
                }
                // final blurred value
                blurred[arrIdX][arrIdZ] = lineTotalHeight / HeightMap.MASK_VOLUME;
            }
        }
    }

    public float getBlurredHeight(int blockX, int blockZ){
        int lowerX = sect.getLowerBounds().getX() - HeightMap.MASK_RADIUS;
        int lowerZ = sect.getLowerBounds().getZ() - HeightMap.MASK_RADIUS;
        int arrIdX = blockX - lowerX;
        int arrIdZ = blockZ - lowerZ;
        return blurred[arrIdX][arrIdZ];
    }

    @Override
    public int hashCode(){
        return sect.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if(o instanceof SectionBlurCache s){
            return s.sect.equals(sect);
        }
        return false;
    }
}
