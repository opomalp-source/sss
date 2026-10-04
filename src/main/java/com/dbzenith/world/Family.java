package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Family: two players can become partners (Promise Ring, then {@code /dbzfamily accept}). Partners who train near
 * each other gain extra TP. {@code /dbzfamily leave} ends it; the other side notices on their next check.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Family {
    private record Proposal(UUID from, long expires) {}

    private static final Map<UUID, Proposal> PROPOSALS = new HashMap<>();
    private static final long PROPOSAL_TICKS = 1200;

    private Family() {}

    public static boolean propose(ServerPlayer from, ServerPlayer to) {
        PlayerData a = ModCapabilities.get(from).orElse(null);
        PlayerData b = ModCapabilities.get(to).orElse(null);
        if (a == null || b == null || from == to) return false;
        if (!a.getPartnerId().isEmpty() || !b.getPartnerId().isEmpty()) {
            from.displayClientMessage(Component.translatable("message.dbzenith.family_taken"), true);
            return false;
        }
        PROPOSALS.put(to.getUUID(), new Proposal(from.getUUID(), from.level().getGameTime() + PROPOSAL_TICKS));
        Component accept = Component.translatable("message.dbzenith.family_accept_button").withStyle(Style.EMPTY
                .withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dbzfamily accept")));
        to.sendSystemMessage(Component.translatable("message.dbzenith.family_proposal", from.getDisplayName()).append(" ").append(accept));
        from.displayClientMessage(Component.translatable("message.dbzenith.family_proposed", to.getDisplayName()), true);
        return true;
    }

    public static boolean accept(ServerPlayer to) {
        Proposal p = PROPOSALS.remove(to.getUUID());
        if (p == null || to.level().getGameTime() > p.expires) return false;
        ServerPlayer from = to.server.getPlayerList().getPlayer(p.from);
        if (from == null) return false;
        PlayerData a = ModCapabilities.get(from).orElse(null);
        PlayerData b = ModCapabilities.get(to).orElse(null);
        if (a == null || b == null || !a.getPartnerId().isEmpty() || !b.getPartnerId().isEmpty()) return false;
        a.setPartner(to.getStringUUID(), to.getGameProfile().getName());
        b.setPartner(from.getStringUUID(), from.getGameProfile().getName());
        to.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.family_joined",
                from.getDisplayName(), to.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || d.getPartnerId().isEmpty()) return false;
        ServerPlayer partner = online(player, d);
        if (partner != null) {
            ModCapabilities.get(partner).ifPresent(pd -> pd.setPartner("", ""));
            partner.sendSystemMessage(Component.translatable("message.dbzenith.family_left", player.getDisplayName()));
        }
        d.setPartner("", "");
        return true;
    }

    /** Once per second: is the partner close, and are we still together? */
    public static void tick(ServerPlayer player, PlayerData d) {
        if (d.getPartnerId().isEmpty()) return;
        ServerPlayer partner = online(player, d);
        if (partner != null) {
            PlayerData pd = ModCapabilities.get(partner).orElse(null);
            if (pd != null && !pd.getPartnerId().equals(player.getStringUUID())) { // they left while we were away
                d.setPartner("", "");
                return;
            }
        }
        int range = DBZConfig.SERVER.partnerRange.get();
        d.setNearPartner(partner != null && partner.level() == player.level() && partner.distanceToSqr(player) <= (double) range * range);
    }

    /** TP gain multiplier for training near your partner. */
    public static double tpMultiplier(PlayerData d) {
        return d.isNearPartner() ? 1.0 + DBZConfig.SERVER.partnerTpBonus.get() : 1.0;
    }

    private static ServerPlayer online(ServerPlayer player, PlayerData d) {
        try {
            return player.server.getPlayerList().getPlayer(UUID.fromString(d.getPartnerId()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dbzfamily")
                .then(Commands.literal("accept").executes(ctx -> {
                    if (accept(ctx.getSource().getPlayerOrException())) return 1;
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.family_no_proposal"));
                    return 0;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    if (leave(ctx.getSource().getPlayerOrException())) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("message.dbzenith.family_you_left"), false);
                        return 1;
                    }
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.family_single"));
                    return 0;
                })));
    }
}
