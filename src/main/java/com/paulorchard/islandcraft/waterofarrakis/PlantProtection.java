package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.CancellableEcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Keeps primroses and burrowbushes from being broken or picked up by normal means.
 *
 * <p>Two layers. The block assets have no {@code Gathering} section, so the game has nothing to break them with and
 * no loot to drop (the same way Dunes of Arrakis makes its sand undiggable). On top of that, break and damage events
 * on these blocks are cancelled here. A block whose support is removed is not broken by a player, so it still breaks
 * (with no loot, as there is no drop list).
 *
 * <p><b>Hook for the future harvesting tool:</b> {@link #addBypass(Predicate)}. If any registered predicate accepts the
 * item in the player's hand, the break is allowed. The assets would also need a {@code Gathering} section for the
 * tool to get anything, which is the tool mod's job.
 */
public final class PlantProtection {

    public static final String PRIMROSE = "Arrakis_Primrose";
    public static final String BURROWBUSH = "Arrakis_Burrowbush";

    private static final List<Predicate<ItemStack>> BYPASS = new CopyOnWriteArrayList<>();

    private PlantProtection() {
    }

    /** Registers a test for items that may break these plants (the future specialised tool). */
    public static void addBypass(Predicate<ItemStack> tool) {
        BYPASS.add(tool);
    }

    public static void removeBypass(Predicate<ItemStack> tool) {
        BYPASS.remove(tool);
    }

    static boolean isPrimrose(BlockType block) {
        return PRIMROSE.equals(block.getId());
    }

    static boolean isBurrowbush(BlockType block) {
        return block.getId() != null && (BURROWBUSH.equals(block.getId())
                || block.getId().startsWith("*" + BURROWBUSH));
    }

    static boolean isProtected(BlockType block) {
        return block != null && (isPrimrose(block) || isBurrowbush(block));
    }

    private static boolean allowed(ItemStack inHand) {
        if (inHand == null || inHand.isEmpty()) {
            return false;
        }
        for (Predicate<ItemStack> tool : BYPASS) {
            if (tool.test(inHand)) {
                return true;
            }
        }
        return false;
    }

    /** Cancels block breaking. */
    static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        Break() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            if (isProtected(event.getBlockType()) && !allowed(event.getItemInHand())) {
                ((CancellableEcsEvent) event).setCancelled(true);
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }

    /** Cancels the damage that would eventually break the block, so no crack animation builds up. */
    static final class Damage extends EntityEventSystem<EntityStore, DamageBlockEvent> {
        Damage() {
            super(DamageBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            if (isProtected(event.getBlockType()) && !allowed(event.getItemInHand())) {
                ((CancellableEcsEvent) event).setCancelled(true);
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }
}
