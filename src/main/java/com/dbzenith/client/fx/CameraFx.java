package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Camera feel: trauma-based shake (strength is trauma squared, so small hits barely move it and big ones really
 * rattle), a quick zoom-in punch for heavy blows, a widening rush for dashes, and a full-screen flash.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class CameraFx {
    private static float trauma;
    private static float kick, kickO;
    private static float rush, rushO;
    private static float flash, flashO;
    private static int flashColor = 0xFFFFFF;

    private CameraFx() {}

    public static void shake(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    /** Shake scaled down with the camera's distance from {@code at}: full strength up close, none beyond {@code radius}. */
    public static void shakeAt(Vec3 at, float amount, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        if (d < radius) shake(amount * (float) (1 - d / radius));
    }

    /** A zoom-in punch, 0..1. */
    public static void kick(float amount) {
        kick = Math.max(kick, amount);
    }

    /** A brief field-of-view widening for a dash. */
    public static void rush() {
        rush = 1f;
    }

    public static void flash(int rgb, float alpha) {
        if (alpha >= flash) flashColor = rgb;
        flash = Math.max(flash, alpha);
    }

    public static float flashAlpha(float partial) {
        return Mth.lerp(partial, flashO, flash);
    }

    public static int flashColor() {
        return flashColor;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        trauma = Math.max(0f, trauma - 0.045f);
        kickO = kick;
        kick *= 0.62f;
        rushO = rush;
        rush *= 0.8f;
        flashO = flash;
        flash = Math.max(0f, flash - 0.08f);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0) return;
        double strength = DBZConfig.CLIENT.screenShake.get();
        if (strength <= 0) return;
        float s = (float) (trauma * trauma * strength);
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + 4.5f * s * wave(t, 0.0f));
        event.setPitch(event.getPitch() + 3.5f * s * wave(t, 1.7f));
        event.setRoll(event.getRoll() + 5.0f * s * wave(t, 3.1f));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        float p = (float) event.getPartialTick();
        float k = Mth.lerp(p, kickO, kick);
        float r = Mth.lerp(p, rushO, rush);
        if ((k < 0.01f && r < 0.01f) || !DBZConfig.CLIENT.fovEffects.get()) return;
        event.setFOV(event.getFOV() * (1 - 0.08f * k + 0.09f * r));
    }

    /** Smooth pseudo-noise in about [-1, 1]: a few unrelated sine waves. */
    private static float wave(float t, float seed) {
        return (Mth.sin(t * 2.3f + seed) + 0.6f * Mth.sin(t * 3.9f + seed * 2.3f) + 0.3f * Mth.sin(t * 7.1f + seed * 4.1f)) / 1.9f;
    }
}
