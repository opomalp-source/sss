package com.dbzenith.transform;

import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.quest.QuestManager;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ultra Instinct (CX-17b), taught by Whis: the body dodges on its own. In the Sign a share of the blows and blasts aimed
 * at you slip past (a quarter, up to half with mastery); Mastered, most do (half, up to three quarters), and each dodge
 * answers with a counter-blow. A dodge is a short sidestep that leaves an afterimage; it costs a little stamina.
 * <p>A technique, not a form (the user, 2026-10-15): anyone who learns it from Whis, of any race, enters it on the
 * technique key over whatever form they are in, and keeps that form. It lifts them at least to its own power (the Sign
 * and Mastered have their own multipliers, growing with mastery), and a little above a form stronger than that. It
 * burns ki while it lasts and can't be held together with Kaioken. Mastered turns the hair and eyes silver. Its level
 * is {@link PlayerData#getUltraInstinct()}: 0 none, {@link #SIGN} or {@link #MASTERED}; its mastery is kept under
 * {@link #SIGN_ID} and {@link #MASTERED_ID}.
 */
public final class UltraInstinct {
    public static final int SIGN = 1, MASTERED = 2;
    public static final String SIGN_ID = "ultra_instinct_sign", MASTERED_ID = "ultra_instinct";
    public static final String SIGN_FLAG = "ultra_instinct";
    public static final String MASTERED_FLAG = "ultra_instinct_mastered";
    /** The quest event each dodge counts towards (Whis's second lesson). */
    public static final String DODGED = "dbzenith:ultra_instinct_dodge";
    /** Levels to reach for the Sign and for Mastered (scaled like the forms' unlock levels). */
    private static final int SIGN_LEVEL = 1500, MASTERED_LEVEL = 2200;
    /** Mastery of the Sign Mastered asks for. */
    private static final double MASTERED_NEEDS_SIGN = 50;
    /** Its own power: strength, speed, ki power. */
    private static final double[] SIGN_POWER = {4.5, 7.0, 4.5}, MASTERED_POWER = {6.5, 10.0, 6.5};
    /** Ki burned a second (% of max), before mastery eases it. */
    private static final double SIGN_DRAIN = 1.2, MASTERED_DRAIN = 1.6;

    private UltraInstinct() {}

    public static boolean isMastered(PlayerData d) {
        return d.getUltraInstinct() == MASTERED;
    }

    public static boolean isIn(PlayerData d) {
        return d.getUltraInstinct() > 0;
    }

    public static String id(int level) {
        return level >= MASTERED ? MASTERED_ID : SIGN_ID;
    }

    public static Component name(int level) {
        return Component.translatable("technique.dbzenith." + id(level));
    }

    /** Why this level can't be entered, or null. */
    public static Component problem(PlayerData d, int level) {
        String flag = level >= MASTERED ? MASTERED_FLAG : SIGN_FLAG;
        if (d.hasFlag(FormHandler.UNLOCK_FLAG + id(level))) return null;           // unlocked by hand (/dbz unlockform)
        if (!d.hasFlag(flag)) return Component.translatable("form.dbzenith.problem.flag." + flag);
        int need = (int) Math.round((level >= MASTERED ? MASTERED_LEVEL : SIGN_LEVEL) * DBZConfig.SERVER.unlockLevelScale.get());
        if (StatCalculator.level(d) < need) return Component.translatable("form.dbzenith.problem.level", need);
        if (level >= MASTERED && d.getMastery(SIGN_ID) < MASTERED_NEEDS_SIGN)
            return Component.translatable("form.dbzenith.problem.mastery", name(SIGN), (int) MASTERED_NEEDS_SIGN);
        return null;
    }

    /** The highest level this fighter can enter (Mastered if they have it), or 0. */
    public static int reachable(PlayerData d) {
        if (problem(d, MASTERED) == null) return MASTERED;
        return problem(d, SIGN) == null ? SIGN : 0;
    }

    /** Enters it at {@code level}: Kaioken drops, a transformation under way stops, the form stays. */
    public static void enter(ServerPlayer p, PlayerData d, int level) {
        Kaioken.stop(p, d, true);
        if (d.isTransforming()) d.stopTransforming();
        boolean fresh = d.getUltraInstinct() == 0;
        d.setUltraInstinct(level);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
        ImpactPacket.at(p.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.KI_HIT, fresh ? 0.9f : 1.2f, level >= MASTERED ? 0xEEF4FF : 0xB8C8FF, p.getId())
                .send(p.serverLevel());
        p.displayClientMessage(Component.translatable("message.dbzenith.ultra_instinct_on", name(level)), true);
    }

    public static void stop(ServerPlayer p, PlayerData d, boolean quiet) {
        if (d.getUltraInstinct() == 0) return;
        d.setUltraInstinct(0);
        if (!quiet) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.POWER_DOWN.get(), SoundSource.PLAYERS, 0.7f, 1.1f);
    }

    /** Every tick from KiTicker: the ki it burns, mastery, and giving out. */
    public static void tick(ServerPlayer p, PlayerData d, long now) {
        int level = d.getUltraInstinct();
        if (level == 0) return;
        if (problem(d, level) != null) {                                           // forgotten, or unlocked by hand and locked again
            stop(p, d, false);
            return;
        }
        String id = id(level);
        double mastery = d.getMastery(id);
        if (!p.getAbilities().instabuild) {
            double drain = FormMath.masteredDrain(level >= MASTERED ? MASTERED_DRAIN : SIGN_DRAIN, mastery);
            d.setKi(d.getKi() - drain * d.getDerived().maxKi() / 100.0 / 20.0);
        }
        if (now % 20 == 0) {
            double gain = DBZConfig.SERVER.masteryGainPerSecond.get() * d.getDerived().spiritModifier() / (level >= MASTERED ? 4.0 : 3.5);
            d.setMastery(id, mastery + gain);
        }
        if (d.getKi() <= 0 && !p.getAbilities().instabuild) {
            stop(p, d, false);
            p.displayClientMessage(Component.translatable("message.dbzenith.ultra_instinct_gave_out"), true);
        }
    }

    /** Its own multiplier for an attribute, grown with mastery as a form's is. */
    public static double power(PlayerData d, int level, Attribute a) {
        double[] p = level >= MASTERED ? MASTERED_POWER : SIGN_POWER;
        double m = a == Attribute.STRENGTH ? p[0] : a == Attribute.DEXTERITY ? p[1] : a == Attribute.KI_POWER ? p[2] : 1.0;
        if (m == 1.0) return 1.0;
        double mastery = d.getMastery(id(level)) / 100.0;
        return 1.0 + (m - 1.0) * (1.0 + DBZConfig.SERVER.masteryMaxMultiplierBonus.get() * mastery);
    }

    /**
     * What it adds over the form's multiplier {@code form}: up to its own power if the form is weaker, and a little on top
     * (8% in the Sign, 15% Mastered) whatever the form.
     */
    public static double factor(PlayerData d, Attribute a, double form) {
        int level = d.getUltraInstinct();
        if (level == 0 || !FormMath.isCombatAttribute(a)) return 1.0;
        double own = power(d, level, a);
        double lift = form > 0 && own > form ? own / form : 1.0;
        return lift * (level >= MASTERED ? 1.15 : 1.08);
    }

    /** Chance (0..1) that a blow slips past, or 0 outside Ultra Instinct. */
    public static double evadeChance(PlayerData d) {
        if (!isIn(d)) return 0;
        double m = Math.min(1.0, d.getMastery(id(d.getUltraInstinct())) / 100.0);
        return isMastered(d) ? 0.5 + 0.25 * m : 0.25 + 0.25 * m;
    }

    /**
     * Rolls a dodge for a blow from {@code foe}. On a dodge the player sidesteps (the blow lands on nothing, and an
     * afterimage stays where they stood), the quest counts it, and Mastered strikes back. Returns whether it dodged.
     */
    public static boolean evade(ServerPlayer player, PlayerData d, Entity foe, double roll) {
        if (foe == null || foe == player || roll >= evadeChance(d)) return false;
        if (d.getStamina() < d.getDerived().maxStamina() * 0.02) return false;            // too winded for the body to move
        d.setStamina(d.getStamina() - d.getDerived().maxStamina() * 0.02);
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        Vec3 away = player.position().subtract(foe.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = player.getLookAngle().multiply(-1, 0, -1);
        Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(player.getRandom().nextBoolean() ? 3.0 : -3.0);
        Vec3 to = player.position().add(side);
        if (level.noCollision(player, player.getBoundingBox().move(side))) player.teleportTo(to.x, player.getY(), to.z);
        // the clients leave an afterimage where they stood, and a silver streak to where they went
        ImpactPacket.at(from.add(0, 1, 0), to.subtract(from), ImpactPacket.UI_DODGE, 1f, isMastered(d) ? 0xEEF4FF : 0xB8C8FF, player.getId()).send(level);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.6f);
        QuestManager.event(player, DODGED);
        player.displayClientMessage(Component.translatable("message.dbzenith.ultra_instinct_dodge"), true);
        if (isMastered(d) && foe instanceof LivingEntity target && foe.isAlive() && foe.distanceToSqr(player) < 64) {
            double damage = DamageCalculator.meleeOutgoing(d, 1.0, 1) * 0.8;               // the counter-blow
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.thrown(level, player), (float) damage);
            ImpactPacket.melee(player, target, ImpactPacket.PUNCH).send(level);
        }
        return true;
    }
}
