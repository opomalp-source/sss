package com.dbzenith.command;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.StatField;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.network.DevScreenshotPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.AttributeTraining;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /dbz} admin/debug command (permission level 2).
 * <pre>
 * /dbz stats [targets]                   show everything
 * /dbz set &lt;targets&gt; &lt;field&gt; &lt;value&gt;     set any StatField (str, ki, tp, release...)
 * /dbz tp add &lt;targets&gt; &lt;amount&gt;        grant training points
 * /dbz race &lt;targets&gt; &lt;race&gt;
 * /dbz path &lt;targets&gt; &lt;path&gt;
 * /dbz refill [targets]                  refill body/ki/stamina
 * /dbz reset &lt;targets&gt;                  wipe to a fresh character
 * </pre>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DBZCommand {
    private static final DynamicCommandExceptionType UNKNOWN_FIELD =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown stat field: " + id));
    private static final DynamicCommandExceptionType UNKNOWN_RACE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown race: " + id));
    private static final DynamicCommandExceptionType UNKNOWN_PATH =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown path: " + id));
    private static final DynamicCommandExceptionType UNKNOWN_TECHNIQUE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown technique: " + id));

    private DBZCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dbz")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> stats(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StatField.ids(), b))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(DBZCommand::set)))))
                .then(Commands.literal("tp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", LongArgumentType.longArg())
                                                .executes(ctx -> {
                                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                                    return apply(ctx, "Added " + amount + " TP to", d -> d.addTrainingPoints(amount));
                                                })))))
                .then(Commands.literal("race")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("race", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Race.values()).map(Race::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "race");
                                            Race race = Arrays.stream(Race.values()).filter(r -> r.id().equals(id)).findFirst()
                                                    .orElseThrow(() -> UNKNOWN_RACE.create(id));
                                            return apply(ctx, "Set race " + race.id() + " for", d -> d.setRace(race));
                                        }))))
                .then(Commands.literal("path")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("path", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(FightingPath.values()).map(FightingPath::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "path");
                                            FightingPath path = Arrays.stream(FightingPath.values()).filter(p -> p.id().equals(id)).findFirst()
                                                    .orElseThrow(() -> UNKNOWN_PATH.create(id));
                                            return apply(ctx, "Set path " + path.id() + " for", d -> d.setPath(path));
                                        }))))
                .then(Commands.literal("refill")
                        .executes(ctx -> applyTo(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), "Refilled", PlayerData::refill))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> apply(ctx, "Refilled", PlayerData::refill))))
                .then(Commands.literal("technique")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("technique", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Techniques.all().stream().map(Technique::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "technique");
                                            Technique t = Techniques.byId(id);
                                            if (t == null) throw UNKNOWN_TECHNIQUE.create(id);
                                            int fired = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                TechniqueHandler.Result r = TechniqueHandler.use(p, t);
                                                if (r == TechniqueHandler.Result.FIRED) fired++;
                                                else ctx.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + ": " + r));
                                            }
                                            int n = fired;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Fired " + id + " for " + n + " player(s)"), true);
                                            return n;
                                        }))))
                .then(Commands.literal("fly")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                        boolean on = FlightHandler.toggle(p);
                                        ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " flight: " + on), true);
                                        n++;
                                    }
                                    return n;
                                })))
                .then(Commands.literal("charge")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean on = BoolArgumentType.getBool(ctx, "on");
                                            return apply(ctx, "Set charging " + on + " for", d -> d.setCharging(on));
                                        }))))
                .then(Commands.literal("guard")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean on = BoolArgumentType.getBool(ctx, "on");
                                            return apply(ctx, "Set guarding " + on + " for", d -> d.setGuarding(on));
                                        }))))
                .then(Commands.literal("train")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("attribute", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Attribute.values()).map(Attribute::id), b))
                                        .then(Commands.argument("times", IntegerArgumentType.integer(1, AttributeTraining.MAX_STEPS_PER_REQUEST))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "attribute");
                                                    Attribute a = Attribute.byId(id);
                                                    if (a == null) throw UNKNOWN_FIELD.create(id);
                                                    int times = IntegerArgumentType.getInteger(ctx, "times");
                                                    return apply(ctx, "Spent TP on " + id + " (up to " + times + ") for", d -> AttributeTraining.upgrade(d, a, times));
                                                })))))
                .then(Commands.literal("devshot")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> devshot(ctx, 0))
                                        .then(Commands.argument("delayTicks", IntegerArgumentType.integer(0, 200))
                                                .executes(ctx -> devshot(ctx, IntegerArgumentType.getInteger(ctx, "delayTicks")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> apply(ctx, "Reset character of", PlayerData::reset)))));
    }

    private static int devshot(CommandContext<CommandSourceStack> ctx, int delay) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        var targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(p -> ModNetwork.sendTo(p, new DevScreenshotPacket(name, delay)));
        return targets.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "field");
        StatField field = StatField.byId(id);
        if (field == null) throw UNKNOWN_FIELD.create(id);
        double value = DoubleArgumentType.getDouble(ctx, "value");
        return apply(ctx, "Set " + id + " = " + value + " for", d -> field.set(d, value));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, String verb, Consumer<PlayerData> action) throws CommandSyntaxException {
        return applyTo(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), verb, action);
    }

    private static int applyTo(CommandSourceStack src, Collection<ServerPlayer> targets, String verb, Consumer<PlayerData> action) {
        int n = 0;
        for (ServerPlayer player : targets) {
            PlayerData data = ModCapabilities.getOrThrow(player);
            action.accept(data);
            data.recomputeIfStale();
            n++;
        }
        int count = n;
        src.sendSuccess(() -> Component.literal(verb + " " + count + " player(s)"), true);
        return count;
    }

    private static int stats(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            PlayerData d = ModCapabilities.getOrThrow(player);
            d.recomputeIfStale();
            DerivedStats s = d.getDerived();
            StringBuilder attrs = new StringBuilder();
            for (Attribute a : Attribute.values()) {
                attrs.append(a.shortName().toUpperCase()).append(' ').append(d.getAttribute(a)).append("  ");
            }
            String text = String.format(
                    "== %s ==%n" +
                    "Race %s | Path %s | Level %d | BP %,d | Created %s%n" +
                    "%s%n" +
                    "TP %,d | Release %d%% | Alignment %d | Age %.1f/%.1f%n" +
                    "Body %.0f/%.0f | Ki %.0f/%.0f | Stamina %.0f/%.0f%n" +
                    "Melee %.1f | Ki dmg %.1f | Def %.1f | Eva %.1f%% | KiCtl %.1f%% | Spirit x%.2f | Transfer %.1f/s | AtkSpd +%.1f%% | Move +%.1f%%",
                    player.getGameProfile().getName(),
                    d.getRace().id(), d.getPath().id(), StatCalculator.level(d), StatCalculator.battlePower(d), d.isCharacterCreated(),
                    attrs.toString().trim(),
                    d.getTrainingPoints(), d.getReleasePercent(), d.getAlignment(), d.getPhysicalAge(), d.getMentalAge(),
                    d.getBody(), s.maxBody(), d.getKi(), s.maxKi(), d.getStamina(), s.maxStamina(),
                    s.meleeDamage(), s.kiDamage(), s.defense(), s.evasion() * 100, s.kiControl() * 100, s.spiritModifier(),
                    s.kiTransfer(), s.attackSpeed() * 100, s.moveSpeed() * 100);
            src.sendSuccess(() -> Component.literal(text), false);
        }
        return targets.size();
    }
}
