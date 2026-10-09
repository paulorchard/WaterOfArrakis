package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * How much of the sun reaches a player: a number from 0 (shade or night) to 1 (full sun). One instance per player
 * (it keeps scratch state and the last readings for /waterdebug and /sunprobe).
 *
 * <p><b>Method</b> (UseShadeRays true). There is no shadow data on the server, so shade is computed. For each of three
 * sample points on the body (head, chest, legs) a ray is followed from the point toward the sun
 * ({@link WorldTimeResource#getSunDirection()}) for ShadeRayLength blocks by {@link SunShade}. The sun fraction is the
 * average of the three results times the sunlight factor (0 night, 1 midday), and 0 while the factor is below
 * MinSunlightFactor. Dawn and dusk therefore heat slowly, midday fully, and a half covered body half.
 *
 * <p><b>Old method</b> (UseShadeRays false): the sun is up and the head is above the chunk height map. Kept for
 * comparison. It reports 1 or 0.
 *
 * <p>Weather and cloud cover are not considered: the server exposes no cloud coverage. Other mods dim the sun with
 * the {@link ModifierType#SUN_INTENSITY_MULTIPLIER} modifier.
 */
final class SunProbe {

    /** Eye height above the feet the default sample heights were written for, in blocks. */
    private static final double DEFAULT_EYE = 1.6;

    private final SunShade shade = new SunShade();
    private final WorldShade worldShade = new WorldShade();
    private final Vector3d raw = new Vector3d();
    private final Vector3d toSun = new Vector3d();

    // ------------------------------------------------------------------ last reading, for the debug commands
    /** Sun fraction of each sample point (head, chest, legs ...), 0..1, before the sunlight factor. */
    double[] samples = new double[0];
    SunShade.Kind[] firstKinds = new SunShade.Kind[0];
    int[] firstX = new int[0];
    int[] firstY = new int[0];
    int[] firstZ = new int[0];
    double[] firstDistance = new double[0];
    boolean[] leftLoaded = new boolean[0];
    double[] sampleHeights = new double[0];
    /** The world's sun direction as the server returns it, and the ray direction toward the sun. */
    final Vector3d rawDirection = new Vector3d();
    final Vector3d rayDirection = new Vector3d();
    double sunlightFactor;
    /** The last result of {@link #measure}. */
    double fraction;

    /**
     * Measures the sun on the player now. Must run on the world thread. Returns the sun fraction 0..1 (also kept in
     * {@link #fraction}).
     */
    double measure(World world, Store<EntityStore> store, Ref<EntityStore> ref, WaterOfArrakisConfig cfg) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        sunlightFactor = time == null ? 0 : Math.max(0, Math.min(1, time.getSunlightFactor()));
        if (time == null || sunlightFactor < cfg.getMinSunlightFactor()) {
            samples = fill(samples, 0);
            return fraction = 0;
        }
        if (!cfg.isUseShadeRays()) {
            return fraction = open(world, store, ref, cfg) ? 1.0 : 0.0;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return fraction = 0;
        }
        raw.set(time.getSunDirection());
        rawDirection.set(raw);
        SunShade.towardSun(raw, toSun);
        rayDirection.set(toSun);

        double[] heights = cfg.getSunSampleHeights();
        double scale = eyeScale(store, ref);
        ensureSize(heights.length);
        Vector3d p = transform.getPosition();
        worldShade.of(world).reset();
        double sum = 0;
        for (int i = 0; i < heights.length; i++) {
            double y = p.y + heights[i] * scale;
            sampleHeights[i] = y - p.y;
            double s = shade.sample(worldShade, p.x, y, p.z, toSun.x, toSun.y, toSun.z, cfg.getShadeRayLength(),
                    cfg.getPartialShadeWeight());
            samples[i] = s;
            firstKinds[i] = shade.firstKind;
            firstX[i] = shade.firstX;
            firstY[i] = shade.firstY;
            firstZ[i] = shade.firstZ;
            firstDistance[i] = shade.firstDistance;
            leftLoaded[i] = shade.leftLoadedArea;
            sum += s;
        }
        return fraction = (sum / heights.length) * sunlightFactor;
    }

    /** The player's eye height against the default 1.6, so a crouching or small model samples lower. */
    private static double eyeScale(Store<EntityStore> store, Ref<EntityStore> ref) {
        ModelComponent model = store.getComponent(ref, ModelComponent.getComponentType());
        if (model == null) {
            return 1.0;
        }
        double eye = model.getModel().getEyeHeight(ref, store);
        return eye > 0.2 ? eye / DEFAULT_EYE : 1.0;
    }

    private void ensureSize(int n) {
        if (samples.length != n) {
            samples = new double[n];
            firstKinds = new SunShade.Kind[n];
            firstX = new int[n];
            firstY = new int[n];
            firstZ = new int[n];
            firstDistance = new double[n];
            leftLoaded = new boolean[n];
            sampleHeights = new double[n];
        }
    }

    private static double[] fill(double[] a, double v) {
        java.util.Arrays.fill(a, v);
        return a;
    }

    // ------------------------------------------------------------------ the old check

    /** True when the sun is up (ignores cover). */
    static boolean sunIsUp(Store<EntityStore> store, WaterOfArrakisConfig cfg) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        return time != null && time.getSunlightFactor() >= cfg.getMinSunlightFactor();
    }

    /** True when nothing solid is above the player's head by the height map. A column that is not loaded counts as covered. */
    static boolean open(World world, Store<EntityStore> store, Ref<EntityStore> ref, WaterOfArrakisConfig cfg) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d p = transform.getPosition();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        int top = chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
        int head = (int) Math.floor(p.y + DEFAULT_EYE);
        return head > top + cfg.getSkyClearBlocks();
    }
}
