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
    /** Cooling in the shade is this many times ExposureDecayPerSecond (comfort: recovery is faster than heating). */
    private double shadeRecoveryMultiplier = 2.0;
    /** Continuous seconds out of the sun before exposure starts to decay. */
    private double exposureGraceSeconds = 5.0;
    /** Day counts as "sun up" when the world's sunlight factor (0 night .. 1 midday) is at least this. */
    private double minSunlightFactor = 0.25;
    /** Blocks above the head that must be free of solid blocks for the player to count as in direct sun. */
    private double skyClearBlocks = 0;
    /**
     * True: the sun check is a ray from the player toward the sun, so shade follows the sun. False: the old check
     * (nothing above the head by the height map), kept so the two can be compared.
     */
    private boolean useShadeRays = true;
    /** Blocks a ray toward the sun is followed. Low sun needs long rays; shorter means far mountains do not shade. */
    private double shadeRayLength = 48;
    /** Shade added by each semi-transparent solid block (leaves) on a ray. 1.0 in total fully shades it. */
    private double partialShadeWeight = 0.5;
    /** Below this sun fraction the player counts as in shade (the grace timer runs). */
    private double shadeThreshold = 0.5;
    /** How many times a second a player's sun check is made. The simulation uses the last result in between. */
    private double shadeChecksPerSecond = 4;
    /** Heights above the feet of the three sample points: head, chest, legs. */
    private double[] sunSampleHeights = {1.6, 1.0, 0.3};
    /** The sun fraction is smoothed over about this many seconds so a shadow edge does not make it flicker. */
    private double sunSmoothingSeconds = 0.5;

    // ------------------------------------------------------------------ water drain
    private double waterDrainBasePerSecond = 0.02;
    private double waterDrainRunPerSecond = 0.03;
    private double waterDrainClimbPerSecond = 0.03;
    private double waterDrainPerJump = 0.05;
    /** Exposure is cut in steps of this many percent for the drain multiplier. */
    private double exposureDrainStepPercent = 10;
    /** Added to the drain multiplier for each full step of exposure (0.10 gives x1.0 .. x2.0 across 0..100). */
    private double exposureDrainStepBonus = 0.10;

    // ------------------------------------------------------------------ stamina costs of this mod
    /**
     * Stamina taken by one jump, in seconds of sprinting: 1.0 is the same as one second of sprint (10% of the stamina
     * bar, since vanilla sprint drains 1.0 of 10 per second). The sprint rate is read from Stamina.json at startup, so
     * this follows it if the asset changes. Full price at every water level. Vanilla charges nothing for a jump.
     */
    private double jumpCostSprintSeconds = 1.0;
    /** The same for a vault (the ledge pull-up, the engine mantling state). */
    private double vaultCostSprintSeconds = 1.0;
    /**
     * A vault that starts within this many seconds after a jump that was charged is the same action: only the extra
     * (the vault cost less what the jump already took) is charged, so a jump into a vault costs the higher of the
     * two, not both.
     */
    private double jumpToVaultWindowSeconds = 0.8;
    /**
     * True (default): while stamina is 0 or less, jumping and sprinting are disabled (short movement effects, renewed
     * while it holds), the same as attacks and blocking, which the game already refuses without stamina. Walking is never
     * blocked, and a single-block step-up is meant to stay possible (whether the engine's automatic step-up counts as a
     * jump is not known). Whether a ledge vault is stopped is not known either. False turns this off.
     */
    private boolean blockActionsAtZeroStamina = true;
    /**
     * Base regeneration pauses, in vanilla seconds (the StaminaRegenDelay stat); the stamina rework multiplies every pause
     * by the pause multiplier P. They are ADDITIONS to vanilla for jump, vault and climbing (vanilla has no pause there).
     * For sprinting vanilla already pauses 0.75 s when sprinting stops (Plugin.Stamina.SprintRegenDelay); a positive value
     * here REPLACES that 0.75 with this one (0.5), 0 keeps vanilla. Jump and vault set at least this much (a longer pause
     * already running is never shortened); climbing holds at least this much while it lasts.
     */
    private double sprintEndPauseSeconds = 0.5;
    private double jumpPauseSeconds = 0.5;
    private double vaultPauseSeconds = 0.5;
    private double climbPauseSeconds = 0.5;
    /** Stamina taken per second of climbing at multiplier 1.0. Vanilla 0.6.8 charges nothing for climbing. */
    private double climbStaminaCostPerSecond = 0;
    /**
     * Natural regeneration is told from a restore (potion, food) by its size: vanilla regenerates in steps of one
     * regenerating amount (0.3, read from Stamina.json), and a tick may hold up to this many steps. A rise that is not a
     * whole number of steps (within NaturalRegenTolerance) is a restore and is left alone.
     */
    private double naturalRegenMaxSteps = 3;
    private double naturalRegenTolerance = 0.02;

    // ------------------------------------------------------------------ stamina curves (catching your breath)
    // w = water / 100, e = exposure / 100, dry = 1 - w. Full water and no exposure give exactly 1.0 for both multipliers.
    /** Pause multiplier P = (1 + PauseDryScale * dry^PauseDryExponent) * (1 + PauseExposureScale * e). */
    private double pauseDryScale = 7.0;
    private double pauseDryExponent = 2.0;
    private double pauseExposureScale = 0.5;
    /** Regen speed multiplier R = 1 / ((1 + RegenDryScale * dry^RegenDryExponent) * (1 + RegenExposureScale * e)). */
    private double regenDryScale = 1.5;
    private double regenDryExponent = 2.0;
    private double regenExposureScale = 0.25;
    /** Water taken for each stamina point that comes back by natural regeneration, before the exposure multiplier. */
    private double regenWaterCostPerPoint = 0.05;
    /** The longest a regeneration pause may be, in seconds (the minimum of the StaminaRegenDelay stat). */
    private double maxPauseSeconds = 60.0;

    // ------------------------------------------------------------------ out of water
    /**
     * Health damage per unit of water that was due to be drained but could not be, while water AND stamina are both 0.
     * 5.0 makes the idle bleed 0.1 HP per second (the baseline drain is 0.02 water per second), i.e. 1 HP every 10
     * seconds in the shade, twice that in full sun (the exposure multiplier still applies), more when running or jumping.
     */
    private double zeroWaterHpPerWaterUnit = 5.0;
    /**
     * While water and stamina are both 0, an action that costs stamina (sprint, jump, vault, climb) costs this much HP
     * per point of stamina it could not take: 1.0 is 1 HP for each stamina the action would have cost (one jump, or one
     * second of sprint, is 1 HP).
     */
    private double zeroStaminaHpPerStamina = 1.0;
    /** The damage is applied at most this often (seconds), so the hurt flash and sound do not repeat many times a second. */
    private double zeroWaterDamageIntervalSeconds = 1.0;
    /**
     * Damage is held in an accumulator and dealt in whole steps of this many HP (fractions carry over). With 1.0 the
     * idle bleed of 0.02 HP per second becomes one hit of 1 HP about every 50 seconds. 0 deals the accumulated amount
     * (fractions included) every interval.
     */
    private double zeroWaterMinHit = 1.0;
    /** Movement speed multiplier at 0 water (0.9 = 10% slower; 0.1 = 10% of normal). Snapped to the nearest 5%. */
    private double zeroWaterSpeedMultiplier = 0.9;
    /** Water and exposure a player has after respawning. */
    private double respawnWater = 50;
    private double respawnExposure = 0;

    // ------------------------------------------------------------------ HUD
    /**
     * How the bar is sized. "percent" (default): the bar starts HudTopPercent of the way down the space above its icon
     * and ends HudIconGap above the icon, so its length is a true share of the screen height. "margins": it starts
     * HudTopMargin virtual pixels from the top. "fixed": a bar HudFixedHeight pixels long starting HudTopMargin down.
     */
    private String hudMode = "percent";
    /** Distance of each bar from its screen edge, in virtual pixels. */
    private double hudEdgeMargin = 18;
    /** Thickness of each bar (the vanilla bars are 12). */
    private double hudBarThickness = 12;
    /** Mode percent: space above the bar as a share of the column (the screen height less the icon strip), in percent. */
    private double hudTopPercent = 6.5;
    /** Modes margins and fixed: space above the bar in virtual pixels, and for fixed the bar length. */
    private double hudTopMargin = 60;
    private double hudFixedHeight = 624;
    /** Gap between the bottom of the bar and the top of its icon, in virtual pixels. */
    private double hudIconGap = 4;
    /** Icon size and its distance from the bottom of the screen, in virtual pixels. */
    private double hudIconSize = 24;
    private double hudIconBottomMargin = 20;
    /** Exposure bar turns from orange to red between this percent and 100. */
    private double exposureRedStartPercent = 75;
    private String exposureColorOrange = "#ff8a1f";
    private String exposureColorRed = "#e0201a";
    private String waterColor = "#2f8fe0";
    /** Smallest change in a bar (percent of its length) that is worth sending to the client. */
    private double hudMinPercentChange = 0.25;

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
        b = num(b, "ShadeRecoveryMultiplier", (c, v) -> c.shadeRecoveryMultiplier = v, c -> c.shadeRecoveryMultiplier,
                "Cooling in the shade is this many times ExposureDecayPerSecond (2 = twice as fast as the sun heats).");
        b = num(b, "ExposureGraceSeconds", (c, v) -> c.exposureGraceSeconds = v, c -> c.exposureGraceSeconds,
                "Continuous seconds out of the sun before exposure starts to decay.");
        b = num(b, "MinSunlightFactor", (c, v) -> c.minSunlightFactor = v, c -> c.minSunlightFactor,
                "The sun counts as up when the world sunlight factor (0 night to 1 midday) is at least this.");
        b = num(b, "SkyClearBlocks", (c, v) -> c.skyClearBlocks = v, c -> c.skyClearBlocks,
                "Only used when UseShadeRays is false: extra blocks above the head that may be solid and still count as sun.");
        b = b.append(new KeyedCodec<>("UseShadeRays", Codec.BOOLEAN, false),
                        (c, v) -> c.useShadeRays = v, c -> c.useShadeRays)
                .documentation("True: sun is a ray from the player toward the sun, so shade moves with the sun. "
                        + "False: the old check (nothing above the head), for comparison.")
                .add();
        b = num(b, "ShadeRayLength", (c, v) -> c.shadeRayLength = v, c -> c.shadeRayLength,
                "Blocks each ray toward the sun is followed.");
        b = num(b, "PartialShadeWeight", (c, v) -> c.partialShadeWeight = v, c -> c.partialShadeWeight,
                "Shade added by each semi-transparent solid block (leaves) on a ray; 1.0 in total is full shade.");
        b = num(b, "ShadeThreshold", (c, v) -> c.shadeThreshold = v, c -> c.shadeThreshold,
                "Below this sun fraction (0..1) the player is in shade and the grace timer runs.");
        b = num(b, "ShadeChecksPerSecond", (c, v) -> c.shadeChecksPerSecond = v, c -> c.shadeChecksPerSecond,
                "How many times a second each player's sun check is made.");
        b = arr(b, "SunSampleHeights", (c, v) -> c.sunSampleHeights = v, c -> c.sunSampleHeights,
                "Heights above the feet of the sample points (head, chest, legs). Each casts its own ray.");
        b = num(b, "SunSmoothingSeconds", (c, v) -> c.sunSmoothingSeconds = v, c -> c.sunSmoothingSeconds,
                "The sun fraction is smoothed over about this long so a shadow edge does not flicker the timer.");
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
        b = num(b, "JumpCostSprintSeconds", (c, v) -> c.jumpCostSprintSeconds = v, c -> c.jumpCostSprintSeconds,
                "Stamina one jump costs, in seconds of sprinting (1.0 = the same as one second of sprint, 10% of the bar). The sprint rate is read from Stamina.json at startup.");
        b = num(b, "VaultCostSprintSeconds", (c, v) -> c.vaultCostSprintSeconds = v, c -> c.vaultCostSprintSeconds,
                "Stamina one vault (ledge pull-up) costs, in seconds of sprinting.");
        b = num(b, "JumpToVaultWindowSeconds", (c, v) -> c.jumpToVaultWindowSeconds = v, c -> c.jumpToVaultWindowSeconds,
                "A vault this soon after a charged jump is the same action: only the extra cost is charged, not both.");
        b = num(b, "SprintEndPauseSeconds", (c, v) -> c.sprintEndPauseSeconds = v, c -> c.sprintEndPauseSeconds,
                "Pause after sprinting stops, in vanilla seconds. Vanilla is 0.75 (set by the game); a positive value replaces it. 0 keeps vanilla.");
        b = num(b, "JumpPauseSeconds", (c, v) -> c.jumpPauseSeconds = v, c -> c.jumpPauseSeconds,
                "Regeneration pause after a jump (an addition to vanilla, which has none). 0 = none.");
        b = num(b, "VaultPauseSeconds", (c, v) -> c.vaultPauseSeconds = v, c -> c.vaultPauseSeconds,
                "Regeneration pause after a vault (an addition to vanilla). 0 = none.");
        b = num(b, "ClimbPauseSeconds", (c, v) -> c.climbPauseSeconds = v, c -> c.climbPauseSeconds,
                "Regeneration pause held while climbing and after it stops (an addition to vanilla). 0 = none.");
        b = b.append(new KeyedCodec<>("BlockActionsAtZeroStamina", Codec.BOOLEAN, false),
                        (c, v) -> c.blockActionsAtZeroStamina = v, c -> c.blockActionsAtZeroStamina)
                .documentation("Disable jumping and sprinting while stamina is 0 or less, like attacks and blocking. Walking and single-block step-ups stay possible.")
                .add();
        b = num(b, "ClimbStaminaCostPerSecond", (c, v) -> c.climbStaminaCostPerSecond = v,
                c -> c.climbStaminaCostPerSecond,
                "Stamina taken per second of climbing at multiplier 1. Vanilla charges none, so 0 keeps vanilla.");
        b = num(b, "NaturalRegenMaxSteps", (c, v) -> c.naturalRegenMaxSteps = v, c -> c.naturalRegenMaxSteps,
                "A stamina rise of 1 to this many regeneration steps (0.3 each) on one tick is natural regeneration; any other rise is a restore.");
        b = num(b, "NaturalRegenTolerance", (c, v) -> c.naturalRegenTolerance = v, c -> c.naturalRegenTolerance,
                "How close a rise must be to a whole number of steps to count as natural regeneration.");
        b = num(b, "PauseDryScale", (c, v) -> c.pauseDryScale = v, c -> c.pauseDryScale,
                "Pause multiplier P = (1 + PauseDryScale * dry^PauseDryExponent) * (1 + PauseExposureScale * exposure), dry = 1 - water. 1.0 at full water and no exposure.");
        b = num(b, "PauseDryExponent", (c, v) -> c.pauseDryExponent = v, c -> c.pauseDryExponent, "See PauseDryScale.");
        b = num(b, "PauseExposureScale", (c, v) -> c.pauseExposureScale = v, c -> c.pauseExposureScale, "See PauseDryScale.");
        b = num(b, "RegenDryScale", (c, v) -> c.regenDryScale = v, c -> c.regenDryScale,
                "Regen speed R = 1 / ((1 + RegenDryScale * dry^RegenDryExponent) * (1 + RegenExposureScale * exposure)). 1.0 at full water and no exposure.");
        b = num(b, "RegenDryExponent", (c, v) -> c.regenDryExponent = v, c -> c.regenDryExponent, "See RegenDryScale.");
        b = num(b, "RegenExposureScale", (c, v) -> c.regenExposureScale = v, c -> c.regenExposureScale, "See RegenDryScale.");
        b = num(b, "RegenWaterCostPerPoint", (c, v) -> c.regenWaterCostPerPoint = v, c -> c.regenWaterCostPerPoint,
                "Water each stamina point of natural regeneration costs, times the exposure drain multiplier (x1.0 to x2.0).");
        b = num(b, "MaxPauseSeconds", (c, v) -> c.maxPauseSeconds = v, c -> c.maxPauseSeconds,
                "The longest regeneration pause in seconds (the StaminaRegenDelay stat goes down to -60).");
        b = num(b, "ZeroWaterHpPerWaterUnit", (c, v) -> c.zeroWaterHpPerWaterUnit = v, c -> c.zeroWaterHpPerWaterUnit,
                "HP of damage per unit of water that could not be drained while water and stamina are both 0 (5 gives 1 HP per 10 s idle in the shade).");
        b = num(b, "ZeroStaminaHpPerStamina", (c, v) -> c.zeroStaminaHpPerStamina = v, c -> c.zeroStaminaHpPerStamina,
                "While water and stamina are both 0, stamina an action could not take is taken as this much HP per point (1 = 1 HP per jump, 1 HP per second of sprint).");
        b = num(b, "ZeroWaterDamageIntervalSeconds", (c, v) -> c.zeroWaterDamageIntervalSeconds = v, c -> c.zeroWaterDamageIntervalSeconds,
                "The thirst damage is dealt at most this often, in seconds.");
        b = num(b, "ZeroWaterMinHit", (c, v) -> c.zeroWaterMinHit = v, c -> c.zeroWaterMinHit,
                "Thirst damage is dealt in whole steps of this many HP; the rest carries over. 0 deals the accumulated amount every interval.");
        b = num(b, "ZeroWaterSpeedMultiplier", (c, v) -> c.zeroWaterSpeedMultiplier = v, c -> c.zeroWaterSpeedMultiplier,
                "Movement speed at 0 water: 0.9 is 10% slower, 0.1 is 10% of normal. Snapped to the nearest 5%.");
        b = num(b, "RespawnWater", (c, v) -> c.respawnWater = v, c -> c.respawnWater, "Water after respawning.");
        b = num(b, "RespawnExposure", (c, v) -> c.respawnExposure = v, c -> c.respawnExposure, "Exposure after respawning.");
        b = str(b, "HudMode", (c, v) -> c.hudMode = v, c -> c.hudMode,
                "percent: bar starts HudTopPercent down and ends HudIconGap above its icon. margins: starts HudTopMargin pixels down. fixed: HudFixedHeight pixels long.");
        b = num(b, "HudEdgeMargin", (c, v) -> c.hudEdgeMargin = v, c -> c.hudEdgeMargin,
                "Distance of each bar from its screen edge, in virtual pixels.");
        b = num(b, "HudBarThickness", (c, v) -> c.hudBarThickness = v, c -> c.hudBarThickness,
                "Thickness of each bar in virtual pixels (the vanilla bars are 12).");
        b = num(b, "HudTopPercent", (c, v) -> c.hudTopPercent = v, c -> c.hudTopPercent,
                "Mode percent: space above the bar as a percent of the column (screen height less the icon strip). 6.5 puts the top of the bar about 6% down the screen.");
        b = num(b, "HudTopMargin", (c, v) -> c.hudTopMargin = v, c -> c.hudTopMargin,
                "Modes margins and fixed: space above the bar in virtual pixels.");
        b = num(b, "HudIconGap", (c, v) -> c.hudIconGap = v, c -> c.hudIconGap,
                "Gap in virtual pixels between the bottom of the bar and the top of its icon.");
        b = num(b, "HudFixedHeight", (c, v) -> c.hudFixedHeight = v, c -> c.hudFixedHeight,
                "Mode fixed: bar length in virtual pixels (624 is 60% of a 1040 pixel screen).");
        b = num(b, "HudIconSize", (c, v) -> c.hudIconSize = v, c -> c.hudIconSize,
                "Size of the water and sun icons in virtual pixels.");
        b = num(b, "HudIconBottomMargin", (c, v) -> c.hudIconBottomMargin = v, c -> c.hudIconBottomMargin,
                "Distance of the icons from the bottom of the screen in virtual pixels.");
        b = num(b, "ExposureRedStartPercent", (c, v) -> c.exposureRedStartPercent = v, c -> c.exposureRedStartPercent,
                "The exposure bar is orange up to here and blends to red at 100.");
        b = str(b, "ExposureColorOrange", (c, v) -> c.exposureColorOrange = v, c -> c.exposureColorOrange,
                "Exposure bar colour from 0 to ExposureRedStartPercent.");
        b = str(b, "ExposureColorRed", (c, v) -> c.exposureColorRed = v, c -> c.exposureColorRed,
                "Exposure bar colour at 100%.");
        b = str(b, "WaterColor", (c, v) -> c.waterColor = v, c -> c.waterColor, "Water bar colour.");
        b = num(b, "HudMinPercentChange", (c, v) -> c.hudMinPercentChange = v, c -> c.hudMinPercentChange,
                "A bar is only re-sent to the client when it changes by at least this many percent of its length.");
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


    /** Multiplier applied to water drain at this exposure: 1 + floor(exposure / step) * bonus. */
    public double exposureDrainMultiplier(double exposure) {
        double step = Math.max(1e-6, exposureDrainStepPercent);
        return 1.0 + Math.floor(exposure / step) * exposureDrainStepBonus;
    }

    // ------------------------------------------------------------------ getters

    public double getInitialWater() { return initialWater; }
    public double getInitialExposure() { return initialExposure; }
    public double getExposureGainPerSecond() { return exposureGainPerSecond; }
    public double getExposureDecayPerSecond() { return exposureDecayPerSecond; }
    public double getShadeRecoveryMultiplier() { return shadeRecoveryMultiplier; }
    public double getExposureGraceSeconds() { return exposureGraceSeconds; }
    public double getMinSunlightFactor() { return minSunlightFactor; }
    public int getSkyClearBlocks() { return (int) skyClearBlocks; }
    public boolean isUseShadeRays() { return useShadeRays; }
    public double getShadeRayLength() { return shadeRayLength; }
    public double getPartialShadeWeight() { return partialShadeWeight; }
    public double getShadeThreshold() { return shadeThreshold; }
    public double getShadeChecksPerSecond() { return shadeChecksPerSecond; }
    public double getSunSmoothingSeconds() { return sunSmoothingSeconds; }
    public double[] getSunSampleHeights() {
        return sunSampleHeights == null || sunSampleHeights.length == 0 ? new double[] {1.6, 1.0, 0.3} : sunSampleHeights;
    }
    public double getWaterDrainBasePerSecond() { return waterDrainBasePerSecond; }
    public double getWaterDrainRunPerSecond() { return waterDrainRunPerSecond; }
    public double getWaterDrainClimbPerSecond() { return waterDrainClimbPerSecond; }
    public double getWaterDrainPerJump() { return waterDrainPerJump; }
    public double getExposureDrainStepPercent() { return exposureDrainStepPercent; }
    public double getJumpCostSprintSeconds() { return jumpCostSprintSeconds; }
    public double getVaultCostSprintSeconds() { return vaultCostSprintSeconds; }
    public double getJumpToVaultWindowSeconds() { return jumpToVaultWindowSeconds; }
    public double getSprintEndPauseSeconds() { return sprintEndPauseSeconds; }
    public double getJumpPauseSeconds() { return jumpPauseSeconds; }
    public double getVaultPauseSeconds() { return vaultPauseSeconds; }
    public double getClimbPauseSeconds() { return climbPauseSeconds; }
    public boolean isBlockActionsAtZeroStamina() { return blockActionsAtZeroStamina; }
    public double getClimbStaminaCostPerSecond() { return climbStaminaCostPerSecond; }
    public double getZeroWaterHpPerWaterUnit() { return zeroWaterHpPerWaterUnit; }
    public double getZeroStaminaHpPerStamina() { return zeroStaminaHpPerStamina; }
    public double getZeroWaterDamageIntervalSeconds() { return zeroWaterDamageIntervalSeconds; }
    public double getZeroWaterMinHit() { return zeroWaterMinHit; }
    public double getZeroWaterSpeedMultiplier() { return zeroWaterSpeedMultiplier; }
    public double getRespawnWater() { return respawnWater; }
    public double getRespawnExposure() { return respawnExposure; }
    public double getPauseDryScale() { return pauseDryScale; }
    public double getPauseDryExponent() { return pauseDryExponent; }
    public double getPauseExposureScale() { return pauseExposureScale; }
    public double getRegenDryScale() { return regenDryScale; }
    public double getRegenDryExponent() { return regenDryExponent; }
    public double getRegenExposureScale() { return regenExposureScale; }
    public double getRegenWaterCostPerPoint() { return regenWaterCostPerPoint; }
    public double getMaxPauseSeconds() { return maxPauseSeconds; }
    public int getNaturalRegenMaxSteps() { return (int) naturalRegenMaxSteps; }
    public double getNaturalRegenTolerance() { return naturalRegenTolerance; }
    public String getHudMode() { return hudMode == null ? "percent" : hudMode; }
    public double getHudEdgeMargin() { return hudEdgeMargin; }
    public double getHudBarThickness() { return hudBarThickness; }
    public double getHudTopPercent() { return hudTopPercent; }
    public double getHudIconGap() { return hudIconGap; }
    public double getHudTopMargin() { return hudTopMargin; }
    public double getHudFixedHeight() { return hudFixedHeight; }
    public double getHudIconSize() { return hudIconSize; }
    public double getHudIconBottomMargin() { return hudIconBottomMargin; }
    public double getExposureRedStartPercent() { return exposureRedStartPercent; }
    public String getExposureColorOrange() { return exposureColorOrange; }
    public String getExposureColorRed() { return exposureColorRed; }
    public String getWaterColor() { return waterColor; }
    public double getHudMinPercentChange() { return hudMinPercentChange; }
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
