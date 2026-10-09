package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.math.iterator.BlockIterator;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.Opacity;
import org.joml.Vector3d;

/**
 * One ray from a sample point toward the sun, walked block by block. The result is how much of the sun reaches the
 * point: 1 is open sky, 0 is fully shaded. Nothing here reads the world directly, so it runs against a fake grid in the
 * tests; the world side is {@link WorldShade}.
 *
 * <p>What a block does to a ray (see {@link #classify}):
 * <ul>
 *   <li>{@link Kind#SOLID}: stone, sand, wood, dirt. The point is fully shaded and the walk stops.</li>
 *   <li>{@link Kind#PARTIAL}: solid blocks that let some light through (leaves). Each adds PartialShadeWeight; the walk
 *       stops when the total reaches 1.</li>
 *   <li>{@link Kind#OPEN}: air, grass, flowers, bushes, torches and everything else that is not a solid block, and
 *       transparent blocks. Fluids are not blocks to the engine and never appear here.</li>
 *   <li>{@link Kind#UNLOADED}: the chunk is not loaded. The ray ends and the rest counts as open sky.</li>
 * </ul>
 *
 * <p>One instance holds scratch state and is not thread safe: use one per player (or per thread).
 */
final class SunShade {

    enum Kind {
        OPEN, PARTIAL, SOLID, UNLOADED
    }

    /** What the ray sees. The real one reads the world; the tests use an array. */
    interface BlockOpacityLookup {
        Kind opacityAt(int x, int y, int z);

        /** Highest y worth walking to; a ray that climbs above it has left the world. */
        int maxY();
    }

    /**
     * Opacity and material to what a ray does, from what the 0.6.8 blocks report (see EXPERIMENT_LOG.md):
     * <ul>
     *   <li>Opacity Solid: stone, sand, dirt, wood, ice. Full shade.</li>
     *   <li>Opacity Cutout: leaves. They report Material Empty (you walk through them), so the material cannot be
     *       used to decide; partial shade.</li>
     *   <li>Opacity Semitransparent: partial shade for solid blocks; open for Material Empty ones (low bushes).</li>
     *   <li>Opacity Transparent: grass, flowers, bushes, torches, cactus, windows. Open.</li>
     * </ul>
     */
    static Kind classify(BlockMaterial material, Opacity opacity) {
        if (opacity == Opacity.Solid) {
            return Kind.SOLID;
        }
        if (opacity == Opacity.Cutout) {
            return Kind.PARTIAL;
        }
        if (opacity == Opacity.Semitransparent && material == BlockMaterial.Solid) {
            return Kind.PARTIAL;
        }
        return Kind.OPEN;
    }

    // ------------------------------------------------------------------ the last walk, for /sunprobe

    /** The first block that was not open on the last walk, or {@link Kind#OPEN} if there was none. */
    Kind firstKind = Kind.OPEN;
    int firstX;
    int firstY;
    int firstZ;
    /** Distance from the sample point to that block, in blocks. */
    double firstDistance;
    /** True when the ray ended at an unloaded chunk. */
    boolean leftLoadedArea;

    // ------------------------------------------------------------------ walk state
    private BlockOpacityLookup lookup;
    private double partialWeight;
    private double blocking;
    private int startX;
    private int startY;
    private int startZ;
    private double originX;
    private double originY;
    private double originZ;

    private final BlockIterator.BlockIteratorProcedure walker = this::visit;

    /**
     * How much sun reaches the point: 1 - blocking, clamped to 0..1.
     *
     * @param dx,dy,dz direction TOWARD the sun, normalised
     * @param length   blocks to follow the ray
     */
    double sample(BlockOpacityLookup lookup, double ox, double oy, double oz, double dx, double dy, double dz,
                  double length, double partialWeight) {
        this.lookup = lookup;
        this.partialWeight = partialWeight;
        this.blocking = 0;
        this.firstKind = Kind.OPEN;
        this.leftLoadedArea = false;
        this.originX = ox;
        this.originY = oy;
        this.originZ = oz;
        this.startX = (int) Math.floor(ox);
        this.startY = (int) Math.floor(oy);
        this.startZ = (int) Math.floor(oz);
        BlockIterator.iterate(ox, oy, oz, dx, dy, dz, length, walker);
        return Math.max(0.0, Math.min(1.0, 1.0 - blocking));
    }

    /** Returns true to keep walking, false to stop. */
    private boolean visit(int x, int y, int z, double px, double py, double pz, double qx, double qy, double qz) {
        if (x == startX && y == startY && z == startZ) {
            return true; // the block the sample point is in does not shade itself
        }
        if (y > lookup.maxY()) {
            return false; // above the world
        }
        Kind kind = lookup.opacityAt(x, y, z);
        switch (kind) {
            case UNLOADED:
                leftLoadedArea = true;
                return false;
            case SOLID:
                noteFirst(kind, x, y, z);
                blocking = 1.0;
                return false;
            case PARTIAL:
                noteFirst(kind, x, y, z);
                blocking += partialWeight;
                return blocking < 1.0;
            default:
                return true;
        }
    }

    private void noteFirst(Kind kind, int x, int y, int z) {
        if (firstKind == Kind.OPEN) {
            firstKind = kind;
            firstX = x;
            firstY = y;
            firstZ = z;
            double ddx = x + 0.5 - originX;
            double ddy = y + 0.5 - originY;
            double ddz = z + 0.5 - originZ;
            firstDistance = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        }
    }

    /**
     * The direction toward the sun from the world's sun direction. The server's value is the direction the light
     * travels (pointing down in the day, see EXPERIMENT_LOG.md), so it is flipped when its y is negative, and
     * normalised. A ray must point up at noon whichever way the engine's vector is defined.
     */
    static void towardSun(Vector3d raw, Vector3d out) {
        out.set(raw);
        if (out.lengthSquared() < 1e-12) {
            out.set(0, 1, 0);
            return;
        }
        out.normalize();
        if (out.y < 0) {
            out.negate();
        }
    }
}
