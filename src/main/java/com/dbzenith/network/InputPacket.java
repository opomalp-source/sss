package com.dbzenith.network;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.combat.HeavyStrike;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Overdrive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: a held-key or toggle input. The server decides what it means. */
public record InputPacket(Action action) {
    public enum Action { CHARGE_START, CHARGE_STOP, GUARD_START, GUARD_STOP, TOGGLE_FLIGHT, LOWER_RELEASE, HEAVY_START, HEAVY_STOP,
        TRANSFORM_UP, TRANSFORM_DOWN, OVERDRIVE_UP, OVERDRIVE_OFF }

    public static void encode(InputPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static InputPacket decode(FriendlyByteBuf buf) {
        return new InputPacket(buf.readEnum(Action.class));
    }

    public static void handle(InputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.isAlive()) return;
        ModCapabilities.get(player).ifPresent(data -> {
            switch (msg.action) {
                case CHARGE_START -> data.setCharging(true);
                case CHARGE_STOP -> data.setCharging(false);
                case GUARD_START -> com.dbzenith.combat.GuardRules.raise(data, player.level().getGameTime());
                case GUARD_STOP -> com.dbzenith.combat.GuardRules.lower(data);
                case TOGGLE_FLIGHT -> FlightHandler.toggle(player);
                case LOWER_RELEASE -> data.setReleasePercent(data.getReleasePercent() - DBZConfig.SERVER.releaseLowerStep.get());
                case HEAVY_START -> data.startHeavyCharge();
                case HEAVY_STOP -> HeavyStrike.release(player, data);
                case TRANSFORM_UP -> FormHandler.transformUp(player);
                case TRANSFORM_DOWN -> FormHandler.revertOne(player);
                case OVERDRIVE_UP -> Overdrive.raise(player);
                case OVERDRIVE_OFF -> Overdrive.stop(player, data, true);
            }
        });
    }
}
