package com.dbzenith.client.ui;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces the vanilla hotbar with glass slots in gold frames between two golden clouds (same positions, so muscle
 * memory and other mods' overlays still line up), and hides vanilla hearts and armour when the Body bar shows them.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class HotbarSkin {
    private HotbarSkin() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (DBZConfig.CLIENT.hideVanillaHearts.get() && com.dbzenith.client.ClientPlayerData.hasData()
                && (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type() || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type())) {
            event.setCanceled(true);
            return;
        }
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type() || !DBZConfig.CLIENT.customHotbar.get()) return;
        if (mc.player.isSpectator() || mc.options.hideGui) return;
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), event.getPartialTick());
    }

    private static void draw(GuiGraphics g, Minecraft mc, int width, int height, float partial) {
        Player player = mc.player;
        int cx = width / 2;
        int top = height - 22;
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(0, 0, -90);
        // clouds first, so the slots sit over their inner edges
        ItemStack off = player.getOffhandItem();
        boolean offLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        int leftEdge = cx - 91 - (!off.isEmpty() && offLeft ? 29 : 0);
        int rightEdge = cx + 91 + (!off.isEmpty() && !offLeft ? 29 : 0);
        g.blit(DbzTheme.UI, leftEdge - 22, top, 200, 0, 28, 22, 256, 256);
        g.blit(DbzTheme.UI, rightEdge - 6, top, 200, 24, 28, 22, 256, 256);
        for (int i = 0; i < 9; i++) g.blit(DbzTheme.UI, cx - 91 + i * 20, top, 176, 0, 22, 22, 256, 256);
        if (!off.isEmpty()) {
            int ox = offLeft ? cx - 91 - 29 : cx + 91 + 7;
            g.blit(DbzTheme.UI, ox, top, 176, 0, 22, 22, 256, 256);
        }
        int sel = player.getInventory().selected;
        g.blit(DbzTheme.UI, cx - 92 + sel * 20, top - 1, 176, 24, 24, 24, 256, 256);
        g.pose().popPose();

        // items, as vanilla places them (with its pick-up "pop")
        int seed = 1;
        for (int i = 0; i < 9; i++) item(g, mc, player, player.getInventory().items.get(i), cx - 90 + i * 20 + 2, height - 19, partial, seed++);
        if (!off.isEmpty()) item(g, mc, player, off, offLeft ? cx - 91 - 26 : cx + 91 + 10, height - 19, partial, seed);
    }

    private static void item(GuiGraphics g, Minecraft mc, Player player, ItemStack stack, int x, int y, float partial, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - partial;
        if (pop > 0) {
            float f = 1 + pop / 5f;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1 / f, (f + 1) / 2, 1);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, x, y);
    }
}
