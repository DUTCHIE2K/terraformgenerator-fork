package org.terraform.structure.small.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.HeightMap;
import org.terraform.coregen.TerraLootTable;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.MegaChunk;
import org.terraform.data.SimpleBlock;
import org.terraform.data.TerraformWorld;
import org.terraform.data.Wall;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.structure.room.CubeRoom;
import org.terraform.utils.BlockUtils;
import org.terraform.utils.GenUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

public class UndergroundDungeonPopulator extends SmallDungeonPopulator {
    private static final int MIN_DUNGEON_Y = 10;

    private static void dropDownBlock(@NotNull SimpleBlock block, @NotNull Material fluid) {
        if (block.isSolid()) {
            Material type = block.getType();
            block.setType(fluid);
            int depth = 0;
            while (!block.isSolid()) {
                block = block.getDown();
                depth++;
                if (depth > 50) {
                    return;
                }
            }

            block.getUp().setType(type);
        }
    }

    private static boolean hasSolidBurial(@NotNull PopulatorDataAbstract data, @NotNull CubeRoom room) {
        int minX = room.getX() - room.getWidthX() / 2;
        int maxX = room.getX() + room.getWidthX() / 2;
        int minY = room.getY();
        int maxY = room.getY() + room.getHeight();
        int minZ = room.getZ() - room.getWidthZ() / 2;
        int maxZ = room.getZ() + room.getWidthZ() / 2;

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    boolean insideCarvedInterior = x >= minX + 1
                                                   && x <= maxX - 1
                                                   && y >= minY + 1
                                                   && y <= maxY - 1
                                                   && z >= minZ + 1
                                                   && z <= maxZ - 1;
                    if (insideCarvedInterior) {
                        continue;
                    }
                    if (!data.getType(x, y, z).isSolid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static @NotNull CubeRoom createDungeonRoom(@NotNull Random rand, int x, int y, int z) {
        return new CubeRoom(
                GenUtils.randOddInt(rand, 9, 15),
                GenUtils.randOddInt(rand, 9, 15),
                GenUtils.randInt(rand, 5, 7),
                x,
                y,
                z
        );
    }

    @Override
    public void populate(@NotNull TerraformWorld tw, @NotNull PopulatorDataAbstract data) {
        MegaChunk mc = new MegaChunk(data.getChunkX(), data.getChunkZ());

        int[] spawnCoords = {data.getChunkX() * 16, data.getChunkZ() * 16};
        int[][] allCoords = getCoordsFromMegaChunk(tw, mc);
        for (int[] coords : allCoords) {
            if (coords[0] >> 4 == data.getChunkX() && coords[1] >> 4 == data.getChunkZ()) {
                spawnCoords = coords;
                break;
            }
        }

        int x = spawnCoords[0];// data.getChunkX()*16 + random.nextInt(16);
        int z = spawnCoords[1];// data.getChunkZ()*16 + random.nextInt(16);
        Random rand = this.getHashedRandom(tw, data.getChunkX(), data.getChunkZ());

        int y = HeightMap.getBlockHeight(tw, x, z) - GenUtils.randInt(
                rand,
                15,
                50
        );// GenUtils.getHighestGround(data, x, z)

        if (y < MIN_DUNGEON_Y) {
            y = MIN_DUNGEON_Y;
        }

        while (y >= MIN_DUNGEON_Y && !data.getType(x, y, z).isSolid()) {
            y--;
        }

        if (y < MIN_DUNGEON_Y) {
            return;
        }

        CubeRoom room = createDungeonRoom(rand, x, y, z);
        while (room.getY() >= MIN_DUNGEON_Y) {
            if (hasSolidBurial(data, room)) {
                spawnDungeonRoom(room, tw, rand, data);
                return;
            }

            int nextY = room.getY() - 1;
            while (nextY >= MIN_DUNGEON_Y && !data.getType(x, nextY, z).isSolid()) {
                nextY--;
            }
            room.setY(nextY);
        }
    }

    public void spawnDungeonRoom(int x,
                                 int y,
                                 int z,
                                 TerraformWorld tw,
                                 @NotNull Random rand,
                                 @NotNull PopulatorDataAbstract data)
    {
        spawnDungeonRoom(createDungeonRoom(rand, x, y, z), tw, rand, data);
    }

    public void spawnDungeonRoom(@NotNull CubeRoom room,
                                 TerraformWorld tw,
                                 @NotNull Random rand,
                                 @NotNull PopulatorDataAbstract data)
    {
        int x = room.getX();
        int y = room.getY();
        int z = room.getZ();
        TerraformGeneratorPlugin.logger.info("Spawning Underground Dungeon at " + x + "," + y + "," + z);
        boolean isWet = false;

        Material fluid = Material.CAVE_AIR;

        SimpleBlock center = room.getCenterSimpleBlock(data);
        if (BlockUtils.isWet(center.getUp())) {
            fluid = Material.WATER;
            isWet = true;
        }

        // Fill with water if the room is wet. If not, use cave air.
        room.fillRoom(data, -1, new Material[] {
                Material.COBBLESTONE, Material.MOSSY_COBBLESTONE
        }, fluid);

        // Make some fence pattern.
        for (Entry<Wall, Integer> entry : room.getFourWalls(data, 0).entrySet()) {
            Wall w = entry.getKey().getUp();
            int length = entry.getValue();
            while (length >= 0) {
                if (length % 2 != 0 && length != entry.getValue()) {
                    w.CAPillar(room.getHeight() - 3, rand, Material.COBBLESTONE_WALL, Material.MOSSY_COBBLESTONE_WALL);
                    if (isWet) {
                        w.waterlog(room.getHeight() - 3);
                    }
                }

                for (int h = 0; h < room.getHeight() - 3; h++) {
                    BlockUtils.correctSurroundingMultifacingData(w.getRelative(0, h, 0).get());
                }

                length--;
                w = w.getLeft();
            }
        }

        // Holes
        for (int i = 0; i < GenUtils.randInt(rand, 0, 3); i++) {
            int[] coords = room.randomCoords(rand);
            int nX = coords[0];
            int nY = coords[1];
            int nZ = coords[2];
            BlockUtils.replaceSphere(
                    rand.nextInt(992),
                    GenUtils.randInt(rand, 1, 3),
                    new SimpleBlock(data, nX, nY, nZ),
                    true,
                    fluid
            );
        }

        // Dropdown blocks
        for (int nx = -room.getWidthX() / 2; nx < room.getWidthX() / 2; nx++) {
            for (int nz = -room.getWidthZ() / 2; nz < room.getWidthZ() / 2; nz++) {
                int ny = room.getHeight();
                if (GenUtils.chance(10, 13)) {
                    continue;
                }
                dropDownBlock(new SimpleBlock(data, x + nx, y + ny, z + nz), fluid);
            }
        }

        // Make spikes from the ceiling
        for (int nx = -room.getWidthX() / 2; nx < room.getWidthX() / 2; nx++) {
            for (int nz = -room.getWidthZ() / 2; nz < room.getWidthZ() / 2; nz++) {
                int ny = room.getHeight() - 1;
                if (GenUtils.chance(9, 10)) {
                    continue;
                }
                for (int i = 0; i < GenUtils.randInt(rand, 1, room.getHeight() - 3); i++) {
                    data.setType(x + nx, y + ny, z + nz, GenUtils.randChoice(Material.COBBLESTONE,
                            Material.MOSSY_COBBLESTONE,
                            Material.COBBLESTONE_WALL,
                            Material.MOSSY_COBBLESTONE_WALL
                    ));
                    BlockUtils.correctSurroundingMultifacingData(new SimpleBlock(data, x + nx, y + ny, z + nz));
                }
            }
        }

        // Make spikes on the floor
        for (int nx = -room.getWidthX() / 2; nx < room.getWidthX() / 2; nx++) {
            for (int nz = -room.getWidthZ() / 2; nz < room.getWidthZ() / 2; nz++) {
                if (GenUtils.chance(9, 10)) {
                    continue;
                }
                for (int i = 0; i < GenUtils.randInt(rand, 1, room.getHeight() - 3); i++) {
                    Wall w = new Wall(new SimpleBlock(data, x + nx, y + 1, z + nz), BlockFace.NORTH);
                    w.LPillar(
                            room.getHeight() - 2,
                            rand,
                            Material.COBBLESTONE,
                            Material.MOSSY_COBBLESTONE,
                            Material.COBBLESTONE_WALL,
                            Material.MOSSY_COBBLESTONE_WALL
                    );
                    BlockUtils.correctSurroundingMultifacingData(w.get());
                }
            }
        }

        // Place Spawner
        EntityType type = switch (rand.nextInt(3)) {
            case (0) -> EntityType.ZOMBIE;
            case (1) -> EntityType.SKELETON;
            case (2) -> EntityType.SPIDER;
            default -> null;
        };
        if (isWet) {
            type = EntityType.DROWNED;
        }

        data.setSpawner(x, y + 1, z, type);

        // Spawn chests
        ArrayList<Entry<Wall, Integer>> entries = new ArrayList<>();
        HashMap<Wall, Integer> walls = room.getFourWalls(data, 1);
        for (Entry<Wall, Integer> entry : walls.entrySet()) {
            if (rand.nextBoolean()) {
                entries.add(entry);
            }
        }

        for (Entry<Wall, Integer> entry : entries) {
            Wall w = entry.getKey();
            int length = entry.getValue();
            int chest = GenUtils.randInt(1, length - 1);
            while (length >= 0) {
                if (length == chest) {
                    Directional dir = (Directional) Bukkit.createBlockData(Material.CHEST);
                    dir.setFacing(w.getDirection());

                    if (isWet && dir instanceof Waterlogged) {
                        ((Waterlogged) dir).setWaterlogged(true);
                    }

                    w.setBlockData(dir);
                    data.lootTableChest(w.get().getX(), w.get().getY(), w.get().getZ(), TerraLootTable.SIMPLE_DUNGEON);
                }
                length--;
                w = w.getLeft();
            }
        }
    }

}
