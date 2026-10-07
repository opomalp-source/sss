package com.dbzenith.network;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.combat.HeavyStrike;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.transform.FormHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: a held-key or toggle input. The server decides what it means. */
public record InputPacket(Action action) {
    public enum Action { CHARGE_START, CHARGE_STOP, GUARD_START, GUARD_STOP, TOGGLE_FLIGHT, LOWER_RELEASE, HEAVY_START, HEAVY_STOP,
        TRANSFORM_UP, TRANSFORM_DOWN, RACIAL_USE, SKILL_USE, KAIOKEN_UP, KAIOKEN_OFF }

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
                case GUARD_START -> {
                    if (com.dbzenith.combat.GuardRules.raise(data, player.level().getGameTime())) com.dbzenith.combat.engine.Evasion.guardRaised(player, data);
                }
                case GUARD_STOP -> com.dbzenith.combat.GuardRules.lower(data);
                case TOGGLE_FLIGHT -> FlightHandler.toggle(player);
                case LOWER_RELEASE -> data.setReleasePercent(data.getReleasePercent() - DBZConfig.SERVER.releaseLowerStep.get());
                case HEAVY_START -> { }
                case HEAVY_STOP -> { }                                       // heavies are presses now (CX-19)
                case TRANSFORM_UP -> FormHandler.transformUp(player);
                case TRANSFORM_DOWN -> FormHandler.revertOne(player);
                case RACIAL_USE -> com.dbzenith.race.RacialSkillEffects.use(player);
                case SKILL_USE -> com.dbzenith.race.RacialSkillEffects.useSkill(player);
                case KAIOKEN_UP -> com.dbzenith.transform.Kaioken.raise(player, data);
                case KAIOKEN_OFF -> com.dbzenith.transform.Kaioken.stop(player, data, false);
            }
        });
    }
}
