package com.dbzenith.ki;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Ki flight. Uses vanilla abilities (mayfly/flying) so movement is smooth and anti-cheat-friendly;
 * {@link PlayerData#isFlying()} records that the flight came from us, so we only ever revoke what we granted.
 */
public final class FlightHandler {
    private FlightHandler() {}

    /** Toggles ki flight. Returns the new state. */
    public static boolean toggle(ServerPlayer player) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return false;
        if (data.isFlying()) {
            stop(player, data);
            return false;
        }
        if (!com.dbzenith.transform.Forms.byId(data.getFormId()).allowsFlight()) return false;
        if (data.getKi() <= 0 && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.dbzenith.no_ki_to_fly"), true);
            return false;
        }
        data.setFlying(true);
        Abilities a = player.getAbilities();
        a.mayfly = true;
        a.flying = true;
        a.setFlyingSpeed(speed(data));
        player.onUpdateAbilities();
        return true;
    }

    public static void stop(ServerPlayer player, PlayerData data) {
        data.setFlying(false);
        Abilities a = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()) {
            a.mayfly = false;
            a.flying = false;
        }
        a.setFlyingSpeed(0.05f);
        player.onUpdateAbilities();
    }

    static void tick(ServerPlayer player, PlayerData data) {
        if (!data.isFlying()) return;
        Abilities a = player.getAbilities();
        boolean changed = false;
        if (!a.mayfly) { // e.g. a gamemode change cleared it
            a.mayfly = true;
            changed = true;
        }
        float speed = speed(data);
        if (Math.abs(a.getFlyingSpeed() - speed) > 1e-4f) {
            a.setFlyingSpeed(speed);
            changed = true;
        }
        if (changed) player.onUpdateAbilities();

        if (a.flying && !a.instabuild) {
            double drain = data.getDerived().maxKi() * DBZConfig.SERVER.flightKiPercentPerSecond.get() / 100.0 / 20.0;
            data.setKi(data.getKi() - drain);
            if (data.getKi() <= 0) {
                stop(player, data);
                player.displayClientMessage(Component.translatable("message.dbzenith.out_of_ki"), true);
            }
        }
    }

    private static float speed(PlayerData data) {
        DBZConfig.Server c = DBZConfig.SERVER;
        return (float) (c.flightBaseSpeed.get() * (1.0 + data.getDerived().moveSpeed() * c.flightSpeedPerMoveBonus.get()));
    }
}
