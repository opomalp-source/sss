package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.network.KnockOutPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is knocked out, on the client (CX-51): for their pose (lying, crawling, carried), and, for you, the screen while
 * you are down (a dark red pulse at the edges, how long you have left, what can save you) and your controls (no
 * jumping, attacking or using things; crawling slowly if you are strong enough, otherwise not moving at all).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientKnockouts {
    /** A downed fighter as last heard: when they bleed out (client game time), of how long, and whether they can crawl. */
    public record Down(long until, int total, boolean crawl) {}

    private static final Map<Integer, Down> DOWN = new HashMap<>();

    private ClientKnockouts() {}

    public static void set(KnockOutPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.ticksLeft() < 0) DOWN.remove(m.entityId());
        else DOWN.put(m.entityId(), new Down((mc.level == null ? 0 : mc.level.getGameTime()) + m.ticksLeft(), m.total(), m.crawl()));
    }

    public static Down get(int entityId) {
        return DOWN.get(entityId);
    }

    public static boolean isDown(int entityId) {
        return DOWN.containsKey(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DOWN.clear();
            com.dbzenith.combat.Limbs.clearClient();
            return;
        }
        if (mc.player == null || !isDown(mc.player.getId())) return;
        if (!ClientCarry.isCarried(mc.player.getId()) && !ClientGrip.isGripped(mc.player.getId())) {   // you crawl head first, the way you look
            mc.player.yBodyRot = mc.player.getYRot();
            mc.player.yBodyRotO = mc.player.yRotO;
        }
        while (mc.options.keyAttack.consumeClick()) { }                       // no fighting, no using things while down
        while (mc.options.keyUse.consumeClick()) { }
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        Down d = DOWN.get(p.getId());
        if (d == null) return;
        var in = event.getInput();
        in.jumping = false;
        if (!d.crawl()) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
        } else {
            in.forwardImpulse *= 0.5f;                                          // dragging yourself along
            in.leftImpulse *= 0.3f;
        }
    }

    private static final java.lang.reflect.Method MOVE = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
            net.minecraft.client.Camera.class, "m_90568_", double.class, double.class, double.class);

    /** Down on the ground, the view drops with you: low over the body in third person, at the ground in first. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !isDown(mc.player.getId()) || ClientCarry.isCarried(mc.player.getId()) || ClientGrip.isGripped(mc.player.getId())) return;
        var cam = event.getCamera();
        boolean detached = cam.isDetached();
        double down = detached ? 0.95 : 1.25, back = detached ? 0.8 : 0;
        net.minecraft.world.phys.Vec3 from = cam.getPosition();
        net.minecraft.world.phys.Vec3 look = new net.minecraft.world.phys.Vec3(cam.getLookVector());
        net.minecraft.world.phys.Vec3 to = from.add(0, -down, 0).subtract(look.multiply(1, 0, 1).scale(back));
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) to = from.add(hit.getLocation().subtract(from).scale(0.8));
        net.minecraft.world.phys.Vec3 d = to.subtract(from);
        net.minecraft.world.phys.Vec3 f = new net.minecraft.world.phys.Vec3(cam.getLookVector()), u = new net.minecraft.world.phys.Vec3(cam.getUpVector()),
                l = new net.minecraft.world.phys.Vec3(cam.getLeftVector());
        try {
            MOVE.invoke(cam, d.dot(f), d.dot(u), d.dot(l));
        } catch (ReflectiveOperationException ignored) {
        }
        if (detached) event.setPitch(Math.max(event.getPitch(), 12));          // looking down on yourself a little
    }

    /** Your screen while you are down. */
    public static final class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            Down d = DOWN.get(mc.player.getId());
            if (d == null) return;
            float now = mc.level.getGameTime() + partialTick;
            float left = Math.max(0, d.until() - now), share = d.total() <= 0 ? 0 : left / d.total();
            // the edges darken and pulse like a slow heartbeat, closing in as time runs out
            float beat = 0.5f + 0.5f * Mth.sin(now * (0.18f + 0.25f * (1 - share)));
            int edge = (int) (90 + 110 * (1 - share) + 30 * beat);
            for (int i = 0; i < 24; i++) {
                int a = (int) (edge * (1 - i / 24f) * (1 - i / 24f));
                int w = (int) (width * 0.012f * (i + 1)), h = (int) (height * 0.012f * (i + 1));
                int c = Math.min(255, a) << 24 | 0x400000;
                g.fill(0, 0, width, Math.max(1, h / 4), c);
                g.fill(0, height - Math.max(1, h / 4), width, height, c);
                g.fill(0, 0, Math.max(1, w / 4), height, c);
                g.fill(width - Math.max(1, w / 4), 0, width, height, c);
            }
            var font = mc.font;
            Component title = Component.translatable("hud.dbzenith.knocked_out");
            g.pose().pushPose();
            g.pose().translate(width / 2f, height * 0.3f, 0);
            g.pose().scale(2.4f, 2.4f, 1);
            g.drawString(font, title, -font.width(title) / 2, 0, 0xFFFF4A3A, true);
            g.pose().popPose();
            int bw = 160, bx = width / 2 - bw / 2, by = (int) (height * 0.3f) + 30;      // the time left
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xA0000000);
            g.fill(bx, by, bx + (int) (bw * share), by + 5, share > 0.3f ? 0xFFE04030 : 0xFFFF2020);
            String secs = String.format("%.0f s", left / 20f);
            g.drawString(font, secs, width / 2 - font.width(secs) / 2, by + 9, 0xFFE0C0C0, true);
            int y = by + 24;
            boolean carried = ClientCarry.isCarried(mc.player.getId());
            ClientGrip.Session grip = ClientGrip.of(mc.player.getId());
            if (grip != null && grip.victim() == mc.player.getId()) {           // someone is finishing you: only a friend's blow can stop it
                var gripper = mc.level.getEntity(grip.gripper());
                Component warn = Component.translatable("hud.dbzenith.being_gripped", gripper == null ? Component.literal("?") : gripper.getDisplayName());
                g.pose().pushPose();
                g.pose().translate(width / 2f, y, 0);
                g.pose().scale(1.5f, 1.5f, 1);
                g.drawString(font, warn, -font.width(warn) / 2, 0, ((int) now / 4) % 2 == 0 ? 0xFFFF3A2A : 0xFFFFC0B0, true);
                g.pose().popPose();
                return;
            }
            if (carried) {
                var carrier = mc.level.getEntity(ClientCarry.carrierOf(mc.player.getId()));
                Component by2 = Component.translatable("hud.dbzenith.carried_by", carrier == null ? Component.literal("?") : carrier.getDisplayName());
                g.drawString(font, by2, width / 2 - font.width(by2) / 2, y, 0xFFF0D0A0, true);
                y += 13;
            }
            for (String key : new String[]{carried ? "hud.dbzenith.knocked_out_carried" : d.crawl() ? "hud.dbzenith.knocked_out_crawl" : null,
                    "hud.dbzenith.knocked_out_help", carried ? null : "hud.dbzenith.knocked_out_give_up"}) {
                if (key == null) continue;
                Component line = Component.translatable(key);
                g.drawString(font, line, width / 2 - font.width(line) / 2, y, 0xFFC8B0B0, true);
                y += 11;
            }
        }
    }
}
