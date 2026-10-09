package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Decides whether a player stands in direct sunlight: the sun is up, and nothing solid is above the head.
 *
 * <p>Day: {@link WorldTimeResource#getSunlightFactor()} (0 at night, 1 at midday) against
 * {@code MinSunlightFactor}. Cover: the chunk height map, which holds the y of the highest block of the column; the
 * player is in the open when the head is above it. Leaves and roofs shade, grass and flowers should not (not seen in
 * game). The stored sky light (a per-block 0..15 value that does not change with the time of day) is not used: the
 * height map answers the same question with one lookup and no dependence on light having been calculated.
 *
 * <p>Weather and cloud cover are not considered: the server exposes no cloud coverage, only the weather asset's
 * sunlight damping, which no vanilla weather sets.
 */
final class SunProbe {

    /** Eye height above the feet, in blocks. */
    private static final double HEAD = 1.6;

    private SunProbe() {
    }

    /** True when the sun is up (ignores cover). */
    static boolean sunIsUp(Store<EntityStore> store, WaterOfArrakisConfig cfg) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        return time != null && time.getSunlightFactor() >= cfg.getMinSunlightFactor();
    }

    /** True when nothing solid is above the player's head. A column that is not loaded counts as covered. */
    static boolean open(World world, Store<EntityStore> store, Ref<EntityStore> ref, WaterOfArrakisConfig cfg) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d p = transform.getPosition();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        int top = chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
        int head = (int) Math.floor(p.y + HEAD);
        return head > top + cfg.getSkyClearBlocks();
    }

    static boolean inDirectSun(World world, Store<EntityStore> store, Ref<EntityStore> ref, WaterOfArrakisConfig cfg) {
        return sunIsUp(store, cfg) && open(world, store, ref, cfg);
    }
}
