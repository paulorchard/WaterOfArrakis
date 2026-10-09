package com.paulorchard.islandcraft.waterofarrakis;

/**
 * What a named modifier changes. Modifiers of one type from different ids combine: the multipliers multiply, the
 * offset adds.
 */
public enum ModifierType {
    /** Multiplies exposure gained in the sun. 0.5 halves it. Default 1. */
    EXPOSURE_GAIN_MULTIPLIER(1.0),
    /** Multiplies exposure lost out of the sun. 2 doubles it. Default 1. */
    EXPOSURE_DECAY_MULTIPLIER(1.0),
    /** Multiplies the baseline (idle) water drain. Default 1. */
    WATER_DRAIN_MULTIPLIER(1.0),
    /** Multiplies the water drain of actions (running, climbing, jumping). Default 1. */
    WATER_ACTION_DRAIN_MULTIPLIER(1.0),
    /**
     * Multiplies how sunlit the player is (the sun fraction, 0..1) before exposure is gained. 0.3 under storm
     * clouds, 0 for a full cover. Set it for one player, or for a whole world with
     * {@link WaterService#setWorldSunIntensity}. Default 1.
     */
    SUN_INTENSITY_MULTIPLIER(1.0),
    /**
     * Multiplies the player's movement speed (walk, run, sprint, strafe): 0.9 is 10% slower. The mod itself registers
     * one under the id "Arrakis:ZeroWater" while water is 0; others stack with it (the product is used, snapped to
     * the nearest 5% and clamped to 5%..100%). Default 1.
     */
    MOVEMENT_SPEED_MULTIPLIER(1.0),
    /**
     * Multiplies the stamina regeneration pause (see StaminaCurves: 1.0 at full water and no exposure, up to 8 at 0
     * water). A cooling item might use 0.7. Default 1.
     */
    STAMINA_PAUSE_MULTIPLIER(1.0),
    /** Multiplies how fast stamina regenerates (after the water and heat curve). Default 1. */
    STAMINA_REGEN_MULTIPLIER(1.0),
    /** Multiplies the water that each point of regenerated stamina costs. 0 makes catching your breath free. Default 1. */
    STAMINA_REGEN_WATER_COST_MULTIPLIER(1.0),
    /** Flat exposure change in percent per second, added after gain and decay. Negative cools. Default 0. */
    EXPOSURE_OFFSET(0.0);

    private final double neutral;

    ModifierType(double neutral) {
        this.neutral = neutral;
    }

    /** The value that changes nothing. */
    public double neutral() {
        return neutral;
    }

    public boolean isMultiplier() {
        return this != EXPOSURE_OFFSET;
    }
}
