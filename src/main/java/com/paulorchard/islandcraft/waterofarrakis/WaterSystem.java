package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.stamina.SprintStaminaRegenDelay;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The server side simulation, run once per world tick for every player: sun exposure, water drain, the stamina rework
 * (regeneration pause and speed, and the water that catching your breath costs), the out-of-water effects, and the HUD
 * refresh. The server is the only authority; the HUD just displays. It is an entity system ordered after the engine's
 * stat regeneration, so each tick it sees exactly what regeneration did and corrects it (see {@link StaminaBreath}).
 */
final class WaterSystem extends EntityTickingSystem<EntityStore> {

    /** What the simulation remembers about one player between ticks. Not saved. */
    private static final class Runtime {
        /** Continuous seconds the player has been in shade (sun fraction below ShadeThreshold). */
        double secondsOutOfSun = 0;
        float lastStamina = Float.NaN;
        boolean wasJumping;
        final ActionCosts actions = new ActionCosts();
        final ThirstAccumulator thirst = new ThirstAccumulator();
        /** Stamina the actions of this tick could not take because there was none left. */
        double unpaidStamina;
        boolean wasDead;
        boolean wasZeroWater;
        boolean bleedAnnounced;
        /** Percent of the speed-slow effect applied now (5..95), 0 for none, and seconds to the next renewal. */
        int slowPct;
        double slowTimer;
        double blockTimer;
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
        boolean wasSprinting;
        /** The pause stat after the last tick (NaN until known), its value now, and whether it is pinned at its minimum. */
        double lastDelay = Double.NaN;
        double delayNow;
        boolean pinned;
        /** The pause multiplier P of the last tick. */
        double lastPause = 1.0;
        /** Water taken by natural regeneration: in the last full second, and over the current stretch of regeneration. */
        double regenClock;
        double sinceRegen = 10;
        double costThisSecond;
        double lastSecondCost;
        double stretchCost;
        double regenPerSecond;
        double regenSecondsLeft;
    }

    private final Supplier<WaterOfArrakisConfig> config;
    private final WaterService service;
    private final Map<UUID, Runtime> runtimes = new ConcurrentHashMap<>();
    /** Stamina one second of sprinting costs, read from Stamina.json when the game has loaded (1.0 until then). */
    private volatile double sprintCostPerSecond = 1.0;
    /** The regeneration step of stamina (0.3), read from Stamina.json at startup. */
    private volatile double naturalAmount = 0.3;
    /** The pause stat index and the engine pause after sprinting (0.75), found on first use. */
    private volatile int delayIndex = Integer.MIN_VALUE;
    private volatile double engineSprintPause = 0.75;
    /** How much the engine refills the pause stat per step (0.1 every 0.1 s), read from StaminaRegenDelay.json. */
    private volatile double delayRefillStep = 0.1;
    /** Built on first use: the component types do not exist yet when the plugin object is created. */
    private Query<EntityStore> query;
    private static final String NO_JUMP_EFFECT = "Arrakis_No_Jump";
    private static final String NO_SPRINT_EFFECT = "Arrakis_No_Sprint";
    /** The modifier id this mod uses for the out-of-water slow; other mods can see and change it with it. */
    static final String ZERO_WATER_MODIFIER = "Arrakis:ZeroWater";
    /** Thirst damage waiting for {@link ThirstDamageSystem}. */
    private final Map<UUID, Float> pendingDamage = new ConcurrentHashMap<>();

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

    void setDelayRefillStep(double step) {
        this.delayRefillStep = step;
    }

    void setNaturalRegenAmount(double amount) {
        this.naturalAmount = amount;
    }

    void setSprintCostPerSecond(double perSecond) {
        this.sprintCostPerSecond = perSecond;
    }

    double sprintCostPerSecond() {
        return sprintCostPerSecond;
    }

    /** Jump and vault counts and the stamina charged for the last of each, for /waterdebug. */
    String jumpVaultInfo(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? "no data" : String.format(java.util.Locale.ROOT,
                "jumps %d (last cost %.2f), vaults %d (last cost %.2f)", rt.actions.jumps, rt.actions.lastJumpCharge,
                rt.actions.vaults, rt.actions.lastVaultCharge);
    }

    /** For /waterdebug: P, the pause stat now, whether it is pinned, and the water cost of regeneration. */
    String breathInfo(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? "no data" : String.format(java.util.Locale.ROOT,
                "pause stat %.2f (%s), P x%.2f; regeneration water: %.3f in the last second, %.3f over this stretch",
                rt.delayNow, rt.pinned ? "PINNED at its minimum" : "not pinned", rt.lastPause, rt.lastSecondCost,
                rt.stretchCost);
    }

    /** Takes (and clears) the damage queued for a player. */
    float takePendingDamage(UUID player) {
        Float amount = pendingDamage.remove(player);
        return amount == null ? 0f : amount;
    }

    /** For /waterdebug: the HP owed but not yet dealt, and the slow in percent of normal speed (0 = none). */
    double thirstOwed(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? 0 : rt.thirst.owed();
    }

    int slowPercent(UUID player) {
        Runtime rt = runtimes.get(player);
        return rt == null ? 0 : rt.slowPct;
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
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType(),
                    EntityStatMap.getComponentType(), TransformComponent.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    /**
     * Runs after the engine has regenerated stats this tick, so the rise in stamina and in the pause stat that this
     * tick made can be read exactly. (The engine's own sprint-pause system declares itself BEFORE that system for the
     * opposite reason: so the pause it writes is seen by the regeneration.)
     */
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<EntityStore, EntityStatsModule.PlayerRegenerateStatsSystem>(Order.AFTER,
                EntityStatsModule.PlayerRegenerateStatsSystem.class));
    }

    /**
     * The world-level pass: makes sure every player has a {@link WaterState} (adding a component is not allowed while
     * entities are being processed, so it is done here, first), then runs the per-player pass.
     */
    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        for (PlayerRef player : world.getPlayerRefs()) {
            service.state(player);
        }
        store.tick(this, dt, systemIndex);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (player == null || ref == null || !ref.isValid()) {
            return;
        }
        tickPlayer(store.getExternalData().getWorld(), store, commandBuffer, ref, player, config.get(), dt);
    }

    private void tickPlayer(World world, Store<EntityStore> store, CommandBuffer<EntityStore> cb, Ref<EntityStore> ref,
                            PlayerRef player, WaterOfArrakisConfig cfg, float dt) {
        WaterState state = store.getComponent(ref, WaterState.getComponentType());
        if (state == null) {
            return;
        }
        Runtime rt = runtimes.computeIfAbsent(player.getUuid(), id -> new Runtime());
        Player p = store.getComponent(ref, Player.getComponentType());
        boolean dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
        boolean creative = p != null && p.getGameMode() == GameMode.Creative;
        MovementStatesComponent movement = store.getComponent(ref, MovementStatesComponent.getComponentType());
        MovementStates states = movement == null ? null : movement.getMovementStates();

        if (dead) {
            rt.wasDead = true;
        } else if (rt.wasDead) {
            // Respawned: start again from the configured water and exposure, not from the empty body that died.
            rt.wasDead = false;
            rt.thirst.reset();
            rt.wasZeroWater = false;
            rt.bleedAnnounced = false;
            rt.slowPct = 0;
            service.setWater(player, cfg.getRespawnWater());
            service.setExposure(player, cfg.getRespawnExposure());
        }

        if (!dead && (!creative || cfg.isSimulateInCreative())) {
            simulate(world, store, cb, ref, player, state, rt, cfg, states, dt);
        } else {
            rt.lastStamina = Float.NaN;
            rt.lastDelay = Double.NaN;
            rt.wasSprinting = false;
            rt.wasJumping = false;
            rt.unpaidStamina = 0;
            rt.actions.reset();
            if (rt.slowPct != 0 && !dead) {
                applySlow(cb, ref, rt, 1.0, dt); // creative or frozen: take the slow off
            }
        }
        updateHud(p, player, state, rt, cfg);
    }

    private void simulate(World world, Store<EntityStore> store, CommandBuffer<EntityStore> cb, Ref<EntityStore> ref,
                          PlayerRef player, WaterState state, Runtime rt, WaterOfArrakisConfig cfg, MovementStates states,
                          float dt) {
        UUID id = player.getUuid();
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
        boolean mantling = states != null && states.mantling;

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
        double due = drain * exposureMult;
        double paid = Math.min(state.water, due);
        double unpaid = due - paid;
        service.applyWater(player, state, state.water - paid);

        // ---- stamina
        staminaStep(store, cb, ref, player, state, rt, cfg, states, jumping, mantling, dt);

        // ---- out of water: slow, and damage once stamina is gone too
        boolean waterZero = state.water <= 0.0;
        boolean staminaZero = rt.lastStamina <= 0.0f;
        if (waterZero) {
            service.setModifier(id, ZERO_WATER_MODIFIER, ModifierType.MOVEMENT_SPEED_MULTIPLIER,
                    cfg.getZeroWaterSpeedMultiplier());
        } else {
            service.removeModifier(id, ZERO_WATER_MODIFIER, ModifierType.MOVEMENT_SPEED_MULTIPLIER);
        }
        applySlow(cb, ref, rt, service.getModifier(id, ModifierType.MOVEMENT_SPEED_MULTIPLIER), dt);
        if (waterZero && !rt.wasZeroWater) {
            player.sendMessage(Message.translation("server.waterOfArrakis.thirstStart"));
        }
        rt.wasZeroWater = waterZero;
        double hit = rt.thirst.step(dt, unpaid, rt.unpaidStamina * cfg.getZeroStaminaHpPerStamina(), waterZero,
                staminaZero, cfg.getZeroWaterHpPerWaterUnit(),
                cfg.getZeroWaterDamageIntervalSeconds(), cfg.getZeroWaterMinHit());
        if (waterZero && staminaZero && !rt.bleedAnnounced) {
            rt.bleedAnnounced = true;
            player.sendMessage(Message.translation("server.waterOfArrakis.thirstBleed"));
        } else if (!waterZero) {
            rt.bleedAnnounced = false;
        }
        if (hit > 0) {
            pendingDamage.merge(id, (float) hit, Float::sum);
        }

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

    /**
     * The stamina rework for one player and tick, run right after the engine has regenerated stamina (see
     * {@link #getDependencies}).
     *
     * <ol>
     *   <li>Natural regeneration (a lump of whole regeneration steps on a tick where nothing blocked it) is scaled by the
     *       regeneration speed R and the water for what came back is taken; a restore (potion, food) is left alone.</li>
     *   <li>The costs this mod adds (jump, vault, climb) are taken at full price at every water level.</li>
     *   <li>The pause stat: the engine's refill is stretched by P, the base pauses are set, the cap and the water-0 pin are
     *       applied ({@link StaminaBreath}).</li>
     * </ol>
     * Vanilla costs (sprint, attacks, guards, dodge) are not touched.
     */
    private void staminaStep(Store<EntityStore> store, CommandBuffer<EntityStore> cb, Ref<EntityStore> ref,
                             PlayerRef player, WaterState state, Runtime rt, WaterOfArrakisConfig cfg,
                             MovementStates states, boolean jumping, boolean mantling, float dt) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }
        int staminaIndex = DefaultEntityStatTypes.getStamina();
        EntityStatValue stamina = stats.get(staminaIndex);
        if (stamina == null) {
            return;
        }
        UUID id = player.getUuid();
        float current = stamina.get();
        rt.unpaidStamina = 0;
        boolean sprinting = states != null && states.sprinting;
        boolean gliding = states != null && states.gliding;
        boolean climbing = states != null && states.climbing;

        double pause = StaminaCurves.pauseMultiplier(cfg, state.water, state.exposure)
                * service.getModifier(id, ModifierType.STAMINA_PAUSE_MULTIPLIER);
        rt.lastPause = pause;
        double speed = StaminaCurves.regenMultiplier(cfg, state.water, state.exposure)
                * service.getModifier(id, ModifierType.STAMINA_REGEN_MULTIPLIER);
        double costPerPoint = StaminaCurves.waterCostPerPoint(cfg, state.exposure)
                * service.getModifier(id, ModifierType.STAMINA_REGEN_WATER_COST_MULTIPLIER);

        rt.regenClock += dt;
        rt.sinceRegen += dt;
        if (rt.regenClock >= 1.0) {
            rt.regenClock -= 1.0;
            rt.lastSecondCost = rt.costThisSecond;
            rt.costThisSecond = 0;
        }
        if (rt.sinceRegen > 2.0) {
            rt.stretchCost = 0; // a new stretch of regeneration starts after two seconds without any
        }

        if (!Float.isNaN(rt.lastStamina) && dt > 0) {
            double adjust = 0;

            // 1. natural regeneration: slowed by R, paid for in water
            double delta = current - rt.lastStamina;
            if (StaminaBreath.isNaturalRegen(delta, naturalAmount, cfg.getNaturalRegenMaxSteps(),
                    cfg.getNaturalRegenTolerance(), !sprinting && !gliding)) {
                StaminaBreath.Regen regen = StaminaBreath.regen(delta, speed, costPerPoint, state.water);
                adjust += regen.points() - delta;
                if (regen.waterCost() > 0) {
                    service.applyWater(player, state, state.water - regen.waterCost());
                    rt.costThisSecond += regen.waterCost();
                    rt.stretchCost += regen.waterCost();
                    rt.sinceRegen = 0;
                }
            }

            // 2. costs this mod adds, full price: one charge per rising edge of the jumping and mantling flags
            double charges = rt.actions.step(jumping, mantling, dt,
                    cfg.getJumpCostSprintSeconds() * sprintCostPerSecond,
                    cfg.getVaultCostSprintSeconds() * sprintCostPerSecond, cfg.getJumpToVaultWindowSeconds());
            if (climbing) {
                charges += cfg.getClimbStaminaCostPerSecond() * dt;
            }
            adjust -= charges;
            // What the actions of this tick could not take because stamina ran out (it matters only at 0 water, where
            // it is taken as HP): the charges beyond what is left, and the sprint drain the engine cannot take at 0.
            rt.unpaidStamina = Math.max(0.0, charges - Math.max(0.0, current));
            if (sprinting && current <= 0) {
                rt.unpaidStamina += sprintCostPerSecond * dt;
            }
            if (adjust < 0 && current + adjust < 0) {
                // Our deduction stops at 0; stamina that vanilla already overdrew is left as it is.
                adjust = Math.min(0.0, -Math.max(0.0, current));
            }
            if (adjust > 0) {
                adjust = Math.min(adjust, stamina.getMax() - current);
            }
            if (adjust != 0) {
                stats.addStatValue(staminaIndex, (float) adjust);
                current = stamina.get();
            }
        }
        rt.lastStamina = current;

        // 3. the pause stat
        int delayIdx = delayIndex(store);
        EntityStatValue delay = delayIdx == Integer.MIN_VALUE ? null : stats.get(delayIdx);
        if (delay != null) {
            double now = delay.get();
            double previous = Double.isNaN(rt.lastDelay) ? now : rt.lastDelay;
            double v = StaminaBreath.stretchRefill(previous, now, pause, delayRefillStep, delay.getMax());
            if (rt.actions.jumpEdge) {
                v = StaminaBreath.atLeast(v, cfg.getJumpPauseSeconds());
            }
            if (rt.actions.vaultEdge) {
                v = StaminaBreath.atLeast(v, cfg.getVaultPauseSeconds());
            }
            if (climbing) {
                v = StaminaBreath.atLeast(v, cfg.getClimbPauseSeconds());
            }
            if (rt.wasSprinting && !sprinting) {
                v = StaminaBreath.sprintEnd(v, cfg.getSprintEndPauseSeconds(), engineSprintPause);
            }
            v = StaminaBreath.capPause(v, pause, cfg.getMaxPauseSeconds());
            v = StaminaBreath.pinAtZeroWater(v, delay.getMin(), state.water);
            rt.pinned = state.water <= 0.0;
            if (v != now) {
                stats.setStatValue(delayIdx, (float) v);
            }
            rt.lastDelay = v;
            rt.delayNow = v;
        }
        rt.wasSprinting = sprinting;
        keepActionsBlocked(cb, ref, rt, cfg, current, dt);
    }

    /** The index of the pause stat, from the engine's sprint-delay resource, else by name. */
    private int delayIndex(Store<EntityStore> store) {
        int cached = delayIndex;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        SprintStaminaRegenDelay sprint = store.getResource(SprintStaminaRegenDelay.getResourceType());
        if (sprint != null && sprint.validate() && sprint.hasDelay()) {
            delayIndex = sprint.getIndex();
            engineSprintPause = -sprint.getValue();
        } else {
            delayIndex = EntityStatType.getAssetMap().getIndex("StaminaRegenDelay");
        }
        return delayIndex;
    }

    /**
     * Keeps the movement slow effect in step with the combined MOVEMENT_SPEED_MULTIPLIER modifier. The effect assets
     * Arrakis_Thirst_Slow_05..95 are 5% steps, so the multiplier is snapped to the nearest step; at 97.5% or more
     * there is no slow. The effect is renewed every 0.5 s (it lasts 2 s, so it ends by itself if this mod stops) and
     * removed the tick the multiplier returns to 1. Applying it again replaces it (OVERWRITE), so it never stacks.
     */
    private void applySlow(CommandBuffer<EntityStore> cb, Ref<EntityStore> ref, Runtime rt, double multiplier, float dt) {
        int pct = multiplier >= 0.975 ? 0 : (int) Math.max(5, Math.min(95, Math.round(multiplier * 20) * 5));
        EffectControllerComponent controller = cb.getComponent(ref, EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }
        rt.slowTimer -= dt;
        if (pct == 0) {
            if (rt.slowPct != 0) {
                int old = EntityEffect.getAssetMap().getIndex(slowId(rt.slowPct));
                if (old != Integer.MIN_VALUE) {
                    controller.removeEffect(ref, old, cb);
                }
                rt.slowPct = 0;
            }
            return;
        }
        if (pct != rt.slowPct || rt.slowTimer <= 0) {
            if (rt.slowPct != 0 && rt.slowPct != pct) {
                int old = EntityEffect.getAssetMap().getIndex(slowId(rt.slowPct));
                if (old != Integer.MIN_VALUE) {
                    controller.removeEffect(ref, old, cb);
                }
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(slowId(pct));
            if (effect != null) {
                controller.addEffect(ref, effect, 2.0f, OverlapBehavior.OVERWRITE, cb);
                rt.slowPct = pct;
                rt.slowTimer = 0.5;
            }
        }
    }

    static String slowId(int pct) {
        return String.format(java.util.Locale.ROOT, "Arrakis_Thirst_Slow_%02d", pct);
    }

    /**
     * While stamina is 0 or less (and BlockActionsAtZeroStamina is on) two short movement effects are kept on the player:
     * Arrakis_No_Jump (MovementEffects.DisableJump) and Arrakis_No_Sprint (DisableSprint). They last 0.4 s and are renewed
     * every 0.15 s, so they end by themselves a moment after stamina is above 0, or if this mod stops, and compose with
     * other mods' effects. Attacks and guarding are already refused without stamina by the game (their interactions
     * start with a StatsCondition on stamina).
     */
    private void keepActionsBlocked(CommandBuffer<EntityStore> cb, Ref<EntityStore> ref, Runtime rt,
                                    WaterOfArrakisConfig cfg, float stamina, float dt) {
        rt.blockTimer -= dt;
        if (!cfg.isBlockActionsAtZeroStamina() || stamina > 0 || rt.blockTimer > 0) {
            return;
        }
        EffectControllerComponent controller = cb.getComponent(ref, EffectControllerComponent.getComponentType());
        if (controller == null) {
            return;
        }
        for (String id : new String[] {NO_JUMP_EFFECT, NO_SPRINT_EFFECT}) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            if (effect != null) {
                controller.addEffect(ref, effect, 0.4f, OverlapBehavior.OVERWRITE, cb);
            }
        }
        rt.blockTimer = 0.15;
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
