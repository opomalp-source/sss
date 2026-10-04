package com.dbzenith.config;

import com.dbzenith.DBZenith;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Server config files live in each world, so a new mod version's default values never reach an existing world.
 * When the file's {@code balanceVersion} is older than {@link DBZConfig.Server#BALANCE_VERSION}, the values that
 * the balance pass changed are reset to their new defaults once. Values the admin set by hand are lost only for
 * those keys, and only on that one upgrade.
 */
public final class BalanceMigration {
    private BalanceMigration() {}

    /** Values whose defaults changed in balance revision 2. */
    static List<ForgeConfigSpec.ConfigValue<?>> revision2(DBZConfig.Server c) {
        return List.of(c.enemyHealthPerLevel, c.enemyDamagePerLevel, c.enemyMaxLevel, c.tpPerPunch, c.tpChargeTrainingInterval,
                c.tpPerMeditationSecond, c.tpPerSecondMovingUnderGravity, c.tpCostPerPoint, c.wishPowerTp);
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != DBZConfig.SERVER_SPEC) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        int from = c.balanceVersion.get();
        if (from >= DBZConfig.Server.BALANCE_VERSION) return;
        if (from < 2) revision2(c).forEach(BalanceMigration::reset);
        c.balanceVersion.set(DBZConfig.Server.BALANCE_VERSION);
        DBZConfig.SERVER_SPEC.save();
        DBZenith.LOGGER.info("Dragon Block Zenith: server config upgraded from balance revision {} to {}", from, DBZConfig.Server.BALANCE_VERSION);
    }

    private static <T> void reset(ForgeConfigSpec.ConfigValue<T> value) {
        value.set(value.getDefault());
    }
}
