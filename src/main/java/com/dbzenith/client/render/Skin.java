package com.dbzenith.client.render;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.combat.SkinColor;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The skin colour a fighter shows, on the client (as {@link SkinColor}, from what the server tells everyone). */
public final class Skin {
    private Skin() {}

    public static int of(LivingEntity e) {
        if (!(e instanceof Player)) return SkinColor.npc(e);
        PublicStatePacket s = ClientPublicStates.get(e.getId());
        if (s == null) return SkinColor.HUMAN;
        if (s.raceLook() && RaceSkinLayer.texture(s) != null) return s.raceSkin() >= 0 ? s.raceSkin() : SkinColor.of(s.raceEnum(), s.variantEnum());
        if (s.skinTone() >= 0) return s.skinTone();
        return SkinColor.of(s.raceEnum(), s.variantEnum());
    }
}
