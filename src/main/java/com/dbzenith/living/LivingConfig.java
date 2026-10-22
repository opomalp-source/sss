package com.dbzenith.living;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.nemesis.NemesisConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/** Reads {@code [living]} in dbzenith-server.toml, with the defaults when the config isn't loaded (tests, clients). */
public final class LivingConfig {
    private LivingConfig() {}

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

    /** The living world needs the Nemesis system (residents are its characters). */
    public static boolean enabled() { return NemesisConfig.enabled() && b(() -> DBZConfig.SERVER.livingEnabled, true); }
    public static boolean autoTown() { return b(() -> DBZConfig.SERVER.livingAutoTown, true); }
    public static boolean social() { return b(() -> DBZConfig.SERVER.livingSocial, true); }
    public static boolean rumors() { return b(() -> DBZConfig.SERVER.livingRumors, true); }
    public static int activeRange() { return i(() -> DBZConfig.SERVER.livingActiveRange, 64); }
    public static int townPopulation() { return i(() -> DBZConfig.SERVER.livingTownPopulation, 10); }
    public static int maxChats() { return i(() -> DBZConfig.SERVER.livingMaxConversationsPerTick, 3); }
    public static int travelers() { return i(() -> DBZConfig.SERVER.livingTravelers, 2); }
    public static int maxCompanions() { return i(() -> DBZConfig.SERVER.livingMaxCompanions, 2); }
    public static long recoverTicks() { return i(() -> DBZConfig.SERVER.livingRecoverMinutes, 10) * 1200L; }
}
