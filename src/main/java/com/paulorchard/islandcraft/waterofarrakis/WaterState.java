package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * A player's saved water and exposure, 0 to 100 each. This is a component on the player entity, so it is written
 * with the player and comes back after a relog. Everything that is not worth saving (timers, modifiers, the HUD)
 * lives in {@link WaterService}.
 *
 * <p>Other mods should use {@link WaterService}, not this class: it clamps, fires the events and applies modifiers.
 */
public class WaterState implements Component<EntityStore> {

    public static final BuilderCodec<WaterState> CODEC = BuilderCodec.builder(WaterState.class, WaterState::new)
            .append(new KeyedCodec<>("Water", Codec.FLOAT, false), (s, v) -> s.water = v, s -> s.water)
            .add()
            .append(new KeyedCodec<>("Exposure", Codec.FLOAT, false), (s, v) -> s.exposure = v, s -> s.exposure)
            .add()
            .build();

    private static ComponentType<EntityStore, WaterState> type;

    float water = 100f;
    float exposure = 0f;
    /** Set once the start values from the config have been applied to a brand new player. */
    transient boolean initialised;

    public WaterState() {
    }

    static void setComponentType(ComponentType<EntityStore, WaterState> componentType) {
        type = componentType;
    }

    public static ComponentType<EntityStore, WaterState> getComponentType() {
        return type;
    }

    public float getWater() {
        return water;
    }

    public float getExposure() {
        return exposure;
    }

    @Override
    public Component<EntityStore> clone() {
        WaterState copy = new WaterState();
        copy.water = water;
        copy.exposure = exposure;
        copy.initialised = initialised;
        return copy;
    }
}
