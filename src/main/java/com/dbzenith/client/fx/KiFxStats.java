package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ki attack effects, counted and budgeted per frame (the ki attack rework, phase 10). Every ki ball asks
 * {@link #blast} whether it may draw in full: far away, or past the frame's budget for its quality, it draws a cheap
 * version (a glow and a plain core). The counts of the last frame show on the debug overlay ({@code showDebugOverlay}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KiFxStats {
    private static int full, cheap, beams;
    private static int lastFull, lastCheap, lastBeams;

    private KiFxStats() {}

    /** How many ki balls may draw in full in one frame, by quality. */
    public static int budget() {
        return switch (KiParticles.quality()) {
            case 0 -> 24;
            case 1 -> 48;
            case 2 -> 96;
            default -> 160;
        };
    }

    /** Beyond this many blocks a ki ball is drawn the cheap way. */
    public static double farDistance() {
        return KiParticles.quality() >= 2 ? 64 : 40;
    }

    /** May this ki ball, {@code distance} blocks from the camera, draw in full this frame? (Counts it either way.) */
    public static boolean blast(double distance) {
        if (distance < farDistance() && full < budget()) {
            full++;
            return true;
        }
        cheap++;
        return false;
    }

    public static void beam() {
        beams++;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        lastFull = full;
        lastCheap = cheap;
        lastBeams = beams;
        full = cheap = beams = 0;
    }

    /** The last frame, for the debug overlay. */
    public static String summary() {
        return String.format("Ki fx: balls %d full + %d cheap (budget %d), beams %d, particles %d/%d, quality %d",
                lastFull, lastCheap, budget(), lastBeams, KiParticles.live(), KiParticles.cap(), KiParticles.quality());
    }
}
