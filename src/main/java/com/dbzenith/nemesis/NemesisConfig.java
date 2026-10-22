package com.dbzenith.nemesis;

import com.dbzenith.config.DBZConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/** Reads {@code [nemesis]} in dbzenith-server.toml, with the defaults when the config isn't loaded (tests, clients). */
public final class NemesisConfig {
    private NemesisConfig() {}

    private static boolean b(java.util.function.Supplier<ForgeConfigSpec.BooleanValue> v, boolean def) {
        try {
            return v.get().get();
        } catch (IllegalStateException | NullPointerException e) {
            return def;
        }
    }

    private static int i(java.util.function.Supplier<ForgeConfigSpec.IntValue> v, int def) {
        try {
            return v.get().get();
        } catch (IllegalStateException | NullPointerException e) {
            return def;
        }
    }

    private static double d(java.util.function.Supplier<ForgeConfigSpec.DoubleValue> v, double def) {
        try {
            return v.get().get();
        } catch (IllegalStateException | NullPointerException e) {
            return def;
        }
    }

    public static boolean enabled() { return b(() -> DBZConfig.SERVER.nemesisEnabled, true); }
    public static boolean promote() { return b(() -> DBZConfig.SERVER.nemesisPromote, true); }
    public static boolean worldSim() { return b(() -> DBZConfig.SERVER.nemesisWorldSim, true); }
    public static boolean ambushes() { return b(() -> DBZConfig.SERVER.nemesisAmbushes, true); }
    public static boolean alliances() { return b(() -> DBZConfig.SERVER.nemesisAlliances, true); }
    public static boolean dialogue() { return b(() -> DBZConfig.SERVER.nemesisDialogue, true); }
    public static int maxCharacters() { return i(() -> DBZConfig.SERVER.nemesisMaxCharacters, 48); }
    public static int maxPresent() { return i(() -> DBZConfig.SERVER.nemesisMaxPresent, 4); }
    public static long cooldownTicks() { return i(() -> DBZConfig.SERVER.nemesisCooldownMinutes, 15) * 1200L; }
    public static double encounterChance() { return d(() -> DBZConfig.SERVER.nemesisEncounterChance, 0.3); }
    public static long returnMinTicks() { return (long) (d(() -> DBZConfig.SERVER.nemesisReturnMinDays, 0.5) * 24000); }
    public static long returnMaxTicks() { return (long) (d(() -> DBZConfig.SERVER.nemesisReturnMaxDays, 2.0) * 24000); }
    public static double survivalChance() { return d(() -> DBZConfig.SERVER.nemesisSurvivalChance, 0.45); }
    public static double growthPerDay() { return d(() -> DBZConfig.SERVER.nemesisGrowthPerDay, 1.5); }
}
