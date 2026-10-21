package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.IconicSystems;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The series worlds' own places, at their landing sites (original block designs after the series): a Saiyan city of
 * round domed buildings round the royal palace on Planet Vegeta; a Frieza Force base with landing pads, a control
 * console and two Medical Machines on Planet Frieza No. 79; Yardratian spires on Yardrat; the Sugarians' glass-domed
 * colony with high-rises and a landing pad on Cereal. Offsets from the plaza centre ({@code dy} 0 is the plaza);
 * null leaves the plaza's air. Pure functions of the offset: chunk order never matters.
 */
public final class IconicLandmarks {
    private IconicLandmarks() {}

    /** The plaza's reach round the site (blocks). */
    public static final int RADIUS = 26;

    public static boolean has(GalaxyCatalog.Id system) {
        return IconicSystems.isNew(system);
    }

    /** The plaza's paving under a world's place. */
    public static BlockState paving(GalaxyCatalog.Id system, int dx, int dz) {
        boolean ring = Math.abs(Math.sqrt(dx * dx + dz * dz) - 12) < .6;
        if (system.equals(IconicSystems.VEGETA)) return (ring ? Blocks.CUT_RED_SANDSTONE : Blocks.SMOOTH_RED_SANDSTONE).defaultBlockState();
        if (system.equals(IconicSystems.FRIEZA)) return (ring ? Blocks.PURPUR_BLOCK : Blocks.SMOOTH_STONE).defaultBlockState();
        if (system.equals(IconicSystems.YARDRAT)) return (ring ? Blocks.PRISMARINE_BRICKS : Blocks.CALCITE).defaultBlockState();
        return (ring ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState();
    }

    public static BlockState piece(GalaxyCatalog.Id system, int dx, int dy, int dz) {
        if (system.equals(IconicSystems.VEGETA)) return vegeta(dx, dy, dz);
        if (system.equals(IconicSystems.FRIEZA)) return frieza(dx, dy, dz);
        if (system.equals(IconicSystems.YARDRAT)) return yardrat(dx, dy, dz);
        return cereal(dx, dy, dz);
    }

    // ---- Planet Vegeta: round Saiyan buildings, the palace in the middle, a pad for attack pods ----

    private static BlockState vegeta(int dx, int dy, int dz) {
        BlockState b;
        if ((b = saiyan(dx, dy, dz, 0, 0, 8, 6)) != null) return b;
        if ((b = saiyan(dx, dy, dz, -15, -8, 5, 4)) != null) return b;
        if ((b = saiyan(dx, dy, dz, 14, -12, 5, 4)) != null) return b;
        if ((b = saiyan(dx, dy, dz, -12, 13, 4, 3)) != null) return b;
        if ((b = saiyan(dx, dy, dz, 15, 11, 4, 3)) != null) return b;
        int px = dx + 2, pz = dz - 18;                                          // the pod pad
        double pad = Math.sqrt(px * px + pz * pz);
        if (dy == 0 && pad <= 5 && pad > 4) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        double pod = Math.sqrt(px * px + (dy - 1.5) * (dy - 1.5) * 1.2 + pz * pz);
        if (pod <= 1.7 && dy >= 0) return (px >= 1 && dy == 2 ? Blocks.RED_STAINED_GLASS : Blocks.WHITE_CONCRETE).defaultBlockState();
        return null;
    }

    /** A round Saiyan building: a walled drum of {@code wall} blocks under a dome, round windows, a door facing the plaza's middle. */
    private static BlockState saiyan(int dx, int dy, int dz, int cx, int cz, int r, int wall) {
        int x = dx - cx, z = dz - cz;
        double d = Math.sqrt(x * x + z * z);
        if (d > r + .5 || dy < 0 || dy > wall + r) return null;
        if (dy < wall) {
            if (d < r - .5) return Blocks.AIR.defaultBlockState();
            double toward = (x * -cx + z * -cz) / Math.max(1, d * Math.max(1, Math.sqrt(cx * cx + cz * cz)));
            if ((cx != 0 || cz != 0) ? toward > .9 && dy < 3 : x == 0 && z > 0 && dy < 3) return Blocks.AIR.defaultBlockState();   // the door
            if (dy == 2 && Math.floorMod((int) Math.round(Math.atan2(z, x) * 4), 3) == 0) return Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            return (dy == wall - 1 ? Blocks.TERRACOTTA : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
        }
        double shell = Math.sqrt(x * x + z * z + (dy - wall) * (dy - wall) * 1.4);
        if (shell <= r + .5 && shell > r - .7) return (dy == wall + r - 1 && d < 1.5 ? Blocks.ORANGE_TERRACOTTA : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
        if (shell <= r - .7) return Blocks.AIR.defaultBlockState();
        return null;
    }

    // ---- Planet Frieza No. 79: the base, its Medical Machines, the control console, landing pads ----

    private static BlockState frieza(int dx, int dy, int dz) {
        int x = dx, z = dz + 4;
        double d = Math.sqrt(x * x + z * z);
        if (d <= 10.5 && dy >= 0 && dy <= 13) {                                 // the base: white walls under a purple dome
            if (dy < 6) {
                if (d < 9.5) {
                    for (int side : new int[]{-4, 4}) {                         // two Medical Machines: glass capsules of treatment liquid
                        int mx = x - side, mz = z + 2;
                        if (mx * mx + mz * mz <= 1) {
                            if (dy == 0) return Blocks.SEA_LANTERN.defaultBlockState();
                            if (dy <= 2) return Blocks.WATER.defaultBlockState();
                            if (dy == 3) return Blocks.GLASS.defaultBlockState();
                            if (dy == 4) return Blocks.IRON_BLOCK.defaultBlockState();
                        }
                        if (mx * mx + mz * mz <= 2.6 && dy >= 1 && dy <= 3) return Blocks.GLASS.defaultBlockState();
                    }
                    if (z == 7 && Math.abs(x) <= 3 && dy == 0) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();   // the control console
                    if (z == 7 && Math.abs(x) <= 3 && dy == 1) return (x % 2 == 0 ? Blocks.REDSTONE_LAMP : Blocks.LIGHT_BLUE_STAINED_GLASS).defaultBlockState();
                    return Blocks.AIR.defaultBlockState();
                }
                if (z < -8 && Math.abs(x) <= 1 && dy < 3) return Blocks.AIR.defaultBlockState();   // the door, towards the pads
                if (dy == 3 && Math.floorMod((int) Math.round(Math.atan2(z, x) * 5), 2) == 0) return Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
                return (dy == 5 ? Blocks.PURPUR_BLOCK : Blocks.WHITE_CONCRETE).defaultBlockState();
            }
            double shell = Math.sqrt(x * x + z * z + (dy - 6) * (dy - 6) * 2.2);
            if (shell <= 10.5 && shell > 9.6) return (dy > 11 ? Blocks.PURPUR_BLOCK : Blocks.PURPLE_STAINED_GLASS).defaultBlockState();
            if (shell <= 9.6) return Blocks.AIR.defaultBlockState();
            return null;
        }
        for (int[] pad : new int[][]{{-13, -17}, {0, -21}, {13, -17}}) {       // landing pads with their yellow rings
            double p = Math.sqrt((dx - pad[0]) * (dx - pad[0]) + (dz - pad[1]) * (dz - pad[1]));
            if (dy == 0 && p <= 4.2) return (p > 3.3 ? Blocks.YELLOW_CONCRETE : Blocks.SMOOTH_STONE).defaultBlockState();
            if (dy == 1 && p <= 4.2 && p > 3.3 && Math.floorMod(dx + dz, 4) == 0) return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return null;
    }

    // ---- Yardrat: slender spires with rounded crowns ----

    private static BlockState yardrat(int dx, int dy, int dz) {
        int[][] spires = {{0, 0, 26}, {-12, -9, 20}, {13, -7, 22}, {-8, 13, 18}, {11, 12, 16}};
        for (int[] s : spires) {
            int x = dx - s[0], z = dz - s[1], h = s[2];
            double d = Math.sqrt(x * x + z * z);
            if (dy >= 0 && dy < h && d <= 2.2) {
                if (d < 1.2) return Blocks.AIR.defaultBlockState();
                if (dy % 5 == 2 && Math.floorMod(x + z + dy, 3) == 0) return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
                return Blocks.CALCITE.defaultBlockState();
            }
            double crown = Math.sqrt(x * x + z * z + (dy - h - 2) * (dy - h - 2) * 1.3);
            if (crown <= 4.2) return (crown < 1.6 ? Blocks.PEARLESCENT_FROGLIGHT : crown > 3.4 ? Blocks.PRISMARINE_BRICKS : Blocks.CALCITE).defaultBlockState();
        }
        double ring = Math.sqrt(dx * dx + dz * dz);                             // a low ring round the elder's spire, for meditation
        if (dy == 0 && ring >= 6 && ring <= 6.8) return Blocks.PRISMARINE_SLAB.defaultBlockState();
        return null;
    }

    // ---- Cereal: the Sugarians' colony under a transparent dome, and a landing pad ----

    private static BlockState cereal(int dx, int dy, int dz) {
        double shell = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.3);
        if (dy >= 0 && shell <= 18.5 && shell > 17.6) return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        if (dy >= 0 && shell <= 17.6) {
            int[][] towers = {{-7, -5, 2, 14}, {6, -6, 2, 11}, {-4, 7, 1, 9}, {7, 6, 1, 12}};
            for (int[] t : towers) {
                if (Math.abs(dx - t[0]) <= t[2] && Math.abs(dz - t[1]) <= t[2] && dy < t[3]) {
                    boolean edge = Math.abs(dx - t[0]) == t[2] || Math.abs(dz - t[1]) == t[2];
                    if (!edge) return Blocks.AIR.defaultBlockState();
                    if (dy % 3 == 1) return Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
                    return Blocks.WHITE_CONCRETE.defaultBlockState();
                }
                if (Math.abs(dx - t[0]) <= t[2] && Math.abs(dz - t[1]) <= t[2] && dy == t[3]) return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
            }
            if (dy == 0 && (dx == 0 || dz == 0) && shell < 17) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();   // streets
            if (dy == 1 && Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0 && shell < 16) return Blocks.SEA_LANTERN.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        }
        double pad = Math.sqrt(dx * dx + (dz + 22) * (dz + 22));
        if (dy == 0 && pad <= 3.5) return (pad > 2.6 ? Blocks.YELLOW_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
        return null;
    }
}
