package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * {@code /dbz settlement} and {@code /dbz living} (operators):
 * <ul>
 *   <li>{@code settlement build [name]}: builds a town (Ginger Town by default) centred where you stand, levels its
 *       ground and peoples it (it changes the land around you: use it where you mean it); {@code build nearby}: on the
 *       best dry, flat site 64-184 blocks away;</li>
 *   <li>{@code settlement list}, {@code settlement info}: the towns, and the nearest in full (places, residents and
 *       what each is doing);</li>
 *   <li>{@code settlement alarm}: puts the nearest town on alert;</li>
 *   <li>{@code living rumors}: what is being said, and who knows it;</li>
 *   <li>{@code living talk <resident>}, {@code living choose <n>}: talk as if you right-clicked it, and answer (debugging);</li>
 *   <li>{@code living fame <player> <amount>}: adds fame.</li>
 * </ul>
 */
public final class LivingCommands {
    private LivingCommands() {}

    public static ArgumentBuilder<CommandSourceStack, ?> settlement() {
        return Commands.literal("settlement").requires(s -> s.hasPermission(2))
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx.getSource(), TownBuilder.GINGER_TOWN, false))
                        .then(Commands.literal("nearby").executes(ctx -> build(ctx.getSource(), TownBuilder.GINGER_TOWN, true)))
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))))
                .then(Commands.literal("list").executes(ctx -> {
                    LivingData ld = LivingData.get(ctx.getSource().getServer());
                    for (Settlement s : ld.settlements()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s at %s (%s): %d places, %d residents%s", s.name,
                                s.center.toShortString(), s.dimension, s.pois.size(), Residents.of(s).size(),
                                s.alarmed(ctx.getSource().getServer().overworld().getGameTime()) ? ", ON ALERT" : "")), false);
                    }
                    return ld.settlements().size();
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    Settlement s = nearest(ctx.getSource());
                    if (s == null) return 0;
                    say(ctx.getSource(), s.name + " at " + s.center.toShortString() + ", radius " + s.radius + (s.unreachable.isEmpty() ? "" : ", unreachable: " + s.unreachable));
                    for (int i = 0; i < s.pois.size(); i++) {
                        Poi p = s.pois.get(i);
                        say(ctx.getSource(), "  #" + i + " " + p.kind().id() + " '" + p.label() + "' " + p.pos().toShortString());
                    }
                    long now = ctx.getSource().getServer().overworld().getGameTime();
                    for (NamedFighter c : Residents.of(s)) {
                        var level = ctx.getSource().getLevel();
                        String doing = c.body != null && level.getEntity(c.body) instanceof Resident r ? r.activity.id() : "(not loaded)";
                        say(ctx.getSource(), String.format(Locale.ROOT, "  %s: %s, lv %d, %s, %s, %s, mood %s, knows %d rumors, %s", c.name, c.occupation, c.level,
                                c.personality.id(), c.traits, c.goal.name().toLowerCase(Locale.ROOT), Mood.of(c, ctx.getSource().getLevel()).id(),
                                c.knowledge.size(), c.status.id() + " " + doing));
                    }
                    return 1;
                }))
                .then(Commands.literal("alarm").executes(ctx -> {
                    Settlement s = nearest(ctx.getSource());
                    if (s == null) return 0;
                    Living.alarm(LivingData.get(ctx.getSource().getServer()), s, ctx.getSource().getPlayer(), ctx.getSource().getServer().overworld().getGameTime());
                    say(ctx.getSource(), s.name + " is on alert");
                    return 1;
                }));
    }

    public static ArgumentBuilder<CommandSourceStack, ?> living() {
        return Commands.literal("living").requires(s -> s.hasPermission(2))
                .then(Commands.literal("rumors").executes(ctx -> {
                    LivingData ld = LivingData.get(ctx.getSource().getServer());
                    long now = ctx.getSource().getServer().overworld().getGameTime();
                    var d = com.dbzenith.nemesis.Nemesis.data();
                    for (Rumor r : ld.rumors()) {
                        long knowers = d == null ? 0 : d.all().stream().filter(c -> c.knowledge.contains(r.id())).count();
                        say(ctx.getSource(), String.format(Locale.ROOT, "[%s] %s (%d know it): %s", r.kind().id(), r.subjectName(), knowers,
                                Rumors.line(r, null, "you", ld, now, 0)));
                    }
                    return ld.rumors().size();
                }))
                .then(Commands.literal("talk").then(Commands.argument("resident", EntityArgument.entity()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();                // as if you right-clicked it (debugging, screenshots)
                    if (!(EntityArgument.getEntity(ctx, "resident") instanceof Resident r)) {
                        ctx.getSource().sendFailure(Component.literal("Not a resident"));
                        return 0;
                    }
                    Conversations.open(r, p);
                    return 1;
                })))
                .then(Commands.literal("choose").then(Commands.argument("option", IntegerArgumentType.integer(1, 8)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Integer entity = Conversations.entity(p);
                    if (entity == null) {
                        ctx.getSource().sendFailure(Component.literal("Not in a conversation"));
                        return 0;
                    }
                    Conversations.choose(p, entity, IntegerArgumentType.getInteger(ctx, "option") - 1);
                    return 1;
                })))
                .then(Commands.literal("fame").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-10000, 10000)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            int n = IntegerArgumentType.getInteger(ctx, "amount");
                            Fame.add(ctx.getSource().getServer(), p, n, ctx.getSource().getServer().overworld().getGameTime());
                            int f = LivingData.get(ctx.getSource().getServer()).fame(p.getUUID());
                            say(ctx.getSource(), p.getGameProfile().getName() + ": fame " + f + " (" + Fame.Tier.of(f).id() + ")");
                            return f;
                        }))));
    }

    static int build(CommandSourceStack src, String name, boolean nearby) {
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
        BlockPos site = nearby ? TownBuilder.findSite(level, at) : new BlockPos(at.getX(), y, at.getZ());
        Settlement s = Living.build(level, site, name);
        say(src, "Built " + s.name + " at " + s.center.toShortString() + " with " + Residents.of(s).size() + " residents");
        return 1;
    }

    static Settlement nearest(CommandSourceStack src) {
        Settlement s = LivingData.get(src.getServer()).nearest(src.getLevel().dimension().location().toString(), BlockPos.containing(src.getPosition()), 100000);
        if (s == null) src.sendFailure(Component.literal("No settlement in this world"));
        return s;
    }

    static void say(CommandSourceStack src, String line) {
        src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
    }
}
