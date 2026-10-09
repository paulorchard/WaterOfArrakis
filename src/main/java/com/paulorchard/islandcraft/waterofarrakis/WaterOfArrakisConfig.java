package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Every tunable of the mod, read from Water_of_Arrakis.json in the plugin's data folder. The defaults below are the
 * design values. Balance here and nowhere else; the code holds no numbers of its own.
 *
 * <p>Water and exposure are both 0 to 100 (percent). "Per second" values are in those units per second.
 */
public class WaterOfArrakisConfig {

    public static final String FILE_NAME = "Water_of_Arrakis";

    // ------------------------------------------------------------------ start values
    private double initialWater = 100;
    private double initialExposure = 0;

    // ------------------------------------------------------------------ exposure (sun)
    /** Exposure gained per second in direct sun. 1.0 means 100 s of continuous sun fills the bar. */
    private double exposureGainPerSecond = 1.0;
    /** Exposure lost per second once the grace period out of the sun has passed. */
    private double exposureDecayPerSecond = 1.0;
    /** Continuous seconds out of the sun before exposure starts to decay. */
    private double exposureGraceSeconds = 5.0;
    /** Day counts as "sun up" when the world's sunlight factor (0 night .. 1 midday) is at least this. */
    private double minSunlightFactor = 0.25;
    /** Blocks above the head that must be free of solid blocks for the player to count as in direct sun. */
    private double skyClearBlocks = 0;

    // ------------------------------------------------------------------ water drain
    private double waterDrainBasePerSecond = 0.02;
    private double waterDrainRunPerSecond = 0.03;
    private double waterDrainClimbPerSecond = 0.03;
    private double waterDrainPerJump = 0.05;
    /** Exposure is cut in steps of this many percent for the drain multiplier. */
    private double exposureDrainStepPercent = 10;
    /** Added to the drain multiplier for each full step of exposure (0.10 gives x1.0 .. x2.0 across 0..100). */
    private double exposureDrainStepBonus = 0.10;

    // ------------------------------------------------------------------ water tiers and stamina
    /**
     * Lower bounds, in percent, of every tier except the last, highest first. A tier INCLUDES its lower bound and
     * EXCLUDES its upper bound, except tier 0 which includes 100. With {75, 50, 25}: tier 0 is 75..100 (75 itself is
     * tier 0), tier 1 is 50..75 (50 is tier 1, 75 is not), tier 2 is 25..50, tier 3 is 0..25 (25 is tier 2).
     */
    private double[] waterTierLowerBounds = {75, 50, 25};
    /** Multiplier on the stamina cost of running (sprinting), jumping and climbing, one entry per tier. Attacks are never scaled. */
    private double[] tierActionStaminaCost = {0.0, 0.5, 1.0, 1.0};
    /** Multiplier on stamina regeneration, one entry per tier. */
    private double[] tierStaminaRegen = {1.0, 1.0, 1.0, 0.5};
    /** Stamina taken by one jump at multiplier 1.0. Vanilla 0.6.8 charges nothing for jumping, so 0 keeps vanilla. */
    private double jumpStaminaCost = 0;
    /** Stamina taken per second of climbing at multiplier 1.0. Vanilla 0.6.8 charges nothing for climbing. */
    private double climbStaminaCostPerSecond = 0;
    /**
     * Stamina regeneration is measured as the rise in stamina between two ticks. A rise faster than this many stamina
     * per second (a potion, food) is not regeneration and is left alone. Vanilla regenerates 3 per second.
     */
    private double regenDeltaCapPerSecond = 4;

    // ------------------------------------------------------------------ HUD
    /** Bar width in pixels (the vanilla health and stamina bars are 318). */
    private double hudBarWidth = 318;
    /** Exposure bar turns from orange to red between this percent and 100. */
    private double exposureRedStartPercent = 75;
    private String exposureColorOrange = "#ff8a1f";
    private String exposureColorRed = "#e0201a";
    private String waterColor = "#2f8fe0";
    /** Smallest change in bar width (pixels) that is worth sending to the client. */
    private double hudMinPixelChange = 1;

    // ------------------------------------------------------------------ items
    /** Units of water a Litrejon holds when full. */
    private double litrejonCapacity = 500;
    /** Most water one drink from the Litrejon restores (also limited by the room left and the units left). */
    private double litrejonDrinkAmount = 50;
    /** A Litrejon with no saved amount (spawned by command) counts as full. False makes it empty. */
    private boolean litrejonStartsFull = true;
    /** Fluid asset ids that count as water to fill from. */
    private String[] waterFluidIds = {"Water_Source", "Water"};
    /** How far the player can reach to fill, in blocks. */
    private double fillReach = 5;

    private double burrowBulbWater = 25;

    private double spicebreadWater = 10;
    private double spicebreadHeal = 10;
    private double spicebreadRegenPerSecond = 2;
    private double spicebreadRegenSeconds = 5;

    private double desertGameWater = 5;
    private double desertGameHeal = 30;

    // ------------------------------------------------------------------ plants
    /** Water one drink from a primrose gives. */
    private double primroseWater = 10;
    /** A primrose never takes water above this; at or above it there is no effect. */
    private double primroseCap = 25;
    /** In-game clock hour (0-24, decimals allowed) at which primroses open, and at which they close again. */
    private double eveningStartHour = 17.0;
    private double eveningEndHour = 20.0;
    /** Burrow Bulbs one burrowbush gives per in-game day. */
    private double burrowbushBulbs = 1;
    /** Chance for each qualifying column of a new chunk to get a primrose cluster (north-facing island slopes). */
    private double primroseChance = 0.015;
    /** Chance for each qualifying column to get a burrowbush (south-facing island slopes). */
    private double burrowbushChance = 0.002;
    /** Steepness of the slope (blocks of height change per block) that counts as facing a direction. */
    private double minSlope = 0.5;
    /** Blocks whose id starts with this are island rock (the Dunes of Arrakis mod's own rule for exposed rock). */
    private String rockPrefix = "Rock_";
    /** Plants are placed when a chunk is first generated. False turns generation off (the blocks stay obtainable). */
    private boolean generatePlants = true;
    /** Plants are only placed in worlds made by these world generator types (empty means every world). */
    private String[] generatorTypes = {"Dunes_of_Arrakis"};

    // ------------------------------------------------------------------ misc
    /** Creative players are left alone (nothing drains, nothing gains) unless this is true. */
    private boolean simulateInCreative = false;

    public static final BuilderCodec<WaterOfArrakisConfig> CODEC = build();

    private static BuilderCodec<WaterOfArrakisConfig> build() {
        BuilderCodec.Builder<WaterOfArrakisConfig> b =
                BuilderCodec.builder(WaterOfArrakisConfig.class, WaterOfArrakisConfig::new)
                        .documentation("Water and exposure settings. Water and exposure are percentages, 0 to 100.");
        b = num(b, "InitialWater", (c, v) -> c.initialWater = v, c -> c.initialWater,
                "Water a new player starts with.");
        b = num(b, "InitialExposure", (c, v) -> c.initialExposure = v, c -> c.initialExposure,
                "Exposure a new player starts with.");
        b = num(b, "ExposureGainPerSecond", (c, v) -> c.exposureGainPerSecond = v, c -> c.exposureGainPerSecond,
                "Exposure gained per second in direct sun (1.0 = 100 s to fill).");
        b = num(b, "ExposureDecayPerSecond", (c, v) -> c.exposureDecayPerSecond = v, c -> c.exposureDecayPerSecond,
                "Exposure lost per second once the grace period out of the sun has passed.");
        b = num(b, "ExposureGraceSeconds", (c, v) -> c.exposureGraceSeconds = v, c -> c.exposureGraceSeconds,
                "Continuous seconds out of the sun before exposure starts to decay.");
        b = num(b, "MinSunlightFactor", (c, v) -> c.minSunlightFactor = v, c -> c.minSunlightFactor,
                "The sun counts as up when the world sunlight factor (0 night to 1 midday) is at least this.");
        b = num(b, "SkyClearBlocks", (c, v) -> c.skyClearBlocks = v, c -> c.skyClearBlocks,
                "Extra blocks above the head that may be solid and still count as sun. 0 means any solid block above shades.");
        b = num(b, "WaterDrainBasePerSecond", (c, v) -> c.waterDrainBasePerSecond = v, c -> c.waterDrainBasePerSecond,
                "Water lost per second doing nothing (0.02 = about 83 minutes from full to empty).");
        b = num(b, "WaterDrainRunPerSecond", (c, v) -> c.waterDrainRunPerSecond = v, c -> c.waterDrainRunPerSecond,
                "Extra water lost per second while running or sprinting.");
        b = num(b, "WaterDrainClimbPerSecond", (c, v) -> c.waterDrainClimbPerSecond = v, c -> c.waterDrainClimbPerSecond,
                "Extra water lost per second while climbing.");
        b = num(b, "WaterDrainPerJump", (c, v) -> c.waterDrainPerJump = v, c -> c.waterDrainPerJump,
                "Water lost by each jump.");
        b = num(b, "ExposureDrainStepPercent", (c, v) -> c.exposureDrainStepPercent = v, c -> c.exposureDrainStepPercent,
                "Exposure is cut into steps of this many percent for the drain multiplier.");
        b = num(b, "ExposureDrainStepBonus", (c, v) -> c.exposureDrainStepBonus = v, c -> c.exposureDrainStepBonus,
                "Added to the water drain multiplier for each full exposure step (0.10 gives x1.0 at <10% up to x2.0 at 100%).");
        b = arr(b, "WaterTierLowerBounds", (c, v) -> c.waterTierLowerBounds = v, c -> c.waterTierLowerBounds,
                "Lower bounds in percent of every water tier but the last, highest first. A tier includes its lower "
                        + "bound and excludes its upper bound (tier 0 also includes 100).");
        b = arr(b, "TierActionStaminaCost", (c, v) -> c.tierActionStaminaCost = v, c -> c.tierActionStaminaCost,
                "Stamina cost multiplier for running, jumping and climbing, one entry per tier (bounds count plus one). "
                        + "Attacks are never changed.");
        b = arr(b, "TierStaminaRegen", (c, v) -> c.tierStaminaRegen = v, c -> c.tierStaminaRegen,
                "Stamina regeneration multiplier, one entry per tier.");
        b = num(b, "JumpStaminaCost", (c, v) -> c.jumpStaminaCost = v, c -> c.jumpStaminaCost,
                "Stamina taken by one jump at multiplier 1. Vanilla charges none, so 0 keeps vanilla.");
        b = num(b, "ClimbStaminaCostPerSecond", (c, v) -> c.climbStaminaCostPerSecond = v,
                c -> c.climbStaminaCostPerSecond,
                "Stamina taken per second of climbing at multiplier 1. Vanilla charges none, so 0 keeps vanilla.");
        b = num(b, "RegenDeltaCapPerSecond", (c, v) -> c.regenDeltaCapPerSecond = v, c -> c.regenDeltaCapPerSecond,
                "A stamina rise faster than this per second is an item, not regeneration, and is never halved.");
        b = num(b, "HudBarWidth", (c, v) -> c.hudBarWidth = v, c -> c.hudBarWidth,
                "Width in pixels of the water and exposure bars (vanilla health and stamina bars are 318).");
        b = num(b, "ExposureRedStartPercent", (c, v) -> c.exposureRedStartPercent = v, c -> c.exposureRedStartPercent,
                "The exposure bar is orange up to here and blends to red at 100.");
        b = str(b, "ExposureColorOrange", (c, v) -> c.exposureColorOrange = v, c -> c.exposureColorOrange,
                "Exposure bar colour from 0 to ExposureRedStartPercent.");
        b = str(b, "ExposureColorRed", (c, v) -> c.exposureColorRed = v, c -> c.exposureColorRed,
                "Exposure bar colour at 100%.");
        b = str(b, "WaterColor", (c, v) -> c.waterColor = v, c -> c.waterColor, "Water bar colour.");
        b = num(b, "HudMinPixelChange", (c, v) -> c.hudMinPixelChange = v, c -> c.hudMinPixelChange,
                "A bar is only re-sent to the client when it changes by at least this many pixels.");
        b = num(b, "LitrejonCapacity", (c, v) -> c.litrejonCapacity = v, c -> c.litrejonCapacity,
                "Units of water a full Litrejon holds.");
        b = num(b, "LitrejonDrinkAmount", (c, v) -> c.litrejonDrinkAmount = v, c -> c.litrejonDrinkAmount,
                "Most water one drink restores: min(this, room left in the player, units left in the flask).");
        b = b.append(new KeyedCodec<>("LitrejonStartsFull", Codec.BOOLEAN, false),
                        (c, v) -> c.litrejonStartsFull = v, c -> c.litrejonStartsFull)
                .documentation("A Litrejon with no saved amount (given by command) counts as full when true, empty when false.")
                .add();
        b = b.append(new KeyedCodec<>("WaterFluidIds", Codec.STRING_ARRAY, false),
                        (c, v) -> c.waterFluidIds = v, c -> c.waterFluidIds)
                .documentation("Fluid ids that count as water when filling the Litrejon.")
                .add();
        b = num(b, "FillReach", (c, v) -> c.fillReach = v, c -> c.fillReach,
                "Blocks the player can reach to fill a Litrejon from water.");
        b = num(b, "BurrowBulbWater", (c, v) -> c.burrowBulbWater = v, c -> c.burrowBulbWater,
                "Water a Burrow Bulb restores.");
        b = num(b, "SpicebreadWater", (c, v) -> c.spicebreadWater = v, c -> c.spicebreadWater,
                "Water Spicebread restores.");
        b = num(b, "SpicebreadHeal", (c, v) -> c.spicebreadHeal = v, c -> c.spicebreadHeal,
                "Health Spicebread restores at once.");
        b = num(b, "SpicebreadRegenPerSecond", (c, v) -> c.spicebreadRegenPerSecond = v, c -> c.spicebreadRegenPerSecond,
                "Health per second Spicebread restores after eating, for SpicebreadRegenSeconds.");
        b = num(b, "SpicebreadRegenSeconds", (c, v) -> c.spicebreadRegenSeconds = v, c -> c.spicebreadRegenSeconds,
                "How long the Spicebread regeneration lasts.");
        b = num(b, "DesertGameWater", (c, v) -> c.desertGameWater = v, c -> c.desertGameWater,
                "Water Desert Game restores.");
        b = num(b, "DesertGameHeal", (c, v) -> c.desertGameHeal = v, c -> c.desertGameHeal,
                "Health Desert Game restores at once.");
        b = num(b, "PrimroseWater", (c, v) -> c.primroseWater = v, c -> c.primroseWater,
                "Water one drink from a primrose gives.");
        b = num(b, "PrimroseCap", (c, v) -> c.primroseCap = v, c -> c.primroseCap,
                "A primrose never takes water above this; at or above it there is no effect.");
        b = num(b, "EveningStartHour", (c, v) -> c.eveningStartHour = v, c -> c.eveningStartHour,
                "In-game clock hour (0-24) at which primroses open.");
        b = num(b, "EveningEndHour", (c, v) -> c.eveningEndHour = v, c -> c.eveningEndHour,
                "In-game clock hour (0-24) at which primroses close again.");
        b = num(b, "BurrowbushBulbs", (c, v) -> c.burrowbushBulbs = v, c -> c.burrowbushBulbs,
                "Burrow Bulbs one burrowbush gives per in-game day.");
        b = num(b, "PrimroseChance", (c, v) -> c.primroseChance = v, c -> c.primroseChance,
                "Chance per qualifying column of a new chunk to get a primrose cluster (north-facing island slopes).");
        b = num(b, "BurrowbushChance", (c, v) -> c.burrowbushChance = v, c -> c.burrowbushChance,
                "Chance per qualifying column of a new chunk to get a burrowbush (south-facing island slopes).");
        b = num(b, "MinSlope", (c, v) -> c.minSlope = v, c -> c.minSlope,
                "Blocks of height change per block that count as a slope facing a direction.");
        b = str(b, "RockPrefix", (c, v) -> c.rockPrefix = v, c -> c.rockPrefix,
                "Surface blocks whose id starts with this count as island rock.");
        b = b.append(new KeyedCodec<>("GeneratorTypes", Codec.STRING_ARRAY, false),
                        (c, v) -> c.generatorTypes = v, c -> c.generatorTypes)
                .documentation("Plants are only placed in worlds made by these generator types. Empty means every world.")
                .add();
        b = b.append(new KeyedCodec<>("GeneratePlants", Codec.BOOLEAN, false),
                        (c, v) -> c.generatePlants = v, c -> c.generatePlants)
                .documentation("Place primroses and burrowbushes when a chunk is first generated.")
                .add();
        b = b.append(new KeyedCodec<>("SimulateInCreative", Codec.BOOLEAN, false),
                        (c, v) -> c.simulateInCreative = v, c -> c.simulateInCreative)
                .documentation("When false, creative players neither drain nor gain.")
                .add();
        return b.build();
    }

    private static BuilderCodec.Builder<WaterOfArrakisConfig> num(BuilderCodec.Builder<WaterOfArrakisConfig> b,
            String key, BiConsumer<WaterOfArrakisConfig, Double> set, Function<WaterOfArrakisConfig, Double> get,
            String doc) {
        return b.append(new KeyedCodec<>(key, Codec.DOUBLE, false), set::accept, get::apply).documentation(doc).add();
    }

    private static BuilderCodec.Builder<WaterOfArrakisConfig> str(BuilderCodec.Builder<WaterOfArrakisConfig> b,
            String key, BiConsumer<WaterOfArrakisConfig, String> set, Function<WaterOfArrakisConfig, String> get,
            String doc) {
        return b.append(new KeyedCodec<>(key, Codec.STRING, false), set::accept, get::apply).documentation(doc).add();
    }

    private static BuilderCodec.Builder<WaterOfArrakisConfig> arr(BuilderCodec.Builder<WaterOfArrakisConfig> b,
            String key, BiConsumer<WaterOfArrakisConfig, double[]> set, Function<WaterOfArrakisConfig, double[]> get,
            String doc) {
        return b.append(new KeyedCodec<>(key, Codec.DOUBLE_ARRAY, false), set::accept, get::apply)
                .documentation(doc).add();
    }

    // ------------------------------------------------------------------ derived rules

    /**
     * The water tier of a water value. 0 is the best (full) tier. A tier includes its lower bound and excludes its
     * upper bound: with bounds {75, 50, 25}, 75.0 is tier 0, 74.99 is tier 1, 50.0 is tier 1, 25.0 is tier 2.
     */
    public int waterTier(double water) {
        for (int i = 0; i < waterTierLowerBounds.length; i++) {
            if (water >= waterTierLowerBounds[i]) {
                return i;
            }
        }
        return waterTierLowerBounds.length;
    }

    public int waterTierCount() {
        return waterTierLowerBounds.length + 1;
    }

    /** Multiplier applied to water drain at this exposure: 1 + floor(exposure / step) * bonus. */
    public double exposureDrainMultiplier(double exposure) {
        double step = Math.max(1e-6, exposureDrainStepPercent);
        return 1.0 + Math.floor(exposure / step) * exposureDrainStepBonus;
    }

    public double actionStaminaCost(int tier) {
        return at(tierActionStaminaCost, tier, 1.0);
    }

    public double staminaRegen(int tier) {
        return at(tierStaminaRegen, tier, 1.0);
    }

    private static double at(double[] values, int index, double fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        return values[Math.min(Math.max(index, 0), values.length - 1)];
    }

    // ------------------------------------------------------------------ getters

    public double getInitialWater() { return initialWater; }
    public double getInitialExposure() { return initialExposure; }
    public double getExposureGainPerSecond() { return exposureGainPerSecond; }
    public double getExposureDecayPerSecond() { return exposureDecayPerSecond; }
    public double getExposureGraceSeconds() { return exposureGraceSeconds; }
    public double getMinSunlightFactor() { return minSunlightFactor; }
    public int getSkyClearBlocks() { return (int) skyClearBlocks; }
    public double getWaterDrainBasePerSecond() { return waterDrainBasePerSecond; }
    public double getWaterDrainRunPerSecond() { return waterDrainRunPerSecond; }
    public double getWaterDrainClimbPerSecond() { return waterDrainClimbPerSecond; }
    public double getWaterDrainPerJump() { return waterDrainPerJump; }
    public double getExposureDrainStepPercent() { return exposureDrainStepPercent; }
    public double getJumpStaminaCost() { return jumpStaminaCost; }
    public double getClimbStaminaCostPerSecond() { return climbStaminaCostPerSecond; }
    public double getRegenDeltaCapPerSecond() { return regenDeltaCapPerSecond; }
    public double getHudBarWidth() { return hudBarWidth; }
    public double getExposureRedStartPercent() { return exposureRedStartPercent; }
    public String getExposureColorOrange() { return exposureColorOrange; }
    public String getExposureColorRed() { return exposureColorRed; }
    public String getWaterColor() { return waterColor; }
    public double getHudMinPixelChange() { return hudMinPixelChange; }
    public double getLitrejonCapacity() { return litrejonCapacity; }
    public double getLitrejonDrinkAmount() { return litrejonDrinkAmount; }
    public boolean isLitrejonStartsFull() { return litrejonStartsFull; }
    public String[] getWaterFluidIds() { return waterFluidIds == null ? new String[0] : waterFluidIds; }
    public double getFillReach() { return fillReach; }
    public double getBurrowBulbWater() { return burrowBulbWater; }
    public double getSpicebreadWater() { return spicebreadWater; }
    public double getSpicebreadHeal() { return spicebreadHeal; }
    public double getSpicebreadRegenPerSecond() { return spicebreadRegenPerSecond; }
    public double getSpicebreadRegenSeconds() { return spicebreadRegenSeconds; }
    public double getDesertGameWater() { return desertGameWater; }
    public double getDesertGameHeal() { return desertGameHeal; }
    public double getPrimroseWater() { return primroseWater; }
    public double getPrimroseCap() { return primroseCap; }
    public double getEveningStartHour() { return eveningStartHour; }
    public double getEveningEndHour() { return eveningEndHour; }
    public int getBurrowbushBulbs() { return (int) burrowbushBulbs; }
    public double getPrimroseChance() { return primroseChance; }
    public double getBurrowbushChance() { return burrowbushChance; }
    public double getMinSlope() { return minSlope; }
    public String getRockPrefix() { return rockPrefix; }
    public boolean isGeneratePlants() { return generatePlants; }
    public String[] getGeneratorTypes() { return generatorTypes == null ? new String[0] : generatorTypes; }

    /** True when the in-game clock hour is inside [EveningStartHour, EveningEndHour) (wraps past midnight). */
    public boolean isEvening(double clockHour) {
        if (eveningStartHour <= eveningEndHour) {
            return clockHour >= eveningStartHour && clockHour < eveningEndHour;
        }
        return clockHour >= eveningStartHour || clockHour < eveningEndHour;
    }

    public boolean isSimulateInCreative() { return simulateInCreative; }
}
