package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The server side simulation, run once per world tick for every player: sun exposure, water drain, the stamina
 * effects of thirst, and the HUD refresh. The server is the only authority; the HUD just displays.
 *
 * <p>Stamina is scaled by watching the stat. In 0.6.8 the stamina bar is driven by {@code Server/Entity/Stats/
 * Stamina.json}: sprinting subtracts 0.1 every 0.1 s and the rest regenerates 0.3 every 0.1 s, as stat "regenerating"
 * entries with conditions, which a plugin cannot scale. So each tick this compares stamina with what it was at the
 * end of the last tick: while sprinting a drop is partly handed back (cost multiplier), and a slow rise is partly
 * taken away (regeneration multiplier). Attacks and other costs are never touched.
 */
final class WaterSystem extends TickingSystem<EntityStore> {

    /** What the simulation remembers about one player between ticks. Not saved. */
    private static final class Runtime {
        /** Continuous seconds the player has been in shade (sun fraction below ShadeThreshold). */
        double secondsOutOfSun = 0;
        float lastStamina = Float.NaN;
        boolean wasJumping;
        boolean inSun;
        final SunProbe probe = new SunProbe();
        /** Seconds since the last sun check, and its result. NaN until the first check. */
        double checkTimer;
        double rawFraction = Double.NaN;
        /** The sun fraction the simulation uses: the checks, smoothed, times the sun intensity modifiers. */
        double fraction;
        /** Exposure change per second on the last tick, for /waterdebug. */
        double exposureRate;
        WaterHud hud;
        int lastTier = -1;
        double regenPerSecond;
        double regenSecondsLeft;
    }

    private final Supplier<WaterOfArrakisConfig> config;
    private final WaterService service;
    private final Map<UUID, Runtime> runtimes = new ConcurrentHashMap<>();

    WaterSystem(Supplier<WaterOfArrakisConfig> config, WaterService service) {
        this.config = config;
        this.service = service;
    }

    void forget(UUID player) {
        runtimes.remove(player);
        service.forget(player);
    }

    /** Starts (or replaces) a health regeneration of perSecond for the given seconds, from food. */
    void addHealthRegen(UUID player, double perSecond, double seconds) {
        Runtime rt = runtimes.computeIfAbsent(player, id -> new Runtime());
        rt.regenPerSecond = perSecond;
        rt.regenSecondsLeft = seconds;
    }

    /** For /waterdebug. */
    boolean isInSun(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt != null && rt.inSun;
    }

    /** Seconds the player has been continuously in shade (0 in the sun). */
    double secondsOutOfSun(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? 0 : rt.secondsOutOfSun;
    }

    /** The player's sun probe with its last readings, or null before the first tick. For the debug commands. */
    SunProbe probeOf(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? null : rt.probe;
    }

    /** Exposure change per second on the player's last tick. */
    double exposureRate(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? 0 : rt.exposureRate;
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WaterOfArrakisConfig cfg = config.get();
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            tickPlayer(world, store, ref, player, cfg, dt);
        }
    }

    private void tickPlayer(World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                            WaterOfArrakisConfig cfg, float dt) {
        WaterState state = service.state(player);
        if (state == null) {
            return;
        }
        Runtime rt = runtimes.computeIfAbsent(player.getUuid(), id -> new Runtime());
        Player p = store.getComponent(ref, Player.getComponentType());
        boolean dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
        boolean creative = p != null && p.getGameMode() == GameMode.Creative;
        MovementStatesComponent movement = store.getComponent(ref, MovementStatesComponent.getComponentType());
        MovementStates states = movement == null ? null : movement.getMovementStates();

        if (!dead && (!creative || cfg.isSimulateInCreative())) {
            simulate(world, store, ref, player, state, rt, cfg, states, dt);
        } else {
            rt.lastStamina = Float.NaN;
            rt.wasJumping = false;
        }
        updateHud(p, player, state, rt, cfg);
    }

    private void simulate(World world, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                          WaterState state, Runtime rt, WaterOfArrakisConfig cfg, MovementStates states, float dt) {
        UUID id = player.getUuid();
        // The tier at the START of the tick decides the stamina effects, so a tick that drains across a boundary
        // is still judged by where the player was.
        int tier = cfg.waterTier(state.water);
        rt.lastTier = tier;

        // ---- exposure
        // 1. The sun check runs ShadeChecksPerSecond times a second; in between the last result is used. The result
        //    is smoothed over SunSmoothingSeconds so walking along a shadow edge does not flicker the timer. (The old
        //    height-map check, UseShadeRays false, is not smoothed so it behaves exactly as it did.)
        rt.checkTimer += dt;
        double interval = 1.0 / Math.max(0.1, cfg.getShadeChecksPerSecond());
        boolean first = Double.isNaN(rt.rawFraction);
        if (first || rt.checkTimer >= interval) {
            rt.checkTimer = 0;
            rt.rawFraction = rt.probe.measure(world, store, ref, cfg);
        }
        double alpha = !cfg.isUseShadeRays() || first || cfg.getSunSmoothingSeconds() <= 0
                ? 1.0 : 1.0 - Math.exp(-dt / cfg.getSunSmoothingSeconds());
        double smoothed = first ? rt.rawFraction : rt.fraction + (rt.rawFraction - rt.fraction) * alpha;
        // 2. Other mods dim the sun (storms) or shield the player with SUN_INTENSITY_MULTIPLIER, per player or per world.
        double intensity = service.getModifier(id, ModifierType.SUN_INTENSITY_MULTIPLIER)
                * service.getModifier(world.getWorldConfig().getUuid(), ModifierType.SUN_INTENSITY_MULTIPLIER);
        rt.fraction = smoothed;
        double fraction = Math.max(0.0, Math.min(1.0, smoothed * intensity));
        service.setSunFraction(id, fraction);

        // 3. Gain is proportional to the sun fraction, so it has no step at the shade threshold. The grace timer
        //    counts continuous time with the fraction below ShadeThreshold. After ExposureGraceSeconds of that the
        //    player is cooling: exposure falls at ExposureDecayPerSecond and nothing is gained. (At the moment the
        //    timer runs out the rate changes from a small gain to the full decay; that is the grace period ending.)
        boolean inShade = fraction < cfg.getShadeThreshold();
        rt.inSun = !inShade;
        double rate;
        if (inShade) {
            rt.secondsOutOfSun += dt;
        } else {
            rt.secondsOutOfSun = 0;
        }
        if (inShade && rt.secondsOutOfSun >= cfg.getExposureGraceSeconds()) {
            rate = -cfg.getExposureDecayPerSecond() * cfg.getShadeRecoveryMultiplier()
                    * service.getModifier(id, ModifierType.EXPOSURE_DECAY_MULTIPLIER);
        } else {
            rate = cfg.getExposureGainPerSecond() * fraction * service.getModifier(id, ModifierType.EXPOSURE_GAIN_MULTIPLIER);
        }
        rate += service.getModifier(id, ModifierType.EXPOSURE_OFFSET);
        rt.exposureRate = rate;
        service.applyExposure(player, state, state.exposure + rate * dt);

        // ---- water
        boolean running = states != null && (states.running || states.sprinting);
        boolean climbing = states != null && states.climbing;
        boolean jumping = states != null && states.jumping;
        boolean jumpStart = jumping && !rt.wasJumping;
        rt.wasJumping = jumping;

        double exposureMult = cfg.exposureDrainMultiplier(state.exposure);
        double baseMult = service.getModifier(id, ModifierType.WATER_DRAIN_MULTIPLIER);
        double actionMult = service.getModifier(id, ModifierType.WATER_ACTION_DRAIN_MULTIPLIER);

        double drain = cfg.getWaterDrainBasePerSecond() * baseMult * dt;
        if (running) {
            drain += cfg.getWaterDrainRunPerSecond() * actionMult * dt;
        }
        if (climbing) {
            drain += cfg.getWaterDrainClimbPerSecond() * actionMult * dt;
        }
        if (jumpStart) {
            drain += cfg.getWaterDrainPerJump() * actionMult;
        }
        service.applyWater(player, state, state.water - drain * exposureMult);

        // ---- stamina
        scaleStamina(store, ref, rt, cfg, tier, states, jumpStart, dt);

        // ---- health regeneration from food (Spicebread)
        if (rt.regenSecondsLeft > 0) {
            double seconds = Math.min(dt, rt.regenSecondsLeft);
            rt.regenSecondsLeft -= seconds;
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.addStatValue(DefaultEntityStatTypes.getHealth(), (float) (rt.regenPerSecond * seconds));
            }
        }
    }

    private void scaleStamina(Store<EntityStore> store, Ref<EntityStore> ref, Runtime rt, WaterOfArrakisConfig cfg,
                              int tier, MovementStates states, boolean jumpStart, float dt) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }
        int staminaIndex = DefaultEntityStatTypes.getStamina();
        EntityStatValue stamina = stats.get(staminaIndex);
        if (stamina == null) {
            return;
        }
        float current = stamina.get();
        if (!Float.isNaN(rt.lastStamina) && dt > 0) {
            double costMult = cfg.actionStaminaCost(tier);
            double regenMult = cfg.staminaRegen(tier);
            double delta = current - rt.lastStamina;
            double adjust = 0;
            boolean sprinting = states != null && states.sprinting;
            if (delta < 0 && sprinting) {
                // Vanilla sprint drain: hand back the share the thirst tier waives.
                adjust += -delta * (1.0 - costMult);
            } else if (delta > 0 && delta / dt <= cfg.getRegenDeltaCapPerSecond()) {
                // Regeneration: take away the share the thirst tier removes.
                adjust -= delta * (1.0 - regenMult);
            }
            // Costs vanilla does not have (0 by default), scaled by the same tier.
            if (jumpStart) {
                adjust -= cfg.getJumpStaminaCost() * costMult;
            }
            if (states != null && states.climbing) {
                adjust -= cfg.getClimbStaminaCostPerSecond() * costMult * dt;
            }
            if (adjust != 0) {
                stats.addStatValue(staminaIndex, (float) adjust);
                current = stamina.get();
            }
        }
        rt.lastStamina = current;
    }

    private void updateHud(Player p, PlayerRef player, WaterState state, Runtime rt, WaterOfArrakisConfig cfg) {
        if (p == null) {
            return;
        }
        // A HUD that is gone (first tick, respawn, world change) is added again.
        if (rt.hud == null || p.getHudManager().getCustomHud(WaterHud.KEY) != rt.hud) {
            rt.hud = new WaterHud(player, cfg);
            p.getHudManager().addCustomHud(player, rt.hud);
        }
        rt.hud.refresh(state.water, state.exposure);
    }
}
