package com.dbzenith.skill;

import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.StrugglePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two beams meeting head-on lock into a struggle. The clash point sits on the line between the two casters and is
 * pushed by each side's beam power, boosted by mashing the ki key (players) or by steady effort (fighters). Drive it
 * all the way and the loser is blasted by both beams' worth of energy; hold out ten seconds and it all explodes
 * where the beams meet. Neither beam damages anything else while they are locked.
 */
public final class BeamStruggle {
    public static final int MAX_TICKS = 200;
    static final double STEP = 0.014;
    private static final Map<UUID, BeamStruggle> BY_OWNER = new HashMap<>();

    final KiBeamEntity a, b;
    double t;                       // 0 = clash at a's hands, 1 = at b's hands
    double mashA, mashB;
    int ticks;
    boolean over;

    private BeamStruggle(KiBeamEntity a, KiBeamEntity b, double t) {
        this.a = a;
        this.b = b;
        this.t = t;
    }

    /** Two beams pointing at each other whose paths come within reach. Returns the meeting fraction, or -1. */
    static double meeting(KiBeamEntity a, KiBeamEntity b) {
        Entity oa = a.getOwner(), ob = b.getOwner();
        if (oa == null || ob == null || oa == ob || a.struggle != null || b.struggle != null) return -1;
        if (a.direction().dot(b.direction()) > -0.25) return -1;               // not head-on enough
        Vec3 pa = a.position(), pb = b.position();
        Vec3 ea = pa.add(a.direction().scale(a.getLength())), eb = pb.add(b.direction().scale(b.getLength()));
        double reach = (a.getWidth() + b.getWidth()) / 2 + 0.8;
        if (ea.distanceTo(eb) > reach + 1.5 && !crossed(pa, ea, pb, eb, reach)) return -1;
        double span = pa.distanceTo(pb);
        if (span < 1) return -1;
        Vec3 meet = ea.add(eb).scale(0.5);
        return Math.max(0.1, Math.min(0.9, meet.subtract(pa).dot(pb.subtract(pa)) / (span * span)));
    }

    /** Whether the two segments pass within {@code reach} of each other (beams already overlapping). */
    private static boolean crossed(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2, double reach) {
        for (int i = 0; i <= 8; i++) {
            Vec3 s = p1.add(q1.subtract(p1).scale(i / 8.0));
            Vec3 d = q2.subtract(p2);
            double len2 = d.lengthSqr();
            double k = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, s.subtract(p2).dot(d) / len2));
            if (s.distanceTo(p2.add(d.scale(k))) < reach) return true;
        }
        return false;
    }

    /** Called by a beam each tick before it extends: finds a beam to lock with. */
    static void lookFor(KiBeamEntity self) {
        Vec3 start = self.position();
        Vec3 end = start.add(self.direction().scale(self.getLength() + 4));
        for (KiBeamEntity other : self.level().getEntitiesOfClass(KiBeamEntity.class, new AABB(start, end).inflate(3), e -> e != self && e.isAlive())) {
            double t = meeting(self, other);
            if (t < 0) continue;
            BeamStruggle s = new BeamStruggle(self, other, t);
            self.struggle = s;
            other.struggle = s;
            BY_OWNER.put(self.getOwner().getUUID(), s);
            BY_OWNER.put(other.getOwner().getUUID(), s);
            for (KiBeamEntity beam : new KiBeamEntity[]{self, other}) {
                if (beam.getOwner() instanceof ServerPlayer p) {
                    ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.BEAM, MAX_TICKS));
                }
            }
            return;
        }
    }

    /** A player hammered the ki key: their side pushes harder for a moment, for a little ki. */
    public static boolean mash(ServerPlayer player) {
        BeamStruggle s = BY_OWNER.get(player.getUUID());
        if (s == null || s.over) return false;
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return false;
        double cost = d.getDerived().maxKi() * 0.01;
        if (d.getKi() < cost) return false;
        d.setKi(d.getKi() - cost);
        if (s.a.getOwner() == player) s.mashA = Math.min(3, s.mashA + 0.5);
        else s.mashB = Math.min(3, s.mashB + 0.5);
        return true;
    }

    public static boolean isStruggling(Entity owner) {
        BeamStruggle s = BY_OWNER.get(owner.getUUID());
        return s != null && !s.over;
    }

    /** Run by beam {@code a} each tick; beam {@code b} just holds. */
    void tick() {
        if (over) return;
        Entity oa = a.getOwner(), ob = b.getOwner();
        if (oa == null || ob == null || !oa.isAlive() || !ob.isAlive() || a.isRemoved() || b.isRemoved()) {
            end(null);
            return;
        }
        ticks++;
        if (!(oa instanceof ServerPlayer)) mashA = Math.min(3, mashA + 0.06 + a.level().random.nextDouble() * 0.06);
        if (!(ob instanceof ServerPlayer)) mashB = Math.min(3, mashB + 0.06 + b.level().random.nextDouble() * 0.06);
        double pa = a.damagePerPulse() * (1 + mashA), pb = b.damagePerPulse() * (1 + mashB);
        t += STEP * (pa - pb) / Math.max(1e-6, pa + pb);
        mashA *= 0.92;
        mashB *= 0.92;

        Vec3 from = a.position(), to = b.position();
        Vec3 clash = from.add(to.subtract(from).scale(t));
        a.holdTo(clash);
        b.holdTo(clash);
        Level level = a.level();
        if (level instanceof ServerLevel sl) {
            if (ticks % 4 == 0) {
                ImpactPacket.at(clash, a.direction(), ImpactPacket.KI_HIT, (a.getWidth() + b.getWidth()) * 1.2f,
                        mixColor(a.getColor(), b.getColor()), -1).send(sl);
            }
            if (ticks % 2 == 0) {
                update(oa, (float) (t * 2 - 1), a.getColor(), b.getColor(), true);
                update(ob, (float) (1 - t * 2), b.getColor(), a.getColor(), true);
            }
        }
        if (t >= 0.96) end(a);
        else if (t <= 0.04) end(b);
        else if (ticks >= MAX_TICKS) end(null);
    }

    /** End it as a draw (a beam vanished on its own). */
    void abort() {
        if (!over) end(null);
    }

    /** {@code winner} null: a draw (or a caster fell), everything explodes at the clash point. */
    private void end(KiBeamEntity winner) {
        over = true;
        Entity oa = a.getOwner(), ob = b.getOwner();
        if (oa != null) BY_OWNER.remove(oa.getUUID(), this);
        if (ob != null) BY_OWNER.remove(ob.getUUID(), this);
        Level level = a.level();
        Vec3 from = a.position(), to = b.position();
        Vec3 clash = from.add(to.subtract(from).scale(t));
        if (level instanceof ServerLevel sl) {
            if (winner != null) {
                KiBeamEntity loser = winner == a ? b : a;
                Entity victim = loser.getOwner();
                if (victim instanceof LivingEntity lv) {
                    lv.invulnerableTime = 0;
                    lv.hurt(ModDamageTypes.kiBlast(level, winner, winner.getOwner()),
                            (float) ((winner.damagePerPulse() + loser.damagePerPulse()) * 6));
                    ImpactPacket.at(lv.getBoundingBox().getCenter(), winner.direction(), ImpactPacket.EXPLOSION, 4f, winner.getColor(),
                            winner.getOwner() == null ? -1 : winner.getOwner().getId()).send(sl);
                }
            } else {
                level.explode(a, clash.x, clash.y, clash.z, 3f, Level.ExplosionInteraction.NONE);
                ImpactPacket.at(clash, a.direction(), ImpactPacket.EXPLOSION, 4.5f, mixColor(a.getColor(), b.getColor()), -1).send(sl);
            }
            update(oa, 0, 0, 0, false);
            update(ob, 0, 0, 0, false);
            for (Entity o : new Entity[]{oa, ob}) {
                if (o instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.STOP, 0));
            }
        }
        a.discard();
        b.discard();
    }

    private static void update(Entity owner, float balance, int mine, int theirs, boolean active) {
        if (owner instanceof ServerPlayer p) ModNetwork.sendTo(p, new StrugglePacket(active, balance, mine, theirs));
    }

    private static int mixColor(int x, int y) {
        return (((x >> 16 & 255) + (y >> 16 & 255)) / 2) << 16 | (((x >> 8 & 255) + (y >> 8 & 255)) / 2) << 8 | ((x & 255) + (y & 255)) / 2;
    }
}
