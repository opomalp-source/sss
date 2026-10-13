package com.dbzenith.world.landmark;

import com.dbzenith.DBZenith;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * Finding the landmarks (CX-33): {@code /landmark locate <name>} says where the nearest one is, {@code /landmark tp
 * <name>} takes you to look at it (operators). Names: tournament, lookout, cell_games, frypan_mountains, west_city.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LandmarkCommand {
    private LandmarkCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("landmark").requires(s -> s.hasPermission(2))
                .then(Commands.literal("locate").then(name().executes(ctx -> find(ctx, false))))
                .then(Commands.literal("tp").then(name().executes(ctx -> find(ctx, true))))
                .then(Commands.literal("survey").then(name().executes(LandmarkCommand::survey))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Landmark.ALL).map(l -> l.id), b));
    }

    /** How the sites round you fare (tuning rarity): wrong biome, crowded out, unsuitable ground, landmark. */
    private static int survey(CommandContext<CommandSourceStack> ctx) {
        Landmark type = Landmark.byId(StringArgumentType.getString(ctx, "name"));
        if (type == null) return 0;
        ServerLevel level = ctx.getSource().getServer().overworld();
        int[] n = LandmarkSites.survey(type, level.getSeed(), LandmarkSites.Terrain.of(level), BlockPos.containing(ctx.getSource().getPosition()), 3);
        ctx.getSource().sendSuccess(() -> Component.literal(type.id + ": biome " + n[0] + ", crowded " + n[1] + ", ground " + n[2] + ", built " + n[3]), false);
        return 1;
    }

    private static int find(CommandContext<CommandSourceStack> ctx, boolean go) {
        Landmark type = Landmark.byId(StringArgumentType.getString(ctx, "name"));
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown landmark"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos from = BlockPos.containing(ctx.getSource().getPosition());
        LandmarkPlan p = LandmarkSites.nearest(type, level.getSeed(), LandmarkSites.Terrain.of(level), from, 12);
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("No " + type.id + " within " + 12 * type.spacing + " blocks"));
            return 0;
        }
        BlockPos at = p.arrival();
        int dist = (int) Math.sqrt(from.distSqr(new BlockPos(p.x, from.getY(), p.z)));
        ctx.getSource().sendSuccess(() -> Component.literal(type.id + " at " + p.x + ", " + p.z + " (" + dist + " blocks)"), false);
        if (go && ctx.getSource().getEntity() instanceof ServerPlayer player) {
            player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 10f);
        }
        return 1;
    }
}
