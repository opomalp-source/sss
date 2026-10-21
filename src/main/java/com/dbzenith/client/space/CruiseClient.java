package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.space.Cruise;
import com.dbzenith.network.CruisePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of an in-system cruise (the space rework, phase 5): while under way the sky is drawn from a point
 * moving along the course (the same easing as the server's), so planets swing past and grow; the server sets you down
 * where that point ends, and the view carries straight on. Sneak or the engage key disengages.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class CruiseClient {
    private CruiseClient() {}

    private static boolean active, cancelSent;
    private static Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
    private static int elapsed, duration = 1, heard;
    private static String name = "";

    public static boolean active() {
        return active;
    }

    public static String name() {
        return name;
    }

    /** Seconds left. */
    public static float remaining() {
        return Math.max(0, duration - elapsed) / 20f;
    }

    /** How fast the view is going now, as a share of the cruise's top speed (0..1). */
    public static float speed(float partial) {
        if (!active) return 0;
        double t = (elapsed + partial) / duration;
        return (float) Math.max(0, Math.min(1, 6 * t * (1 - t) / 1.5));
    }

    public static void receive(CruisePacket m) {
        if (!active) cancelSent = false;
        heard = 0;
        if (m.active()) {
            if (!active) SpaceAudio.cue(false);
            active = true;
            from = m.from();
            to = m.to();
            elapsed = m.elapsed();
            duration = Math.max(1, m.duration());
            name = m.name();
            return;
        }
        active = false;                                                          // set down: the real position now is where the view was
        SpaceClient.arrive(m.velocity());
        SpaceAudio.cue(true);
    }

    /** Where the sky is seen from: under way, the point along the course; otherwise where you are. */
    public static Vec3 view(Vec3 real, float partial) {
        if (!active) return real;
        Vec3 offset = real.subtract(from);                                       // your own small motion about the start (the camera's eye height)
        return from.add(to.subtract(from).scale(Cruise.ease((elapsed + partial) / duration))).add(offset);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active) return;
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        elapsed = Math.min(duration, elapsed + 1);
        if (++heard > 80) active = false;                                        // the server stopped talking (a lost packet): give the view back
        if (!cancelSent && mc.screen == null && mc.options.keyShift.isDown()) {
            cancelSent = true;
            com.dbzenith.network.ModNetwork.sendToServer(new CruisePacket.Engage(-1));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void input(MovementInputUpdateEvent e) {
        if (!active) return;
        var i = e.getInput();
        i.forwardImpulse = i.leftImpulse = 0;
        i.jumping = i.shiftKeyDown = i.up = i.down = i.left = i.right = false;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        active = false;
    }
}
