package com.paulorchard.islandcraft.waterofarrakis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterRulesTest {

    private final WaterOfArrakisConfig cfg = new WaterOfArrakisConfig();

    @Test
    void tierBoundariesIncludeTheLowerBound() {
        assertEquals(0, cfg.waterTier(100));
        assertEquals(0, cfg.waterTier(75));
        assertEquals(1, cfg.waterTier(74.99));
        assertEquals(1, cfg.waterTier(50));
        assertEquals(2, cfg.waterTier(49.99));
        assertEquals(2, cfg.waterTier(25));
        assertEquals(3, cfg.waterTier(24.99));
        assertEquals(3, cfg.waterTier(0));
    }

    @Test
    void tierEffectsMatchTheDesign() {
        assertEquals(0.0, cfg.actionStaminaCost(0));
        assertEquals(0.5, cfg.actionStaminaCost(1));
        assertEquals(1.0, cfg.actionStaminaCost(2));
        assertEquals(1.0, cfg.actionStaminaCost(3));
        assertEquals(1.0, cfg.staminaRegen(2));
        assertEquals(0.5, cfg.staminaRegen(3));
    }

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
}
