package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.anim.AnimController;
import com.dbzenith.client.anim.Anims;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.client.fx.KiParticles;
import com.dbzenith.combat.Grip;
import com.dbzenith.network.GripPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Grips on this client (the knocked-out rework): both sides' finisher animations, the victim held where the finisher's
 * timeline puts them ({@link Grip#victimAt}, as on the server), the gripper's own controls held still, a hand that
 * glows with gathering ki for the ki finishers, and the killing frame: an impact frame and a jolt for anyone close.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientGrip {
    /** A grip under way: who, which finisher, since when (client game time), facing which way. */
    public record Session(int gripper, int victim, Grip.Finisher finisher, long start, float yaw) {}

    private static final List<Session> SESSIONS = new ArrayList<>();

    private ClientGrip() {}

    public static void set(GripPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Grip.Finisher f = Grip.Finisher.values()[Math.max(0, Math.min(Grip.Finisher.values().length - 1, m.finisher()))];
        switch (m.phase()) {
            case GripPacket.START -> {
                SESSIONS.removeIf(s -> s.gripper == m.gripperId() || s.victim == m.victimId());
                Entity g = mc.level.getEntity(m.gripperId());
                float yaw = g == null ? 0 : g.getYRot();
                if (g instanceof LivingEntity lg && mc.level.getEntity(m.victimId()) instanceof LivingEntity v) {
                    Vec3 to = v.position().subtract(lg.position());
                    yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                }
                SESSIONS.add(new Session(m.gripperId(), m.victimId(), f, mc.level.getGameTime(), yaw));
                AnimController.playLocked(m.gripperId(), Anims.gripper(f.ordinal()), f.length);
                if (!(mc.level.getEntity(m.gripperId()) instanceof net.minecraft.client.player.AbstractClientPlayer))
                    AnimController.playOn(m.gripperId(), Anims.gripper(f.ordinal()));
                AnimController.playOn(m.victimId(), Anims.gripped(f.ordinal()));
            }
            case GripPacket.KILL -> {
                Entity v = mc.level.getEntity(m.victimId());
                if (v != null) {
                    double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(v.position());
                    if (d < 18) {
                        CameraFx.impactFrame();
                        CameraFx.shake(0.45f * (float) (1 - d / 18));
                        CameraFx.aberrate(0.6f * (float) (1 - d / 18));
                    }
                }
            }
            default -> {
                SESSIONS.removeIf(s -> s.gripper == m.gripperId());
                if (m.phase() == GripPacket.BROKEN) {
                    AnimController.playOn(m.gripperId(), Anims.custom("hit_heavy", Anims.HIT_HEAVY));
                }
            }
        }
    }

    public static Session of(int entityId) {
        for (Session s : SESSIONS) if (s.gripper == entityId || s.victim == entityId) return s;
        return null;
    }

    public static boolean isGripping(int id) {
        for (Session s : SESSIONS) if (s.gripper == id) return true;
        return false;
    }

    public static boolean isGripped(int id) {
        for (Session s : SESSIONS) if (s.victim == id) return true;
        return false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SESSIONS.clear();
            return;
        }
        if (SESSIONS.isEmpty()) return;
        long now = mc.level.getGameTime();
        for (Session s : new ArrayList<>(SESSIONS)) {
            float age = now - s.start;
            if (age > s.finisher.length + 20) {
                SESSIONS.remove(s);
                continue;
            }
            Entity g = mc.level.getEntity(s.gripper), v = mc.level.getEntity(s.victim);
            if (!(g instanceof LivingEntity lg) || !(v instanceof LivingEntity lv)) continue;
            lg.setYRot(s.yaw);
            lg.yBodyRot = s.yaw;
            lg.setYHeadRot(s.yaw);
            if (age < s.finisher.killAt && lv.isAlive()) {                     // the victim where the finisher has them
                Vec3 now3 = Grip.victimAt(s.finisher, age, lg.getX(), lg.getY(), lg.getZ(), s.yaw);
                Vec3 was = Grip.victimAt(s.finisher, Math.max(0, age - 1), lg.xo, lg.yo, lg.zo, s.yaw);
                lv.setPos(now3.x, now3.y, now3.z);
                lv.xo = lv.xOld = was.x;
                lv.yo = lv.yOld = was.y;
                lv.zo = lv.zOld = was.z;
                float face = Grip.victimUpright(s.finisher, age) ? s.yaw + 180 : s.yaw + 90;
                lv.yBodyRot = lv.yBodyRotO = face;
                if (lv != mc.player) {
                    lv.setYRot(face);
                    lv.yRotO = face;
                }
                lv.setDeltaMovement(Vec3.ZERO);
            }
            if (event.phase == TickEvent.Phase.END) glow(s, lg, age);
        }
    }

    /** A hand gathering ki for the ki finishers, then the burst; dust for the slams and stomps. */
    private static void glow(Session s, LivingEntity g, float age) {
        double yr = Math.toRadians(s.yaw);
        Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr)), right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3 hand = g.position().add(0, g.getBbHeight() * 0.72, 0).add(fwd.scale(0.55)).add(right.scale(0.3));
        int c = com.dbzenith.client.fx.FxDraw.mix(auraColor(g), 0xFFFFFF, 0.3f);
        boolean gathering = switch (s.finisher) {
            case KI_EXECUTION -> age >= 22 && age < 37;
            case KI_SPEAR -> age >= 14 && age < 28;
            default -> false;
        };
        var r = g.getRandom();
        if (gathering) {
            for (int i = 0; i < 3; i++) {
                double ux = r.nextGaussian(), uy = r.nextGaussian(), uz = r.nextGaussian(), l = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6, d = 0.6 + r.nextDouble() * 0.4;
                KiParticles.add(KiParticles.MOTE, hand.x + ux / l * d, hand.y + uy / l * d, hand.z + uz / l * d, -ux / l * d / 10, -uy / l * d / 10, -uz / l * d / 10,
                        0.35f, c, 10, 1f, 0);
            }
            KiParticles.add(KiParticles.SPARK, hand.x, hand.y, hand.z, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, 0.6f, 0xFFFFFF, 3, 0.8f, 0);
        }
        if (age == s.finisher.killAt) {
            Vec3 at = g.position().add(fwd.scale(Grip.victimOffset(s.finisher, age).z)).add(0, 0.6, 0);
            switch (s.finisher) {
                case KI_EXECUTION, KI_SPEAR -> {
                    KiParticles.burst(at, c, 50, 0.5f, 1.1f);
                    CameraFx.flash(c, 0.3f);
                }
                default -> {
                    KiParticles.burst(at.add(0, -0.4, 0), 0xFFF0D0, 24, 0.35f, 0.7f);
                    com.dbzenith.client.fx.ImpactFx.cracks(g.position().add(fwd.scale(Grip.victimOffset(s.finisher, age).z)).add(0, 0.02, 0), 2.4f);
                }
            }
        }
    }

    private static int auraColor(LivingEntity e) {
        var st = ClientPublicStates.get(e.getId());
        return st != null && st.auraColor() != 0 ? st.auraColor() : 0x9FE8FF;
    }

    /** Gripping: you are held in place until it is done. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!isGripping(event.getEntity().getId())) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }
}
