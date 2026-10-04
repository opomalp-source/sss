package com.dbzenith.client;

import com.dbzenith.item.ScouterItem;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Scouter readout: a lock-on reticle and the power level of what you look at. */
public final class ScouterOverlay implements IGuiOverlay {
    private static final int GREEN = 0xFF40FF70;
    private static final int TINT = 0x2830FF60;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ScouterItem.wears(mc.player)) return;
        g.fill(width - 120, 0, width, height, TINT); // the lens over one eye

        LivingEntity target = ScouterItem.target(mc.player);
        if (target == null) return;
        long power = powerOf(target);
        int cx = width / 2;
        int cy = height / 2;
        int r = 14;
        g.fill(cx - r, cy - r, cx - r + 5, cy - r + 1, GREEN);
        g.fill(cx - r, cy - r, cx - r + 1, cy - r + 5, GREEN);
        g.fill(cx + r - 5, cy - r, cx + r, cy - r + 1, GREEN);
        g.fill(cx + r - 1, cy - r, cx + r, cy - r + 5, GREEN);
        g.fill(cx - r, cy + r - 1, cx - r + 5, cy + r, GREEN);
        g.fill(cx - r, cy + r - 5, cx - r + 1, cy + r, GREEN);
        g.fill(cx + r - 5, cy + r - 1, cx + r, cy + r, GREEN);
        g.fill(cx + r - 1, cy + r - 5, cx + r, cy + r, GREEN);

        Font font = mc.font;
        Component name = target.getDisplayName();
        Component reading = Component.translatable("hud.dbzenith.scouter_power", String.format("%,d", power));
        Component dist = Component.translatable("hud.dbzenith.scouter_distance", (int) mc.player.distanceTo(target));
        g.drawString(font, name, cx + r + 4, cy - r, GREEN);
        g.drawString(font, reading, cx + r + 4, cy - r + 10, GREEN);
        g.drawString(font, dist, cx + r + 4, cy - r + 20, 0xFF90C8A0);
    }

    /** Other players' power comes from their synced public state; creatures are estimated client-side. */
    private static long powerOf(LivingEntity e) {
        if (e instanceof Player p) {
            PublicStatePacket state = ClientPublicStates.get(p.getId());
            return state == null ? 0 : state.battlePower();
        }
        return ScouterItem.mobPower(e);
    }
}
