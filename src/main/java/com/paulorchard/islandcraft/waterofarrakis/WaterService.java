package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The public API of Water of Arrakis. Other mods (equipment, tools, vendors) get it with
 * {@link WaterOfArrakisPlugin#service()} and then read and change a player's water and exposure, register modifiers
 * and listen for changes.
 *
 * <p><b>Units.</b> Water and exposure are floats from 0 to 100 (percent of the bar). Every setter clamps.
 *
 * <p><b>Threads.</b> A player's values live in that player's world store. Call the getters and setters on the world
 * thread of the player (from a system, an interaction, a command, or inside {@code world.execute(...)}). Modifiers
 * and listeners are thread safe and can be registered from anywhere.
 *
 * <p><b>Modifiers.</b> A modifier has an id you choose (use your mod's name, for example {@code "MyMod:CoolingVest"}),
 * a {@link ModifierType} and a value. Two mods using different ids stack: multipliers multiply, offsets add. Using the
 * same id and type again replaces your earlier value. Modifiers are not saved: register them again when the player
 * joins or equips the item.
 */
public final class WaterService {

    public static final double MIN = 0.0;
    public static final double MAX = 100.0;

    private final Supplier<WaterOfArrakisConfig> config;
    private final CopyOnWriteArrayList<WaterListener> listeners = new CopyOnWriteArrayList<>();
    /** player -> modifier id -> type -> value. */
    private final Map<UUID, Map<String, Map<ModifierType, Double>>> modifiers = new ConcurrentHashMap<>();
    /** player -> sun fraction of the last tick. */
    private final Map<UUID, Double> sunFractions = new ConcurrentHashMap<>();

    WaterService(Supplier<WaterOfArrakisConfig> config) {
        this.config = config;
    }

    // ------------------------------------------------------------------ reading and writing values

    /** Current water, 0..100. A player who has not been seen yet reports the configured starting water. */
    public double getWater(PlayerRef player) {
        WaterState state = stateOrNull(player);
        return state == null ? config.get().getInitialWater() : state.water;
    }

    /** Current exposure, 0..100. */
    public double getExposure(PlayerRef player) {
        WaterState state = stateOrNull(player);
        return state == null ? config.get().getInitialExposure() : state.exposure;
    }

    /** Sets water (clamped to 0..100). Returns the value now stored. */
    public double setWater(PlayerRef player, double value) {
        WaterState state = state(player);
        return state == null ? value : applyWater(player, state, value);
    }

    /** Adds to water (negative removes), clamped. Returns the value now stored. */
    public double addWater(PlayerRef player, double amount) {
        WaterState state = state(player);
        return state == null ? amount : applyWater(player, state, state.water + amount);
    }

    /** Sets exposure (clamped to 0..100). Returns the value now stored. */
    public double setExposure(PlayerRef player, double value) {
        WaterState state = state(player);
        return state == null ? value : applyExposure(player, state, value);
    }

    /** Adds to exposure (negative removes), clamped. Returns the value now stored. */
    public double addExposure(PlayerRef player, double amount) {
        WaterState state = state(player);
        return state == null ? amount : applyExposure(player, state, state.exposure + amount);
    }

    // ------------------------------------------------------------------ derived values

    /** Water tier of the player now: 0 is best. See {@code WaterTierLowerBounds} in the config for the edges. */
    public int getWaterTier(PlayerRef player) {
        return config.get().waterTier(getWater(player));
    }

    /** Exposure step: floor(exposure / ExposureDrainStepPercent). 0 below 10%, 10 at 100% by default. */
    public int getExposureStep(PlayerRef player) {
        return exposureStep(getExposure(player));
    }

    /** The water drain multiplier that exposure alone gives right now (1.0 to 2.0 by default). */
    public double getExposureDrainMultiplier(PlayerRef player) {
        return config.get().exposureDrainMultiplier(getExposure(player));
    }

    // ------------------------------------------------------------------ modifiers

    /**
     * Registers or replaces a modifier. For multiplier types the value is the factor (0.5 halves, 2 doubles); for
     * {@link ModifierType#EXPOSURE_OFFSET} it is percent of exposure per second.
     */
    public void setModifier(UUID player, String id, ModifierType type, double value) {
        modifiers.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .put(type, value);
    }

    public void setModifier(PlayerRef player, String id, ModifierType type, double value) {
        setModifier(player.getUuid(), id, type, value);
    }

    /** Removes one modifier of this id and type. */
    public void removeModifier(UUID player, String id, ModifierType type) {
        Map<String, Map<ModifierType, Double>> byId = modifiers.get(player);
        if (byId != null && byId.get(id) != null) {
            byId.get(id).remove(type);
        }
    }

    /** Removes every modifier registered under this id (for example when your item is unequipped). */
    public void removeModifiers(UUID player, String id) {
        Map<String, Map<ModifierType, Double>> byId = modifiers.get(player);
        if (byId != null) {
            byId.remove(id);
        }
    }

    public void removeModifiers(PlayerRef player, String id) {
        removeModifiers(player.getUuid(), id);
    }

    /** The combined value of every modifier of this type: product for multipliers, sum for the offset. */
    public double getModifier(UUID player, ModifierType type) {
        double result = type.neutral();
        Map<String, Map<ModifierType, Double>> byId = modifiers.get(player);
        if (byId == null) {
            return result;
        }
        for (Map<ModifierType, Double> byType : byId.values()) {
            Double value = byType.get(type);
            if (value != null) {
                result = type.isMultiplier() ? result * value : result + value;
            }
        }
        return result;
    }

    public double getModifier(PlayerRef player, ModifierType type) {
        return getModifier(player.getUuid(), type);
    }

    /** Every combined modifier of a player, for debugging. */
    public Map<ModifierType, Double> getModifiers(UUID player) {
        Map<ModifierType, Double> all = new EnumMap<>(ModifierType.class);
        for (ModifierType type : ModifierType.values()) {
            all.put(type, getModifier(player, type));
        }
        return all;
    }

    // ------------------------------------------------------------------ sun

    /**
     * How sunlit the player was on the last simulation tick, 0 (shade or night) to 1 (full sun), after the sun
     * intensity modifiers. Read only; 0 for a player not yet simulated. Gain in exposure is proportional to it.
     */
    public double getSunFraction(PlayerRef player) {
        return sunFractions.getOrDefault(player.getUuid(), 0.0);
    }

    public double getSunFraction(UUID player) {
        return sunFractions.getOrDefault(player, 0.0);
    }

    /**
     * Dims the sun for every player in a world: for example the storm mod sets 0.2 while a Coriolis storm blows and
     * removes it afterwards. Same id rules as {@link #setModifier}: the id is yours, the value replaces your last one.
     *
     * @param worldId the world's UUID ({@code world.getWorldConfig().getUuid()})
     */
    public void setWorldSunIntensity(UUID worldId, String id, double multiplier) {
        setModifier(worldId, id, ModifierType.SUN_INTENSITY_MULTIPLIER, multiplier);
    }

    public void removeWorldSunIntensity(UUID worldId, String id) {
        removeModifier(worldId, id, ModifierType.SUN_INTENSITY_MULTIPLIER);
    }

    void setSunFraction(UUID player, double fraction) {
        sunFractions.put(player, fraction);
    }

    // ------------------------------------------------------------------ listeners

    public void addListener(WaterListener listener) {
        listeners.add(listener);
    }

    public void removeListener(WaterListener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ internals (also used by the simulation)

    void forget(UUID player) {
        modifiers.remove(player);
        sunFractions.remove(player);
    }

    int exposureStep(double exposure) {
        return (int) Math.floor(exposure / Math.max(1e-6, config.get().getExposureDrainStepPercent()));
    }

    double applyWater(PlayerRef player, WaterState state, double value) {
        double clamped = clamp(value);
        double old = state.water;
        if (clamped == old) {
            return old;
        }
        state.water = (float) clamped;
        double now = state.water;
        for (WaterListener l : listeners) {
            l.onWaterChanged(player, old, now);
        }
        int oldTier = config.get().waterTier(old);
        int newTier = config.get().waterTier(now);
        if (oldTier != newTier) {
            for (WaterListener l : listeners) {
                l.onWaterTierChanged(player, oldTier, newTier);
            }
        }
        return now;
    }

    double applyExposure(PlayerRef player, WaterState state, double value) {
        double clamped = clamp(value);
        double old = state.exposure;
        if (clamped == old) {
            return old;
        }
        state.exposure = (float) clamped;
        double now = state.exposure;
        for (WaterListener l : listeners) {
            l.onExposureChanged(player, old, now);
        }
        int oldStep = exposureStep(old);
        int newStep = exposureStep(now);
        if (oldStep != newStep) {
            for (WaterListener l : listeners) {
                l.onExposureStepChanged(player, oldStep, newStep);
            }
        }
        return now;
    }

    /** The saved state of a player, created with the configured start values on first use. Null if not in a world. */
    WaterState state(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        // Adding a component is a structural change the store refuses while it is running a system (an interaction,
        // for instance). The simulation tick adds it to every player outside of that, so reading is enough here.
        WaterState state = store.getComponent(ref, WaterState.getComponentType());
        if (state == null) {
            try {
                state = store.ensureAndGetComponent(ref, WaterState.getComponentType());
            } catch (IllegalStateException busy) {
                return null;
            }
        }
        if (!state.initialised) {
            state.initialised = true;
            if (state.water == 100f && state.exposure == 0f) {
                state.water = (float) clamp(config.get().getInitialWater());
                state.exposure = (float) clamp(config.get().getInitialExposure());
            }
        }
        return state;
    }

    private WaterState stateOrNull(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref.getStore().getComponent(ref, WaterState.getComponentType());
    }

    static double clamp(double value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
