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
        double secondsOutOfSun = 0;
        float lastStamina = Float.NaN;
        boolean wasJumping;
        boolean inSun;
        double sunlightFactor;
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

    double secondsOutOfSun(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? 0 : rt.secondsOutOfSun;
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
        rt.sunlightFactor = 0;
        boolean sun = SunProbe.inDirectSun(world, store, ref, cfg);
        rt.inSun = sun;
        double exposure = state.exposure;
        if (sun) {
            rt.secondsOutOfSun = 0;
            exposure += cfg.getExposureGainPerSecond() * service.getModifier(id, ModifierType.EXPOSURE_GAIN_MULTIPLIER) * dt;
        } else {
            rt.secondsOutOfSun += dt;
            if (rt.secondsOutOfSun >= cfg.getExposureGraceSeconds()) {
                exposure -= cfg.getExposureDecayPerSecond()
                        * service.getModifier(id, ModifierType.EXPOSURE_DECAY_MULTIPLIER) * dt;
            }
        }
        exposure += service.getModifier(id, ModifierType.EXPOSURE_OFFSET) * dt;
        service.applyExposure(player, state, exposure);

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
