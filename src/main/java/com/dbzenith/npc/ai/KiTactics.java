package com.dbzenith.npc.ai;

import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ki attacks: only the techniques of the fighter's profile, fired through {@code TechniqueHandler.spawn} as before,
 * but now paid for from its ki (the technique's own cost), on the technique's own cooldown (longer when badly hurt,
 * as for players), aimed with a lead on a moving foe that is good but never perfect, held back when an ally is in the
 * line of fire, and with the cast's animation and release sound. Techniques that can be charged (Finger Beam,
 * Supernova) are charged for real: the fighter stands in its charge pose with its aura up, and a hard blow breaks the
 * charge.
 */
final class KiTactics {
    private final KiFighter mob;
    private final NpcBrain brain;
    /** The longest an NPC charges a technique (ticks). */
    static final int MAX_CHARGE = 60;
    private final Map<String, Long> cooldowns = new HashMap<>();
    Technique charging;
    long chargeStart;
    int chargeFor;

    KiTactics(KiFighter mob, NpcBrain brain) {
        this.mob = mob;
        this.brain = brain;
    }

    List<Technique> known() {
        return mob.techniques();
    }

    boolean ready(Technique t, long now) {
        return t.style() != Technique.Style.SELF && cooldowns.getOrDefault(t.id(), Long.MIN_VALUE) <= now && brain.res.ki >= cost(t);
    }

    static double cost(Technique t) {
        return t.kiCost();
    }

    static boolean isBig(Technique t) {
        return t.style() == Technique.Style.BEAM || t.explosionPower() >= 1.5 || t.damageMult() >= 2;
    }

    /** The cheap one to throw now: the cheapest ready non-big technique (homing or a volley against a foe that dodges). */
    Technique quick(long now) {
        Technique best = null;
        double bestScore = Double.MAX_VALUE;
        for (Technique t : known()) {
            if (isBig(t) || !ready(t, now)) continue;
            double score = cost(t) - (brain.foe.dodgy() > 0.3 && (t.homing() || t.count() > 1) ? 100 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    /** The big one: the strongest ready technique it can afford. */
    Technique big(long now) {
        Technique best = null;
        for (Technique t : known()) {
            if (!isBig(t) || !ready(t, now)) continue;
            if (best == null || t.damageMult() > best.damageMult()) best = t;
        }
        return best;
    }

    /** Whether an ally stands in the line of fire. */
    boolean allyInLine(LivingEntity foe) {
        Vec3 eye = mob.getEyePosition(), at = foe.getBoundingBox().getCenter();
        for (KiFighter ally : brain.squad) {
            if (ally == foe || !ally.isAlive()) continue;
            AABB box = ally.getBoundingBox().inflate(0.6);
            if (box.clip(eye, at).isPresent() || box.contains(eye)) return true;
        }
        return false;
    }

    /** Faces the foe, leading it by its motion (better fighters lead better, never perfectly). */
    void aim(LivingEntity foe, double speed) {
        Vec3 eye = mob.getEyePosition();
        Vec3 at = foe.getBoundingBox().getCenter();
        double travel = speed > 0 ? eye.distanceTo(at) / speed : 0;
        double skill = 0.8 * brain.quality() * (0.75 + 0.25 * brain.unit(21));
        Vec3 v = foe.getDeltaMovement();
        if (foe.onGround()) v = new Vec3(v.x, 0, v.z);
        at = at.add(v.scale(Math.min(30, travel) * skill));
        Vec3 to = at.subtract(eye);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(pitch);
    }

    /** Fires {@code t} at the foe, charged to {@code charge} (0..1). Returns false if it could not. */
    boolean fire(Technique t, LivingEntity foe, double charge, long now) {
        if (t == null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) return false;
        if (!com.dbzenith.combat.Limbs.canUseKi(mob) || com.dbzenith.registry.ModEffects.isStunned(mob) || com.dbzenith.registry.ModEffects.isKiSealed(mob)) return false;
        if (!brain.forms.current().allowsTechniques()) return false;
        if (allyInLine(foe)) {
            brain.note("held fire: ally in line");
            return false;
        }
        if (!brain.res.spendKi(cost(t), now)) return false;
        aim(foe, t.style() == Technique.Style.BEAM ? 0 : t.speed());
        double damage = mob.kiDamage() * Math.sqrt(Math.max(0.1, t.damageMult())) * TechniqueHandler.chargeDamage(t, charge);
        TechniqueHandler.spawn(level, mob, t, damage, charge > 0 ? TechniqueHandler.chargeScale(t, charge) : 1f);
        long cd = (long) (t.cooldownTicks() * com.dbzenith.combat.Wounds.cooldown(mob) * (brain.tier == AiProfile.Tier.LIGHT ? 1.3 : 1.0));
        cooldowns.put(t.id(), now + Math.max(10, cd));
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), AnimEventPacket.forTechnique(mob, t));
        boolean beam = t.style() == Technique.Style.BEAM;
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), TechniqueHandler.releaseSound(t), SoundSource.HOSTILE,
                beam ? 0.6f : 0.75f + 0.2f * Math.min(2f, t.size()), beam ? 1.5f : 1.1f - 0.12f * Math.min(2f, t.size()));
        brain.noteUse(t);
        return true;
    }

    /** Starts charging a chargeable technique for {@code ticks}. */
    void beginCharge(Technique t, int ticks, long now) {
        charging = t;
        chargeStart = now;
        chargeFor = Math.max(10, Math.min(Math.min(ticks, MAX_CHARGE), t.chargeTicks()));   // a few seconds at most: a 45 s charge leaves it a sitting duck
        mob.setAiFlag(KiFighter.AI_CHARGING, true);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.dbzenith.registry.ModSounds.AURA_CHARGE.get(), SoundSource.HOSTILE, 0.6f, 1.4f);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new AnimEventPacket(mob.getId(), AnimEventPacket.FOCUS, 0));
    }

    /** One tick of a charge: released when full (or when the foe is about to get away). Returns true while charging. */
    boolean tickCharge(LivingEntity foe, long now) {
        if (charging == null) return false;
        if (foe == null || !foe.isAlive()) {
            fizzle(false);
            return false;
        }
        aim(foe, charging.style() == Technique.Style.BEAM ? 0 : charging.speed());
        long held = now - chargeStart;
        if (held >= chargeFor || !mob.hasLineOfSight(foe) && held > 10) {
            Technique t = charging;
            charging = null;
            mob.setAiFlag(KiFighter.AI_CHARGING, brain.powering);
            cooldowns.remove(t.id());
            fire(t, foe, Math.min(1.0, held / (double) Math.max(1, t.chargeTicks())), now);
            return false;
        }
        return true;
    }

    /** A charge broken (a hard blow) or given up: half the ki is lost. */
    void fizzle(boolean hit) {
        if (charging == null) return;
        brain.res.drainKi(cost(charging) * 0.5, mob.level().getGameTime());
        if (hit) brain.note("charge broken");
        charging = null;
        mob.setAiFlag(KiFighter.AI_CHARGING, brain.powering);
    }

    /** Tests and debug: how long until a technique is ready (0 if it is). */
    long cooldownLeft(String id, long now) {
        return Math.max(0, cooldowns.getOrDefault(id, Long.MIN_VALUE) - now);
    }
}
