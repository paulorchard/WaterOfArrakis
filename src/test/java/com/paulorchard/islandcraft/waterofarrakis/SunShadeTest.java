package com.paulorchard.islandcraft.waterofarrakis;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.Opacity;
import com.paulorchard.islandcraft.waterofarrakis.SunShade.BlockOpacityLookup;
import com.paulorchard.islandcraft.waterofarrakis.SunShade.Kind;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ray walk against a fake block grid. */
class SunShadeTest {

    /** A 64 x 64 x 64 grid of open air. Cells outside x/z 0..63, or with x at or above {@code loadedUntilX}, are unloaded. */
    private static final class Grid implements BlockOpacityLookup {
        final Kind[][][] cells = new Kind[64][64][64];
        int loadedUntilX = 64;

        Grid() {
            for (Kind[][] plane : cells) {
                for (Kind[] row : plane) {
                    java.util.Arrays.fill(row, Kind.OPEN);
                }
            }
        }

        void set(int x, int y, int z, Kind kind) {
            cells[x][y][z] = kind;
        }

        @Override
        public Kind opacityAt(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= loadedUntilX || z >= 64 || y < 0 || y >= 64) {
                return Kind.UNLOADED;
            }
            return cells[x][y][z];
        }

        @Override
        public int maxY() {
            return 63;
        }
    }

    private static final double LENGTH = 48;
    private static final double WEIGHT = 0.5;
    private final SunShade shade = new SunShade();

    private double up(Grid g, double x, double y, double z) {
        return shade.sample(g, x, y, z, 0, 1, 0, LENGTH, WEIGHT);
    }

    private double elevated(Grid g, double degrees, double x, double y, double z) {
        double r = Math.toRadians(degrees);
        return shade.sample(g, x, y, z, Math.cos(r), Math.sin(r), 0, LENGTH, WEIGHT);
    }

    @Test
    void openSkyIsFullSun() {
        assertEquals(1.0, up(new Grid(), 10.5, 5.5, 10.5), 1e-9);
    }

    @Test
    void solidBlockOverheadIsFullShade() {
        Grid g = new Grid();
        g.set(10, 8, 10, Kind.SOLID);
        assertEquals(0.0, up(g, 10.5, 5.5, 10.5), 1e-9);
        assertEquals(Kind.SOLID, shade.firstKind);
        assertEquals(8, shade.firstY);
    }

    @Test
    void theBlockThePointIsInDoesNotShadeItself() {
        Grid g = new Grid();
        g.set(10, 5, 10, Kind.SOLID);
        assertEquals(1.0, up(g, 10.5, 5.5, 10.5), 1e-9);
    }

    @Test
    void aWallOnTheSunSideShadesOnlyWhenTheSunIsLowEnough() {
        Grid g = new Grid();
        for (int y = 0; y <= 8; y++) {
            for (int z = 0; z < 64; z++) {
                g.set(15, y, z, Kind.SOLID);
            }
        }
        // Nothing is overhead. At 30 degrees the ray reaches x = 15 at about y = 4: inside the wall.
        assertEquals(0.0, elevated(g, 30, 10.5, 1.5, 10.5), 1e-9);
        // At 70 degrees it reaches x = 15 at about y = 14: over the top.
        assertEquals(1.0, elevated(g, 70, 10.5, 1.5, 10.5), 1e-9);
        // The shadow side is behind the wall, the sunny side is in front: a point on the far side (x = 16) with the
        // sun at 30 degrees is open.
        assertEquals(1.0, elevated(g, 30, 16.5, 1.5, 10.5), 1e-9);
    }

    @Test
    void leavesShadePartlyAndTwoShadeFully() {
        Grid g = new Grid();
        g.set(10, 7, 10, Kind.PARTIAL);
        assertEquals(0.5, up(g, 10.5, 5.5, 10.5), 1e-9);
        g.set(10, 8, 10, Kind.PARTIAL);
        assertEquals(0.0, up(g, 10.5, 5.5, 10.5), 1e-9);
    }

    @Test
    void aSolidBlockBehindLeavesStillShadesFully() {
        Grid g = new Grid();
        g.set(10, 7, 10, Kind.PARTIAL);
        g.set(10, 9, 10, Kind.SOLID);
        assertEquals(0.0, up(g, 10.5, 5.5, 10.5), 1e-9);
    }

    @Test
    void grassAndFlowersNeverShade() {
        // What the game reports for grass, flowers and low bushes: Material Empty, Opacity Transparent.
        assertEquals(Kind.OPEN, SunShade.classify(BlockMaterial.Empty, Opacity.Transparent));
        assertEquals(Kind.OPEN, SunShade.classify(BlockMaterial.Empty, Opacity.Semitransparent));
        Grid g = new Grid();
        for (int y = 6; y < 20; y++) {
            g.set(10, y, 10, SunShade.classify(BlockMaterial.Empty, Opacity.Transparent));
        }
        assertEquals(1.0, up(g, 10.5, 5.5, 10.5), 1e-9);
    }

    /** The combinations the 0.6.8 blocks really report (headless /sunprobe blocks). */
    @Test
    void blockClasses() {
        // Rock, sand, dirt, wood, ice: Solid / Solid.
        assertEquals(Kind.SOLID, SunShade.classify(BlockMaterial.Solid, Opacity.Solid));
        // Leaves: Empty / Cutout. They do shade, in part.
        assertEquals(Kind.PARTIAL, SunShade.classify(BlockMaterial.Empty, Opacity.Cutout));
        assertEquals(Kind.PARTIAL, SunShade.classify(BlockMaterial.Solid, Opacity.Cutout));
        assertEquals(Kind.PARTIAL, SunShade.classify(BlockMaterial.Solid, Opacity.Semitransparent));
        // Windows, cactus: Solid / Transparent. Torches: Empty / Transparent.
        assertEquals(Kind.OPEN, SunShade.classify(BlockMaterial.Solid, Opacity.Transparent));
        assertEquals(Kind.OPEN, SunShade.classify(BlockMaterial.Empty, Opacity.Transparent));
    }

    @Test
    void aRayThatLeavesTheLoadedAreaIsOpen() {
        Grid g = new Grid();
        g.loadedUntilX = 20;
        assertEquals(1.0, elevated(g, 10, 10.5, 1.5, 10.5), 1e-9);
        assertTrue(shade.leftLoadedArea);
    }

    @Test
    void aRayThatClimbsAboveTheWorldStops() {
        Grid g = new Grid();
        assertEquals(1.0, up(g, 10.5, 60.5, 10.5), 1e-9);
    }

    @Test
    void theRayPointsUpAtNoonWhicheverWayTheEngineVectorPoints() {
        Vector3d out = new Vector3d();
        // The server's value is the direction the light travels: down at noon.
        SunShade.towardSun(new Vector3d(0.1, -0.95, 0.05), out);
        assertTrue(out.y > 0, "ray must point toward the sun, up at noon");
        assertEquals(1.0, out.length(), 1e-9);
        // If it were already pointing at the sun it is left alone.
        SunShade.towardSun(new Vector3d(0.1, 0.95, 0.05), out);
        assertTrue(out.y > 0);
        // Not normalised input.
        SunShade.towardSun(new Vector3d(0, -3, 0), out);
        assertEquals(1.0, out.y, 1e-9);
    }

    /** Not an assertion of speed, a measurement printed for the log: cost of one 3-ray check on a fake grid. */
    @Test
    void measureTheCostOfACheck() {
        Grid g = new Grid();
        g.set(12, 9, 10, Kind.PARTIAL);
        SunShade s = new SunShade();
        double r = Math.toRadians(40);
        double sum = 0;
        for (int i = 0; i < 200_000; i++) { // warm up
            sum += s.sample(g, 10.5, 1.6 + (i & 1), 10.5, Math.cos(r), Math.sin(r), 0.2, LENGTH, WEIGHT);
        }
        int checks = 300_000;
        long start = System.nanoTime();
        for (int i = 0; i < checks; i++) {
            sum += s.sample(g, 10.5, 1.6, 10.5, Math.cos(r), Math.sin(r), 0.2, LENGTH, WEIGHT);
            sum += s.sample(g, 10.5, 1.0, 10.5, Math.cos(r), Math.sin(r), 0.2, LENGTH, WEIGHT);
            sum += s.sample(g, 10.5, 0.3, 10.5, Math.cos(r), Math.sin(r), 0.2, LENGTH, WEIGHT);
        }
        double microseconds = (System.nanoTime() - start) / 1000.0 / checks;
        System.out.printf("SUNSHADE-COST: %.2f microseconds per 3-ray check on a fake grid (sum %.1f)%n", microseconds, sum);
        assertTrue(microseconds < 1000);
    }
}
