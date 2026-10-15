package com.dbzenith.client;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.transform.GreatApe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Staring at the full moon (the Great Ape rework): a red pulse at the screen's edges with each heartbeat, deeper and
 * quicker the longer you look, until the change takes you. Smoothly fades when you look away. No words.
 */
public final class MoonGazeOverlay implements IGuiOverlay {
    private float shown;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        int gaze = ModCapabilities.get(mc.player).map(d -> d.getMoonGaze()).orElse(0);
        float want = Math.min(1f, gaze / (float) GreatApe.GAZE_SECONDS);
        shown += (want - shown) * 0.05f;
        if (shown < 0.01f) return;
        float now = mc.level.getGameTime() + partialTick;
        float rate = 0.25f + 0.35f * shown;                                       // the heart quickens
        float beat = (float) Math.pow(Math.max(0, Mth.sin(now * rate)), 6);       // a sharp thump, then quiet
        int edge = (int) (70 * shown + 90 * shown * beat);
        for (int i = 0; i < 24; i++) {
            float f = 1 - i / 24f;
            int a = Math.min(255, (int) (edge * f * f));
            if (a <= 0) continue;
            int w = (int) (width * 0.012f * (i + 1)), h = (int) (height * 0.012f * (i + 1));
            int c = a << 24 | 0x6A0606;
            g.fill(0, 0, width, Math.max(1, h / 4), c);
            g.fill(0, height - Math.max(1, h / 4), width, height, c);
            g.fill(0, 0, Math.max(1, w / 4), height, c);
            g.fill(width - Math.max(1, w / 4), 0, width, height, c);
        }
    }
}
