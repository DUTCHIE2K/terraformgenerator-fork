package org.terraform.structure;

import org.terraform.structure.room.CubeRoom;
import org.terraform.structure.room.RoomLayoutGenerator;

import java.util.ArrayList;

/**
 * This class will hold the runtime state for each unique structure
 * at each unique position. Upon initialisation, calculate path locations
 * and room locations.
 */
public class JigsawState {

    // This will hold the room coordinates.
    // Lowest index generates first.
    public final ArrayList<RoomLayoutGenerator> roomPopulatorStates = new ArrayList<>();
    volatile boolean calculatedRange = false;
    int minChunkX = Integer.MAX_VALUE;
    int minChunkZ = Integer.MAX_VALUE;
    int maxChunkX = Integer.MIN_VALUE;
    int maxChunkZ = Integer.MIN_VALUE;

    /**
     * Called to check if chunkX,chunkZ will contain a piece
     */
    public boolean isInRange(int chunkX, int chunkZ) {
        if (!calculatedRange) {
            synchronized (this) {
                if (!calculatedRange) {
                    int newMinChunkX = Integer.MAX_VALUE;
                    int newMinChunkZ = Integer.MAX_VALUE;
                    int newMaxChunkX = Integer.MIN_VALUE;
                    int newMaxChunkZ = Integer.MIN_VALUE;

                    for (RoomLayoutGenerator gen : roomPopulatorStates) {
                        for (CubeRoom room : gen.getRooms()) {
                            int[] lowerCorner = room.getLowerCorner();
                            int[] upperCorner = room.getUpperCorner();
                            newMinChunkX = Math.min(newMinChunkX, lowerCorner[0] >> 4);
                            newMaxChunkX = Math.max(newMaxChunkX, upperCorner[0] >> 4);
                            newMinChunkZ = Math.min(newMinChunkZ, lowerCorner[1] >> 4);
                            newMaxChunkZ = Math.max(newMaxChunkZ, upperCorner[1] >> 4);
                        }

                        newMinChunkX = Math.min(newMinChunkX, (gen.getCentX() - gen.getRange()) >> 4);
                        newMaxChunkX = Math.max(newMaxChunkX, (gen.getCentX() + gen.getRange()) >> 4);
                        newMinChunkZ = Math.min(newMinChunkZ, (gen.getCentZ() - gen.getRange()) >> 4);
                        newMaxChunkZ = Math.max(newMaxChunkZ, (gen.getCentZ() + gen.getRange()) >> 4);
                    }

                    minChunkX = newMinChunkX;
                    minChunkZ = newMinChunkZ;
                    maxChunkX = newMaxChunkX;
                    maxChunkZ = newMaxChunkZ;
                    calculatedRange = true;
                }
            }
        }

        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}
