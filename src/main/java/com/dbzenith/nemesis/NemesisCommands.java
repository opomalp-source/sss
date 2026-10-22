package com.dbzenith.nemesis;

import com.dbzenith.npc.KiFighter;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * {@code /dbz nemesis} (operators): look into the Nemesis system and nudge it.
 * <ul>
 *   <li>{@code list}: every character, status, rank, level;</li>
 *   <li>{@code info <name>}: one character in full (traits, record per player, bonds with other characters, history);</li>
 *   <li>{@code promote <fighter>}: make an ordinary fighter a named character now;</li>
 *   <li>{@code encounter <player> <kind> [name]}: bring a character to a player now (revenge, rival_challenge, ambush,
 *       training, intervention, alliance), past cooldowns and chance;</li>
 *   <li>{@code simulate <hours>}: run the world simulation for that many in-game hours;</li>
 *   <li>{@code bond <name> <player> <affinity> <rivalry>}: set where a character stands with a player.</li>
 * </ul>
 */
public final class NemesisCommands {
    private NemesisCommands() {}

    public static ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("nemesis").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    NemesisData d = NemesisData.get(ctx.getSource().getServer());
                    for (NamedFighter c : d.all()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s [%s] %s lv %d, %s, rep %d, %d players, %d characters",
                                c.fullName(), c.status.id(), c.personality.id(), c.level, c.rank().id(), c.reputation, c.players.size(), c.characters.size())), false);
                    }
                    return d.all().size();
                }))
                .then(Commands.literal("info").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                    NamedFighter c = find(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                    if (c == null) return 0;
                    for (String line : describe(c, ctx.getSource().getServer().overworld().getGameTime())) {
                        ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
                    }
                    return 1;
                })))
                .then(Commands.literal("promote").then(Commands.argument("fighter", EntityArgument.entity()).executes(ctx -> {
                    Entity e = EntityArgument.getEntity(ctx, "fighter");
                    if (!(e instanceof KiFighter k)) {
                        ctx.getSource().sendFailure(Component.literal("Not an NPC fighter"));
                        return 0;
                    }
                    NamedFighter c = Nemesis.identify(k);
                    if (c == null) c = Nemesis.promote(k, "command");
                    if (c == null) {
                        ctx.getSource().sendFailure(Component.literal("Could not promote (the Nemesis system is off, or full)"));
                        return 0;
                    }
                    NamedFighter cc = c;
                    ctx.getSource().sendSuccess(() -> Component.literal("Now " + cc.fullName()), true);
                    return 1;
                })))
                .then(Commands.literal("encounter").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> {
                            for (Encounters.Kind k : Encounters.Kind.values()) b.suggest(k.name().toLowerCase(Locale.ROOT));
                            return b.buildFuture();
                        }).executes(ctx -> encounter(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "kind"), null))
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> encounter(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "kind"), StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("simulate").then(Commands.argument("hours", IntegerArgumentType.integer(1, 240)).executes(ctx -> {
                    int hours = IntegerArgumentType.getInteger(ctx, "hours");
                    NemesisData d = NemesisData.get(ctx.getSource().getServer());
                    long now = ctx.getSource().getServer().overworld().getGameTime();
                    for (int h = 0; h < hours; h++) WorldSim.step(d, now - (hours - h) * 1000L);
                    ctx.getSource().sendSuccess(() -> Component.literal("Simulated " + hours + " in-game hours"), true);
                    return hours;
                })))
                .then(Commands.literal("bond").then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("affinity", IntegerArgumentType.integer(-100, 100)).then(Commands.argument("rivalry", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            NamedFighter c = find(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            if (c == null) return 0;
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
                            NemesisData.get(ctx.getSource().getServer()).index(c, p.getUUID());
                            b.encounters = Math.max(1, b.encounters);
                            b.affinity = IntegerArgumentType.getInteger(ctx, "affinity");
                            b.rivalry = IntegerArgumentType.getInteger(ctx, "rivalry");
                            b.state = Relationship.of(b.affinity, b.rivalry, b.encounters, b.spars);
                            NemesisData.get(ctx.getSource().getServer()).setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal(c.name + " and " + p.getGameProfile().getName() + ": " + b.state.id()), true);
                            return 1;
                        }))))));
    }

    static NamedFighter find(CommandSourceStack src, String name) {
        for (NamedFighter c : NemesisData.get(src.getServer()).all()) {
            if (c.name.equalsIgnoreCase(name) || c.fullName().equalsIgnoreCase(name)) return c;
        }
        src.sendFailure(Component.literal("No character named " + name));
        return null;
    }

    static int encounter(CommandSourceStack src, ServerPlayer p, String kindName, String name) {
        Encounters.Kind kind;
        try {
            kind = Encounters.Kind.valueOf(kindName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Kinds: revenge, rival_challenge, ambush, training, intervention, alliance"));
            return 0;
        }
        NamedFighter c = null;
        if (name != null) c = find(src, name);
        else for (NamedFighter x : NemesisData.get(src.getServer()).knowing(p.getUUID())) if (x.active() && x.body == null) c = x;
        if (c == null || !c.active() || c.body != null) {
            src.sendFailure(Component.literal("No character away who knows " + p.getGameProfile().getName()));
            return 0;
        }
        var foe = kind == Encounters.Kind.ALLIANCE || kind == Encounters.Kind.INTERVENTION
                ? p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p).stream().findFirst().orElse(null) : null;
        KiFighter body = Encounters.start(new Encounters.Choice(c, kind, foe), p, p.level().getGameTime());
        if (body == null) {
            src.sendFailure(Component.literal("No safe ground near " + p.getGameProfile().getName()));
            return 0;
        }
        NamedFighter cc = c;
        src.sendSuccess(() -> Component.literal(cc.fullName() + " arrives (" + kind.name().toLowerCase(Locale.ROOT) + ")"), true);
        return 1;
    }

    /** The full readout of a character. */
    public static java.util.List<String> describe(NamedFighter c, long now) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(c.fullName() + " (" + c.type + ", " + (c.race.isEmpty() ? "no race" : c.race) + ") " + c.status.id()
                + (c.returnAt > now ? String.format(", back in %.1f days", (c.returnAt - now) / 24000.0) : ""));
        out.add(String.format("%s%s, level %d (%.0f xp, growth %.2f, cap %d), %s rep %d, battles %d (%d won, %d lost), survived death %d",
                c.personality.id(), c.traits.isEmpty() ? "" : " + " + c.traits, c.level, c.xp, c.growth, Progression.cap(c), c.rank().id(),
                c.reputation, c.battles, c.victories, c.defeats, c.survivedDeaths));
        out.add("goal " + c.goal.name().toLowerCase(Locale.ROOT) + ", forms " + c.forms + ", learned " + c.learned);
        c.players.forEach((id, b) -> out.add(String.format("  %s: %s (affinity %.0f, rivalry %.0f), %d-%d, spared %d/%d, escapes %d, retreats %d, met %d, spars %d%s",
                b.name, b.state.id(), b.affinity, b.rivalry, b.wins, b.losses, b.sparedThem, b.sparedByThem, b.escapes, b.retreats, b.encounters, b.spars,
                b.habits == null ? "" : ", knows their style")));
        c.characters.forEach((id, b) -> out.add(String.format("  ~ %s: %s (affinity %.0f, rivalry %.0f), %d-%d", b.name, b.state.id(), b.affinity, b.rivalry, b.wins, b.losses)));
        int n = 0;
        var it = c.history.descendingIterator();
        while (it.hasNext() && n++ < 8) {
            Memory m = it.next();
            out.add(String.format("  %.1fd ago: %s %s", (now - m.tick()) / 24000.0, m.kind().id(), m.detail()));
        }
        return out;
    }
}
