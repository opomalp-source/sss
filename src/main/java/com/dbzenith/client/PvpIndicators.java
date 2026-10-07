package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Who is in PvP mode (CX-19): a red badge at the top of your own screen while you are, and a red crossed-swords mark
 * before the name of every player who is.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class PvpIndicators {
    private PvpIndicators() {}

    public static boolean inPvp(Player p) {
        PublicStatePacket s = ClientPublicStates.get(p.getId());
        return s != null && s.has(PublicStatePacket.PVP);
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player p && inPvp(p)) {
            event.setContent(Component.literal("⚔ ").withStyle(ChatFormatting.RED).append(event.getContent()));
        }
    }

    /** The badge: a small red pill at the top centre. */
    public static final class Badge implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || !inPvp(mc.player)) return;
            Component text = Component.translatable("hud.dbzenith.pvp");
            int w = mc.font.width(text) + 22, x = width / 2 - w / 2, y = 3;
            float pulse = 0.75f + 0.25f * (float) Math.sin((mc.player.tickCount + partialTick) * 0.15);
            Ui.round(g, x, y, w, 13, 4, 0xD0400A0A);
            Ui.outline(g, x, y, w, 13, 4, ((int) (pulse * 255) << 24) | 0xFF3030);
            g.drawString(mc.font, "⚔", x + 5, y + 3, 0xFFFF5A4A, true);
            g.drawString(mc.font, text, x + 16, y + 3, 0xFFFFE8E0, true);
        }
    }
}
