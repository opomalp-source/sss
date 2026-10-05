package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The aura: a ring of animated flame tongues leaning in over the head (a saturated outer body, an additive white-hot
 * core and, while powering up, tall licks), a glow on the ground, a soft halo and, for lightning forms, crackling
 * bolts. Powering up roars; a held transformation burns steadily; god-ki forms burn calm and tight. Seen by everyone,
 * including yourself in third person.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AuraRenderer {
    private static final ResourceLocation FLAME = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/aura_flame.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");
    private static final ResourceLocation STREAK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fx_streak.png");
    private static final int FRAMES = 8;

    private AuraRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (com.dbzenith.client.ui.PortraitRenderer.isDrawing()) return;
        Player player = event.getEntity();
        if (player.isInvisible() || player.isSpectator()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        boolean powering = state.powering();
        Form form = Forms.byId(state.form());
        boolean kaioken = state.has(PublicStatePacket.KAIOKEN);
        boolean held = !form.isBase() || state.overdrive() > 0 || kaioken;
        if (!powering && !held) return;
        if (form == Forms.GREAT_APE) return; // the ape has no ki aura, only its size

        float partial = event.getPartialTick();
        float t = player.tickCount + partial;
        boolean calm = form.calmAura() && !kaioken;                          // Kaioken roars, even on a god form
        float release = 0.75f + 0.25f * Mth.clamp(state.release() / 100f, 0f, 1.5f);
        float tier = 1f + 0.07f * Math.min(5, form.tier());
        float height = (powering ? 2.5f : 2.05f) * release * tier * (calm ? 0.88f : 1f);
        float strength = powering ? 1f : 0.72f;
        int color = state.auraColor();
        int core = FxDraw.mix(color, 0xFFFFFF, 0.55f);

        // camera, relative to the player's feet, in the (possibly form-scaled) model space
        Minecraft mc = Minecraft.getInstance();
        Vec3 feet = new Vec3(Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));
        float scale = com.dbzenith.transform.GreatApe.scaleOf(player);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition().subtract(feet).scale(1 / scale);
        Vec3 view = new Vec3(0, 0.9, 0).subtract(eye).normalize();
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = new Vec3(0, 1, 0).subtract(view.scale(view.y * 0.6)).normalize();  // lean towards a high camera

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        long seed = player.getId() * 341873128712L;

        // halo behind everything
        VertexConsumer halo = buffers.getBuffer(FxRenderTypes.additive(GLOW));
        FxDraw.billboard(pose, halo, mc.getEntityRenderDispatcher().cameraOrientation(), 0, 1.0, 0,
                (calm ? 3.0f : 3.6f) * release, 0, 0, 0, 1, 1, color, (int) (120 * strength));

        // ground glow while powering up on the ground
        if (powering && player.onGround()) {
            VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(RING));
            FxDraw.plane(pose, ring, new Vec3(0, 0.04, 0), new Vec3(0, 1, 0), 1.15f + 0.08f * Mth.sin(t * 0.7f), t * 0.05f,
                    color, 110, LightTexture.FULL_BRIGHT);
        }

        float speed = calm ? 0.22f : powering ? 0.65f : 0.4f;
        int detail = com.dbzenith.config.DBZConfig.CLIENT.auraDetail.get();   // 0 low, 1 normal, 2 high
        float more = detail == 0 ? 0.6f : detail == 2 ? 1.4f : 1f;
        // outer body: ordinary blending keeps the colour saturated in daylight
        VertexConsumer outer = buffers.getBuffer(FxRenderTypes.soft(FLAME));
        tongues(pose, outer, Math.round((calm ? 12 : 14) * more), calm ? 0.42f : 0.56f, calm ? 1.05f : 1.3f, height, -0.25f, t, speed, seed,
                right, up, color, (int) ((calm ? 150 : 175) * strength), calm ? 0.05f : 0.14f);
        // white-hot core
        VertexConsumer inner = buffers.getBuffer(FxRenderTypes.additive(FLAME));
        tongues(pose, inner, Math.round(9 * more), 0.3f, 0.95f, height * 0.82f, -0.15f, t, speed * 1.2f, seed + 7, right, up, core,
                (int) ((calm ? 170 : 150) * strength), 0.08f);
        // tall licks breaking off the top while powering up
        if (powering && !calm && detail > 0) {
            tongues(pose, inner, 6, 0.45f, 0.6f, height * 1.3f, 0.1f, t, speed * 1.5f, seed + 13, right, up, color, 160, 0.25f);
        }

        if (form.lightning()) lightning(pose, buffers, player, t, eye, color);
    }

    /** {@code n} flame tongues around the body, their tips drawn in towards the axis above the head. */
    private static void tongues(PoseStack pose, VertexConsumer vc, int n, float radius, float width, float height, float baseY,
                                float t, float speed, long seed, Vec3 right, Vec3 up, int color, int alpha, float wobble) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        for (int i = 0; i < n; i++) {
            float phase = (float) (((seed >>> (i % 32)) & 1023) / 1023.0 * Mth.TWO_PI) + i * 2.1f;
            float ang = i * Mth.TWO_PI / n + t * 0.015f;
            float h = height * (1 + wobble * Mth.sin(t * 0.37f + phase) + 0.5f * wobble * Mth.sin(t * 0.91f + phase * 1.7f));
            float w = width * (0.9f + 0.1f * Mth.sin(t * 0.5f + phase));
            Vec3 base = new Vec3(Mth.cos(ang) * radius, baseY, Mth.sin(ang) * radius);
            Vec3 tip = new Vec3(base.x * 0.3, baseY, base.z * 0.3).add(up.scale(h))
                    .add(right.scale(0.08 * Mth.sin(t * 0.21f + phase)));
            Vec3 half = right.scale(w / 2);
            int frame = Math.floorMod((int) (t * speed + phase * 3), FRAMES);
            float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
            corner(vc, m, nm, base.subtract(half), u0, 1, color, alpha, light);
            corner(vc, m, nm, base.add(half), u1, 1, color, alpha, light);
            corner(vc, m, nm, tip.add(half), u1, 0, color, alpha, light);
            corner(vc, m, nm, tip.subtract(half), u0, 0, color, alpha, light);
        }
    }

    /** Two or three jagged bolts crawling over the body, re-struck every other tick. */
    private static void lightning(PoseStack pose, MultiBufferSource buffers, Player player, float t, Vec3 eye, int color) {
        VertexConsumer vc = buffers.getBuffer(FxRenderTypes.additive(STREAK));
        int glow = FxDraw.mix(color, 0xBFE6FF, 0.5f);
        int strike = (int) t / 2;
        for (int k = 0; k < 3; k++) {
            RandomSource rnd = RandomSource.create(player.getId() * 7919L + strike * 131L + k * 31L);
            if (rnd.nextFloat() > 0.6f) continue;
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 p = new Vec3(Math.cos(a) * 0.38, 0.3 + rnd.nextDouble() * 1.4, Math.sin(a) * 0.38);
            for (int s = 0; s < 6; s++) {
                a += (rnd.nextDouble() - 0.5) * 1.2;
                Vec3 next = new Vec3(Math.cos(a) * (0.3 + rnd.nextDouble() * 0.2), p.y + (rnd.nextDouble() - 0.45) * 0.4,
                        Math.sin(a) * (0.3 + rnd.nextDouble() * 0.2));
                FxDraw.ribbon(pose, vc, p, next, eye, 0.2f, glow, 140);
                FxDraw.ribbon(pose, vc, p, next, eye, 0.06f, 0xFFFFFF, 255);
                p = next;
            }
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int rgb, int alpha, int light) {
        FxDraw.vertex(vc, m, n, (float) p.x, (float) p.y, (float) p.z, u, v, rgb, alpha, light);
    }
}
