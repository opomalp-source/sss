package com.dbzenith.client.fx;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Ultimate cinematics (CX-19): a cut-in band with the technique's name across the screen, a flash in the caster's
 * colour and a heavy shake. The game never stops; it is all on the screen of whoever is near. Client config
 * {@code ultimateCinematic} turns it off.
 */
public final class Cinematics {
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
    }
}
