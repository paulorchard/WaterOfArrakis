package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which burrowbushes of one world have been used, and on which in-game day. Saved with the world (the same
 * mechanism as the other IslandCraft mods' world resources), so it survives chunk unloads and server restarts. The
 * bush's own look (the Depleted block state) is saved in the chunk; this record is what lets the daily rollover find
 * the bushes to reset.
 */
public class BurrowbushUsage implements Resource<EntityStore> {

    public static final String ID = "WaterOfArrakis_Burrowbush";

    public static final BuilderCodec<BurrowbushUsage> CODEC = BuilderCodec
            .builder(BurrowbushUsage.class, BurrowbushUsage::new)
            .append(new KeyedCodec<>("Used", Codec.STRING_ARRAY, false),
                    (u, v) -> u.load(v), u -> u.save())
            .add()
            .build();

    private static ResourceType<EntityStore, BurrowbushUsage> type;

    /** "x,y,z" -> in-game epoch day of the last use. */
    final Map<String, Long> used = new HashMap<>();

    static void setResourceType(ResourceType<EntityStore, BurrowbushUsage> resourceType) {
        type = resourceType;
    }

    static BurrowbushUsage of(Store<EntityStore> store) {
        return store.getResource(type);
    }

    static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private void load(String[] values) {
        used.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            int cut = value.lastIndexOf('=');
            if (cut > 0) {
                try {
                    used.put(value.substring(0, cut), Long.parseLong(value.substring(cut + 1)));
                } catch (NumberFormatException ignored) {
                    // a damaged entry is dropped; the bush then simply counts as unused
                }
            }
        }
    }

    private String[] save() {
        List<String> out = new ArrayList<>();
        used.forEach((k, v) -> out.add(k + "=" + v));
        return out.toArray(new String[0]);
    }

    @Override
    public Resource<EntityStore> clone() {
        BurrowbushUsage copy = new BurrowbushUsage();
        copy.used.putAll(used);
        return copy;
    }
}
