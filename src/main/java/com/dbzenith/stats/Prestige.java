package com.dbzenith.stats;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.Races;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Overdrive;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Prestige: at a high character level, start your attributes and TP over for a permanent bonus to TP gains and to
 * STR/DEX/KI_POWER. Race, appearance, techniques, mastery, forms unlocked by flags and quest progress are kept.
 */
public final class Prestige {
    private Prestige() {}

    public static boolean eligible(PlayerData data) {
        return StatCalculator.level(data) >= DBZConfig.SERVER.prestigeLevel.get();
    }

    public static boolean prestige(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || !eligible(d)) return false;
        Overdrive.stop(player, d, false);
        FormHandler.revertToBase(player, d);
        int start = DBZConfig.SERVER.startingAttribute.get();
        for (Attribute a : Attribute.values()) {
            d.setAttribute(a, start + Races.of(d.getRace()).startBonus(a) + CharacterCreation.bodyBonus(d.getBodyType(), a));
        }
        d.setTrainingPoints(0);
        d.setPrestige(d.getPrestige() + 1);
        d.recomputeIfStale();
        d.refill();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.prestiged",
                player.getDisplayName(), d.getPrestige()).withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    /** Client to server: the player confirmed the prestige dialog. */
    public record Packet() {
        public static void encode(Packet msg, FriendlyByteBuf buf) {}

        public static Packet decode(FriendlyByteBuf buf) {
            return new Packet();
        }

        public static void handle(Packet msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) prestige(player);
        }
    }
}
