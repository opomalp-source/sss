package com.dbzenith.transform;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;

import java.util.List;

/**
 * Server-side transformation rules: requirements, entering/leaving forms, per-tick drain and mastery.
 */
public final class FormHandler {
    private FormHandler() {}

    /** Why {@code form} can't be entered manually right now, or null if it can. */
    public static Component problem(PlayerData data, Form form) {
        if (form.isBase()) return null;
        if (!form.races().contains(data.getRace())) return Component.translatable("form.dbzenith.problem.race");
        if (form.trigger() != Form.Trigger.MANUAL) return Component.translatable("form.dbzenith.problem.trigger");
        if (form.requiredFlag() != null && !data.hasFlag(form.requiredFlag())) {
            return Component.translatable("form.dbzenith.problem.flag." + form.requiredFlag());
        }
        int level = FormMath.unlockLevel(form);
        if (StatCalculator.level(data) < level) return Component.translatable("form.dbzenith.problem.level", level);
        if (form.parentMasteryRequired() > 0 && data.getMastery(form.parent()) < form.parentMasteryRequired()) {
            return Component.translatable("form.dbzenith.problem.mastery",
                    Component.translatable(Forms.byId(form.parent()).translationKey()), (int) form.parentMasteryRequired());
        }
        return null;
    }

    /**
     * Transform key: go to the selected target form if it is a reachable descendant of the current form,
     * otherwise to the first unlocked child of the current form.
     */
    public static boolean transformUp(ServerPlayer player) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !player.isAlive()) return false;
        if (com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.ki_sealed"), true);
            return false;
        }
        Form current = Forms.byId(data.getFormId());
        Form next = null;
        if (Forms.exists(data.getTargetForm())) {
            Form target = Forms.byId(data.getTargetForm());
            if (target != current && Forms.isOnPath(current.id(), target) && pathUnlocked(data, current, target)) next = target;
        }
        if (next == null) {
            for (Form child : Forms.children(current.id(), data.getRace())) {
                if (problem(data, child) == null) {
                    next = child;
                    break;
                }
            }
        }
        if (next == null) {
            List<Form> children = Forms.children(current.id(), data.getRace());
            Component reason = children.isEmpty() ? Component.translatable("form.dbzenith.problem.none")
                    : problem(data, children.get(0));
            player.displayClientMessage(reason == null ? Component.translatable("form.dbzenith.problem.none") : reason, true);
            return false;
        }
        double cost = data.getDerived().maxKi() * DBZConfig.SERVER.transformKiCostPercent.get() / 100.0;
        if (data.getKi() < cost && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.dbzenith.no_ki_to_transform"), true);
            return false;
        }
        if (!player.getAbilities().instabuild) data.setKi(data.getKi() - cost);
        enter(player, data, next);
        return true;
    }

    /** Every form between {@code from} (exclusive) and {@code to} (inclusive) is unlocked. */
    private static boolean pathUnlocked(PlayerData data, Form from, Form to) {
        for (Form f = to; f != null && f != from; f = f.isBase() ? null : Forms.byId(f.parent())) {
            if (problem(data, f) != null) return false;
        }
        return true;
    }

    /** Enters a form with effects. No requirement checks (callers check). */
    public static void enter(ServerPlayer player, PlayerData data, Form form) {
        data.setFormId(form.id());
        if (!form.isBase()) data.setFlag("has_transformed", true);
        data.recomputeIfStale();
        if (!form.allowsOverdrive()) Overdrive.stop(player, data, false);
        if (!form.allowsFlight() && data.isFlying()) com.dbzenith.ki.FlightHandler.stop(player, data);
        burst(player.serverLevel(), player, form.auraColor(), form.isBase() ? 12 : 40);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                form.isBase() ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, form.isBase() ? 1.2f : 1.5f);
        player.refreshDimensions();
        if (!form.isBase()) {
            player.displayClientMessage(Component.translatable("message.dbzenith.transformed", Component.translatable(form.translationKey())), true);
        }
    }

    /** Shift+transform key: drop one tier (to the parent form). */
    public static void revertOne(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(data -> {
            Form current = Forms.byId(data.getFormId());
            if (current.isBase() || current.trigger() != Form.Trigger.MANUAL) return;
            enter(player, data, Forms.byId(current.parent()));
        });
    }

    public static void revertToBase(ServerPlayer player, PlayerData data) {
        if (data.isTransformed()) enter(player, data, Forms.BASE);
    }

    /** Called every server tick from KiTicker. */
    public static void tick(ServerPlayer player, PlayerData data, long gameTime) {
        Form form = Forms.byId(data.getFormId());
        if (form.isBase()) return;
        double mastery = data.getMastery(form.id());
        double kiDrain = FormMath.masteredDrain(form.kiDrainPercent(), mastery) * data.getDerived().maxKi() / 100.0 / 20.0;
        double staDrain = FormMath.masteredDrain(form.staminaDrainPercent(), mastery) * data.getDerived().maxStamina() / 100.0 / 20.0;
        if (!player.getAbilities().instabuild) {
            data.setKi(data.getKi() - kiDrain);
            data.setStamina(data.getStamina() - staDrain);
        }
        if (gameTime % 20 == 0) {
            double gain = DBZConfig.SERVER.masteryGainPerSecond.get() * data.getDerived().spiritModifier() / (1.0 + 0.5 * form.tier());
            data.setMastery(form.id(), mastery + gain);
        }
        if (form.kiDrainPercent() > 0 && data.getKi() <= 0 && !player.getAbilities().instabuild) {
            revertToBase(player, data);
            player.displayClientMessage(Component.translatable("message.dbzenith.form_exhausted"), true);
        }
    }

    static void burst(ServerLevel level, ServerPlayer player, int color, int count) {
        Vector3f rgb = new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
        level.sendParticles(new DustParticleOptions(rgb, 2.0f), player.getX(), player.getY() + 1, player.getZ(), count, 0.6, 1.0, 0.6, 0.1);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
    }
}
