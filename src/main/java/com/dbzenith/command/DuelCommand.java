package com.dbzenith.command;

import com.dbzenith.DBZenith;
import com.dbzenith.duel.Duel;
import com.dbzenith.duel.DuelLadder;
import com.dbzenith.duel.Duels;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /duel} (CX-19 phase 9): {@code /duel <player> [best of 1|3|5] [full|melee]} challenges; {@code accept} and
 * {@code decline} answer (the chat buttons run them); {@code forfeit}; {@code watch <player>} and {@code leave};
 * {@code stats [player]} and {@code top}.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DuelCommand {
    private DuelCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("duel")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> challenge(ctx, 1, Duel.Rules.FULL))
                        .then(Commands.argument("bestOf", IntegerArgumentType.integer(1, 5))
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("1", "3", "5"), b))
                                .executes(ctx -> challenge(ctx, IntegerArgumentType.getInteger(ctx, "bestOf"), Duel.Rules.FULL))
                                .then(Commands.argument("rules", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("full", "melee"), b))
                                        .executes(ctx -> challenge(ctx, IntegerArgumentType.getInteger(ctx, "bestOf"),
                                                StringArgumentType.getString(ctx, "rules").equalsIgnoreCase("melee") ? Duel.Rules.MELEE : Duel.Rules.FULL)))))
                .then(Commands.literal("accept")
                        .executes(ctx -> Duels.answer(ctx.getSource().getPlayerOrException(), null, true) ? 1 : 0)
                        .then(Commands.argument("from", StringArgumentType.word())
                                .executes(ctx -> Duels.answer(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "from"), true) ? 1 : 0)))
                .then(Commands.literal("decline")
                        .executes(ctx -> Duels.answer(ctx.getSource().getPlayerOrException(), null, false) ? 1 : 0)
                        .then(Commands.argument("from", StringArgumentType.word())
                                .executes(ctx -> Duels.answer(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "from"), false) ? 1 : 0)))
                .then(Commands.literal("forfeit").executes(ctx -> {
                    if (Duels.forfeit(ctx.getSource().getPlayerOrException())) return 1;
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.duel_not_in"));
                    return 0;
                }))
                .then(Commands.literal("watch").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    if (Duels.watch(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))) return 1;
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.duel_cant_watch"));
                    return 0;
                })))
                .then(Commands.literal("leave").executes(ctx -> {
                    if (Duels.leaveWatching(ctx.getSource().getPlayerOrException())) return 1;
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.duel_not_watching"));
                    return 0;
                }))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> stats(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("top").executes(ctx -> {
                    List<DuelLadder.Record> top = DuelLadder.get(ctx.getSource().getServer()).top(10);
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.dbzenith.duel_top").withStyle(ChatFormatting.GOLD), false);
                    for (int i = 0; i < top.size(); i++) {
                        DuelLadder.Record r = top.get(i);
                        int rank = i + 1;
                        ctx.getSource().sendSuccess(() -> Component.translatable("message.dbzenith.duel_top_line", rank, r.name, r.rating, r.wins, r.losses, r.draws), false);
                    }
                    return top.size();
                })));
    }

    private static int challenge(CommandContext<CommandSourceStack> ctx, int bestOf, Duel.Rules rules) throws CommandSyntaxException {
        return Duels.challenge(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"), bestOf, rules) ? 1 : 0;
    }

    private static int stats(CommandSourceStack src, ServerPlayer p) {
        DuelLadder.Record r = DuelLadder.get(src.getServer()).peek(p.getUUID());
        if (r == null) {
            src.sendSuccess(() -> Component.translatable("message.dbzenith.duel_no_record", p.getDisplayName()), false);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("message.dbzenith.duel_stats", p.getDisplayName(), r.rating, r.wins, r.losses, r.draws)
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
