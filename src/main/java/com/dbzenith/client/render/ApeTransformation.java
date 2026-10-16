package com.dbzenith.client.render;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.ApeChange;
import com.dbzenith.transform.GreatApe;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Starts on a witnessed form change, never on first render or joining beside an existing ape. */
public final class ApeTransformation {
    private record Change(int start, PublicStatePacket before) {}
    private static final Map<Player, Change> CHANGES = new WeakHashMap<>();
    private ApeTransformation() {}

    public static void begin(AbstractClientPlayer player, PublicStatePacket before) {
        CHANGES.put(player, new Change(player.tickCount, before));
    }

    public static float age(Player player, float partial) {
        Change c = CHANGES.get(player);
        if (!ApeLook.is(ClientPublicStates.get(player.getId()))) {
            CHANGES.remove(player);
            return ApeChange.CAMERA_END;
        }
        return c == null ? ApeChange.CAMERA_END : Math.max(0, player.tickCount - c.start + partial);
    }

    public static PublicStatePacket before(Player player) {
        Change c = CHANGES.get(player);
        return c == null ? ClientPublicStates.get(player.getId()) : c.before;
    }

    public static float scale(Player player, float partial) {
        float target = GreatApe.scaleOf(player);
        return ApeLook.is(ClientPublicStates.get(player.getId()))
                ? 1 + (target - 1) * ApeChange.growth(age(player, partial)) : target;
    }
}
