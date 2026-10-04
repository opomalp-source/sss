package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shows a player's chosen title before their name in chat and in the player list. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleEvents {
    private TitleEvents() {}

    @SubscribeEvent
    public static void nameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity().level().isClientSide) return;
        Component prefix = prefix(event.getEntity());
        if (prefix != null) event.setDisplayname(Component.empty().append(prefix).append(event.getDisplayname()));
    }

    @SubscribeEvent
    public static void tabListFormat(PlayerEvent.TabListNameFormat event) {
        Component prefix = prefix(event.getEntity());
        if (prefix != null) event.setDisplayName(Component.empty().append(prefix).append(event.getEntity().getName()));
    }

    private static Component prefix(net.minecraft.world.entity.player.Player player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || d.getTitle().isEmpty()) return null;
        return Component.literal("[").append(Component.translatable("title.dbzenith." + d.getTitle())).append("] ")
                .withStyle(ChatFormatting.GOLD);
    }

    /** Choose a title (must be earned; "" clears it). Returns false if not earned. */
    public static boolean select(ServerPlayer player, String id) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return false;
        if (!id.isEmpty() && LifeSim.earnedTitles(d).stream().noneMatch(t -> t.id().equals(id))) return false;
        d.setTitle(id);
        player.refreshDisplayName();
        player.refreshTabListName();
        return true;
    }
}
