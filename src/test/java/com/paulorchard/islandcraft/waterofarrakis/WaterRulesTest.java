package com.paulorchard.islandcraft.waterofarrakis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterRulesTest {

    private final WaterOfArrakisConfig cfg = new WaterOfArrakisConfig();

    @Test
    void exposureMultipliesDrainInTenPercentSteps() {
        assertEquals(1.0, cfg.exposureDrainMultiplier(0), 1e-9);
        assertEquals(1.0, cfg.exposureDrainMultiplier(9.99), 1e-9);
        assertEquals(1.1, cfg.exposureDrainMultiplier(10), 1e-9);
        assertEquals(1.1, cfg.exposureDrainMultiplier(19.9), 1e-9);
        assertEquals(1.2, cfg.exposureDrainMultiplier(20), 1e-9);
        assertEquals(2.0, cfg.exposureDrainMultiplier(100), 1e-9);
    }

    @Test
    void idleDrainEmptiesInAboutEightyThreeMinutes() {
        assertEquals(83.3, 100 / cfg.getWaterDrainBasePerSecond() / 60, 0.1);
    }

    @Test
    void modifiersStackAndReplace() {
        WaterService service = new WaterService(() -> cfg);
        UUID p = UUID.randomUUID();
        service.setModifier(p, "a", ModifierType.WATER_DRAIN_MULTIPLIER, 0.5);
        service.setModifier(p, "b", ModifierType.WATER_DRAIN_MULTIPLIER, 0.5);
        service.setModifier(p, "a", ModifierType.EXPOSURE_OFFSET, -0.25);
        service.setModifier(p, "b", ModifierType.EXPOSURE_OFFSET, -0.25);
        assertEquals(0.25, service.getModifier(p, ModifierType.WATER_DRAIN_MULTIPLIER), 1e-9);
        assertEquals(-0.5, service.getModifier(p, ModifierType.EXPOSURE_OFFSET), 1e-9);
        service.setModifier(p, "a", ModifierType.WATER_DRAIN_MULTIPLIER, 1.0);
        assertEquals(0.5, service.getModifier(p, ModifierType.WATER_DRAIN_MULTIPLIER), 1e-9);
        service.removeModifiers(p, "a");
        service.removeModifiers(p, "b");
        assertEquals(1.0, service.getModifier(p, ModifierType.WATER_DRAIN_MULTIPLIER), 1e-9);
        assertEquals(1.0, service.getModifier(p, ModifierType.EXPOSURE_GAIN_MULTIPLIER), 1e-9);
    }

    @Test
    void exposureColourIsOrangeThenBlendsToRed() {
        WaterOfArrakisConfig c = new WaterOfArrakisConfig();
        assertEquals("#ff8a1f", WaterHud.blend(c.getExposureColorOrange(), c.getExposureColorRed(), 0));
        assertEquals("#e0201a", WaterHud.blend(c.getExposureColorOrange(), c.getExposureColorRed(), 1));
        assertEquals("#f0551d", WaterHud.blend(c.getExposureColorOrange(), c.getExposureColorRed(), 0.5));
    }

    @Test
    void clampKeepsValuesInRange() {
        assertEquals(0.0, WaterService.clamp(-5));
        assertEquals(100.0, WaterService.clamp(180));
        assertEquals(42.0, WaterService.clamp(42));
    }

    @Test
    void eveningWindowIncludesStartAndExcludesEnd() {
        assertEquals(false, cfg.isEvening(16.99));
        assertEquals(true, cfg.isEvening(17.0));
        assertEquals(true, cfg.isEvening(19.99));
        assertEquals(false, cfg.isEvening(20.0));
        assertEquals(false, cfg.isEvening(3.0));
    }

    @Test
    void barSplitIsAShareOfTheBarAtAnySize() {
        int[] full = WaterHud.split(100);
        assertEquals(0, full[0]);
        assertEquals(WaterHud.SCALE, full[1]);
        int[] empty = WaterHud.split(0);
        assertEquals(WaterHud.SCALE, empty[0]);
        assertEquals(0, empty[1]);
        int[] quarter = WaterHud.split(25);
        assertEquals(750, quarter[0]);
        assertEquals(250, quarter[1]);
        assertEquals(WaterHud.SCALE, quarter[0] + quarter[1]);
        // Out of range values are clamped.
        assertEquals(WaterHud.SCALE, WaterHud.split(250)[1]);
        assertEquals(0, WaterHud.split(-5)[1]);
    }

    @Test
    void barStartsSixPointFivePercentDownAndEndsFourPixelsAboveItsIcon() {
        WaterHud.Weights w = WaterHud.weights(cfg);
        assertEquals(65, w.top());
        assertEquals(935, w.bar());
        assertEquals(0, w.bottom());
        assertEquals(WaterHud.SCALE, w.top() + w.bar() + w.bottom());
        // icon margin 20 + icon 24 + gap 4: the bar ends 48 px above the bottom of the screen.
        assertEquals(48, WaterHud.columnBottom(cfg));
    }

    @Test
    void barIsOnlySentWhenItMovedEnoughOrReachedAnEnd() {
        assertEquals(true, WaterHud.changed(Double.NaN, 50, 0.25));
        assertEquals(false, WaterHud.changed(50, 50, 0.25));
        assertEquals(false, WaterHud.changed(50, 50.1, 0.25));
        assertEquals(true, WaterHud.changed(50, 50.3, 0.25));
        assertEquals(true, WaterHud.changed(0.1, 0, 0.25));
        assertEquals(true, WaterHud.changed(99.9, 100, 0.25));
    }

    // ---- Part 2: jump and vault costs

    private static final double WINDOW = 0.8;

    @Test
    void oneJumpIsChargedOnceNotEveryTickItIsHeld() {
        ActionCosts a = new ActionCosts();
        double total = 0;
        for (int i = 0; i < 20; i++) {
            total += a.step(true, false, 0.05, 1.0, 1.0, WINDOW); // jumping flag held for 20 ticks
        }
        assertEquals(1.0, total, 1e-9);
        assertEquals(1, a.jumps);
        a.step(false, false, 0.05, 1.0, 1.0, WINDOW);
        assertEquals(1.0, a.step(true, false, 0.05, 1.0, 1.0, WINDOW), 1e-9); // a second jump is a second charge
        assertEquals(2, a.jumps);
    }

    @Test
    void aVaultIsChargedOnceOnItsOwn() {
        ActionCosts a = new ActionCosts();
        double total = 0;
        for (int i = 0; i < 10; i++) {
            total += a.step(false, true, 0.05, 1.0, 1.0, WINDOW);
        }
        assertEquals(1.0, total, 1e-9);
        assertEquals(1, a.vaults);
        assertEquals(0, a.jumps);
    }

    @Test
    void aJumpThatBecomesAVaultIsChargedTheHigherCostNotBoth() {
        ActionCosts a = new ActionCosts();
        double total = a.step(true, false, 0.05, 1.0, 1.0, WINDOW);
        total += a.step(true, false, 0.3, 1.0, 1.0, WINDOW);
        total += a.step(true, true, 0.05, 1.0, 1.0, WINDOW); // vault starts 0.35 s after the jump
        assertEquals(1.0, total, 1e-9);
        assertEquals(1, a.jumps);
        assertEquals(1, a.vaults);
        assertEquals(0.0, a.lastVaultCharge, 1e-9);
        // With a dearer vault only the extra is charged.
        ActionCosts b = new ActionCosts();
        double sum = b.step(true, false, 0.05, 1.0, 1.5, WINDOW);
        sum += b.step(true, true, 0.2, 1.0, 1.5, WINDOW);
        assertEquals(1.5, sum, 1e-9);
    }

    @Test
    void aVaultLongAfterAJumpIsAFreshCharge() {
        ActionCosts a = new ActionCosts();
        a.step(true, false, 0.05, 1.0, 1.0, WINDOW);
        a.step(false, false, 2.0, 1.0, 1.0, WINDOW);
        assertEquals(1.0, a.step(false, true, 0.05, 1.0, 1.0, WINDOW), 1e-9);
    }

    // ---- Part 3: out of water

    @Test
    void oneUnitOfUnpaidWaterIsOneHpWhileWaterAndStaminaAreZero() {
        ThirstAccumulator t = new ThirstAccumulator();
        double dealt = 0;
        // 1 unit due each second for 3 seconds, at 1 HP per unit, in 0.1 s ticks.
        for (int i = 0; i < 30; i++) {
            dealt += t.step(0.1, 0.1, 0.0, true, true, 1.0, 1.0, 1.0);
        }
        assertEquals(3.0, dealt + t.owed(), 1e-9);
        assertEquals(true, dealt >= 2.0);
        assertEquals(dealt, Math.floor(dealt), 1e-9, "dealt in whole HP steps");
    }

    @Test
    void thirstFractionsCarryOverAndAreDealtOncePerInterval() {
        ThirstAccumulator t = new ThirstAccumulator();
        double dealt = 0;
        int hits = 0;
        // Idle bleed: 0.02 water per second for 100 seconds in 0.05 s ticks = 2 HP in total.
        for (int i = 0; i < 2000; i++) {
            double h = t.step(0.05, 0.02 * 0.05, 0.0, true, true, 1.0, 1.0, 1.0);
            if (h > 0) {
                hits++;
                dealt += h;
            }
        }
        assertEquals(2, hits);
        assertEquals(2.0, dealt, 1e-9);
        assertEquals(0.0, t.owed(), 1e-6);
    }

    @Test
    void noThirstDamageWhileStaminaIsLeftOrWaterIsBack() {
        ThirstAccumulator t = new ThirstAccumulator();
        // Water 0 but stamina above 0: nothing owed, nothing dealt.
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, t.step(0.1, 1.0, 0.0, true, false, 1.0, 1.0, 1.0), 1e-9);
        }
        assertEquals(0.0, t.owed(), 1e-9);
        // Both zero: owed builds up; as soon as the player has water again it is dropped.
        t.step(0.1, 0.4, 0.0, true, true, 1.0, 100.0, 1.0);
        assertEquals(true, t.owed() > 0);
        t.step(0.1, 0.0, 0.0, false, true, 1.0, 1.0, 1.0);
        assertEquals(0.0, t.owed(), 1e-9);
    }

    @Test
    void thirstDamageScalesWithThePerUnitSetting() {
        ThirstAccumulator t = new ThirstAccumulator();
        double dealt = t.step(2.0, 1.0, 0.0, true, true, 3.0, 1.0, 1.0); // 1 unit at 3 HP per unit, interval already passed
        assertEquals(3.0, dealt, 1e-9);
    }

    @Test
    void zeroMinHitDealsTheFractionEveryInterval() {
        ThirstAccumulator t = new ThirstAccumulator();
        double dealt = t.step(1.0, 0.02, 0.0, true, true, 1.0, 1.0, 0.0);
        assertEquals(0.02, dealt, 1e-9);
    }

    @Test
    void slowSnapsToFivePercentSteps() {
        assertEquals("Arrakis_Thirst_Slow_90", WaterSystem.slowId(90));
        assertEquals("Arrakis_Thirst_Slow_05", WaterSystem.slowId(5));
    }

    // ---- damage retune: 1 HP per 10 s idle, stamina actions cost HP

    @Test
    void idleBleedAtZeroWaterAndZeroStaminaIsOneHpPerTenSecondsInTheShade() {
        double hpPerSecond = cfg.getWaterDrainBasePerSecond() * cfg.getZeroWaterHpPerWaterUnit();
        assertEquals(0.1, hpPerSecond, 1e-9);
        // Run it through the accumulator for 100 s of idle in 0.05 s ticks: 10 HP in whole 1 HP hits.
        ThirstAccumulator t = new ThirstAccumulator();
        double dealt = 0;
        int hits = 0;
        for (int i = 0; i < 2000; i++) {
            double h = t.step(0.05, cfg.getWaterDrainBasePerSecond() * 0.05, 0.0, true, true,
                    cfg.getZeroWaterHpPerWaterUnit(), cfg.getZeroWaterDamageIntervalSeconds(), cfg.getZeroWaterMinHit());
            if (h > 0) {
                hits++;
                dealt += h;
            }
        }
        assertEquals(10, hits);
        assertEquals(10.0, dealt, 1e-9);
    }

    @Test
    void fullSunDoublesTheIdleBleed() {
        double perSecond = cfg.getWaterDrainBasePerSecond() * cfg.exposureDrainMultiplier(100) * cfg.getZeroWaterHpPerWaterUnit();
        assertEquals(0.2, perSecond, 1e-9);
    }

    @Test
    void staminaActionsCostHpWhenThereIsNoStaminaToPayThem() {
        ThirstAccumulator t = new ThirstAccumulator();
        // A jump: 1 stamina not paid at 1 HP per stamina, water 0 and stamina 0, interval passed: 1 HP now.
        assertEquals(1.0, t.step(1.0, 0.0, 1.0, true, true, 5.0, 1.0, 1.0), 1e-9);
        // Sprinting for 2 seconds at 1 stamina per second: 2 HP over 2 s (a hit each second).
        double total = 0;
        for (int i = 0; i < 20; i++) {
            total += t.step(0.1, 0.0, 0.1, true, true, 5.0, 1.0, 1.0);
        }
        assertEquals(2.0, total + t.owed(), 1e-6);
        assertEquals(true, total >= 1.0);
    }

    @Test
    void staminaActionsCostNoHpWhileThereIsStaminaOrWater() {
        ThirstAccumulator t = new ThirstAccumulator();
        // Stamina left (not zero): the action was paid in stamina, nothing owed in HP.
        assertEquals(0.0, t.step(1.0, 0.0, 1.0, true, false, 5.0, 1.0, 1.0), 1e-9);
        assertEquals(0.0, t.owed(), 1e-9);
        // Water left: no HP either.
        assertEquals(0.0, t.step(1.0, 0.0, 1.0, false, true, 5.0, 1.0, 1.0), 1e-9);
        assertEquals(0.0, t.owed(), 1e-9);
    }

    @Test
    void jumpAndVaultMarkTheTickTheyStartEvenWhenFree() {
        ActionCosts a = new ActionCosts();
        // Free (cost 0, high water): still an edge, because the regeneration pause does not depend on the cost.
        a.step(false, false, 0.05, 0.0, 0.0, WINDOW);
        assertEquals(false, a.edgeThisTick);
        a.step(true, false, 0.05, 0.0, 0.0, WINDOW);
        assertEquals(true, a.edgeThisTick);
        a.step(true, false, 0.05, 0.0, 0.0, WINDOW);
        assertEquals(false, a.edgeThisTick, "held flag is not a new edge");
        a.step(false, true, 0.05, 0.0, 0.0, WINDOW);
        assertEquals(true, a.edgeThisTick);
    }

    // ---- stamina rework, Part 1: the curves (no behaviour change yet)

    @Test
    void pauseAndRegenAreExactlyVanillaAtFullWaterAndNoExposure() {
        assertEquals(1.0, StaminaCurves.pauseMultiplier(cfg, 100, 0), 0.0);
        assertEquals(1.0, StaminaCurves.regenMultiplier(cfg, 100, 0), 0.0);
    }

    @Test
    void pauseCurveMatchesTheDesignNumbers() {
        assertEquals(1.5, StaminaCurves.pauseMultiplier(cfg, 100, 100), 1e-9);       // full water, full exposure
        assertEquals(1.4375, StaminaCurves.pauseMultiplier(cfg, 75, 0), 1e-9);       // 1.44
        assertEquals(2.75, StaminaCurves.pauseMultiplier(cfg, 50, 0), 1e-9);
        assertEquals(4.9375, StaminaCurves.pauseMultiplier(cfg, 25, 0), 1e-9);       // 4.94
        assertEquals(8.0, StaminaCurves.pauseMultiplier(cfg, 0, 0), 1e-9);
        assertEquals(12.0, StaminaCurves.pauseMultiplier(cfg, 0, 100), 1e-9);        // 8 x 1.5
    }

    @Test
    void regenCurveMatchesTheDesignNumbers() {
        assertEquals(0.727, StaminaCurves.regenMultiplier(cfg, 50, 0), 1e-3);
        assertEquals(0.542, StaminaCurves.regenMultiplier(cfg, 25, 0), 1e-3);        // the brief said "about 0.52"; the formula gives 0.54
        assertEquals(0.4, StaminaCurves.regenMultiplier(cfg, 0, 0), 1e-9);
        assertEquals(0.32, StaminaCurves.regenMultiplier(cfg, 0, 100), 1e-9);        // heat lowers it further
    }

    @Test
    void pauseRisesAndRegenFallsAsWaterFallsAndExposureRises() {
        for (int e = 0; e <= 100; e += 25) {
            double lastP = 0;
            double lastSlow = 0;
            for (int w = 100; w >= 0; w -= 5) {
                double p = StaminaCurves.pauseMultiplier(cfg, w, e);
                double slow = 1.0 / StaminaCurves.regenMultiplier(cfg, w, e);
                assertEquals(true, p > lastP || w == 100, "P rises as water falls");
                assertEquals(true, slow > lastSlow || w == 100, "1/R rises as water falls");
                lastP = p;
                lastSlow = slow;
            }
        }
        for (int w = 100; w >= 0; w -= 25) {
            assertEquals(true, StaminaCurves.pauseMultiplier(cfg, w, 100) > StaminaCurves.pauseMultiplier(cfg, w, 0));
            assertEquals(true, StaminaCurves.regenMultiplier(cfg, w, 100) < StaminaCurves.regenMultiplier(cfg, w, 0));
        }
    }

    @Test
    void anEffectivePauseIsCappedAtTheStatLimit() {
        // A 10 s bow pause at 0 water and full exposure would be 120 s: capped at 60.
        assertEquals(60.0, StaminaCurves.effectivePause(cfg, 10, 0, 100), 1e-9);
        assertEquals(0.5, StaminaCurves.effectivePause(cfg, 0.5, 100, 0), 1e-9);
    }

    @Test
    void regenWaterCostScalesWithTheExposureDrainMultiplier() {
        assertEquals(0.5, StaminaCurves.waterCostPerFullRefill(cfg, 0), 1e-9);       // 10 x 0.05
        assertEquals(1.0, StaminaCurves.waterCostPerFullRefill(cfg, 100), 1e-9);     // x2.0
        assertEquals(0.05 * cfg.exposureDrainMultiplier(30), StaminaCurves.waterCostPerPoint(cfg, 30), 1e-9);
    }

    // ---- stamina rework, Part 2: rules

    private static final double STEP = 0.3;

    @Test
    void naturalRegenIsWholeStepsOnATickWhereNothingBlockedIt() {
        assertEquals(true, StaminaBreath.isNaturalRegen(0.3, STEP, 3, 0.02, true));
        assertEquals(true, StaminaBreath.isNaturalRegen(0.6, STEP, 3, 0.02, true));
        assertEquals(true, StaminaBreath.isNaturalRegen(0.9, STEP, 3, 0.02, true));
        assertEquals(false, StaminaBreath.isNaturalRegen(1.2, STEP, 3, 0.02, true));   // four steps: not a tick of regen
        assertEquals(false, StaminaBreath.isNaturalRegen(0.5, STEP, 3, 0.02, true));   // Food_Stamina_Regen_Tiny
        assertEquals(false, StaminaBreath.isNaturalRegen(1.5, STEP, 3, 0.02, true));   // Food_Stamina_Regen_Large (5 steps)
        assertEquals(false, StaminaBreath.isNaturalRegen(10.0, STEP, 3, 0.02, true));  // a restore
        assertEquals(false, StaminaBreath.isNaturalRegen(0.3, STEP, 3, 0.02, false));  // sprinting or gliding
        assertEquals(false, StaminaBreath.isNaturalRegen(-0.3, STEP, 3, 0.02, true));
    }

    @Test
    void regenSpeedAndWaterCostApplyToTheStaminaThatCameBack() {
        StaminaBreath.Regen r = StaminaBreath.regen(0.3, 0.5, 0.05, 100);
        assertEquals(0.15, r.points(), 1e-9);
        assertEquals(0.0075, r.waterCost(), 1e-9);   // 0.15 points x 0.05
        // A full refill of 10 points at speed 1 costs 0.5 water.
        assertEquals(0.5, StaminaBreath.regen(10, 1.0, 0.05, 100).waterCost(), 1e-9);
    }

    @Test
    void waterThatCannotPayGivesOnlyTheShareItCanPay() {
        StaminaBreath.Regen r = StaminaBreath.regen(0.3, 1.0, 0.05, 0.0075); // would cost 0.015, has half of that
        assertEquals(0.15, r.points(), 1e-9);
        assertEquals(0.0075, r.waterCost(), 1e-9);
        StaminaBreath.Regen none = StaminaBreath.regen(0.3, 1.0, 0.05, 0.0);
        assertEquals(0.0, none.points(), 1e-9);
        assertEquals(0.0, none.waterCost(), 1e-9);
    }

    @Test
    void pauseStretchGivesBackOnlyAFractionOfEachRefillStep() {
        // P = 4: a 0.1 refill step keeps 0.025, so a 1 s vanilla pause lasts 4 s.
        double v = -1.0;
        int steps = 0;
        while (v < 0 && steps < 1000) {
            double engine = Math.min(0.0, v + 0.1);      // the engine refills 0.1 per step
            v = StaminaBreath.stretchRefill(v, engine, 4.0, 0.1, 0.0);
            steps++;
        }
        assertEquals(40, steps, 1);  // 40 steps of 0.1 s is 4 s
        // P = 1 is exactly vanilla: 10 steps.
        v = -1.0;
        steps = 0;
        while (v < 0 && steps < 1000) {
            v = StaminaBreath.stretchRefill(v, Math.min(0.0, v + 0.1), 1.0, 0.1, 0.0);
            steps++;
        }
        assertEquals(10, steps, 1);
    }

    @Test
    void aValueAnActionWroteIsNotMistakenForARefill() {
        // An interaction sets -0.7 over a -3 pause: a rise of 2.3 is a write, left as written.
        assertEquals(-0.7, StaminaBreath.stretchRefill(-3.0, -0.7, 5.0, 0.1, 0.0), 1e-9);
    }

    @Test
    void aStretchedPauseIsCappedAtTheLimit() {
        // 10 s bow pause at P = 12: 120 s would be lived; the stat is moved to -60 / 12 = -5 so 60 s remain.
        assertEquals(-5.0, StaminaBreath.capPause(-10.0, 12.0, 60.0), 1e-9);
        assertEquals(-0.5, StaminaBreath.capPause(-0.5, 12.0, 60.0), 1e-9);   // a short pause is not raised
        assertEquals(-0.5, StaminaBreath.capPause(-0.5, 1.0, 60.0), 1e-9);
    }

    @Test
    void theStatIsPinnedAtItsMinimumAtZeroWaterAndReleasedAboveIt() {
        assertEquals(-60.0, StaminaBreath.pinAtZeroWater(-0.2, -60.0, 0.0), 1e-9);
        assertEquals(-60.0, StaminaBreath.pinAtZeroWater(0.0, -60.0, 0.0), 1e-9);
        assertEquals(-0.2, StaminaBreath.pinAtZeroWater(-0.2, -60.0, 0.01), 1e-9);
        // After drinking from the pin the cap still applies: at P = 8 the stat is brought to -7.5, a 60 s pause.
        double released = StaminaBreath.capPause(StaminaBreath.pinAtZeroWater(-60.0, -60.0, 5.0), 8.0, 60.0);
        assertEquals(-7.5, released, 1e-9);
    }

    @Test
    void basePausesNeverShortenALongerRunningPause() {
        assertEquals(-0.5, StaminaBreath.atLeast(0.0, 0.5), 1e-9);
        assertEquals(-0.5, StaminaBreath.atLeast(-0.2, 0.5), 1e-9);
        assertEquals(-3.0, StaminaBreath.atLeast(-3.0, 0.5), 1e-9);   // a battleaxe pause is kept
        assertEquals(-0.2, StaminaBreath.atLeast(-0.2, 0.0), 1e-9);   // 0 turns the base pause off
    }

    @Test
    void sprintEndReplacesTheEnginePauseButKeepsALongerOne() {
        // The engine wrote -0.75 this tick: replaced by 0.5.
        assertEquals(-0.5, StaminaBreath.sprintEnd(-0.75, 0.5, 0.75), 1e-9);
        assertEquals(-0.5, StaminaBreath.sprintEnd(-0.65, 0.5, 0.75), 1e-9);
        // A longer pause was already running (the engine leaves it): kept.
        assertEquals(-3.0, StaminaBreath.sprintEnd(-3.0, 0.5, 0.75), 1e-9);
        // No pause at all and the engine wrote none: at least 0.5.
        assertEquals(-0.5, StaminaBreath.sprintEnd(0.0, 0.5, 0.75), 1e-9);
        // 0 keeps vanilla.
        assertEquals(-0.75, StaminaBreath.sprintEnd(-0.75, 0.0, 0.75), 1e-9);
    }

    @Test
    void staminaModifiersMultiplyAcrossIds() {
        WaterService service = new WaterService(() -> cfg);
        UUID p = UUID.randomUUID();
        service.setModifier(p, "a", ModifierType.STAMINA_PAUSE_MULTIPLIER, 0.5);
        service.setModifier(p, "b", ModifierType.STAMINA_PAUSE_MULTIPLIER, 0.5);
        service.setModifier(p, "a", ModifierType.STAMINA_REGEN_MULTIPLIER, 2.0);
        service.setModifier(p, "b", ModifierType.STAMINA_REGEN_WATER_COST_MULTIPLIER, 0.0);
        assertEquals(0.25, service.getModifier(p, ModifierType.STAMINA_PAUSE_MULTIPLIER), 1e-9);
        assertEquals(2.0, service.getModifier(p, ModifierType.STAMINA_REGEN_MULTIPLIER), 1e-9);
        assertEquals(0.0, service.getModifier(p, ModifierType.STAMINA_REGEN_WATER_COST_MULTIPLIER), 1e-9);
    }

    @Test
    void jumpAndVaultMarkWhichOneStarted() {
        ActionCosts a = new ActionCosts();
        a.step(true, false, 0.05, 1.0, 1.0, WINDOW);
        assertEquals(true, a.jumpEdge);
        assertEquals(false, a.vaultEdge);
        a.step(true, true, 0.05, 1.0, 1.0, WINDOW);
        assertEquals(false, a.jumpEdge);
        assertEquals(true, a.vaultEdge);
    }
}
