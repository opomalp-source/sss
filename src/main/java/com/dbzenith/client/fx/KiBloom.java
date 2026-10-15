package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Ki bloom (CX-45). Everything that adds light (auras' glow layers, blasts, beams, sparks, rings, flashes, lightning)
 * is drawn into a target of its own instead of onto the picture, with the world's depth copied in first so walls still
 * hide it. At the end of the world pass that light is added back as it was (the same picture as before, since adding
 * is adding) and once more blurred at half size, so it spills soft light onto everything around it. Strength: client
 * config {@code kiBloom} (0 off). Off with Fabulous graphics or a shader pack, where everything draws as before.
 */
public final class KiBloom {
    private static ShaderInstance blur, add, post;
    private static TextureTarget glow, a, b, scene;
    private static final ResourceLocation NOISE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_noise.png");
    private static boolean used, inside;
    /** Inside the world pass: light drawn outside it (the HUD portrait) goes straight onto the picture, not into the bloom. */
    private static boolean inLevel;

    private KiBloom() {}

    @Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "bloom_blur"),
                    DefaultVertexFormat.POSITION_TEX), s -> blur = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "bloom_add"),
                    DefaultVertexFormat.POSITION_TEX), s -> add = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "ki_post"),
                    DefaultVertexFormat.POSITION_TEX), s -> post = s);
        }
    }

    /** Whether light goes through the bloom this frame. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return blur != null && add != null && DBZConfig.CLIENT.kiBloom.get() > 0.01
                && mc.options.graphicsMode().get() != GraphicsStatus.FABULOUS
                && !com.dbzenith.client.aura.AuraShaders.packOn();
    }

    /** Light is about to be drawn: into the glow target (made ready on the first use each frame). */
    public static void begin() {
        if (!active() || !inLevel) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (glow == null || glow.width != main.width || glow.height != main.height) resize(main);
        if (!used) {
            glow.setClearColor(0, 0, 0, 0);
            glow.clear(Minecraft.ON_OSX);
            glow.copyDepthFrom(main);
            used = true;
        }
        glow.bindWrite(false);
        inside = true;
    }

    /** Back to drawing onto the picture. */
    public static void end() {
        if (!inside) return;
        inside = false;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    private static void resize(RenderTarget main) {
        if (glow != null) { glow.destroyBuffers(); a.destroyBuffers(); b.destroyBuffers(); scene.destroyBuffers(); }
        glow = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        int hw = Math.max(1, main.width / 2), hh = Math.max(1, main.height / 2);
        a = new TextureTarget(hw, hh, false, Minecraft.ON_OSX);
        b = new TextureTarget(hw, hh, false, Minecraft.ON_OSX);
        for (TextureTarget t : new TextureTarget[]{glow, a, b, scene}) t.setFilterMode(GL11.GL_LINEAR);
    }

    @Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Composite {
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) inLevel = true;
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
            inLevel = false;
            float pt = event.getPartialTick();
            float aberration = CameraFx.aberration(pt), impact = CameraFx.impact();
            boolean wasUsed = used;
            boolean postNow = post != null && active() && (wasUsed && DBZConfig.CLIENT.heatHaze.get() > 0.01 || aberration > 0.01f || impact > 0.01f);
            if (!wasUsed && !postNow) return;
            used = false;
            end();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (glow == null || glow.width != main.width || glow.height != main.height) {
                if (!wasUsed) resize(main);
                else return;
            }
            float strength = DBZConfig.CLIENT.kiBloom.get().floatValue();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();

            if (wasUsed) {                                                   // the blur, at half size: across, down, then once more wider
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                pass(blur, glow, a, 1.5f / glow.width, 0);
                pass(blur, a, b, 0, 1.5f / glow.height);
                pass(blur, b, a, 3f / glow.width, 0);
                pass(blur, a, b, 0, 3f / glow.height);
            }
            if (postNow) {                                                   // the ki attack rework, phase 9: shimmer, colour split, impact frame
                scene.setClearColor(0, 0, 0, 0);
                scene.clear(Minecraft.ON_OSX);
                scene.bindWrite(true);
                draw(add, main, 1f, 0, 0);                                  // a copy of the picture to read from
                main.bindWrite(true);
                Minecraft mc = Minecraft.getInstance();
                post.setSampler("GlowSampler", b.getColorTextureId());
                post.setSampler("NoiseSampler", mc.getTextureManager().getTexture(NOISE).getId());
                set(post, "Time", ((mc.level == null ? 0 : mc.level.getGameTime() % 24000) + pt) / 20f);
                set(post, "Haze", wasUsed && KiParticles.quality() > 0 ? DBZConfig.CLIENT.heatHaze.get().floatValue() : 0f);   // none at low quality (phase 10)
                set(post, "Aberration", aberration);
                set(post, "Impact", impact);
                draw(post, scene, 1f, 0, 0);
                for (int unit = 2; unit >= 0; unit--) {                          // leave the texture units as found: the HUD draws next
                    RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
                    RenderSystem.bindTexture(0);
                }
            }

            if (wasUsed) {                                                   // onto the picture: the light itself, then its glow
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                main.bindWrite(true);
                draw(add, glow, 1f, 0, 0);
                draw(add, b, 0.7f * strength, 0, 0);
            }

            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void set(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }

    private static void pass(ShaderInstance shader, RenderTarget from, TextureTarget to, float dx, float dy) {
        to.setClearColor(0, 0, 0, 0);
        to.clear(Minecraft.ON_OSX);
        to.bindWrite(true);
        draw(shader, from, 1f, dx, dy);
    }

    private static void draw(ShaderInstance shader, RenderTarget from, float gain, float dx, float dy) {
        shader.setSampler("DiffuseSampler", from.getColorTextureId());
        if (shader.getUniform("Direction") != null) shader.getUniform("Direction").set(dx, dy);
        if (shader.getUniform("Gain") != null) shader.getUniform("Gain").set(gain);
        RenderSystem.setShader(() -> shader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(-1, -1, 0).uv(0, 0).endVertex();
        bb.vertex(1, -1, 0).uv(1, 0).endVertex();
        bb.vertex(1, 1, 0).uv(1, 1).endVertex();
        bb.vertex(-1, 1, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }
}
