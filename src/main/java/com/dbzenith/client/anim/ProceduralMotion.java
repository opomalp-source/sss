package com.dbzenith.client.anim;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Secondary motion no keyframe can know about, applied to the whole figure as it is drawn: banking into turns in
 * flight, pitching into climbs and dives, leaning into a fast run, and a squash on a hard landing. Smoothed per
 * client tick and interpolated per frame.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ProceduralMotion {
    private static final class Motion {
        float bank, bankO, pitch, pitchO, squash, squashO;
        boolean wasAirborne;
        double fallSpeed;
        boolean pushed;
    }

    private static final Map<Player, Motion> MOTION = new WeakHashMap<>();

    private ProceduralMotion() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            Motion m = MOTION.computeIfAbsent(p, k -> new Motion());
            m.bankO = m.bank;
            m.pitchO = m.pitch;
            m.squashO = m.squash;
            PublicStatePacket state = ClientPublicStates.get(p.getId());
            boolean flying = state != null && state.has(PublicStatePacket.FLYING) && !p.onGround();
            double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            float targetBank = 0, targetPitch = 0;
            if (flying) {
                float yawRate = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
                targetBank = Mth.clamp(-yawRate * 2.2f * (float) Math.min(1, speed / 0.6), -38, 38);   // lean into the turn
                targetPitch = Mth.clamp((float) (-dy * 30), -20, 20);                                  // nose up climbing, down diving
            } else if (p.onGround() && speed > 0.32) {
                targetPitch = Mth.clamp((float) ((speed - 0.32) * 40), 0, 14);                          // a fast run leans in
            }
            m.bank += (targetBank - m.bank) * 0.25f;
            m.pitch += (targetPitch - m.pitch) * 0.25f;
            // a hard landing squashes, then springs back
            boolean airborne = !p.onGround();
            if (airborne) m.fallSpeed = Math.max(m.fallSpeed, -dy);
            else if (m.wasAirborne) {
                if (m.fallSpeed > 0.55) {
                    m.squash = (float) Math.min(1, (m.fallSpeed - 0.4) * 1.2);
                    com.dbzenith.client.ClientSounds.land(p, m.squash);
                }
                m.fallSpeed = 0;
            }
            m.wasAirborne = airborne;
            m.squash = Math.max(0, m.squash - 0.18f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Motion m = MOTION.get(p);
        if (m == null) return;
        m.pushed = false;
        if (!DBZConfig.CLIENT.proceduralMotion.get() || event.isCanceled() || com.dbzenith.client.ui.PortraitRenderer.isDrawing()) return;
        float pt = event.getPartialTick();
        float bank = Mth.lerp(pt, m.bankO, m.bank), pitch = Mth.lerp(pt, m.pitchO, m.pitch), squash = Mth.lerp(pt, m.squashO, m.squash);
        if (Math.abs(bank) < 0.1f && Math.abs(pitch) < 0.1f && squash < 0.01f) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        m.pushed = true;
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vector3f forward = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vector3f right = new Vector3f(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        float mid = p.getBbHeight() * 0.5f;
        pose.translate(0, mid, 0);
        pose.mulPose(new Quaternionf().rotationAxis(bank * Mth.DEG_TO_RAD, forward));
        pose.mulPose(new Quaternionf().rotationAxis(pitch * Mth.DEG_TO_RAD, right));
        pose.translate(0, -mid, 0);
        if (squash > 0.01f) pose.scale(1 + 0.09f * squash, 1 - 0.18f * squash, 1 + 0.09f * squash);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        Motion m = MOTION.get(event.getEntity());
        if (m != null && m.pushed) {
            event.getPoseStack().popPose();
            m.pushed = false;
        }
    }
}
