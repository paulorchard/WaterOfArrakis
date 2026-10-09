package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * /sunprobe, the development view of the sun check.
 *
 * <pre>
 * /sunprobe               for each sample point: the first block that blocks or shades its ray and how far, or open
 * /sunprobe ray           the same, and marks the three rays and the blocking blocks with particles for 5 s
 * /sunprobe time          the raw sun direction, the ray direction and the sunlight factor now (works from the console)
 * /sunprobe blocks [id..] what a block reports: material, opacity, draw type and the class a ray gives it
 *                         (works from the console; with no ids a default list of leaves, plants, rock, ice, windows)
 * </pre>
 */
final class SunProbeCommand {

    private static final String[] DEFAULT_BLOCKS = {
            "Plant_Leaves_Oak", "Plant_Grass_Arid", "Plant_Grass_Lush", "Plant_Flower_Common_Red", "Plant_Bush_Arid",
            "Plant_Cactus_1", "Wood_Oak_Trunk", "Soil_Sand", "Soil_Grass", "Rock_Sandstone_Red", "Rock_Ice",
            "Furniture_Crude_Window", "Furniture_Ancient_Window", "Furniture_Cybercity_Windows_Full",
            "Furniture_Crude_Torch", Litrejon.ITEM_ID
    };

    private SunProbeCommand() {
    }

    static WorldPositional build(Supplier<WaterOfArrakisConfig> config) {
        return WorldPositional.build("sunprobe", "server.commands.sunprobe.desc", (ctx, world, store, args) -> {
            String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            switch (mode) {
                case "time" -> time(ctx, world, store);
                case "blocks" -> blocks(ctx, args);
                default -> player(ctx, world, store, config.get(), mode.equals("ray"));
            }
        }, "mode", "id1", "id2", "id3", "id4", "id5", "id6", "id7", "id8");
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
    }

    private static void time(CommandContext ctx, World world, Store<EntityStore> store) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        Vector3d raw = new Vector3d(time.getSunDirection());
        Vector3d toSun = new Vector3d();
        SunShade.towardSun(raw, toSun);
        say(ctx, String.format(Locale.ROOT, "%s clock %.2f h, day progress %.3f, sunlight factor %.3f",
                world.getName(), GameClock.hour(store), time.getDayProgress(), time.getSunlightFactor()));
        say(ctx, String.format(Locale.ROOT, "getSunDirection (raw): %.3f %.3f %.3f  length %.3f",
                raw.x, raw.y, raw.z, raw.length()));
        say(ctx, String.format(Locale.ROOT, "ray toward the sun:    %.3f %.3f %.3f  elevation %.1f degrees",
                toSun.x, toSun.y, toSun.z, Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, toSun.y))))));
    }

    private static void blocks(CommandContext ctx, String[] args) {
        int count = Math.max(0, args.length - 1);
        String[] ids = count == 0 ? DEFAULT_BLOCKS : java.util.Arrays.copyOfRange(args, 1, args.length);
        for (String id : ids) {
            int index = BlockType.getAssetMap().getIndex(id);
            say(ctx, index == Integer.MIN_VALUE ? id + ": no such block" : id + ": " + WorldShade.describe(index));
        }
    }

    private static void player(CommandContext ctx, World world, Store<EntityStore> store, WaterOfArrakisConfig cfg,
                               boolean mark) {
        if (!ctx.isPlayer()) {
            say(ctx, "Run it as a player, or use: sunprobe time | sunprobe blocks [ids]");
            return;
        }
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        PlayerRef player = ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        SunProbe probe = new SunProbe();
        double fraction = probe.measure(world, store, ref, cfg);
        say(ctx, String.format(Locale.ROOT, "sun fraction %.3f (sunlight factor %.3f, UseShadeRays %s)", fraction,
                probe.sunlightFactor, cfg.isUseShadeRays()));
        if (!cfg.isUseShadeRays() || probe.samples.length == 0 || probe.sunlightFactor < cfg.getMinSunlightFactor()) {
            say(ctx, "No rays: " + (cfg.isUseShadeRays() ? "the sun is down (below MinSunlightFactor)" : "UseShadeRays is false"));
            return;
        }
        say(ctx, String.format(Locale.ROOT, "toward the sun %.3f %.3f %.3f (raw %.3f %.3f %.3f)", probe.rayDirection.x,
                probe.rayDirection.y, probe.rayDirection.z, probe.rawDirection.x, probe.rawDirection.y,
                probe.rawDirection.z));
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = transform.getPosition();
        String[] names = {"head", "chest", "legs"};
        for (int i = 0; i < probe.samples.length; i++) {
            String name = i < names.length ? names[i] : "point " + i;
            String what;
            if (probe.firstKinds[i] == SunShade.Kind.OPEN) {
                what = probe.leftLoaded[i] ? "open (ray left the loaded area)" : "open";
            } else {
                what = String.format(Locale.ROOT, "%s at %d %d %d, %.1f blocks: %s", probe.firstKinds[i],
                        probe.firstX[i], probe.firstY[i], probe.firstZ[i], probe.firstDistance[i],
                        blockName(world, probe.firstX[i], probe.firstY[i], probe.firstZ[i]));
            }
            say(ctx, String.format(Locale.ROOT, "%s (+%.2f): %.2f  %s", name, probe.sampleHeights[i],
                    probe.samples[i], what));
        }
        say(ctx, "stored sky light at the head, for comparison only: "
                + skyLight(world, (int) Math.floor(p.x), (int) Math.floor(p.y + 1.6), (int) Math.floor(p.z)));
        if (mark) {
            markRays(world, store, probe, p, cfg);
            say(ctx, "Marking the rays with particles for 5 s: dust = head ray, sand = chest ray, hard dust = legs ray, dirt = blocking block.");
        }
    }

    private static String blockName(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return "(not loaded)";
        }
        BlockType type = chunk.getBlockType(x, y, z);
        return type == null ? "air" : WorldShade.describe(chunk.getBlock(x, y, z));
    }

    private static String skyLight(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null || y < 0 || y >= ChunkUtil.HEIGHT) {
            return "n/a";
        }
        BlockSection section = chunk.getBlockChunk().getSectionAtBlockY(y);
        return section != null && section.hasGlobalLight() ? String.valueOf(section.getGlobalLight().getSkyLight(x, y, z))
                : "not lit yet";
    }

    private static final String[] RAY_PARTICLES = {"Block_Break_Dust", "Block_Break_Sand", "Block_Land_Hard_Dust"};

    /** Puffs along each ray every 1.5 blocks, repeated every second for 5 seconds so the rays can be seen. */
    private static void markRays(World world, Store<EntityStore> store, SunProbe probe, Vector3d feet,
                                 WaterOfArrakisConfig cfg) {
        final double length = cfg.getShadeRayLength();
        final Vector3d dir = new Vector3d(probe.rayDirection);
        final double[] heights = probe.sampleHeights.clone();
        final Vector3d origin = new Vector3d(feet);
        final int[][] hits = new int[heights.length][];
        for (int i = 0; i < heights.length; i++) {
            hits[i] = probe.firstKinds[i] == SunShade.Kind.OPEN ? null
                    : new int[] {probe.firstX[i], probe.firstY[i], probe.firstZ[i]};
        }
        for (int burst = 0; burst < 5; burst++) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
                Store<EntityStore> s = world.getEntityStore().getStore();
                for (int i = 0; i < heights.length; i++) {
                    String particle = RAY_PARTICLES[Math.min(i, RAY_PARTICLES.length - 1)];
                    for (double d = 1.5; d <= length; d += 1.5) {
                        ParticleUtil.spawnParticleEffect(particle, new Vector3d(origin.x + dir.x * d,
                                origin.y + heights[i] + dir.y * d, origin.z + dir.z * d), s);
                    }
                    if (hits[i] != null) {
                        ParticleUtil.spawnParticleEffect("Block_Break_Dirt",
                                new Vector3d(hits[i][0] + 0.5, hits[i][1] + 0.5, hits[i][2] + 0.5), s);
                    }
                }
            }), burst, TimeUnit.SECONDS);
        }
    }
}
