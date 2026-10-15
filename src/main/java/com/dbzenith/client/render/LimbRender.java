package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.BloodFx;
import com.dbzenith.combat.Blood;
import com.dbzenith.combat.Limbs;
import com.dbzenith.network.ImpactPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Lost limbs on the model (CX-53): the arm or leg is gone (every layer drawn on the body leaves it out too), and in its
 * place a short stump in the fighter's own skin ({@link Skin}: a Namekian's is green). In first person, a lost arm's
 * hand is not drawn, and with the right arm gone what you hold is in the left.
 * <p>The limbs rework: the stump's end shows how the wound is:
 * <ul>
 *   <li><b>open:</b> raw, in the colour of their blood, the bone showing (it keeps seeping until it is closed);</li>
 *   <li><b>bandaged:</b> wrapped, the blood soaking through;</li>
 *   <li><b>seared</b> (by ki, or closed with ki): burnt black at the middle, an angry rim;</li>
 *   <li><b>healed:</b> skin drawn over the end, with a pale, puckered seam.</li>
 * </ul>
 * Namekians and Majins grow lost limbs back ({@link ImpactPacket#REGROW}): a bud of slime (pink goo for a Majin) swells
 * at the stump, then the limb pushes out of it, thin at first and filling out, wet and dripping, while they strain and
 * thrust it out ({@code Anims.REGENERATE}).
 * <p>Players through the render events; NPC fighters through {@link #hide}/{@link #show} around their own render.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class LimbRender {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation PROSTHETIC = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/prosthetic.png");
    private static final int SEAR = 0x6A1410, CHAR = 0x24120C, BONE = 0xEDE3D0, BANDAGE = 0xE8E1D0;
    /** The regrowth: ticks in all, and the first part of it, the bud swelling before the limb comes. */
    public static final int REGROW_TICKS = 44, BUD = 12;

    private LimbRender() {}

    // ------------------------------------------------------------------ growing back (the limbs rework)

    /** Who is growing what back: per limb, when it began (game ticks, -1 none) and the slime's colour. */
    private static final class Regrowth {
        final long[] start = {-1, -1, -1, -1};
        final int[] color = new int[4];
    }

    private static final Int2ObjectOpenHashMap<Regrowth> REGROWING = new Int2ObjectOpenHashMap<>();

    /** From the server: a limb grows back. */
    public static void onRegrow(ImpactPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int limb = Mth.clamp((int) m.scale(), 0, 3);
        Regrowth r = REGROWING.computeIfAbsent(m.attackerId(), k -> new Regrowth());
        r.start[limb] = mc.level.getGameTime();
        r.color[limb] = m.color();
        com.dbzenith.client.anim.AnimController.playOn(m.attackerId(), com.dbzenith.client.anim.Anims.regenerate(limb));
    }

    /** How far along a limb's regrowth is, 0..1, or -1 if it isn't growing back. */
    public static float regrowing(LivingEntity e, Limbs.Limb l) {
        Regrowth r = REGROWING.get(e.getId());
        if (r == null || r.start[l.ordinal()] < 0) return -1;
        float t = (e.level().getGameTime() - r.start[l.ordinal()] + Minecraft.getInstance().getFrameTime()) / REGROW_TICKS;
        return t >= 1 ? -1 : Math.max(0, t);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            REGROWING.clear();
            return;
        }
        if (mc.isPaused() || REGROWING.isEmpty()) return;
        long now = mc.level.getGameTime();
        RandomSource rnd = mc.level.random;
        var it = REGROWING.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Regrowth r = en.getValue();
            boolean any = false;
            for (Limbs.Limb l : Limbs.Limb.values()) {
                long s = r.start[l.ordinal()];
                if (s < 0) continue;
                long age = now - s;
                if (age >= REGROW_TICKS) {
                    r.start[l.ordinal()] = -1;
                    continue;
                }
                any = true;
                if (!(mc.level.getEntity(en.getIntKey()) instanceof LivingEntity e) || age % 2 != 0) continue;
                Vec3 at = BloodFx.socket(e, l);                                   // slime dripping off it as it grows
                float grown = age < BUD ? 0 : (age - BUD) / (float) (REGROW_TICKS - BUD);
                if (l.arm()) at = at.add(0, -grown * 0.5 * e.getBbHeight() / 1.8, 0);
                else at = at.add(0, -grown * 0.35 * e.getBbHeight() / 1.8, 0);
                int n = age < BUD ? 1 : 2;
                for (int i = 0; i < n; i++)
                    BloodFx.add(at.add(rnd.nextGaussian() * 0.06, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.06),
                            new Vec3(rnd.nextGaussian() * 0.02, -0.02 - rnd.nextFloat() * 0.03, rnd.nextGaussian() * 0.02), r.color[l.ordinal()], Blood.GOO, 0.05f + rnd.nextFloat() * 0.04f);
            }
            if (!any) it.remove();
        }
    }

    // ------------------------------------------------------------------ leaving them out

    /**
     * Leaves out lost limbs (before drawing) and sizes limbs growing back; returns what it changed (lost limbs in the
     * low four bits, scaled ones in the next four), to {@link #show} after.
     */
    public static int hide(HumanoidModel<?> m, LivingEntity e) {
        Limbs.State s = Limbs.of(e);
        boolean growing = REGROWING.containsKey(e.getId());
        if (s.severed == 0 && !growing) return 0;
        int changed = 0;
        for (Limbs.Limb l : Limbs.Limb.values()) {
            float t = growing && !s.severed(l) ? regrowing(e, l) : -1;
            if (s.severed(l) || t >= 0 && t < BUD / (float) REGROW_TICKS) {    // lost (a stump or a prosthetic in its place), or only a bud yet
                for (ModelPart p : parts(m, l)) {
                    if (p.visible) {
                        p.visible = false;
                        changed |= 1 << l.ordinal();
                    }
                }
            } else if (t >= 0) {                                              // pushing out of the stump: long and thin, filling out
                float g = (t - BUD / (float) REGROW_TICKS) / (1 - BUD / (float) REGROW_TICKS);
                float len = 0.12f + 0.88f * (1 - (1 - g) * (1 - g) * (1 - g));
                float girth = 0.62f + 0.38f * g + 0.07f * Mth.sin(g * 19f) * (1 - g);
                for (ModelPart p : parts(m, l)) {
                    p.xScale = girth;
                    p.zScale = girth;
                    p.yScale = len;
                }
                changed |= 1 << (l.ordinal() + 4);
            }
        }
        return changed;
    }

    public static void show(HumanoidModel<?> m, int changed) {
        if (changed == 0) return;
        for (Limbs.Limb l : Limbs.Limb.values()) {
            if ((changed & 1 << l.ordinal()) != 0) for (ModelPart p : parts(m, l)) p.visible = true;
            if ((changed & 1 << (l.ordinal() + 4)) != 0) for (ModelPart p : parts(m, l)) {
                p.xScale = 1;
                p.yScale = 1;
                p.zScale = 1;
            }
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

    // ------------------------------------------------------------------ first person

    /** First person: a lost arm's hand isn't there. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        Limbs.Limb l = event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? Limbs.Limb.RIGHT_ARM : Limbs.Limb.LEFT_ARM;
        Limbs.State s = Limbs.of(event.getPlayer());
        if (!s.severed(l)) return;
        event.setCanceled(true);
        if (!s.prosthetic(l)) return;                                           // a prosthetic hand, in steel (CX-54)
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(event.getPlayer())
                instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r)) return;
        PlayerModel<net.minecraft.client.player.AbstractClientPlayer> m = r.getModel();
        m.attackTime = 0;
        m.crouching = false;
        m.swimAmount = 0;
        m.setupAnim(event.getPlayer(), 0, 0, 0, 0, 0);
        ModelPart arm = l == Limbs.Limb.RIGHT_ARM ? m.rightArm : m.leftArm;
        arm.xRot = 0;
        boolean was = arm.visible;
        arm.visible = true;
        arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entitySolid(PROSTHETIC)), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        arm.visible = was;
    }

    /**
     * First person with the right arm gone: what you hold is in your left hand (the limbs rework). (Your own game keeps
     * the main hand on the side your options say; others see the server's swap.)
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.player == null || !Limbs.of(mc.player).leftHanded()) return;
        if (mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) return;
        event.setCanceled(true);
        ItemStack item = event.getItemStack();
        if (item.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        float swing = event.getSwingProgress(), equip = event.getEquipProgress();
        float root = Mth.sqrt(swing);
        pose.pushPose();
        pose.translate(0.4f * Mth.sin(root * Mth.PI), 0.2f * Mth.sin(root * Mth.TWO_PI), -0.2f * Mth.sin(swing * Mth.PI));   // the vanilla swing, on the left
        pose.translate(-0.56f, -0.52f + equip * -0.6f, -0.72f);
        float a = Mth.sin(swing * swing * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(-(45 + a * -20)));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(root * Mth.PI) * 20));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(root * Mth.PI) * -80));
        pose.mulPose(Axis.YP.rotationDegrees(45));
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player, item, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, true,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    // ------------------------------------------------------------------ the stumps

    /** The stumps, as a layer of the figure (and the slime on a limb growing back). */
    public static final class StumpLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        public StumpLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Limbs.State s = Limbs.of(e);
            boolean growing = REGROWING.containsKey(e.getId());
            if (s.severed == 0 && !growing || e.isInvisible()) return;
            int skin = Skin.of(e), blood = BloodFx.color(e);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
            M m = getParentModel();
            for (Limbs.Limb l : Limbs.Limb.values()) {
                ModelPart p = parts(m, l)[0];
                float t = growing && !s.severed(l) ? regrowing(e, l) : -1;
                if (t >= 0) {
                    Regrowth r = REGROWING.get(e.getId());
                    int goo = r == null ? 0xB890D8 : r.color[l.ordinal()];
                    float bud = BUD / (float) REGROW_TICKS;
                    if (t < bud) {                                              // the bud swelling at the stump
                        float k = t / bud;
                        pose.pushPose();
                        p.translateAndRotate(pose);
                        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
                        stump(pose, vc, l, skin, mix(skin, goo, 0.5f), light, overlay);
                        float w = 0.6f + 1.2f * k, len = 0.4f + 2.2f * k * k, y = l.arm() ? 2.6f : 3.4f;
                        float cx = l.arm() ? (l == Limbs.Limb.RIGHT_ARM ? -1 : 1) : 0;
                        box(pose, vc, (cx - w) / 16f, y / 16f, -w / 16f, (cx + w) / 16f, (y + len) / 16f, w / 16f, mix(skin, goo, 0.65f), light, overlay);
                        pose.popPose();
                    } else {                                                     // the new limb, wet with slime, drying as it fills out
                        float g = (t - bud) / (1 - bud);
                        float alpha = 0.7f * (1 - g) * (1 - g);
                        if (alpha > 0.02f) {
                            boolean was = p.visible;
                            p.visible = true;
                            p.render(pose, buffers.getBuffer(RenderType.entityTranslucent(WHITE)), light, overlay,
                                    (goo >> 16 & 255) / 255f, (goo >> 8 & 255) / 255f, (goo & 255) / 255f, alpha);
                            p.visible = was;
                        }
                    }
                    continue;
                }
                if (!s.severed(l)) continue;
                if (s.prosthetic(l)) {                                         // a prosthetic: the limb itself, in steel (CX-54)
                    boolean was = p.visible;
                    p.visible = true;
                    p.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PROSTHETIC)), light, overlay);
                    p.visible = was;
                    continue;
                }
                pose.pushPose();
                p.translateAndRotate(pose);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
                stump(pose, vc, l, skin, darker(skin), light, overlay);
                end(pose, vc, l, s.stump(l), skin, blood, light, overlay);
                pose.popPose();
            }
        }
    }

    /** The stump's box in the part's space (pixels): the shoulder and a short stub of the upper arm, or the top of the thigh. */
    private static float[] stumpBox(Limbs.Limb l) {
        boolean right = l == Limbs.Limb.RIGHT_ARM || l == Limbs.Limb.RIGHT_LEG;
        if (l.arm()) return new float[]{right ? -3 : -1, -2, -2, right ? 1 : 3, 2.6f, 2};
        return new float[]{-2, 0, -2, 2, 3.4f, 2};
    }

    /** The stump: skin on the sides and the top, {@code endColor} across the cut end (+y). */
    private static void stump(PoseStack pose, VertexConsumer vc, Limbs.Limb l, int skin, int endColor, int light, int overlay) {
        float[] b = stumpBox(l);
        float x0 = b[0] / 16f, y0 = b[1] / 16f, z0 = b[2] / 16f, x1 = b[3] / 16f, y1 = b[4] / 16f, z1 = b[5] / 16f;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, skin, light, overlay);   // front
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, skin, light, overlay);    // back
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, skin, light, overlay);   // sides
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, skin, light, overlay);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, skin, light, overlay);   // shoulder / hip
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, endColor, light, overlay);   // the end
    }

    /** What the end of a stump shows: how the wound is (the limbs rework). */
    private static void end(PoseStack pose, VertexConsumer vc, Limbs.Limb l, int state, int skin, int blood, int light, int overlay) {
        float[] b = stumpBox(l);
        float x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5], y = b[4];
        float cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        switch (state) {
            case Limbs.OPEN -> {                                                // raw: flesh in their blood's colour, the bone
                plate(pose, vc, x0 + 0.3f, z0 + 0.3f, x1 - 0.3f, z1 - 0.3f, y, 1, mix(blood, 0x000000, 0.25f), light, overlay);
                plate(pose, vc, x0 + 0.9f, z0 + 0.9f, x1 - 0.9f, z1 - 0.9f, y, 2, mix(blood, 0xFF9A8A, 0.18f), light, overlay);
                plate(pose, vc, cx - 0.6f, cz - 0.6f, cx + 0.6f, cz + 0.6f, y, 3, BONE, light, overlay);
            }
            case Limbs.BANDAGED -> {                                            // wrapped, the blood soaking through
                float i = -0.25f;
                box(pose, vc, (x0 + i) / 16f, (y - 1.6f) / 16f, (z0 + i) / 16f, (x1 - i) / 16f, (y + 0.35f) / 16f, (z1 - i) / 16f, BANDAGE, light, overlay);
                box(pose, vc, (x0 + i - 0.05f) / 16f, (y - 1.1f) / 16f, (z0 + i - 0.05f) / 16f, (x1 - i + 0.05f) / 16f, (y - 0.7f) / 16f, (z1 - i + 0.05f) / 16f,
                        mix(BANDAGE, 0x8A7A60, 0.25f), light, overlay);                // the turn of the wrap
                plate(pose, vc, x0 + 0.6f, z0 + 0.6f, x1 - 0.6f, z1 - 0.6f, y + 0.35f, 1, mix(BANDAGE, blood, 0.35f), light, overlay);
                plate(pose, vc, cx - 0.9f, cz - 0.7f, cx + 0.8f, cz + 0.9f, y + 0.35f, 2, mix(BANDAGE, blood, 0.7f), light, overlay);
            }
            case Limbs.SEARED -> {                                              // burnt shut
                plate(pose, vc, x0 + 0.25f, z0 + 0.25f, x1 - 0.25f, z1 - 0.25f, y, 1, SEAR, light, overlay);
                plate(pose, vc, x0 + 0.9f, z0 + 0.9f, x1 - 0.9f, z1 - 0.9f, y, 2, CHAR, light, overlay);
                plate(pose, vc, cx - 0.5f, cz - 0.9f, cx + 0.2f, cz + 0.3f, y, 3, mix(CHAR, 0x5A3A2A, 0.5f), light, overlay);
            }
            default -> {                                                        // healed over: skin, rounded, a pale seam
                int pale = ScarLayer.mix(skin, 0xF4E8E0, 0.4f);
                box(pose, vc, (x0 + 0.5f) / 16f, y / 16f, (z0 + 0.5f) / 16f, (x1 - 0.5f) / 16f, (y + 0.45f) / 16f, (z1 - 0.5f) / 16f, skin, light, overlay);
                float yy = y + 0.45f;
                plate(pose, vc, x0 + 0.5f, z0 + 0.5f, x1 - 0.5f, z1 - 0.5f, yy, 1, mix(skin, 0x000000, 0.08f), light, overlay);
                plate(pose, vc, x0 + 0.9f, cz - 0.22f, x1 - 0.9f, cz + 0.22f, yy, 2, pale, light, overlay);
                for (int k = -1; k <= 1; k++) {                                   // puckers drawn in toward the seam
                    float px = cx + k * 1.0f;
                    plate(pose, vc, px - 0.12f, cz - 1.1f, px + 0.12f, cz + 1.1f, yy, 3, mix(pale, skin, 0.4f), light, overlay);
                }
            }
        }
    }

    /** A flat piece on the stump's end, a hair proud of what's under it ({@code layer}). */
    private static void plate(PoseStack pose, VertexConsumer vc, float x0, float z0, float x1, float z1, float y, int layer, int color, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float yy = (y + layer * 0.02f) / 16f;
        quad(vc, m, n, x0 / 16f, yy, z0 / 16f, x1 / 16f, yy, z0 / 16f, x1 / 16f, yy, z1 / 16f, x0 / 16f, yy, z1 / 16f, 0, 1, 0, color, light, overlay);
    }

    /** A plain box (blocks), one colour. */
    private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, c, light, overlay);
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, c, light, overlay);
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, c, light, overlay);
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, c, light, overlay);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, c, light, overlay);
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, c, light, overlay);
    }

    private static int darker(int c) {
        return (int) (((c >> 16) & 255) * 0.6f) << 16 | (int) (((c >> 8) & 255) * 0.45f) << 8 | (int) ((c & 255) * 0.4f);
    }

    private static int mix(int a, int b, float t) {
        return ScarLayer.mix(a, b, t);
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
