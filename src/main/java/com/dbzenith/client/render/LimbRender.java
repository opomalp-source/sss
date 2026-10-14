package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.combat.Limbs;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Lost limbs on the model (CX-53): the arm or leg is gone (every layer drawn on the body leaves it out too), and in its
 * place a short stump, skin round the sides and a dark, seared end. In first person, a lost arm's hand is not drawn.
 * Players through the render events; NPC fighters through {@link #hide}/{@link #show} around their own render.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class LimbRender {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int SEARED = 0x6A1410, DEFAULT_SKIN = 0xD9A87A;

    private LimbRender() {}

    /** Leaves out lost limbs (before drawing); returns what it hid, to {@link #show} after. */
    public static int hide(HumanoidModel<?> m, LivingEntity e) {
        Limbs.State s = Limbs.of(e);
        if (s.severed == 0) return 0;
        int hidden = 0;
        for (Limbs.Limb l : Limbs.Limb.values()) {
            if (!s.severed(l) || s.prosthetic(l)) continue;
            for (ModelPart p : parts(m, l)) {
                if (p.visible) {
                    p.visible = false;
                    hidden |= 1 << l.ordinal();
                }
            }
        }
        return hidden;
    }

    public static void show(HumanoidModel<?> m, int hidden) {
        if (hidden == 0) return;
        for (Limbs.Limb l : Limbs.Limb.values()) {
            if ((hidden & 1 << l.ordinal()) == 0) continue;
            for (ModelPart p : parts(m, l)) p.visible = true;
        }
    }

    private static ModelPart[] parts(HumanoidModel<?> m, Limbs.Limb l) {
        ModelPart base = switch (l) {
            case RIGHT_ARM -> m.rightArm;
            case LEFT_ARM -> m.leftArm;
            case RIGHT_LEG -> m.rightLeg;
            case LEFT_LEG -> m.leftLeg;
        };
        if (m instanceof PlayerModel<?> pm) {
            ModelPart over = switch (l) {
                case RIGHT_ARM -> pm.rightSleeve;
                case LEFT_ARM -> pm.leftSleeve;
                case RIGHT_LEG -> pm.rightPants;
                case LEFT_LEG -> pm.leftPants;
            };
            return new ModelPart[]{base, over};
        }
        return new ModelPart[]{base};
    }

    private static int playerHidden;

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        playerHidden = hide(event.getRenderer().getModel(), event.getEntity());
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        show(event.getRenderer().getModel(), playerHidden);
        playerHidden = 0;
    }

    /** First person: a lost arm's hand isn't there. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        Limbs.Limb l = event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? Limbs.Limb.RIGHT_ARM : Limbs.Limb.LEFT_ARM;
        Limbs.State s = Limbs.of(event.getPlayer());
        if (s.severed(l) && !s.prosthetic(l)) event.setCanceled(true);
    }

    /** The stumps, as a layer of the figure. */
    public static final class StumpLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        public StumpLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Limbs.State s = Limbs.of(e);
            if (s.severed == 0 || e.isInvisible()) return;
            PublicStatePacket state = ClientPublicStates.get(e.getId());
            int skin = state != null && state.skinTone() >= 0 ? state.skinTone() : DEFAULT_SKIN;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
            M m = getParentModel();
            for (Limbs.Limb l : Limbs.Limb.values()) {
                if (!s.severed(l) || s.prosthetic(l)) continue;
                ModelPart p = parts(m, l)[0];
                pose.pushPose();
                p.translateAndRotate(pose);
                boolean right = l == Limbs.Limb.RIGHT_ARM || l == Limbs.Limb.RIGHT_LEG;
                float x0, x1, y0, y1;
                if (l.arm()) {
                    x0 = right ? -3 : -1; x1 = right ? 1 : 3; y0 = -2; y1 = 2.6f;   // the shoulder, a short stub of the upper arm
                } else {
                    x0 = -2; x1 = 2; y0 = 0; y1 = 3.4f;                            // the top of the thigh
                }
                cube(pose, vc, x0 / 16f, y0 / 16f, -2 / 16f, x1 / 16f, y1 / 16f, 2 / 16f, skin, SEARED, light, overlay);
                pose.popPose();
            }
        }
    }

    /** A box in the part's space: skin on the sides and the top, the seared cut at the bottom (+y). */
    private static void cube(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                             int side, int cut, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, side, light, overlay);   // front
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, side, light, overlay);    // back
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, side, light, overlay);   // sides
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, side, light, overlay);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, side, light, overlay);   // shoulder / hip
        float i = 0.012f;                                                     // the cut, a seared rim round it
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, darker(side), light, overlay);
        quad(vc, m, n, x0 + i, y1 + 0.001f, z0 + i, x1 - i, y1 + 0.001f, z0 + i, x1 - i, y1 + 0.001f, z1 - i, x0 + i, y1 + 0.001f, z1 - i, 0, 1, 0, cut, light, overlay);
    }

    private static int darker(int c) {
        return (int) (((c >> 16) & 255) * 0.6f) << 16 | (int) (((c >> 8) & 255) * 0.45f) << 8 | (int) ((c & 255) * 0.4f);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int rgb, int light, int overlay) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        vc.vertex(m, ax, ay, az).color(r, g, b, 1f).uv(0, 0).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, 1f).uv(1, 0).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, 1f).uv(1, 1).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, 1f).uv(0, 1).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
