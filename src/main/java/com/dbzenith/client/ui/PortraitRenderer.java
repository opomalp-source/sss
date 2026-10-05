package com.dbzenith.client.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws a player's live head and shoulders into a GUI rectangle: the real model with its race skin, form hair and
 * current animation, but held upright and facing three-quarters to the viewer. Auras and afterimages are skipped
 * while it draws ({@link #isDrawing()}).
 */
public final class PortraitRenderer {
    private static boolean drawing;

    private PortraitRenderer() {}

    public static boolean isDrawing() {
        return drawing;
    }

    /**
     * @param cx,cy  where the centre of the head lands
     * @param scale  pixels per block (a head is half a block)
     * @param turn   degrees the face is turned from straight at the viewer (positive: towards the viewer's left)
     * @param clip   scissor rectangle {x0, y0, x1, y1}
     */
    public static void draw(GuiGraphics g, AbstractClientPlayer player, int cx, int cy, float scale, float turn, int[] clip) {
        float giant = com.dbzenith.transform.GreatApe.scaleOf(player);
        float s = scale / giant;

        float yBody = player.yBodyRot, yBodyO = player.yBodyRotO, yRot = player.getYRot(), xRot = player.getXRot();
        float yHead = player.yHeadRot, yHeadO = player.yHeadRotO, xRotO = player.xRotO, yRotO = player.yRotO;
        float facing = 180f + turn;
        player.yBodyRot = player.yBodyRotO = facing;
        player.setYRot(facing);
        player.yRotO = facing;
        player.setXRot(0);
        player.xRotO = 0;
        player.yHeadRot = player.yHeadRotO = facing;

        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        drawing = true;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + 1.5f * scale, 150);                 // head centre: 1.5 blocks up, at any form scale
        pose.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        undoBodyTransform(pose, player, facing);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(player, 0, 0, 0, 0f, 1f, pose, g.bufferSource(), 0xF000F0));
            g.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            pose.popPose();
            Lighting.setupFor3DItems();
            drawing = false;
            g.disableScissor();
            player.yBodyRot = yBody;
            player.yBodyRotO = yBodyO;
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            player.yHeadRot = yHead;
            player.yHeadRotO = yHeadO;
        }
    }

    /**
     * playerAnimator tips the whole figure (flight, lunges) inside the renderer, after the body-yaw turn:
     * yaw . T where T = translate(p + 0.7 up) . rotZ . rotY . rotX . translate(0.7 down). Pre-multiplying
     * yaw . T^-1 . yaw^-1 leaves just the yaw, so the portrait stays upright while the limbs still move.
     */
    private static void undoBodyTransform(PoseStack pose, AbstractClientPlayer player, float bodyYaw) {
        IAnimation anim = PlayerAnimationAccess.getPlayerAnimLayer(player);
        if (!anim.isActive()) return;
        float partial = Minecraft.getInstance().getFrameTime();
        Vec3f p = anim.get3DTransform("body", TransformType.POSITION, partial, Vec3f.ZERO);
        Vec3f r = anim.get3DTransform("body", TransformType.ROTATION, partial, Vec3f.ZERO);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        pose.translate(0, 0.7, 0);
        pose.mulPose(Axis.XP.rotation(-r.getX()));
        pose.mulPose(Axis.YP.rotation(-r.getY()));
        pose.mulPose(Axis.ZP.rotation(-r.getZ()));
        pose.translate(-p.getX(), -p.getY() - 0.7, -p.getZ());
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180f));
    }
}
