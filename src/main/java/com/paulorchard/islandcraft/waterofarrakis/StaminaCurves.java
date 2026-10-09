package com.paulorchard.islandcraft.waterofarrakis;

/**
 * The curves of the stamina rework: how much longer the pause before stamina regenerates gets, how much slower it
 * regenerates, and what a point of regeneration costs in water, from the player's water and exposure. Pure functions of
 * the config, no engine types, so they are unit tested and printed by /staminacurve.
 *
 * <p>With w = water / 100, e = exposure / 100 and dry = 1 - w:
 * <pre>
 *   P = (1 + PauseDryScale * dry^PauseDryExponent) * (1 + PauseExposureScale * e)        pause multiplier (1.0 = vanilla)
 *   R = 1 / ((1 + RegenDryScale * dry^RegenDryExponent) * (1 + RegenExposureScale * e))  regen speed multiplier
 *   cost per point = RegenWaterCostPerPoint * (1 + floor(exposure / step) * bonus)        water
 * </pre>
 * Continuous: no tiers and no thresholds. Full water with no exposure gives exactly 1.0 for P and R.
 */
final class StaminaCurves {

    private StaminaCurves() {
    }

    /** Vanilla regenerates 3.0 stamina per second (0.3 every 0.1 s) and the stat holds 10. */
    static final double VANILLA_REGEN_PER_SECOND = 3.0;
    static final double STAMINA_MAX = 10.0;

    private static double dry(double water) {
        return 1.0 - Math.max(0.0, Math.min(100.0, water)) / 100.0;
    }

    private static double heat(double exposure) {
        return Math.max(0.0, Math.min(100.0, exposure)) / 100.0;
    }

    /** P: how many times longer every regeneration pause lasts. 1.0 at full water and no exposure. */
    static double pauseMultiplier(WaterOfArrakisConfig cfg, double water, double exposure) {
        return dryPart(cfg, water) * (1.0 + cfg.getPauseExposureScale() * heat(exposure));
    }

    /** The water half of P, so /waterdebug can show the parts. */
    static double dryPart(WaterOfArrakisConfig cfg, double water) {
        return 1.0 + cfg.getPauseDryScale() * Math.pow(dry(water), cfg.getPauseDryExponent());
    }

    /** The exposure half of P. */
    static double heatPart(WaterOfArrakisConfig cfg, double exposure) {
        return 1.0 + cfg.getPauseExposureScale() * heat(exposure);
    }

    /** R: regeneration speed against vanilla. 1.0 at full water and no exposure, lower from there. */
    static double regenMultiplier(WaterOfArrakisConfig cfg, double water, double exposure) {
        double slow = (1.0 + cfg.getRegenDryScale() * Math.pow(dry(water), cfg.getRegenDryExponent()))
                * (1.0 + cfg.getRegenExposureScale() * heat(exposure));
        return 1.0 / slow;
    }

    /** Water taken for one stamina point of natural regeneration at this exposure. */
    static double waterCostPerPoint(WaterOfArrakisConfig cfg, double exposure) {
        return cfg.getRegenWaterCostPerPoint() * cfg.exposureDrainMultiplier(exposure);
    }

    /** Water for refilling the whole stamina bar from empty. */
    static double waterCostPerFullRefill(WaterOfArrakisConfig cfg, double exposure) {
        return waterCostPerPoint(cfg, exposure) * STAMINA_MAX;
    }

    /** A pause of {@code baseSeconds} as the player lives it, capped at MaxPauseSeconds. */
    static double effectivePause(WaterOfArrakisConfig cfg, double baseSeconds, double water, double exposure) {
        return Math.min(cfg.getMaxPauseSeconds(), baseSeconds * pauseMultiplier(cfg, water, exposure));
    }

    /** Seconds to refill the whole bar once regeneration has started. */
    static double refillSeconds(WaterOfArrakisConfig cfg, double water, double exposure) {
        return STAMINA_MAX / (VANILLA_REGEN_PER_SECOND * regenMultiplier(cfg, water, exposure));
    }
}
