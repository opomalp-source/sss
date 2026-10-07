package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ultimate cinematics (CX-19): a cut-in band with the technique's name across the screen, a flash in the caster's
 * colour and a heavy shake for everyone near; for the caster, the camera swings round to the front for a moment to
 * show the pose (phase 5), then goes back to the view they had. The game never stops. Client config
 * {@code ultimateCinematic} turns it all off.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class Cinematics {
    private static final int CAMERA_TICKS = 36;
    private static CameraType before;
    private static int cameraLeft;

    private Cinematics() {}

    public static void ultimate(int casterId, String techniqueId, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !DBZConfig.CLIENT.ultimateCinematic.get()) return;
        Entity caster = mc.level.getEntity(casterId);
        Technique t = Techniques.byId(techniqueId);
        Component name = t != null ? t.name() : Component.literal(techniqueId);
        boolean mine = casterId == mc.player.getId();
        com.dbzenith.client.ui.CutInOverlay.show(name, color, mine ? "cutin.dbzenith.ultimate" : "cutin.dbzenith.ultimate_incoming");
        CameraFx.flash(color, 0.55f);
        if (caster != null) CameraFx.shakeAt(caster.position(), 0.6f, 64);
        else CameraFx.shake(0.3f);
        CameraFx.kick(mine ? 0.5f : 0.3f);
        if (mine) {                                                             // the ultimate camera: a front shot of the caster
            if (cameraLeft <= 0) before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            cameraLeft = CAMERA_TICKS;
            CameraFx.speedLines(color, 0.8f);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || cameraLeft <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cameraLeft = 0;
            return;
        }
        if (mc.isPaused()) return;
        if (--cameraLeft == 0 && before != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(before);   // unless they changed it
            before = null;
        }
    }
}
