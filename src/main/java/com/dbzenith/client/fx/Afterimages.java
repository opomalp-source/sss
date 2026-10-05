package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Afterimages: fading, aura-tinted copies of a fighter left along the path of a dash or full-speed flight. Each copy
 * is the player's own model in its current pose, drawn where the player was a few ticks ago.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class Afterimages {
    private static final int LIFE = 7;
    private static final double MIN_SPACING_SQ = 0.35 * 0.35;

    private record Ghost(double x, double y, double z, float bodyYaw, int born) {}

    private static final class Trail {
        final ArrayDeque<Ghost> ghosts = new ArrayDeque<>();
        int until;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static int ticks;

    private Afterimages() {}

    /** Leave afterimages behind {@code player} for the next {@code forTicks} ticks. */
    public static void keepAlive(Player player, int forTicks) {
        Trail t = TRAILS.computeIfAbsent(player.getId(), id -> new Trail());
        t.until = Math.max(t.until, ticks + forTicks);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAILS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Trail> e = it.next();
            Trail t = e.getValue();
            Entity who = mc.level.getEntity(e.getKey());
            if (who instanceof Player p && ticks <= t.until) {
                Ghost last = t.ghosts.peekLast();
                if (last == null || p.distanceToSqr(last.x, last.y, last.z) > MIN_SPACING_SQ) {
                    t.ghosts.addLast(new Ghost(p.xo, p.yo, p.zo, p.yBodyRotO, ticks));
                }
            }
            while (!t.ghosts.isEmpty() && ticks - t.ghosts.peekFirst().born > LIFE) t.ghosts.removeFirst();
            if (t.ghosts.isEmpty() && ticks > t.until) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (com.dbzenith.client.ui.PortraitRenderer.isDrawing()) return;
        Trail trail = TRAILS.get(event.getEntity().getId());
        if (trail == null || trail.ghosts.isEmpty() || !DBZConfig.CLIENT.afterimages.get()) return;
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state != null && com.dbzenith.transform.Forms.GREAT_APE.id().equals(state.form())) return;
        int tint = FxDraw.mix(state == null ? 0xD9F2FF : state.auraColor(), 0xFFFFFF, 0.35f);
        float r = ((tint >> 16) & 255) / 255f, g = ((tint >> 8) & 255) / 255f, b = (tint & 255) / 255f;

        float partial = event.getPartialTick();
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        float scale = com.dbzenith.transform.GreatApe.scaleOf(player);

        // the same whole-figure transform playerAnimator applies to the live model
        IAnimation anim = PlayerAnimationAccess.getPlayerAnimLayer(player);
        Vec3f bodyPos = Vec3f.ZERO, bodyRot = Vec3f.ZERO;
        if (anim.isActive()) {
            bodyPos = anim.get3DTransform("body", TransformType.POSITION, partial, Vec3f.ZERO);
            bodyRot = anim.get3DTransform("body", TransformType.ROTATION, partial, Vec3f.ZERO);
        }

        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(event.getRenderer().getTextureLocation(player)));
        PoseStack pose = event.getPoseStack();
        for (Ghost ghost : trail.ghosts) {
            float age = ticks - ghost.born + partial;
            float alpha = 0.5f * (1 - age / (LIFE + 1));
            if (alpha <= 0.02f) continue;
            pose.pushPose();
            pose.translate((ghost.x - px) / scale, (ghost.y - py) / scale, (ghost.z - pz) / scale);
            pose.mulPose(Axis.YP.rotationDegrees(180f - ghost.bodyYaw));
            pose.translate(bodyPos.getX(), bodyPos.getY() + 0.7, bodyPos.getZ());
            pose.mulPose(Axis.ZP.rotation(bodyRot.getZ()));
            pose.mulPose(Axis.YP.rotation(bodyRot.getY()));
            pose.mulPose(Axis.XP.rotation(bodyRot.getX()));
            pose.translate(0, -0.7, 0);
            pose.scale(-1f, -1f, 1f);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0, -1.501f, 0);
            model.renderToBuffer(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            pose.popPose();
        }
    }
}
