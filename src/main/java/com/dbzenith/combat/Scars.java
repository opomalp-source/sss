package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.ScarsPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Battle scars (the limbs rework; the user: "warriors that fight a lot and get hurt a lot should get scars that look good
 * and convincing, and make sense"). Earned, not picked:
 * <ul>
 *   <li>A heavy wound (15% of your health or more in one blow) can leave a scar, more surely the deeper it went and for
 *       a blade; going down (knocked out) leaves one most of the time. One a minute at most; fourteen in all (a new one
 *       then takes the place of the faintest).</li>
 *   <li>It goes where the blow landed: the side it came from, high or low, the face for a hard blow high from the
 *       front.</li>
 *   <li>Its kind is what made it: a blade or a ki disc cuts a line (some healed with stitches), a ki blast or beam
 *       burns, a heavy blow splits the skin in a short jagged line, a thrust leaves a puncture. (A healed stump shows its
 *       own seam, on the stump.)</li>
 *   <li>Scars start angry and red and fade, over a few days, to pale scar tissue; they are drawn in the fighter's own
 *       skin tones (a Namekian's scars are a paler green), by the clients.</li>
 *   <li>They stay for life: a Senzu Bean heals wounds, not scars. A wish to be made whole (Restoration) clears them.</li>
 * </ul>
 * Players only; kept over death (a body that has fought keeps its marks). Sent to clients in {@link ScarsPacket}.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Scars {
    /** What made it. */
    public static final int CUT = 0, BURN = 1, SPLIT = 2, PUNCTURE = 3;
    /** Where: the face, the body front and back, each arm and leg (front or outer side). */
    public static final int FACE = 0, CHEST = 1, BACK = 2, RIGHT_ARM = 3, LEFT_ARM = 4, RIGHT_LEG = 5, LEFT_LEG = 6;
    public static final int MAX = 14;
    private static final String TAG = "dbzenith_scars";

    /** One scar: where (part, side: 0 front 1 outer/back), the spot (0..1 across and down the face), its angle, length (0..1), kind, its seed, when. */
    public record Scar(int part, int side, float u, float v, float angle, float length, int kind, int seed, long made) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putByte("p", (byte) part);
            t.putByte("s", (byte) side);
            t.putFloat("u", u);
            t.putFloat("v", v);
            t.putFloat("a", angle);
            t.putFloat("l", length);
            t.putByte("k", (byte) kind);
            t.putInt("r", seed);
            t.putLong("t", made);
            return t;
        }

        static Scar load(CompoundTag t) {
            return new Scar(t.getByte("p"), t.getByte("s"), t.getFloat("u"), t.getFloat("v"), t.getFloat("a"), t.getFloat("l"), t.getByte("k"), t.getInt("r"), t.getLong("t"));
        }
    }

    private Scars() {}

    public static List<Scar> of(LivingEntity e) {
        List<Scar> out = new ArrayList<>();
        ListTag list = e.getPersistentData().getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(Scar.load(list.getCompound(i)));
        return out;
    }

    private static void store(LivingEntity e, List<Scar> scars) {
        ListTag list = new ListTag();
        for (Scar s : scars) list.add(s.save());
        if (list.isEmpty()) e.getPersistentData().remove(TAG);
        else e.getPersistentData().put(TAG, list);
        broadcast(e, scars);
    }

    /** Adds a scar (making room by letting the faintest go). */
    public static void add(LivingEntity e, Scar s) {
        List<Scar> scars = of(e);
        if (scars.size() >= MAX) {
            Scar faintest = scars.get(0);
            for (Scar x : scars) if (x.length < faintest.length || x.length == faintest.length && x.made < faintest.made) faintest = x;
            scars.remove(faintest);
        }
        scars.add(s);
        store(e, scars);
    }

    /** A scar of any kind, anywhere (the {@code /dbz scars} cheat). */
    public static Scar random(LivingEntity e) {
        int kind = e.getRandom().nextInt(4);
        return place(e, e.damageSources().generic(), kind, 0.25f + e.getRandom().nextFloat() * 0.55f);
    }

    /** Every scar as if made {@code days} earlier (the cheat: to see them settle). */
    public static void age(LivingEntity e, float days) {
        List<Scar> aged = new ArrayList<>();
        for (Scar s : of(e)) aged.add(new Scar(s.part(), s.side(), s.u(), s.v(), s.angle(), s.length(), s.kind(), s.seed(), s.made() - (long) (days * 24000)));
        if (!aged.isEmpty()) store(e, aged);
    }

    public static void clear(LivingEntity e) {
        if (!of(e).isEmpty()) store(e, new ArrayList<>());
    }

    // ------------------------------------------------------------------ earning them

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator() || event.getAmount() <= 0) return;
        if (KnockOut.isDown(p)) return;
        float share = event.getAmount() / Math.max(1f, p.getMaxHealth());
        earn(p, event.getSource(), share);
    }

    /** A blow of {@code share} of their health: maybe a scar, where it landed. Returns whether it left one. */
    public static boolean earn(LivingEntity p, DamageSource src, float share) {
        if (share < 0.15f) return false;
        long now = p.level().getGameTime();
        long last = p.getPersistentData().contains("dbzenith_scar_last") ? p.getPersistentData().getLong("dbzenith_scar_last") : Long.MIN_VALUE / 2;
        if (now - last < 1200) return false;                               // one a minute at most
        int kind = kind(src);
        float chance = (kind == CUT ? 0.55f : kind == BURN ? 0.4f : 0.3f) + (share - 0.15f) * 1.2f;
        if (p.getRandom().nextFloat() > chance) return false;
        p.getPersistentData().putLong("dbzenith_scar_last", now);
        add(p, place(p, src, kind, share));
        return true;
    }

    /**
     * Going down (knocked out) leaves a scar most of the time, where the last blow landed (the feel pass: a hard fight
     * marks you even when no single blow was huge). The one-a-minute limit holds.
     */
    public static boolean downed(LivingEntity p, DamageSource src) {
        if (!(p instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return false;
        long now = p.level().getGameTime();
        long last = p.getPersistentData().contains("dbzenith_scar_last") ? p.getPersistentData().getLong("dbzenith_scar_last") : Long.MIN_VALUE / 2;
        if (now - last < 1200 || p.getRandom().nextFloat() > 0.65f) return false;
        p.getPersistentData().putLong("dbzenith_scar_last", now);
        add(p, place(p, src, kind(src), 0.35f));
        return true;
    }

    /** What kind of scar a blow leaves. */
    static int kind(DamageSource src) {
        Entity direct = src.getDirectEntity();
        if (direct instanceof com.dbzenith.skill.KiBlastEntity b) return b.getStyle() == com.dbzenith.skill.Technique.Style.DISK ? CUT : BURN;
        if (direct instanceof com.dbzenith.skill.KiBeamEntity || src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION)) return BURN;
        if (Limbs.cutting(src)) return CUT;
        if (src.is(DamageTypeTags.IS_PROJECTILE)) return PUNCTURE;
        return SPLIT;
    }

    /** Where it goes: where the blow came from. */
    static Scar place(LivingEntity v, DamageSource src, int kind, float share) {
        var r = v.getRandom();
        Entity by = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        double yr = Math.toRadians(v.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        double front = 1, side = r.nextGaussian(), height = r.nextDouble();
        if (by != null) {
            Vec3 to = by.position().subtract(v.position());
            front = to.dot(fwd);
            side = to.dot(right);
            height = (by.getY() + by.getBbHeight() * 0.5 - v.getY()) / Math.max(0.5, v.getBbHeight());
        }
        int part;
        if (height > 0.85 && front > 0 && share > 0.28 && r.nextFloat() < 0.5f) part = FACE;   // a hard blow, high, from the front
        else if (height > 0.45 && Math.abs(side) < 0.5) part = front >= 0 ? CHEST : BACK;
        else if (height > 0.45) part = side > 0 ? RIGHT_ARM : LEFT_ARM;
        else part = side > 0.2 ? RIGHT_LEG : side < -0.2 ? LEFT_LEG : (r.nextBoolean() ? RIGHT_LEG : LEFT_LEG);
        Limbs.State limbs = Limbs.of(v);                                      // nothing to scar where a limb is gone
        if (part == RIGHT_ARM && limbs.severed(Limbs.Limb.RIGHT_ARM) || part == LEFT_ARM && limbs.severed(Limbs.Limb.LEFT_ARM)
                || part == RIGHT_LEG && limbs.severed(Limbs.Limb.RIGHT_LEG) || part == LEFT_LEG && limbs.severed(Limbs.Limb.LEFT_LEG)) part = CHEST;
        int s = part == CHEST || part == BACK || part == FACE ? 0 : r.nextFloat() < 0.65f ? 0 : 1;
        float length = Math.min(1f, (kind == PUNCTURE ? 0.15f : kind == SPLIT ? 0.3f : 0.45f) + share * (kind == CUT ? 1.1f : 0.6f) + r.nextFloat() * 0.15f);
        float angle = (float) ((r.nextDouble() - 0.5) * Math.PI * (kind == CUT ? 1.0 : 0.6));
        if (part == FACE) {
            length = Math.min(length, 0.55f);
            return new Scar(part, 0, 0.2f + r.nextFloat() * 0.6f, 0.25f + r.nextFloat() * 0.5f, (float) (Math.PI * (0.25 + r.nextDouble() * 0.15) * (r.nextBoolean() ? 1 : -1)),
                    length, kind, r.nextInt(), v.level().getGameTime());
        }
        return new Scar(part, s, 0.2f + r.nextFloat() * 0.6f, 0.15f + r.nextFloat() * 0.7f, angle, length, kind, r.nextInt(), v.level().getGameTime());
    }

    // ------------------------------------------------------------------ telling clients

    private static void broadcast(LivingEntity e, List<Scar> scars) {
        ScarsPacket m = new ScarsPacket(e.getId(), scars);
        if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, m);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player t && event.getEntity() instanceof ServerPlayer p) {
            List<Scar> s = of(t);
            if (!s.isEmpty()) ModNetwork.sendTo(p, new ScarsPacket(t.getId(), s));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !of(p).isEmpty()) broadcast(p, of(p));
    }

    /** Kept over a death. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG)) event.getEntity().getPersistentData().put(TAG, old.getList(TAG, Tag.TAG_COMPOUND).copy());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !of(p).isEmpty()) broadcast(p, of(p));
    }
}
