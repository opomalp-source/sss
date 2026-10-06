package com.dbzenith.command;

import com.dbzenith.DBZenith;
import com.dbzenith.race.Absorption;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /dbzfusion accept}: any player answers a Namekian fusion request; {@code dance} and {@code potara} answer an invitation to
 * the Fusion Dance or the Potara (12c). The chat buttons run them. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FusionCommand {
    private FusionCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dbzfusion")
                .then(Commands.literal("accept").executes(ctx -> {
                    if (Absorption.acceptFusion(ctx.getSource().getPlayerOrException())) return 1;
                    ctx.getSource().sendFailure(Component.translatable("message.dbzenith.fusion_no_request"));
                    return 0;
                }))
                .then(Commands.literal("dance").executes(ctx -> answer(ctx.getSource().getPlayerOrException(), com.dbzenith.fusion.FusionDance.DANCE)))
                .then(Commands.literal("potara").executes(ctx -> answer(ctx.getSource().getPlayerOrException(), com.dbzenith.fusion.FusionDance.POTARA))));
    }

    private static int answer(net.minecraft.server.level.ServerPlayer player, int kind) {
        if (com.dbzenith.fusion.FusionDance.accept(player, kind)) return 1;
        player.sendSystemMessage(Component.translatable("message.dbzenith.fusion_no_request"));
        return 0;
    }
}
