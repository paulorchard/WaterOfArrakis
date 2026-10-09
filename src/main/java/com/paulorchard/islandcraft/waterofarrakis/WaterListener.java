package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Callbacks for changes to a player's water and exposure. Register with {@link WaterService#addListener}. All methods
 * are optional. They run on the world thread of the player, possibly every tick, so keep them cheap and do not block.
 */
public interface WaterListener {

    /** Water changed. Fired for every change, including the slow drain each tick. Values are 0..100. */
    default void onWaterChanged(PlayerRef player, double oldValue, double newValue) {
    }

    /** Exposure changed. Fired for every change, including the gain and decay each tick. Values are 0..100. */
    default void onExposureChanged(PlayerRef player, double oldValue, double newValue) {
    }

    /**
     * The water tier changed (tier 0 is the best, see {@code WaterTierLowerBounds} in the config), by drinking,
     * draining, or a command.
     */
    default void onWaterTierChanged(PlayerRef player, int oldTier, int newTier) {
    }

    /**
     * Exposure crossed into a different drain step (every {@code ExposureDrainStepPercent}, so every 10% by default).
     * The water drain multiplier changes with the step.
     */
    default void onExposureStepChanged(PlayerRef player, int oldStep, int newStep) {
    }
}
