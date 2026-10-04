package com.dbzenith.network;

import com.dbzenith.client.ClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client, dev tooling only: asks the client to save a screenshot. Ignored unless the client
 * was started with {@code -Ddbzenith.devAutomation=true} (only the clientTest run config sets it).
 */
public record DevScreenshotPacket(String name, int delayTicks) {
    public static void encode(DevScreenshotPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, 64);
        buf.writeVarInt(msg.delayTicks);
    }

    public static DevScreenshotPacket decode(FriendlyByteBuf buf) {
        return new DevScreenshotPacket(buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(DevScreenshotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Runs on the client thread only; ClientEvents is loaded lazily here, never on a dedicated server.
        ClientEvents.devScreenshot(msg.name, msg.delayTicks);
    }
}
