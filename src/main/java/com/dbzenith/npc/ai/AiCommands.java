package com.dbzenith.npc.ai;

import com.dbzenith.npc.KiFighter;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;

/**
 * {@code /dbz ai}: the combat AI's debug readout (operators).
 * <ul>
 *   <li>{@code inspect [fighter]}: state, target and threat, action and why, ki/stamina/guard, form and the reason for
 *       the last transformation, the opponent profile it has built, what it has learned, the top scores, its squad,
 *       and its recent adaptations (default: the nearest fighter within 32 blocks);</li>
 *   <li>{@code watch}: a one-line readout of the nearest fighter on the action bar, twice a second (again: off);</li>
 *   <li>{@code personality <fighter> <name>}: changes a fighter's temperament;</li>
 *   <li>{@code memory <fighter>}: what an important fighter remembers of its opponents;</li>
 *   <li>{@code stats}: how many fighters are thinking.</li>
 * </ul>
 */
public final class AiCommands {
    private AiCommands() {}

    public static ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("ai").requires(s -> s.hasPermission(2))
                .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource(), nearest(ctx.getSource().getPlayerOrException(), 32)))
                        .then(Commands.argument("fighter", EntityArgument.entity()).executes(ctx -> inspect(ctx.getSource(), EntityArgument.getEntity(ctx, "fighter")))))
                .then(Commands.literal("watch").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean on = AiEvents.WATCHERS.add(p.getUUID());
                    if (!on) AiEvents.WATCHERS.remove(p.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal("AI watch " + (on ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("personality").then(Commands.argument("fighter", EntityArgument.entity())
                        .then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> {
                            for (Personality p : Personality.values()) b.suggest(p.id());
                            return b.buildFuture();
                        }).executes(ctx -> {
                            Entity e = EntityArgument.getEntity(ctx, "fighter");
                            Personality p = Personality.byId(StringArgumentType.getString(ctx, "name"));
                            if (!(e instanceof KiFighter k) || p == null) {
                                ctx.getSource().sendFailure(Component.literal("A fighter and one of " + Arrays.toString(Personality.values())));
                                return 0;
                            }
                            k.brain().setPersonality(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(k.getName().getString() + " is now " + p.id()), true);
                            return 1;
                        }))))
                .then(Commands.literal("memory").then(Commands.argument("fighter", EntityArgument.entity()).executes(ctx -> {
                    Entity e = EntityArgument.getEntity(ctx, "fighter");
                    AiMemory.Npc n = AiMemory.get(ctx.getSource().getServer()).peek(e.getUUID());
                    if (n == null) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Remembers nobody (only bosses remember)"), false);
                        return 0;
                    }
                    n.opponents.forEach((id, o) -> ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s: %d fights, %d won, %d lost, %d fled, grudge %.0f",
                            o.name.isEmpty() ? id.toString() : o.name, o.fights, o.wins, o.losses, o.fled, o.grudge)), false));
                    return n.opponents.size();
                })))
                .then(Commands.literal("stats").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(AiEvents.activeCount() + " fighters thinking; "
                            + AiMemory.get(ctx.getSource().getServer()).size() + " remembered"), false);
                    return 1;
                }));
    }

    static int inspect(CommandSourceStack src, Entity e) {
        if (!(e instanceof KiFighter k)) {
            src.sendFailure(Component.literal("No NPC fighter there"));
            return 0;
        }
        for (String line : k.brain().describe()) src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** The nearest NPC fighter within {@code range}, or null. */
    static KiFighter nearest(ServerPlayer p, double range) {
        KiFighter best = null;
        double bd = range * range;
        for (KiFighter k : p.level().getEntitiesOfClass(KiFighter.class, p.getBoundingBox().inflate(range))) {
            double d = k.distanceToSqr(p);
            if (d < bd && k.isAlive()) {
                bd = d;
                best = k;
            }
        }
        return best;
    }
}
