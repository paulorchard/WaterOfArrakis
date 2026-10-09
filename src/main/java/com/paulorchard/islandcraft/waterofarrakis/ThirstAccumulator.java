package com.paulorchard.islandcraft.waterofarrakis;

/**
 * The thirst damage rule, with no engine types so it is unit tested. One instance per player.
 *
 * <p>Water that was due to be drained but could not be (the player has none left) becomes health damage, but only
 * while water AND stamina are both 0: stamina is spent first. In that state a stamina cost that cannot be paid
 * (sprinting, a jump, a vault, climbing) is taken as HP too. The damage is not dealt every tick (that would repeat the
 * hurt flash and sound many times a second): it collects here and is dealt at most once per interval, in whole steps
 * of {@code minHit} HP, the fraction carrying over.
 *
 * <p>The accumulator is not saved. On logout it is dropped, so a player never logs back in to a hit that was owed.
 * It is also cleared as soon as the player has water again.
 */
final class ThirstAccumulator {

    private double owed;
    private double sinceHit;

    /** HP waiting to be dealt, for /waterdebug. */
    double owed() {
        return owed;
    }

    /**
     * Call once per simulated tick. Returns the HP to deal now, usually 0.
     *
     * @param unpaidWater water that was due this tick and could not be taken
     * @param unpaidStaminaHp HP owed for stamina costs (sprint, jump, vault, climb) that could not be paid
     * @param waterZero   water is 0 after this tick's drain
     * @param staminaZero stamina is 0 (or below)
     * @param perUnit     HP per unit of unpaid water
     * @param interval    seconds between hits at the least
     * @param minHit      step size in HP; 0 deals the whole accumulated amount
     */
    double step(double dt, double unpaidWater, double unpaidStaminaHp, boolean waterZero, boolean staminaZero,
                double perUnit, double interval, double minHit) {
        sinceHit += dt;
        if (!waterZero) {
            owed = 0;
            return 0;
        }
        if (staminaZero) {
            owed += Math.max(0.0, unpaidWater) * perUnit + Math.max(0.0, unpaidStaminaHp);
        }
        if (!staminaZero || sinceHit < interval) {
            return 0;
        }
        double hit = minHit >= 1.0 ? Math.floor(owed / minHit) * minHit : owed;
        if (hit <= 0) {
            return 0;
        }
        owed -= hit;
        sinceHit = 0;
        return hit;
    }

    void reset() {
        owed = 0;
        sinceHit = 0;
    }
}
