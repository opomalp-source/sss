package com.dbzenith.client;

import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.Minecraft;

/** Whether this client's own player is in PvP mode (CX-20), as the server last said. */
public final class ClientPvp {
    private ClientPvp() {}

    public static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PublicStatePacket s = ClientPublicStates.get(mc.player.getId());
        return s != null && s.has(PublicStatePacket.PVP);
    }
}
