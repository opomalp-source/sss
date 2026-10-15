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
    private static float lines, linesO;           // speed lines (CX-19e): a counter or a critical
    private static int linesColor = 0xFFFFFF, linesSeed;

    private CameraFx() {}

    public static void shake(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    /** A steady rumble: the shake held at least this high while it lasts (set every tick), 0..1. */
    public static void rumble(float level) {
        trauma = Math.max(trauma, Math.min(1f, level));
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
        alpha *= DBZConfig.CLIENT.screenFlash.get().floatValue();                // the player's flash strength, capped low for those sensitive to it
        if (calm()) alpha = Math.min(alpha, 0.2f);
        if (alpha <= 0) return;
        if (alpha >= flash) flashColor = rgb;
        flash = Math.max(flash, alpha);
    }

    /** Reduce flashing is on (accessibility, the ki attack rework, phase 7). */
    public static boolean calm() {
        return DBZConfig.CLIENT.reduceFlashing.get();
    }

    /**
     * Which strike a flickering effect is on at time {@code t} (ticks), re-struck every {@code every} ticks: lightning,
     * arcs. With reduce flashing on it changes four times as slowly.
     */
    public static long strike(float t, float every) {
        return (long) (t / (calm() ? every * 4 : every));
    }

    /** How much a flicker swings (1 normally, a quarter with reduce flashing on). */
    public static float flicker() {
        return calm() ? 0.25f : 1f;
    }

    private static float aberration, aberrationO;
    private static int impactFrame;

    /** Red and blue pulling apart toward the screen's edges for a moment, 0..1 (phase 9). */
    public static void aberrate(float amount) {
        if (!DBZConfig.CLIENT.chromaticAberration.get()) return;
        aberration = Math.max(aberration, Math.min(1f, amount));
    }

    public static float aberration(float partial) {
        return Mth.lerp(partial, aberrationO, aberration);
    }

    /** An anime impact frame: a frame or two of stark black and white (phase 9; never with reduce flashing). */
    public static void impactFrame() {
        if (DBZConfig.CLIENT.impactFrames.get() && !calm()) impactFrame = 3;
    }

    /** The impact frame now: full on the first tick, fading over the next two. */
    public static float impact() {
        return impactFrame >= 3 ? 1f : impactFrame == 2 ? 0.55f : impactFrame == 1 ? 0.2f : 0f;
    }

    private static float focus, focusO;

    /** Your own charge drawing the view in a little, 0..1 (set every tick while charging). */
    public static void focus(float amount) {
        focus = Math.max(focus, amount);
    }

    public static float flashAlpha(float partial) {
        return Mth.lerp(partial, flashO, flash);
    }

    public static int flashColor() {
        return flashColor;
    }

    /** Speed lines rushing in from the screen's edges for a moment (a counter, a critical), 0..1. */
    public static void speedLines(int rgb, float amount) {
        if (!DBZConfig.CLIENT.fovEffects.get()) return;
        if (amount >= lines) {
            linesColor = rgb;
            linesSeed++;
        }
        lines = Math.max(lines, Math.min(1f, amount));
    }

    public static float speedLinesAlpha(float partial) {
        return Mth.lerp(partial, linesO, lines);
    }

    public static int speedLinesColor() {
        return linesColor;
    }

    public static int speedLinesSeed() {
        return linesSeed;
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
        linesO = lines;
        lines = Math.max(0f, lines - 0.12f);
        focusO = focus;
        focus = Math.max(0f, focus - 0.08f);
        aberrationO = aberration;
        aberration = Math.max(0f, aberration - 0.07f);
        if (impactFrame > 0) impactFrame--;
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

    /** Dev automation: a fixed narrow field of view for close-up screenshots (0 = off). */
    public static float devZoom;
    /** Dev automation: the local player's body turned this many degrees from their view, for three-quarter screenshots. */
    public static float devTurn;

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (devZoom > 0) event.setFOV(devZoom);
        float p = (float) event.getPartialTick();
        float k = Mth.lerp(p, kickO, kick);
        float r = Mth.lerp(p, rushO, rush), fc = Mth.lerp(p, focusO, focus);
        if ((k < 0.01f && r < 0.01f && fc < 0.01f) || !DBZConfig.CLIENT.fovEffects.get()) return;
        event.setFOV(event.getFOV() * (1 - 0.08f * k + 0.09f * r - 0.06f * fc));   // a charge draws the view in a little (phase 7)
    }

    /** Smooth pseudo-noise in about [-1, 1]: a few unrelated sine waves. */
    private static float wave(float t, float seed) {
        return (Mth.sin(t * 2.3f + seed) + 0.6f * Mth.sin(t * 3.9f + seed * 2.3f) + 0.3f * Mth.sin(t * 7.1f + seed * 4.1f)) / 1.9f;
    }
}
