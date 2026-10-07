package com.dbzenith.client;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The duel camera for watchers (CX-19 phase 9). While watching ({@code /duel watch}, as a spectator), the view glides to
 * keep both duelists in frame: off to the side of the line between them, far enough back to fit the gap, a little above,
 * looking at the middle. Moving yourself (any movement key) hands the camera back for a moment.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class DuelCam {
    private static int a = -1, b = -1;
    private static int pausedUntil;
    private static int ticks;
    private static float side = 1;

    private DuelCam() {}

    public static void set(int first, int second) {
        a = first;
        b = second;
    }

    public static boolean active() {
        return a >= 0 && b >= 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            a = b = -1;
            return;
        }
        ticks++;
        if (!p.isSpectator()) return;
        if (p.input.forwardImpulse != 0 || p.input.leftImpulse != 0 || p.input.jumping || p.input.shiftKeyDown) {
            pausedUntil = ticks + 60;                                           // they want to look round themselves
            return;
        }
        if (ticks < pausedUntil) return;
        Entity ea = mc.level.getEntity(a), eb = mc.level.getEntity(b);
        if (ea == null || eb == null) return;
        Vec3 pa = ea.position().add(0, 1, 0), pb = eb.position().add(0, 1, 0);
        Vec3 mid = pa.add(pb).scale(0.5), gap = pb.subtract(pa);
        Vec3 flat = new Vec3(gap.x, 0, gap.z);
        Vec3 across = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0, flat.x).normalize();
        Vec3 toMe = p.position().subtract(mid);
        if (toMe.dot(across.scale(side)) < -2) side = -side;                    // stay on whichever side we are
        double back = Math.max(7, gap.length() * 0.9 + 4);
        Vec3 want = mid.add(across.scale(back * side)).add(0, 2.5 + gap.length() * 0.15, 0);
        Vec3 now = p.position().lerp(want, 0.15);
        p.setPos(now.x, now.y, now.z);
        p.setDeltaMovement(Vec3.ZERO);
        Vec3 look = mid.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - p.getYRot()) * 0.25f);
        p.setXRot(p.getXRot() + (pitch - p.getXRot()) * 0.25f);
    }
}
