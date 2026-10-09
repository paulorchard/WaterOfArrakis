package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The development commands. No permission group is set, so only operators can run them.
 *
 * <pre>
 * /water set|add &lt;n&gt; [player]
 * /exposure set|add &lt;n&gt; [player]
 * /waterdebug [player]
 * </pre>
 */
final class WaterCommands {

    private static final String LANG = "server.commands.";

    private WaterCommands() {
    }

    /** /water and /exposure share everything but which value they touch. */
    static final class ValueCommand extends AbstractCommandCollection {

        interface Op {
            double apply(WaterService service, PlayerRef player, double amount);
        }

        ValueCommand(String name, WaterService service, Op set, Op add) {
            super(name, LANG + name + ".desc");
            addSubCommand(WorldPositional.build("set", LANG + name + ".set.desc",
                    (ctx, world, store, args) -> run(ctx, store, service, set, name, args), "amount", "player"));
            addSubCommand(WorldPositional.build("add", LANG + name + ".add.desc",
                    (ctx, world, store, args) -> run(ctx, store, service, add, name, args), "amount", "player"));
        }

        private static void run(CommandContext ctx, Store<EntityStore> store, WaterService service, Op op,
                                String name, String[] args) {
            double amount;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                say(ctx, "Not a number: " + args[0]);
                return;
            }
            PlayerRef target = resolve(ctx, store, args, 1);
            if (target == null) {
                return;
            }
            double now = op.apply(service, target, amount);
            say(ctx, String.format(Locale.ROOT, "%s %s is now %.2f", target.getUsername(), name, now));
        }
    }

    static WorldPositional debug(WaterService service, WaterSystem system, Supplier<WaterOfArrakisConfig> config) {
        return WorldPositional.build("waterdebug", LANG + "waterdebug.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target == null) {
                return;
            }
            WaterOfArrakisConfig cfg = config.get();
            double water = service.getWater(target);
            double exposure = service.getExposure(target);
            int tier = cfg.waterTier(water);
            Ref<EntityStore> ref = target.getReference();
            float stamina = -1;
            EntityStatMap stats = ref == null ? null : store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null && stats.get(DefaultEntityStatTypes.getStamina()) != null) {
                stamina = stats.get(DefaultEntityStatTypes.getStamina()).get();
            }
            var mods = service.getModifiers(target.getUuid());
            say(ctx, String.format(Locale.ROOT, "%s: water %.2f, exposure %.2f", target.getUsername(), water, exposure));
            say(ctx, String.format(Locale.ROOT,
                    "tier %d of %d (stamina cost x%.2f, regen x%.2f), exposure step %d",
                    tier, cfg.waterTierCount() - 1, cfg.actionStaminaCost(tier), cfg.staminaRegen(tier),
                    service.getExposureStep(target)));
            say(ctx, String.format(Locale.ROOT,
                    "drain multiplier from exposure x%.2f; modifiers: gain x%.2f, decay x%.2f, drain x%.2f, "
                            + "action drain x%.2f, offset %+.2f/s",
                    cfg.exposureDrainMultiplier(exposure), mods.get(ModifierType.EXPOSURE_GAIN_MULTIPLIER),
                    mods.get(ModifierType.EXPOSURE_DECAY_MULTIPLIER), mods.get(ModifierType.WATER_DRAIN_MULTIPLIER),
                    mods.get(ModifierType.WATER_ACTION_DRAIN_MULTIPLIER), mods.get(ModifierType.EXPOSURE_OFFSET)));
            double rate = system.exposureRate(target.getUuid());
            double shadeSeconds = system.secondsOutOfSun(target.getUuid());
            SunProbe probe = system.probeOf(target.getUuid());
            say(ctx, String.format(Locale.ROOT,
                    "sun fraction %.2f (threshold %.2f, UseShadeRays %s): %s; stamina %.2f",
                    service.getSunFraction(target), cfg.getShadeThreshold(), cfg.isUseShadeRays(),
                    shadeSeconds <= 0 ? "in the sun" : String.format(Locale.ROOT, "in shade for %.1f s (grace %.1f s)",
                            shadeSeconds, cfg.getExposureGraceSeconds()), stamina));
            say(ctx, String.format(Locale.ROOT, "exposure rate now %+.3f per second (%s); sun-intensity modifier x%.2f",
                    rate, rate < 0 ? "cooling" : "heating",
                    mods.get(ModifierType.SUN_INTENSITY_MULTIPLIER)));
            if (probe != null) {
                say(ctx, String.format(Locale.ROOT,
                        "sunlight factor %.2f; sun direction raw %.2f %.2f %.2f, toward the sun %.2f %.2f %.2f",
                        probe.sunlightFactor, probe.rawDirection.x, probe.rawDirection.y, probe.rawDirection.z,
                        probe.rayDirection.x, probe.rayDirection.y, probe.rayDirection.z));
                StringBuilder samples = new StringBuilder("sample points (head, chest, legs):");
                for (double s : probe.samples) {
                    samples.append(String.format(Locale.ROOT, " %.2f", s));
                }
                say(ctx, samples.toString());
            }
        }, "player");
    }

    /**
     * /waterplants [radiusInChunks]: places primroses and burrowbushes in the loaded chunks around you, as if they
     * had just been generated (for testing in a world that already exists; the same seed gives the same plants, and a
     * second run adds nothing). Also prints the slope and surface block under you.
     */
    static WorldPositional plants(Supplier<WaterOfArrakisConfig> config) {
        return WorldPositional.build("waterplants", LANG + "waterplants.desc", (ctx, world, store, args) -> {
            PlayerRef player = resolve(ctx, store, new String[0], 0);
            if (player == null) {
                return;
            }
            Ref<EntityStore> ref = player.getReference();
            var transform = ref == null ? null : store.getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            int radius = 2;
            if (args.length > 0) {
                try {
                    radius = Math.max(0, Math.min(8, Integer.parseInt(args[0])));
                } catch (NumberFormatException e) {
                    say(ctx, "Not a number: " + args[0]);
                    return;
                }
            }
            int cx = (int) Math.floor(transform.getPosition().x) >> 5;
            int cz = (int) Math.floor(transform.getPosition().z) >> 5;
            int chunks = 0;
            int placed = 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    var chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(cx + dx, cz + dz));
                    if (chunk != null) {
                        chunks++;
                        placed += PlantGenerator.populate(world, chunk, config.get());
                    }
                }
            }
            say(ctx, String.format(Locale.ROOT, "Looked at %d loaded chunks, placed %d plants.", chunks, placed));
        }, "radius");
    }

    private static World world(Store<EntityStore> store) {
        return store.getExternalData().getWorld();
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
    }

    /** The named player, or the caller if no name was typed. Must be in the world the command runs in. */
    private static PlayerRef resolve(CommandContext ctx, Store<EntityStore> store, String[] args, int index) {
        if (index < args.length) {
            PlayerRef found = Universe.get().getPlayer(args[index], NameMatching.DEFAULT);
            if (found == null) {
                say(ctx, "No such player: " + args[index]);
                return null;
            }
            Ref<EntityStore> ref = found.getReference();
            if (ref == null || ref.getStore() != store) {
                say(ctx, found.getUsername() + " is in another world; run the command from there.");
                return null;
            }
            return found;
        }
        if (!ctx.isPlayer()) {
            say(ctx, "Name a player; the console has no water.");
            return null;
        }
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        return ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
    }
}
