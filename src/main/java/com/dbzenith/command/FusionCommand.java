package com.dbzenith.command;

import com.dbzenith.DBZenith;
import com.dbzenith.race.Absorption;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /dbzfusion accept}: any player answers a Namekian fusion request (the chat button runs it). */
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
                })));
    }
}
