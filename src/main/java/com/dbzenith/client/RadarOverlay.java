package com.dbzenith.client;

import com.dbzenith.network.RadarPacket;
import com.dbzenith.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The Dragon Radar screen, top-right, while the radar is in either hand. Up = the direction you face. */
public final class RadarOverlay implements IGuiOverlay {
    private static final int R = 44;
    private static final int RANGE_BLOCKS = 1000; // display scale; the server filters by the configured range

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!mc.player.getMainHandItem().is(ModItems.DRAGON_RADAR.get()) && !mc.player.getOffhandItem().is(ModItems.DRAGON_RADAR.get())) return;

        int cx = width - R - 8;
        int cy = R + 8;
        // screen: dark green disc with grid
        for (int dy = -R; dy <= R; dy++) {
            int half = (int) Math.sqrt(R * R - dy * dy);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xE0103A18);
        }
        g.fill(cx - R, cy, cx + R, cy + 1, 0x8040FF60);
        g.fill(cx, cy - R, cx + 1, cy + R, 0x8040FF60);
        for (int ring = R / 3; ring < R; ring += R / 3) {
            for (int a = 0; a < 360; a += 6) {
                int px = cx + (int) (ring * Mth.cos(a * Mth.DEG_TO_RAD));
                int py = cy + (int) (ring * Mth.sin(a * Mth.DEG_TO_RAD));
                g.fill(px, py, px + 1, py + 1, 0x8040FF60);
            }
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF); // you

        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        double nearest = Double.MAX_VALUE;
        Font font = mc.font;
        for (RadarPacket.Blip b : ClientRadar.blips()) {
            double dx = b.x() + 0.5 - mc.player.getX();
            double dz = b.z() + 0.5 - mc.player.getZ();
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
            // rotate world offset so the facing direction points up
            double rx = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);
            double rz = dz * Mth.cos(yaw) - dx * Mth.sin(yaw);
            double scale = (double) R / RANGE_BLOCKS;
            int px = cx - (int) Math.round(rx * scale);
            int py = cy - (int) Math.round(rz * scale);
            double off = Math.hypot(px - cx, py - cy);
            if (off > R - 3) { // clamp to the rim
                px = cx + (int) ((px - cx) * (R - 3) / off);
                py = cy + (int) ((py - cy) * (R - 3) / off);
            }
            boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
            g.fill(px - 2, py - 2, px + 3, py + 3, blink ? 0xFFFFC020 : 0xFFFF8000);
            g.drawString(font, String.valueOf(b.star()), px + 3, py - 4, 0xFFFFE080, false);
        }
        Component label = nearest == Double.MAX_VALUE ? Component.translatable("hud.dbzenith.radar_none")
                : Component.translatable("hud.dbzenith.radar_nearest", (int) nearest);
        g.drawString(font, label, cx - font.width(label) / 2, cy + R + 3, 0xFF80FF90);
    }
}
