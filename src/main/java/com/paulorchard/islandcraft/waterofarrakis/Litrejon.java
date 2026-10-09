package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Litrejon: a reusable flask of up to {@code LitrejonCapacity} (500) units of water. Its amount is saved on the
 * item stack as metadata {@value #KEY}, and mirrored to the stack's durability so the item shows a bar and the
 * amount in its tooltip.
 *
 * <p><b>Extension hook:</b> other mods fill flasks by registering a {@link FillSource} with
 * {@link #addFillSource}. When a player uses a Litrejon, the sources are asked in order; the first that returns
 * a positive number of units has filled it. The built-in source is looking at a water block.
 */
public final class Litrejon {

    public static final String ITEM_ID = "Arrakis_Litrejon";
    public static final String KEY = "Arrakis_Water_Units";

    /** Something a flask can be filled from: a water block, a well, a rain barrel, an equipment perk. */
    public interface FillSource {
        /**
         * Called when a player uses a Litrejon that has room. Return how many units are available (the flask
         * takes what fits, up to {@code room}), or 0 if this source does not apply. Runs on the world thread.
         */
        double offer(PlayerRef player, Ref<EntityStore> ref, CommandBuffer<EntityStore> buffer, World world, double room);
    }

    private static final List<FillSource> SOURCES = new CopyOnWriteArrayList<>();

    private Litrejon() {
    }

    public static void addFillSource(FillSource source) {
        SOURCES.add(source);
    }

    public static void removeFillSource(FillSource source) {
        SOURCES.remove(source);
    }

    static List<FillSource> fillSources() {
        return SOURCES;
    }

    public static boolean isLitrejon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ITEM_ID.equals(stack.getItemId());
    }

    public static double capacity() {
        return WaterOfArrakisPlugin.get().config().getLitrejonCapacity();
    }

    /** Units of water in the stack. A stack with no saved amount is full or empty per {@code LitrejonStartsFull}. */
    public static double getAmount(ItemStack stack) {
        Integer saved = stack.getFromMetadataOrNull(KEY, Codec.INTEGER);
        if (saved == null) {
            return WaterOfArrakisPlugin.get().config().isLitrejonStartsFull() ? capacity() : 0;
        }
        return Math.max(0, Math.min(capacity(), saved));
    }

    /** A copy of the stack holding this many units (clamped to 0..capacity), durability bar included. */
    public static ItemStack withAmount(ItemStack stack, double units) {
        double clamped = Math.max(0, Math.min(capacity(), units));
        return stack.withMetadata(KEY, Codec.INTEGER, (int) Math.round(clamped))
                .withMaxDurability(capacity())
                .withDurability(clamped);
    }
}
