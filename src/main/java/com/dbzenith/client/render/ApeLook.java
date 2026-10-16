package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.GreatApe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * The Great Ape as the player's own figure grown giant (the user's pictures, v0.134): their body under a coat of fur
 * ({@link ApeLayer}), an ape's muzzle, brow and ears on their head ({@link ApeHeadModel}), red eyes, their own hairstyle
 * in the fur's colour, their tail, and whatever clothes survived the change (battle armour stretches). Textures are
 * painted by {@code tools/GreatApeGen.java}: three furs, the moon ape's brown, the golden ape's gold and the legendary
 * ape's green-gold.
 */
public final class ApeLook {
    private ApeLook() {}

    private static final String DIR = "textures/entity/ape/";
    public static final ResourceLocation EYES = tex("eyes");
    private static final ResourceLocation[] BODY = {tex("body_brown"), tex("body_golden"), tex("body_legendary")};
    private static final ResourceLocation[] PARTS = {tex("parts_brown"), tex("parts_golden"), tex("parts_legendary")};
    /** The hair in each fur's colour (a little lighter than the coat, so the locks read against it). */
    private static final int[] HAIR = {0x6A3A1E, 0xE8B23A, 0xAAB43A};
    private static final int[] TAIL = {0x5A3018, 0xD8A22A, 0x9CA82C};

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(DBZenith.MOD_ID, DIR + name + ".png");
    }

    public static boolean is(PublicStatePacket state) {
        return state != null && GreatApe.isApeForm(state.form());
    }

    public static boolean is(Entity e) {
        return e instanceof net.minecraft.world.entity.player.Player && is(ClientPublicStates.get(e.getId()));
    }

    /** 0 brown (the moon), 1 golden, 2 legendary. */
    static int fur(String form) {
        return "golden_ape".equals(form) ? 1 : "legendary_great_ape".equals(form) ? 2 : 0;
    }

    public static ResourceLocation body(String form) {
        return BODY[fur(form)];
    }

    public static ResourceLocation parts(String form) {
        return PARTS[fur(form)];
    }

    public static int hairColor(String form) {
        return HAIR[fur(form)];
    }

    public static int tailColor(String form) {
        return TAIL[fur(form)];
    }
}
