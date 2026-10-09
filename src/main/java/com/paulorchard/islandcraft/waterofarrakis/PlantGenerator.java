package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;

import java.util.Random;

/**
 * Places primroses and burrowbushes on the slopes of island rock.
 *
 * <p><b>Directions.</b> East is +X and north is -Z (the other IslandCraft mods use the same axes). A column is on a
 * north-facing slope when the ground falls toward -Z: the height one block to the north is lower than the height one
 * block to the south by at least 2 x MinSlope. A south-facing slope is the opposite. The test only reads the height
 * map inside the chunk (the rows at the chunk edge are skipped), so it never waits for a neighbouring chunk.
 *
 * <p><b>What counts as island rock:</b> the top block's id starts with RockPrefix ("Rock_"), the rule the Dunes of
 * Arrakis spawn search uses for exposed rock. Sand dunes never qualify. The Dunes of Arrakis world generator has no
 * placement hooks to reuse (its biome has an empty Props list and its terrain is a data-only density graph), so the
 * plants go in when a chunk is first generated, from a plugin event, and the generator is not touched.
 *
 * <p>Placement is deterministic: the same seed and column always give the same answer, so running it again over a
 * chunk that already has its plants adds nothing.
 */
final class PlantGenerator {

    private static final int SIZE = 32;

    private PlantGenerator() {
    }

    /** True when this world's generator type is one of GeneratorTypes (or the list is empty). */
    static boolean appliesTo(World world, WaterOfArrakisConfig cfg) {
        String[] allowed = cfg.getGeneratorTypes();
        if (allowed.length == 0) {
            return true;
        }
        IWorldGenProvider provider = world.getWorldConfig().getWorldGenProvider();
        String type = provider == null ? null : IWorldGenProvider.CODEC.getIdFor(provider.getClass());
        for (String name : allowed) {
            if (name.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** Places plants in one chunk. Returns how many were placed. */
    static int populate(World world, WorldChunk chunk, WaterOfArrakisConfig cfg) {
        long seed = world.getWorldConfig().getSeed();
        int baseX = chunk.getX() * SIZE;
        int baseZ = chunk.getZ() * SIZE;
        int placed = 0;
        for (int lz = 1; lz < SIZE - 1; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                int top = chunk.getHeight(lx, lz);
                int x = baseX + lx;
                int z = baseZ + lz;
                if (!isRock(chunk.getBlockType(x, top, z), cfg) || chunk.getBlock(x, top + 1, z) != 0) {
                    continue;
                }
                int north = chunk.getHeight(lx, lz - 1);
                int south = chunk.getHeight(lx, lz + 1);
                double slope = (south - north) / 2.0;
                String plant = null;
                double chance = 0;
                if (slope >= cfg.getMinSlope()) {
                    plant = PlantProtection.PRIMROSE;
                    chance = cfg.getPrimroseChance();
                } else if (-slope >= cfg.getMinSlope()) {
                    plant = PlantProtection.BURROWBUSH;
                    chance = cfg.getBurrowbushChance();
                }
                if (plant != null && roll(seed, x, z) < chance) {
                    if (chunk.setBlock(x, top + 1, z, plant)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean isRock(BlockType block, WaterOfArrakisConfig cfg) {
        return block != null && block.getId() != null && block.getId().startsWith(cfg.getRockPrefix());
    }

    /** A number in [0, 1) that depends only on the seed and the column. */
    private static double roll(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        return new Random(h).nextDouble();
    }
}
