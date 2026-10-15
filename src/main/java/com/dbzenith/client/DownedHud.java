package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Grip;
import com.dbzenith.network.DownedActionPacket;
import com.dbzenith.network.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The downed, as others see them (the knocked-out rework):
 * <ul>
 *   <li>looking at one up close, what you can do: grip (the grip key, the direction you hold picks the finisher), carry,
 *       help up, or save them with a Senzu Bean; carrying someone, how to put them down or throw them;</li>
 *   <li>the grip key itself (it takes the place of swapping hands only while there is someone to grip), and throwing
 *       whoever you carry with the attack key.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class DownedHud {
    private DownedHud() {}

    /** The downed fighter you are looking at within reach, or null. */
    public static LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l && ClientKnockouts.isDown(l.getId())
                && l.distanceTo(mc.player) <= Grip.REACH + 0.5) return l;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();        // lying low, they are easy to miss: look near them
        LivingEntity best = null;
        double bestDot = 0.8;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || l == mc.player || !ClientKnockouts.isDown(l.getId()) || l.distanceTo(mc.player) > Grip.REACH + 0.5) continue;
            Vec3 to = l.position().add(0, 0.3, 0).subtract(eye).normalize();
            double dot = to.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = l;
            }
        }
        return best;
    }

    /** The direction held, for the finisher: 0 none or forward, 1 left, 2 right, 3 back, 4 jumping, 5 sneaking. */
    private static int input() {
        var o = Minecraft.getInstance().options;
        if (o.keyShift.isDown()) return 5;
        if (o.keyJump.isDown()) return 4;
        if (o.keyLeft.isDown()) return 1;
        if (o.keyRight.isDown()) return 2;
        if (o.keyDown.isDown()) return 3;
        return 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        boolean down = ClientKnockouts.isDown(mc.player.getId());
        LivingEntity t = down ? null : target();
        if (t != null && !ClientGrip.isGripping(mc.player.getId())) {
            while (mc.options.keySwapOffhand.consumeClick()) { }              // the grip key is F by default: no swapping hands now
        }
        while (ModKeys.GRIP.consumeClick()) {
            if (t != null && !ClientCarry.isCarrying(mc.player.getId()) && !ClientGrip.isGripping(mc.player.getId()))
                ModNetwork.CHANNEL.sendToServer(new DownedActionPacket(DownedActionPacket.GRIP, t.getId(), input()));
        }
        if (ClientCarry.isCarrying(mc.player.getId())) {                       // arms full: attacking throws them
            boolean thrown = false;
            while (mc.options.keyAttack.consumeClick()) thrown = true;
            if (thrown) ModNetwork.CHANNEL.sendToServer(new DownedActionPacket(DownedActionPacket.THROW, -1, 0));
            mc.options.keyAttack.setDown(false);
        }
        if (ClientGrip.isGripping(mc.player.getId())) {                        // in a finisher: nothing else
            while (mc.options.keyAttack.consumeClick()) { }
            while (mc.options.keyUse.consumeClick()) { }
        }
    }

    // ------------------------------------------------------------------ what you can do

    /** Under the crosshair: the actions open to you on a downed fighter, or while carrying one. */
    public static final class Prompt implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            Font font = mc.font;
            int y = height / 2 + 14;
            if (ClientGrip.isGripping(mc.player.getId())) return;
            if (ClientCarry.isCarrying(mc.player.getId())) {
                line(g, font, Component.translatable("hud.dbzenith.carry_prompt"), width, y);
                return;
            }
            if (ClientKnockouts.isDown(mc.player.getId())) return;
            LivingEntity t = target();
            if (t == null) return;
            line(g, font, Component.translatable("hud.dbzenith.grip_prompt", ModKeys.GRIP.getTranslatedKeyMessage()), width, y);
            line(g, font, Component.translatable("hud.dbzenith.grip_variants"), width, y + 11);
            boolean senzu = mc.player.getMainHandItem().is(com.dbzenith.registry.ModItems.SENZU_BEAN.get());
            line(g, font, Component.translatable(senzu ? "hud.dbzenith.save_prompt" : "hud.dbzenith.help_prompt"), width, y + 22);
        }

        private static void line(GuiGraphics g, Font font, Component text, int width, int y) {
            int w = font.width(text);
            g.fill(width / 2 - w / 2 - 3, y - 2, width / 2 + w / 2 + 3, y + 9, 0x90000000);
            g.drawString(font, text, width / 2 - w / 2, y, 0xFFF0E0D0, true);
        }
    }
}
