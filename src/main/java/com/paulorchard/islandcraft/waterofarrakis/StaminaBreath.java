package com.paulorchard.islandcraft.waterofarrakis;

/**
 * The rules of the stamina rework that decide numbers, with no engine types so they are unit tested.
 *
 * <p>Two stats are involved. Stamina (max 10) and the pause stat StaminaRegenDelay (-60 to 0, refilled by the engine at
 * +1 per second; stamina regenerates only while it is 0). The stat is kept in "vanilla seconds": a value of -X is an X
 * second pause at vanilla speed. The mod lengthens a pause by giving back only {@code 1 / P} of each refill step, so the
 * pause left is {@code |stat| x P} seconds at the current water and exposure.
 */
final class StaminaBreath {

    private StaminaBreath() {
    }

    /** A rise of the pause stat bigger than this in one tick is an action writing a value, not the engine refilling it. */
    static final double REFILL_STEP_MAX = 0.35;

    /**
     * Is this stamina rise natural regeneration? It is a whole number of regeneration steps (1 to {@code maxSteps}) of
     * {@code stepAmount} (0.3), within {@code tolerance}, on a tick where nothing blocked regeneration. Anything else (a
     * potion, a food, a regenerating food that adds 0.5 or 1.5) is a restore.
     */
    static boolean isNaturalRegen(double rise, double stepAmount, int maxSteps, double tolerance, boolean allowed) {
        if (!allowed || rise <= 0 || stepAmount <= 0) {
            return false;
        }
        double steps = rise / stepAmount;
        long whole = Math.round(steps);
        return whole >= 1 && whole <= maxSteps && Math.abs(rise - whole * stepAmount) <= tolerance;
    }

    /** The outcome of one natural regeneration lump: what stamina keeps and what it costs in water. */
    record Regen(double points, double waterCost) {
    }

    /**
     * Applies the regeneration speed multiplier to a natural lump and charges the water for what actually came back.
     * If the water left cannot pay for it, only the share it can pay is given and the water goes to 0.
     *
     * @param lump          the rise the engine made this tick (already clamped at the stamina maximum)
     * @param speed         regeneration speed multiplier R
     * @param costPerPoint  water per stamina point
     * @param water         water the player has
     */
    static Regen regen(double lump, double speed, double costPerPoint, double water) {
        double points = Math.max(0.0, lump) * Math.max(0.0, speed);
        double cost = points * costPerPoint;
        if (cost <= 0) {
            return new Regen(points, 0.0);
        }
        if (cost > water) {
            double share = Math.max(0.0, water) / cost;
            return new Regen(points * share, Math.max(0.0, water));
        }
        return new Regen(points, cost);
    }

    /**
     * What the pause stat becomes after the engine refilled it from {@code previous} to {@code now}: only {@code 1 / P} of
     * a refill step is kept. A rise that is too big to be a refill step (an action wrote a new value) is left as written.
     *
     * <p>The engine clamps the stat at its maximum (0), so the step that reaches 0 is smaller than a full step. If the
     * rise were scaled the pause would approach 0 forever and never end (each step keeps a fraction of what is left). So
     * when the engine has reached the maximum, a full nominal step ({@code refillStep}, 0.1) is what is scaled, and the
     * result is clamped at the maximum: the pause ends after exactly P times the vanilla time.
     */
    static double stretchRefill(double previous, double now, double pauseMultiplier, double refillStep, double statMax) {
        double rise = now - previous;
        if (rise <= 0 || rise > REFILL_STEP_MAX || pauseMultiplier <= 1.0) {
            return now;
        }
        if (now >= statMax - 1e-9) {
            return Math.min(statMax, previous + refillStep / pauseMultiplier);
        }
        return previous + rise / pauseMultiplier;
    }

    /**
     * Keeps a pause from lasting longer than {@code maxSeconds} once stretched: the remaining {@code |stat| x P} is capped
     * by moving the stat to {@code -maxSeconds / P}. Never raises a value (a shorter pause stays).
     */
    static double capPause(double stat, double pauseMultiplier, double maxSeconds) {
        double p = Math.max(1.0, pauseMultiplier);
        double limit = -maxSeconds / p;
        return Math.max(stat, limit);
    }

    /**
     * Water 0 holds the pause at the stat's minimum so stamina never moves. Above 0 the value is left alone and the
     * normal cap applies, so the first pause after drinking is still long (up to {@code maxSeconds}).
     */
    static double pinAtZeroWater(double stat, double statMin, double water) {
        return water <= 0.0 ? statMin : stat;
    }

    /** A base pause (jump, vault, climb): at least {@code baseSeconds}, never shortening a longer pause already running. */
    static double atLeast(double stat, double baseSeconds) {
        return baseSeconds <= 0 ? stat : Math.min(stat, -baseSeconds);
    }

    /**
     * The pause when sprinting stops. The engine has just written its own value ({@code engineSeconds}, 0.75) unless a
     * longer pause was running. If the stat is that engine value (or one refill step above it) it is replaced by
     * {@code baseSeconds}; a longer pause already running is kept; with {@code baseSeconds} 0 vanilla is left alone.
     */
    static double sprintEnd(double stat, double baseSeconds, double engineSeconds) {
        if (baseSeconds <= 0) {
            return stat;
        }
        boolean engineWrote = stat <= -engineSeconds + REFILL_STEP_MAX && stat >= -engineSeconds - 0.001;
        if (engineWrote) {
            return -baseSeconds;
        }
        return atLeast(stat, baseSeconds);
    }
}
