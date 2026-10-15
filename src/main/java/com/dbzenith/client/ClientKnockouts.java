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
        if (mc.player == null) return;
        if (!isDown(mc.player.getId())) {
            if (crawlingSelf(mc)) {                                           // crawling on a lost leg: head first, the way you look
                mc.player.yBodyRot = mc.player.getYRot();
                mc.player.yBodyRotO = mc.player.yRotO;
            }
            return;
        }
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

    /** You are crawling on a lost leg (the knocked-out rework). */
    private static boolean crawlingSelf(Minecraft mc) {
        return mc.player != null && com.dbzenith.client.anim.AnimController.crawling(mc.player, ClientPublicStates.get(mc.player.getId())) && mc.player.onGround();
    }

    private static final java.lang.reflect.Method MOVE = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
            net.minecraft.client.Camera.class, "m_90568_", double.class, double.class, double.class);

    /** Down on the ground, the view drops with you: low over the body in third person, at the ground in first. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ClientCarry.isCarried(mc.player.getId()) || ClientGrip.isGripped(mc.player.getId())) return;
        if (!isDown(mc.player.getId()) && !crawlingSelf(mc)) return;                // down, or crawling on a lost leg
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

    /**
     * Your screen while you are down: no words, no timer (the user: "remove that big knocked text in the middle or any
     * other indicators"). Only the edges darken a little and pulse with a slow heartbeat, a touch faster as time runs out.
     */
    public static final class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            Down d = DOWN.get(mc.player.getId());
            if (d == null) return;
            float now = mc.level.getGameTime() + partialTick;
            float left = Math.max(0, d.until() - now), share = d.total() <= 0 ? 0 : left / d.total();
            float beat = 0.5f + 0.5f * Mth.sin(now * (0.16f + 0.14f * (1 - share)));
            int edge = (int) (40 + 40 * (1 - share) + 18 * beat);
            for (int i = 0; i < 20; i++) {
                int a = (int) (edge * (1 - i / 20f) * (1 - i / 20f));
                int w = (int) (width * 0.01f * (i + 1)), h = (int) (height * 0.01f * (i + 1));
                int c = Math.min(255, a) << 24 | 0x180404;
                g.fill(0, 0, width, Math.max(1, h / 4), c);
                g.fill(0, height - Math.max(1, h / 4), width, height, c);
                g.fill(0, 0, Math.max(1, w / 4), height, c);
                g.fill(width - Math.max(1, w / 4), 0, width, height, c);
            }
        }
    }
}
