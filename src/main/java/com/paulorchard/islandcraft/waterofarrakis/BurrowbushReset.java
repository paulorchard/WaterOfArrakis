package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Iterator;
import java.util.Map;

/**
 * The daily rollover for burrowbushes. Every couple of seconds it looks at the used bushes of the world; any whose
 * use day is before the current in-game day is switched back to its normal state and forgotten. A bush in a chunk
 * that is not loaded stays on the list until the chunk loads, so a bush that was used before a restart (or a long
 * absence) is reset when the player comes back, not lost.
 */
final class BurrowbushReset extends TickingSystem<EntityStore> {

    private static final double INTERVAL_SECONDS = 2.0;

    private double timer;

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        timer += dt;
        if (timer < INTERVAL_SECONDS) {
            return;
        }
        timer = 0;
        BurrowbushUsage usage = BurrowbushUsage.of(store);
        if (usage == null || usage.used.isEmpty()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        long today = GameClock.day(store);
        Iterator<Map.Entry<String, Long>> it = usage.used.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() >= today) {
                continue;
            }
            String[] p = entry.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            if (world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z)) == null) {
                continue;
            }
            setDepleted(world, x, y, z, false);
            it.remove();
        }
    }

    /** Switches a burrowbush at this position to Depleted or back to its normal state. No-op if it is not a bush. */
    static void setDepleted(World world, int x, int y, int z, boolean depleted) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }
        BlockType block = chunk.getBlockType(x, y, z);
        if (block == null || !PlantProtection.isBurrowbush(block)) {
            return;
        }
        chunk.setBlockInteractionState(new Vector3i(x, y, z), block,
                depleted ? BurrowbushInteraction.DEPLETED : "default");
    }
}
