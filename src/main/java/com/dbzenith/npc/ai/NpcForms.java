package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.registry.ModSounds;
import com.dbzenith.stats.Attribute;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.GodKi;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * An NPC's transformations, using only the existing forms of its race and variant ({@code Forms}) and their rules:
 * <ul>
 *   <li>which: manual forms of its race and variant, one tier at a time up the parent chain; not the giants (a Great
 *       Ape, a Giant Namekian: NPC bodies cannot grow), and not those needing a flag or god ki (no NPC has them);</li>
 *   <li>when unlocked: when the character level the NPC fights like ({@link Power#equivalentLevel}) reaches the form's
 *       unlock level (scaled by {@code unlockLevelScale}); an NPC is taken to have mastered its earlier forms;</li>
 *   <li>cost: {@code transformKiCostPercent} of its ki, paid when the power-up completes; the form drains its ki and
 *       stamina every second as for players (at 0 ki it falls back to base); the power-up takes the players' time
 *       ({@code transformTimeBase} + {@code transformTimePerTier} x tier, shorter for elite fighters) and a hard enough
 *       blow ({@code transformInterruptDamage}) breaks it;</li>
 *   <li>what it gives: {@code npc_ai.formPowerShare} of the form's multiplier on its blows and ki.</li>
 * </ul>
 * Shown to everyone through the fighter's synced form and flags: the form's aura, the power-up's particles and sound,
 * the burst on completion, and a combat-log line.
 */
public final class NpcForms {
    String form = Forms.BASE.id();
    String target = "";
    int ticks, total;
    long cooldownUntil, changedAt = Long.MIN_VALUE / 2;

    public Form current() {
        return Forms.byId(form);
    }

    public boolean transforming() {
        return !target.isEmpty();
    }

    public String target() {
        return target;
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    static boolean eligible(Form f, AiProfile p, int equivalentLevel) {
        return !f.isBase() && p.race() != null && f.races().contains(p.race()) && f.allows(p.variant())
                && f.trigger() == Form.Trigger.MANUAL && f.scale() <= 1.35f && f.allowsTechniques() && f.allowsFlight()
                && f.requiredFlag() == null && GodKi.required(f) <= 1 && FormMath.unlockLevel(f) <= equivalentLevel;
    }

    /** The next form up from where it is: the first eligible child, or null. */
    public Form nextUp(AiProfile p, int equivalentLevel) {
        if (p.race() == null) return null;
        for (Form f : Forms.children(form, p.race(), p.variant())) if (eligible(f, p, equivalentLevel)) return f;
        return null;
    }

    /** Every form it could climb to from base, in order (for the debug readout and tests). */
    public static List<Form> ladder(AiProfile p, int equivalentLevel) {
        List<Form> out = new ArrayList<>();
        if (p.race() == null) return out;
        String at = Forms.BASE.id();
        for (int guard = 0; guard < 12; guard++) {
            Form next = null;
            for (Form f : Forms.children(at, p.race(), p.variant())) if (eligible(f, p, equivalentLevel)) { next = f; break; }
            if (next == null) break;
            out.add(next);
            at = next.id();
        }
        return out;
    }

    /** What a form adds to an NPC's blows and ki: its mean multiplier, shared by {@code formPowerShare}. */
    public static double factor(Form f) {
        if (f == null || f.isBase()) return 1;
        double mean = (f.multiplier(Attribute.STRENGTH) + f.multiplier(Attribute.DEXTERITY) + f.multiplier(Attribute.KI_POWER)) / 3.0;
        double share;
        try {
            share = DBZConfig.SERVER.aiFormPowerShare.get();
        } catch (IllegalStateException e) {
            share = 0.3;
        }
        return 1 + Math.max(0, mean - 1) * share;
    }

    /** Ki the power-up costs. */
    public static double cost(NpcResources r) {
        return r.maxKi * DBZConfig.SERVER.transformKiCostPercent.get() / 100.0;
    }

    /** Ki a form drains each second (an NPC has some mastery: three quarters of a beginner's drain). */
    public static double drainPerSecond(Form f, NpcResources r) {
        return f.isBase() ? 0 : f.kiDrainPercent() * 0.75 * r.maxKi / 100.0;
    }

    static int powerUpTicks(Form f, AiProfile.Tier tier) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double full = c.transformTimeBase.get() + c.transformTimePerTier.get() * f.tier();
        return Math.max(10, (int) Math.round(full * (tier == AiProfile.Tier.ELITE ? 0.6 : 0.85)));
    }

    /** Starts powering up into {@code f}. */
    void begin(KiFighter k, Form f, AiProfile.Tier tier) {
        target = f.id();
        ticks = 0;
        total = powerUpTicks(f, tier);
        k.level().playSound(null, k.getX(), k.getY(), k.getZ(), ModSounds.POWERUP.get(), SoundSource.HOSTILE, 0.9f, 0.9f);
    }

    void stop() {
        target = "";
        ticks = total = 0;
    }

    /** One tick of the power-up: the aura builds, the ground answers; at the end it is paid for and taken. Returns true when done. */
    boolean tickPowerUp(KiFighter k, NpcResources r, long now) {
        Form f = Forms.byId(target);
        if (f.isBase()) {
            stop();
            return false;
        }
        ticks++;
        ServerLevel level = (ServerLevel) k.level();
        float progress = ticks / (float) Math.max(1, total);
        int c = f.auraColor();
        Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        if (ticks % 2 == 0) level.sendParticles(new DustParticleOptions(rgb, 1.2f + progress), k.getX(), k.getY() + 0.2, k.getZ(),
                4 + (int) (progress * 8), 0.5 + progress * 0.5, 0.1, 0.5 + progress * 0.5, 0.15);
        if (ticks % 6 == 0) {
            var below = level.getBlockState(k.blockPosition().below());
            if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), k.getX(), k.getY() + 0.1, k.getZ(),
                    6 + (int) (progress * 10), 1.2 + progress, 0.05, 1.2 + progress, 0.25);
            level.playSound(null, k.getX(), k.getY(), k.getZ(), ModSounds.POWERUP.get(), SoundSource.HOSTILE, 0.25f + progress * 0.35f, 0.5f + progress);
        }
        if (ticks % 20 == 10 && f.lightning()) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, k.getX(), k.getY() + 1, k.getZ(), 12, 0.6, 1.0, 0.6, 0.3);
        if (ticks < total) return false;
        stop();
        if (!r.spendKi(cost(r), now)) return false;                               // paid when it holds, as for players
        enter(k, f, now);
        return true;
    }

    /** Takes a form (or base): the change everyone sees. No requirement checks (callers check). */
    void enter(KiFighter k, Form f, long now) {
        Form from = current();
        form = f.id();
        changedAt = now;
        cooldownUntil = now + (f.isBase() ? 200 : 160);
        k.aiFormChanged(f);
        ServerLevel level = (ServerLevel) k.level();
        if (f.isBase()) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0.85f, 0.95f, 1f), 2.0f), k.getX(), k.getY() + 1, k.getZ(), 10, 0.6, 1.0, 0.6, 0.1);
            level.playSound(null, k.getX(), k.getY(), k.getZ(), ModSounds.POWER_DOWN.get(), SoundSource.HOSTILE, 0.8f, 1f);
            return;
        }
        ImpactPacket.at(k.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 0.6f + f.tier() * 0.15f, f.auraColor(), k.getId()).send(level);
        level.playSound(null, k.getX(), k.getY(), k.getZ(), ModSounds.TRANSFORM.get(), SoundSource.HOSTILE, 1.3f, 1f);
        if (!from.id().equals(f.id())) com.dbzenith.combat.CombatLog.near(k, Component.translatable("log.dbzenith.npc_transform", k.getDisplayName(),
                Component.translatable(f.translationKey())));
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("form", form);
        t.putLong("cooldown", cooldownUntil);
        return t;
    }

    void load(CompoundTag t) {
        form = t.contains("form") && Forms.exists(t.getString("form")) ? t.getString("form") : Forms.BASE.id();
        cooldownUntil = t.getLong("cooldown");
        target = "";
    }
}
