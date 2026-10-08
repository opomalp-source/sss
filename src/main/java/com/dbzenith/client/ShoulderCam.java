package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * The PvP camera (CX-20): turning PvP on switches to third person with the camera over your right shoulder
 * ({@code shoulderOffset} blocks to the right, {@code shoulderHeight} up), easing in; turning it off goes back to the view
 * you had. The camera never goes into a wall (it slides in as far as there is room). Client config {@code pvpCamera}.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ShoulderCam {
    private static final Method MOVE = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90568_", double.class, double.class, double.class);
    private static boolean was;
    private static CameraType before;
    private static float amount, amountO;

    private ShoulderCam() {}

    /** Whether the shoulder view is (mostly) in place: lock-on then needs no framing trick of its own. */
    public static boolean active() {
        return amount > 0.5f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            was = false;
            amount = amountO = 0;
            return;
        }
        boolean on = ClientPvp.on() && DBZConfig.CLIENT.pvpCamera.get();
        if (on && !was) {                                                    // PvP on: third person
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!on && was) {                                             // off: the view you had (unless you changed it)
            if (before != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(before);
            before = null;
        }
        was = on;
        amountO = amount;
        boolean shoulder = on && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
        amount = shoulder ? Math.min(1f, amount + 0.2f) : Math.max(0f, amount - 0.2f);
    }

    /** After the camera is placed (and before the world is drawn from it): slide it over the shoulder. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float a = Mth.lerp((float) event.getPartialTick(), amountO, amount);
        Camera cam = event.getCamera();
        Minecraft mc = Minecraft.getInstance();
        if (a <= 0.01f || !cam.isDetached() || mc.level == null) return;
        a = a * a * (3 - 2 * a);
        double right = DBZConfig.CLIENT.shoulderOffset.get() * a, up = DBZConfig.CLIENT.shoulderHeight.get() * a;
        Vector3f left = cam.getLeftVector();
        Vec3 from = cam.getPosition();
        Vec3 to = from.add(-left.x() * right, up, -left.z() * right);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam.getEntity()));
        double room = 1;
        if (hit.getType() != HitResult.Type.MISS) {                          // a wall: only as far as there is room
            double want = from.distanceTo(to);
            room = want < 1e-4 ? 0 : Math.max(0, hit.getLocation().distanceTo(from) - 0.2) / want;
        }
        try {
            MOVE.invoke(cam, 0.0, up * room, -right * room);                 // move(forward, up, left)
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
