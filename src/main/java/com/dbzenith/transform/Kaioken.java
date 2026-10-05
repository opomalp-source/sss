package com.dbzenith.transform;

import com.dbzenith.combat.BodyHealth;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.stats.Attribute;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Kaioken: multiply your power by straining your body. Stages go up one key press at a time, as far as the
 * learned level allows (x2, x4, x10, x20). Every stage burns body each second, more when your Willpower lags behind
 * your Spirit; it works in base form and in calm god forms (Kaioken on Blue). Below 10% body it gives out.
 */
public final class Kaioken {
    public static final String ID = "kaioken";
    private static final int[] MAX_STAGE = {0, 2, 4, 10, 20};
    public static final int RED = 0xFF2A1E;

    private Kaioken() {}

    public static int maxStage(PlayerData d) {
        return MAX_STAGE[Math.min(MAX_STAGE.length - 1, d.getSkillLevel(ID))];
    }

    /** Power multiplier at a stage: +10% a stage to x2 at 10, then +5% a stage to x2.5 at 20. */
    public static double multiplier(int stage) {
        if (stage <= 0) return 1.0;
        return stage <= 10 ? 1.0 + 0.1 * stage : 2.0 + 0.05 * (stage - 10);
    }

    public static boolean formAllows(Form f) {
        return f.isBase() || f.calmAura();
    }

    /** Body burned per second, as a fraction of max: 0.4% a stage, times the strain. */
    public static double drainPerSecond(PlayerData d, int stage) {
        int wil = Math.max(1, d.getAttribute(Attribute.WILLPOWER)), spi = d.getAttribute(Attribute.SPIRIT);
        double strain = 1.0 + Math.min(2.0, Math.max(0, spi - wil) / (double) wil);
        return 0.004 * stage * strain;
    }

    /** The Kaioken key: one stage up. */
    public static boolean raise(ServerPlayer player, PlayerData d) {
        int max = maxStage(d);
        if (max == 0) {
            player.displayClientMessage(Component.translatable("message.dbzenith.kaioken_unknown"), true);
            return false;
        }
        if (!formAllows(Forms.byId(d.getFormId()))) {
            player.displayClientMessage(Component.translatable("message.dbzenith.kaioken_form"), true);
            return false;
        }
        if (d.getKaiokenStage() >= max) {
            player.displayClientMessage(Component.translatable("message.dbzenith.kaioken_max", max), true);
            return false;
        }
        if (d.getBody() < d.getDerived().maxBody() * 0.15) {
            player.displayClientMessage(Component.translatable("message.dbzenith.kaioken_weak"), true);
            return false;
        }
        int stage = d.getKaiokenStage() + 1;
        d.setKaiokenStage(stage);
        player.displayClientMessage(Component.translatable("message.dbzenith.kaioken", stage), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                0.8f, 0.6f + stage * 0.03f);
        ImpactPacket.at(player.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.KI_HIT, 0.5f + stage * 0.04f, RED, player.getId())
                .send(player.serverLevel());
        return true;
    }

    public static void stop(ServerPlayer player, PlayerData d, boolean quiet) {
        if (d.getKaiokenStage() == 0) return;
        d.setKaiokenStage(0);
        if (!quiet) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.7f, 0.8f);
    }

    /** Every tick from KiTicker: the burn, and the Kaioken giving out. */
    public static void tick(ServerPlayer player, PlayerData d, long now) {
        int stage = d.getKaiokenStage();
        if (stage == 0) return;
        if (!formAllows(Forms.byId(d.getFormId())) || d.getSkillLevel(ID) == 0) {
            stop(player, d, false);
            return;
        }
        if (player.getAbilities().instabuild) return;
        double max = d.getDerived().maxBody();
        d.setBody(d.getBody() - max * drainPerSecond(d, stage) / 20.0);
        if (now % 20 == 0) BodyHealth.mirror(player, d);
        if (d.getBody() < max * 0.10) {
            stop(player, d, false);
            player.displayClientMessage(Component.translatable("message.dbzenith.kaioken_gave_out"), true);
        }
    }
}
