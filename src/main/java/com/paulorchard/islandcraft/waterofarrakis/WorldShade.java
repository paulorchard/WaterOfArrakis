package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.Arrays;

/**
 * Reads the real world for {@link SunShade}. Block ids are resolved to a {@link SunShade.Kind} once and remembered
 * (a block's material and opacity never change while the game runs), and the last chunk is kept between lookups
 * because a ray stays in one chunk for most of its length. One instance per probe user, bind it to a world with
 * {@link #of}.
 */
final class WorldShade implements SunShade.BlockOpacityLookup {

    /** Shared by every instance. 0 = not looked up yet, otherwise Kind ordinal + 1. Races are harmless: same answer. */
    private static volatile byte[] kinds = new byte[8192];

    private World world;
    private long cachedIndex = Long.MIN_VALUE;
    private WorldChunk cachedChunk;

    WorldShade of(World world) {
        if (this.world != world) {
            this.world = world;
            this.cachedIndex = Long.MIN_VALUE;
            this.cachedChunk = null;
        }
        return this;
    }

    /** Forgets the chunk kept from the last walk: it may have unloaded since. Call once per check. */
    void reset() {
        cachedIndex = Long.MIN_VALUE;
        cachedChunk = null;
    }

    @Override
    public SunShade.Kind opacityAt(int x, int y, int z) {
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return SunShade.Kind.OPEN;
        }
        long index = ChunkUtil.indexChunkFromBlock(x, z);
        if (index != cachedIndex) {
            cachedChunk = world.getChunkIfLoaded(index);
            cachedIndex = index;
        }
        if (cachedChunk == null) {
            return SunShade.Kind.UNLOADED;
        }
        return kindOf(cachedChunk.getBlock(x, y, z));
    }

    @Override
    public int maxY() {
        return ChunkUtil.HEIGHT_MINUS_1;
    }

    /** What a block id does to a ray; air and unknown ids are open. */
    static SunShade.Kind kindOf(int blockId) {
        if (blockId <= 0) {
            return SunShade.Kind.OPEN;
        }
        byte[] table = kinds;
        if (blockId < table.length && table[blockId] != 0) {
            return SunShade.Kind.values()[table[blockId] - 1];
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        SunShade.Kind kind = type == null ? SunShade.Kind.OPEN : SunShade.classify(type.getMaterial(), type.getOpacity());
        if (blockId >= table.length) {
            table = Arrays.copyOf(table, Math.max(blockId + 1, table.length * 2));
            kinds = table;
        }
        table[blockId] = (byte) (kind.ordinal() + 1);
        return kind;
    }

    /** For /sunprobe: what a block reports, in words. */
    static String describe(int blockId) {
        if (blockId <= 0) {
            return "air";
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null) {
            return "unknown id " + blockId;
        }
        return type.getId() + " (material " + type.getMaterial() + ", opacity " + type.getOpacity() + ", draw "
                + type.getDrawType() + ") -> " + kindOf(blockId);
    }
}
