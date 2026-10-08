package com.dbzenith.client.screen;

import com.dbzenith.client.motion.Clip;
import com.dbzenith.client.motion.MotionData;
import com.dbzenith.client.motion.MotionEngine;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.style.StyleSlot;
import com.dbzenith.style.Styles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev (CX-20 styles): one figure drawn once per clip, frozen at a phase and turned to an angle, so poses can be judged
 * side by side. Opened by a devshot:
 * <ul>
 *   <li>{@code posesheet_<slot>_<phase%>[_<turn>]}: the default and every style's clip for an animation slot;</li>
 *   <li>{@code posesheet_clips_<prefix>_<phase%>[_<turn>]}: every clip whose id starts with the prefix;</li>
 *   <li>{@code posesheetnpc_...}: the same on the nearest NPC instead of the player.</li>
 * </ul>
 * Flight slots tip the figure as the engine does in flight.
 */
public class PoseSheetScreen extends Screen {
    /** The devshot that opened it. */
    public final String key;
    private final List<String[]> cells;
    private final String heading;
    private final float phase, turn, pitch;
    private final LivingEntity subject;

    public PoseSheetScreen(String key, String heading, List<String[]> cells, float phase, float turn, float pitch, LivingEntity subject) {
        super(Component.literal("Pose sheet"));
        this.key = key;
        this.heading = heading;
        this.cells = cells;
        this.phase = phase;
        this.turn = turn;
        this.pitch = pitch;
        this.subject = subject;
    }

    /** The default clip for a slot, then each style's (labelled by style). */
    public static List<String[]> slotCells(StyleSlot slot) {
        List<String[]> out = new ArrayList<>();
        String def = switch (slot) {
            case FIGHT_STANCE -> "fight_idle";
            case FIGHT_STEPS -> "fight_walk";
            case FLIGHT -> "cruise";
            case FAST_FLIGHT -> "fast";
            default -> slot.state;
        };
        out.add(new String[]{"Default", def});
        for (Styles.Style s : Styles.all()) {
            String clip = s.clips().get(slot);
            if (clip != null) out.add(new String[]{Component.translatable(s.nameKey()).getString(), clip});
        }
        return out;
    }

    /** Every clip whose id starts with {@code prefix}, labelled by id. */
    public static List<String[]> prefixCells(String prefix) {
        List<String[]> out = new ArrayList<>();
        for (String id : new java.util.TreeSet<>(MotionData.clipIds())) if (id.startsWith(prefix)) out.add(new String[]{id, id});
        return out;
    }

    public static float pitchFor(StyleSlot slot) {
        return slot == StyleSlot.FLIGHT ? 62f : slot == StyleSlot.FAST_FLIGHT ? 80f : 0f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF4A5A6E);
        Ui.text(g, font, heading + " @ " + (int) (phase * 100) + "%", 6, 4, 0xFFFFFFFF, 0.8f);
        LivingEntity e = subject != null && subject.isAlive() ? subject : minecraft.player;
        if (e == null || cells.isEmpty()) return;
        int cols = Math.min(6, cells.size()), rows = (cells.size() + cols - 1) / cols;
        int cw = width / cols, ch = (height - 14) / rows;
        float size = e.getBbHeight() / 1.8f;
        int scale = (int) (Math.min(cw * 1.1f, ch) * 0.42f / Math.max(0.6f, size));
        MotionEngine.devEntity = e == minecraft.player ? null : e;
        try {
            for (int i = 0; i < cells.size(); i++) {
                Clip c = MotionData.clip(cells.get(i)[1]);
                int cx = (i % cols) * cw + cw / 2, cy = 14 + (i / cols) * ch;
                Ui.centered(g, font, Component.literal(cells.get(i)[0]), cx, cy + 2, c == null ? 0xFFFF6060 : 0xFFFFFFFF, 0.6f);
                if (c == null) continue;
                MotionEngine.devClip = c;
                MotionEngine.devPhase = phase;
                MotionEngine.devPitch = pitch;
                figure(g, cx, cy + ch - (int) (ch * 0.1f), scale, turn, e);
            }
        } finally {
            MotionEngine.devClip = null;
            MotionEngine.devEntity = null;
        }
    }

    /** Draws a figure at a fixed turn (degrees, 0 facing the viewer), like the inventory does but at any angle. */
    static void figure(GuiGraphics g, int x, int y, int scale, float turn, LivingEntity e) {
        float by = e.yBodyRot, byo = e.yBodyRotO, yr = e.getYRot(), yro = e.yRotO, xr = e.getXRot(), xro = e.xRotO, hy = e.yHeadRotO, h = e.yHeadRot;
        e.yBodyRot = e.yBodyRotO = 180f + turn;
        e.yRotO = 180f + turn;
        e.xRotO = 0;
        e.setYRot(180f + turn);
        e.setXRot(0);
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf cam = new org.joml.Quaternionf().rotateX(-8f * (float) Math.PI / 180f);
        pose.mul(cam);
        InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, cam, e);
        e.yBodyRot = by;
        e.yBodyRotO = byo;
        e.yRotO = yro;
        e.xRotO = xro;
        e.setYRot(yr);
        e.setXRot(xr);
        e.yHeadRotO = hy;
        e.yHeadRot = h;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
