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
