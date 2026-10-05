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
        if (!form.allows(data.getVariant())) return Component.translatable("form.dbzenith.problem.variant");
        if (form.trigger() != Form.Trigger.MANUAL) return Component.translatable("form.dbzenith.problem.trigger");
        if (form.requiredFlag() != null && !data.hasFlag(form.requiredFlag())) {
            return Component.translatable("form.dbzenith.problem.flag." + form.requiredFlag());
        }
        int godKi = GodKi.required(form);
        if (godKi > 1 && GodKi.level(data) < godKi) return Component.translatable("form.dbzenith.problem.god_ki", godKi);
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
        return transformUp(player, false);
    }

    /** @param instant skip the power-up (tests, scripted scenes) */
    public static boolean transformUp(ServerPlayer player, boolean instant) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !player.isAlive()) return false;
        if (data.isTransforming()) return false;                         // already powering up
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
            for (Form child : Forms.children(current.id(), data.getRace(), data.getVariant())) {
                if (problem(data, child) == null) {
                    next = child;
                    break;
                }
            }
        }
        if (next == null) {
            List<Form> children = Forms.children(current.id(), data.getRace(), data.getVariant());
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
        int time = instant || player.getAbilities().instabuild ? 0 : transformTime(data, next);
        if (time <= 0) {
            enter(player, data, next);
        } else {
            data.startTransforming(next.id(), time);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.POWERUP.get(), SoundSource.PLAYERS, 0.9f, 0.9f);
        }
        return true;
    }

    /**
     * Ticks to power up into a form: instant once it is mastered to {@code instantTransformMastery}, otherwise longer
     * the higher the tier and the less mastered it is.
     */
    public static int transformTime(PlayerData data, Form form) {
        double instant = DBZConfig.SERVER.instantTransformMastery.get();
        double mastery = data.getMastery(form.id());
        if (instant <= 0 || mastery >= instant) return 0;
        double full = DBZConfig.SERVER.transformTimeBase.get() + DBZConfig.SERVER.transformTimePerTier.get() * form.tier();
        return Math.max(10, (int) Math.round(full * (1.0 - 0.6 * mastery / instant)));
    }

    /** One tick of powering up: the aura builds, the ground shakes, and at the end the form takes hold. */
    static void tickTransforming(ServerPlayer player, PlayerData data) {
        Form target = Forms.byId(data.getTransformTarget());
        if (!player.isAlive() || target.isBase() || com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            data.stopTransforming();
            return;
        }
        int t = data.getTransformTicks() + 1;
        int total = data.getTransformTotal();
        data.setTransformTicks(t);
        ServerLevel level = player.serverLevel();
        float progress = t / (float) total;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
        int c = target.auraColor();
        Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        if (t % 2 == 0) {
            level.sendParticles(new DustParticleOptions(rgb, 1.2f + progress), player.getX(), player.getY() + 0.2, player.getZ(),
                    4 + (int) (progress * 8), 0.5 + progress * 0.5, 0.1, 0.5 + progress * 0.5, 0.15);
        }
        if (t % 6 == 0) {                                                     // the ground cracks and lifts
            net.minecraft.world.level.block.state.BlockState below = level.getBlockState(player.blockPosition().below());
            if (!below.isAir()) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), player.getX(), player.getY() + 0.1,
                        player.getZ(), 6 + (int) (progress * 10), 1.2 + progress, 0.05, 1.2 + progress, 0.25);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.POWERUP.get(), SoundSource.PLAYERS,
                    0.25f + progress * 0.35f, 0.5f + progress);
        }
        if (t % 20 == 10 && target.lightning()) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 12, 0.6, 1.0, 0.6, 0.3);
        }
        if (t >= total) {
            data.stopTransforming();
            if (problem(data, target) == null) {
                enter(player, data, target);
                com.dbzenith.network.ImpactPacket.at(player.position().add(0, 1, 0), new net.minecraft.world.phys.Vec3(0, 1, 0),
                        com.dbzenith.network.ImpactPacket.EXPLOSION, 0.6f + target.tier() * 0.15f, c, player.getId()).send(level);
            }
        }
    }

    /** A hard enough hit breaks a power-up: the ki spent is lost. */
    public static void interrupt(ServerPlayer player, PlayerData data) {
        if (!data.isTransforming()) return;
        data.stopTransforming();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.GUARD_BREAK.get(), SoundSource.PLAYERS, 0.8f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dbzenith.transform_interrupted"), true);
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
        if (!Kaioken.formAllows(form)) Kaioken.stop(player, data, true);
        if (!form.allowsFlight() && data.isFlying()) com.dbzenith.ki.FlightHandler.stop(player, data);
        burst(player.serverLevel(), player, form.auraColor(), form.isBase() ? 12 : 40);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                form.isBase() ? com.dbzenith.registry.ModSounds.POWER_DOWN.get() : com.dbzenith.registry.ModSounds.TRANSFORM.get(), SoundSource.PLAYERS, form.isBase() ? 0.8f : 1.3f, 1f);
        player.refreshDimensions();
        com.dbzenith.race.RacialSkillEffects.onFormEntered(player, data, form.isBase());
        if (!form.isBase()) {
            player.displayClientMessage(Component.translatable("message.dbzenith.transformed", Component.translatable(form.translationKey())), true);
        }
    }

    /** Shift+transform key: drop one tier (to the parent form). */
    public static void revertOne(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(data -> {
            if (data.isTransforming()) {
                data.stopTransforming();
                return;
            }
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
        if (data.isTransforming()) tickTransforming(player, data);
        Form form = Forms.byId(data.getFormId());
        if (form.isBase()) return;
        double mastery = data.getMastery(form.id());
        double kiDrain = FormMath.masteredDrain(form.kiDrainPercent(), mastery) * GodKi.drainFactor(data, form) * com.dbzenith.race.RacialSkills.factor(data, com.dbzenith.race.RacialSkill.Stat.FORM_DRAIN) * data.getDerived().maxKi() / 100.0 / 20.0;
        double staDrain = FormMath.masteredDrain(form.staminaDrainPercent(), mastery) * GodKi.drainFactor(data, form) * com.dbzenith.race.RacialSkills.factor(data, com.dbzenith.race.RacialSkill.Stat.FORM_DRAIN) * data.getDerived().maxStamina() / 100.0 / 20.0;
        if (!player.getAbilities().instabuild) {
            data.setKi(data.getKi() - kiDrain);
            data.setStamina(data.getStamina() - staDrain);
        }
        if (gameTime % 20 == 0) {
            double gain = DBZConfig.SERVER.masteryGainPerSecond.get() * data.getDerived().spiritModifier() / (1.0 + 0.5 * form.tier())
                    * com.dbzenith.race.RacialSkills.factor(data, com.dbzenith.race.RacialSkill.Stat.MASTERY_GAIN);
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
