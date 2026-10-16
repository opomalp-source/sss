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
 * The shoulder camera (CX-20, always on since CX-37): in third person behind, the camera sits over your right shoulder,
 * walking, flying and fighting alike ({@code shoulderCamera}); you start in third person ({@code startThirdPerson}).
 * Turning PvP on also switches to third person with the camera over your right shoulder
 * ({@code shoulderOffset} blocks to the right, {@code shoulderHeight} up), easing in; turning it off goes back to the view
 * you had. The camera never goes into a wall (it slides in as far as there is room). Client config {@code pvpCamera}.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ShoulderCam {
    private static final Method MOVE = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90568_", double.class, double.class, double.class);
    private static final Method POSITION = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90584_", double.class, double.class, double.class);
    private static boolean was, joined;
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
            joined = false;
            amount = amountO = 0;
            return;
        }
        if (!joined) {                                                       // just joined a world: start in third person
            joined = true;
            if (DBZConfig.CLIENT.startThirdPerson.get() && mc.options.getCameraType().isFirstPerson() && !Boolean.getBoolean("dbzenith.devAutomation")) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
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
        boolean shoulder = (on || DBZConfig.CLIENT.shoulderCamera.get()) && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;   // over the shoulder whenever behind
        amount = shoulder ? Math.min(1f, amount + 0.2f) : Math.max(0f, amount - 0.2f);
    }

    /**
     * A giant form (the Great Ape) seen from outside: the camera stands back in proportion, so the whole ape is in view
     * (as far as there is room behind it).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGiantCamera(ViewportEvent.ComputeCameraAngles event) {
        Camera cam = event.getCamera();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(cam.getEntity() instanceof net.minecraft.world.entity.player.Player p)) return;
        float target = com.dbzenith.transform.GreatApe.scaleOf(p);
        if (target <= 1.01f) return;
        float s = com.dbzenith.client.render.ApeTransformation.scale(p, (float) event.getPartialTick());
        float age = com.dbzenith.client.render.ApeTransformation.age(p, (float) event.getPartialTick());
        float roarRoom = com.dbzenith.client.render.ApeLook.is(p)
                ? com.dbzenith.transform.ApeChange.phase(age, 80, com.dbzenith.transform.ApeChange.ROAR) * (1 - com.dbzenith.transform.ApeChange.phase(age, 174, com.dbzenith.transform.ApeChange.CAMERA_END)) : 0;
        double y = Mth.lerp(event.getPartialTick(), p.yo, p.getY());
        double x = Mth.lerp(event.getPartialTick(), p.xo, p.getX());
        double z = Mth.lerp(event.getPartialTick(), p.zo, p.getZ());
        // Pivot both F5 views at the eyes, following visible growth instead of the server's instant size change.
        double eye = p.getEyeHeight() * s / target;
        Vec3 from = new Vec3(x, y + eye, z);
        if (!cam.isDetached()) {
            position(cam, from);
            return;
        }
        double back = 4 + (s - 1) * (2.8 + 1.1 * roarRoom);
        Vector3f look = cam.getLookVector();
        Vec3 to = from.subtract(look.x() * back, look.y() * back, look.z() * back);
        double room = back;
        // Check the camera's corners as well as its centre, so walls cannot clip through the near plane.
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? -0.2 : 0.2, (i & 2) == 0 ? -0.2 : 0.2, (i & 4) == 0 ? -0.2 : 0.2);
            BlockHitResult hit = mc.level.clip(new ClipContext(from.add(corner), to.add(corner), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) room = Math.min(room, Math.max(0, hit.getLocation().distanceTo(from.add(corner)) - 0.3));
        }
        position(cam, from.subtract(look.x() * room, look.y() * room, look.z() * room));
    }

    private static void position(Camera cam, Vec3 at) {
        try {
            POSITION.invoke(cam, at.x, at.y, at.z);
        } catch (ReflectiveOperationException ignored) {
        }
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
