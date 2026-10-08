package com.dbzenith.command;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.StatField;
import com.dbzenith.combat.HeavyStrike;
import com.dbzenith.ki.DashHandler;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.network.DevScreenshotPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.AttributeTraining;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.GreatApe;
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
import net.minecraft.world.entity.LivingEntity;
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
    private static final DynamicCommandExceptionType UNKNOWN_FORM =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown form: " + id));
    private static final DynamicCommandExceptionType UNKNOWN_TECHNIQUE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown technique: " + id));

    private DBZCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /pvp, /pvp on, /pvp off: anyone, for themselves (CX-19)
        dispatcher.register(Commands.literal("pvp")
                .executes(ctx -> com.dbzenith.combat.PvpRules.toggle(ctx.getSource().getPlayerOrException(), null) ? 1 : 0)
                .then(Commands.literal("on").executes(ctx -> com.dbzenith.combat.PvpRules.toggle(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
                .then(Commands.literal("off").executes(ctx -> com.dbzenith.combat.PvpRules.toggle(ctx.getSource().getPlayerOrException(), false) ? 1 : 0)));
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
                                            return apply(ctx, "Set race " + race.id() + " for", d -> com.dbzenith.race.CharacterCreation.applyRace(d, race));
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
                                                TechniqueHandler.Result r = TechniqueHandler.use(p, t, true);
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
                .then(Commands.literal("form")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Forms.all().stream().map(Form::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "form");
                                            if (!Forms.exists(id)) throw UNKNOWN_FORM.create(id);
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                FormHandler.enter(p, ModCapabilities.getOrThrow(p), Forms.byId(id));
                                                n++;
                                            }
                                            int count = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Forced form " + id + " on " + count + " player(s)"), true);
                                            return n;
                                        }))))
                .then(Commands.literal("transform")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) if (FormHandler.transformUp(p)) n++;
                                    int count = n;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Transformed " + count + " player(s)"), true);
                                    return n;
                                })))
                .then(Commands.literal("mastery")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Forms.all().stream().map(Form::id), b))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "form");
                                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                                    return apply(ctx, "Set " + id + " mastery " + v + " for", d -> d.setMastery(id, v));
                                                })))))
                .then(Commands.literal("face")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("parts", StringArgumentType.word())   // one digit a part: eyes brows mouth nose ears extra
                                        .executes(ctx -> {
                                            String parts = StringArgumentType.getString(ctx, "parts");
                                            int face = 0;
                                            com.dbzenith.appearance.FaceParts.Part[] all = com.dbzenith.appearance.FaceParts.Part.values();
                                            for (int i = 0; i < Math.min(all.length, parts.length()); i++) {
                                                face = com.dbzenith.appearance.FaceParts.with(face, all[i], Character.digit(parts.charAt(i), 10));
                                            }
                                            int f = face;
                                            return apply(ctx, "Set face " + parts + " for", d -> d.setFace(f));
                                        }))))
                .then(Commands.literal("move")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("sweep", "breaker", "knockdown", "uppercut", "rush", "zhit", "dodge", "recover"), b))
                                        .executes(ctx -> {
                                            String move = StringArgumentType.getString(ctx, "move");
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.getOrThrow(p);
                                                long now = p.level().getGameTime();
                                                int anim = switch (move) {
                                                    case "sweep" -> { com.dbzenith.combat.engine.CombatEngine.press(p, new com.dbzenith.combat.engine.Moves.Input(com.dbzenith.combat.engine.Move.Button.HEAVY, com.dbzenith.combat.engine.Move.Dir.BACK, false, false, true)); yield -1; }
                                                    case "breaker" -> { d.setLastFoeHitTick(now); p.setShiftKeyDown(true);
                                                        com.dbzenith.combat.engine.Evasion.dashKey(p, d, 0, 0); p.setShiftKeyDown(false); yield -1; }
                                                    case "knockdown" -> { com.dbzenith.combat.CombatMoves.knockDown(p, now); yield -1; }
                                                    case "uppercut" -> com.dbzenith.network.AnimEventPacket.UPPERCUT;
                                                    case "rush" -> com.dbzenith.network.AnimEventPacket.RUSH;
                                                    case "zhit" -> com.dbzenith.network.AnimEventPacket.ZHIT;
                                                    case "dodge" -> com.dbzenith.network.AnimEventPacket.DODGE;
                                                    case "recover" -> com.dbzenith.network.AnimEventPacket.RECOVER;
                                                    default -> -2;
                                                };
                                                if (anim == -2) continue;
                                                if (anim >= 0) com.dbzenith.network.ModNetwork.sendToTrackingAndSelf(p, new com.dbzenith.network.AnimEventPacket(p.getId(), anim, 0));
                                                n++;
                                            }
                                            int count = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Move " + move + " for " + count + " player(s)"), true);
                                            return n;
                                        }))))
                .then(Commands.literal("skill")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                com.dbzenith.race.RacialSkills.universal().stream().map(com.dbzenith.race.RacialSkill::id), b))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
                                                .executes(ctx -> {
                                                    com.dbzenith.race.RacialSkill s = com.dbzenith.race.RacialSkills.byId(StringArgumentType.getString(ctx, "skill"));
                                                    if (s == null || !s.learned()) {
                                                        ctx.getSource().sendFailure(Component.literal("Not a universal skill"));
                                                        return 0;
                                                    }
                                                    int lv = Math.min(s.maxLevel(), IntegerArgumentType.getInteger(ctx, "level"));
                                                    return apply(ctx, "Set " + s.id() + " level " + lv + " for", d -> {
                                                        d.setSkillLevel(s.id(), lv);
                                                        if (lv > 0 && s.isActive()) d.setSkillSelected(s.id());
                                                    });
                                                })))))
                .then(Commands.literal("racial")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                com.dbzenith.race.RacialSkills.all().stream().filter(com.dbzenith.race.RacialSkill::isActive)
                                                        .map(com.dbzenith.race.RacialSkill::id), b))
                                        .executes(ctx -> {
                                            com.dbzenith.race.RacialSkill s = com.dbzenith.race.RacialSkills.byId(StringArgumentType.getString(ctx, "skill"));
                                            if (s == null || !s.isActive()) {
                                                ctx.getSource().sendFailure(Component.literal("Not an active racial skill"));
                                                return 0;
                                            }
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.getOrThrow(p);
                                                if (com.dbzenith.race.RacialSkillEffects.use(p, d, s)) n++;
                                            }
                                            int count = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Used " + s.id() + " for " + count + " player(s)"), true);
                                            return n;
                                        }))))
                .then(Commands.literal("godki")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, com.dbzenith.transform.GodKi.MAX))
                                        .executes(ctx -> {
                                            int lv = IntegerArgumentType.getInteger(ctx, "level");
                                            return apply(ctx, "Set god ki level " + lv + " for", d -> {
                                                d.setFlag(com.dbzenith.transform.GodKi.FLAG, lv > 0);
                                                d.setGodKiXp(com.dbzenith.transform.GodKi.xpFor(lv));
                                            });
                                        }))))
                .then(Commands.literal("flag")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("god_ki"), b))
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    String flag = StringArgumentType.getString(ctx, "flag");
                                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                                    return apply(ctx, "Set flag " + flag + "=" + on + " for", d -> d.setFlag(flag, on));
                                                })))))
                .then(Commands.literal("learn")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("technique", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Techniques.all().stream().map(Technique::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "technique");
                                            Technique t = Techniques.byId(id);
                                            if (t == null) throw UNKNOWN_TECHNIQUE.create(id);
                                            return apply(ctx, "Taught " + id + " to", d -> com.dbzenith.skill.TechniqueLibrary.learnFree(d, t));
                                        }))))
                .then(Commands.literal("summon")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            var who = EntityArgument.getPlayer(ctx, "player");
                                            for (var set : com.dbzenith.dragonball.BallSet.values()) {           // whichever set is gathered there
                                                if (com.dbzenith.dragonball.DragonBalls.trySummon(ctx.getSource().getLevel(), who, pos, set)) return 1;
                                            }
                                            return 0;
                                        }))))
                .then(Commands.literal("curse")                                      // the Black Star curse (12d)
                        .then(Commands.literal("start").executes(ctx -> {
                            com.dbzenith.dragonball.DragonBalls.curse(ctx.getSource().getServer(), ctx.getSource().getServer().overworld().getGameTime());
                            return 1;
                        }))
                        .then(Commands.literal("doom").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            com.dbzenith.dragonball.DragonBallData.get(server, com.dbzenith.dragonball.BallSet.BLACK_STAR).setCurse(server.overworld().getGameTime(), false);
                            com.dbzenith.dragonball.DragonBalls.tickCurse(server, server.overworld().getGameTime() + 1);
                            return 1;
                        }))
                        .then(Commands.literal("lift").executes(ctx -> {
                            com.dbzenith.dragonball.DragonBalls.liftCurse(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("meteor").executes(ctx -> {             // one meteor, a dozen blocks ahead of you
                            var p = ctx.getSource().getPlayerOrException();
                            var ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(14));
                            int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ahead.x), (int) Math.floor(ahead.z));
                            com.dbzenith.dragonball.DragonBalls.meteorAt(p.serverLevel(), new net.minecraft.world.phys.Vec3(ahead.x, y, ahead.z), p.level().getGameTime());
                            return 1;
                        })))
                .then(Commands.literal("tail")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean on = BoolArgumentType.getBool(ctx, "on");
                                            return apply(ctx, "Set tail " + on + " for", d -> d.setTail(on));
                                        }))))
                .then(Commands.literal("pvp")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                    var targets = EntityArgument.getPlayers(ctx, "targets");
                                    for (ServerPlayer p : targets) com.dbzenith.combat.PvpRules.set(p, on, true);
                                    ctx.getSource().sendSuccess(() -> Component.literal("PvP mode " + (on ? "on" : "off") + " for " + targets.size() + " player(s)"), true);
                                    return targets.size();
                                }))))
                .then(Commands.literal("strike")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("button", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("light", "heavy"), b))
                                        .executes(ctx -> strike(ctx, "neutral"))
                                        .then(Commands.argument("direction", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("neutral", "forward", "back", "side", "up", "down"), b))
                                                .executes(ctx -> strike(ctx, StringArgumentType.getString(ctx, "direction")))))))
                .then(Commands.literal("special")                                   // the special meter (CX-19)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0)).executes(ctx -> {
                                    double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount");
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) ModCapabilities.get(p).ifPresent(d -> d.setSpecial(v));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Special meter set to " + v), true);
                                    return 1;
                                }))))
                .then(Commands.literal("style")                                     // CX-20: fighting styles
                        .then(Commands.literal("grant").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("style", StringArgumentType.word()).suggests(DBZCommand::suggestStyles)
                                        .executes(ctx -> styleGrant(ctx, true)))))
                        .then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("style", StringArgumentType.word()).suggests(DBZCommand::suggestStyles)
                                        .executes(ctx -> styleGrant(ctx, false)))))
                        .then(Commands.literal("equip").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.dbzenith.style.StyleSlot.ALL).map(s -> s.id), b))
                                        .then(Commands.argument("style", StringArgumentType.word()).suggests(DBZCommand::suggestStyles).executes(ctx -> {
                                            String slot = StringArgumentType.getString(ctx, "slot"), style = StringArgumentType.getString(ctx, "style");
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.getOrThrow(p);
                                                if (com.dbzenith.style.StyleLogic.equip(p, d, slot, style.equals("default") ? "" : style)) n++;
                                            }
                                            int count = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Set " + slot + " to " + style + " for " + count + " player(s)"), true);
                                            return count;
                                        })))))
                        .then(Commands.literal("affinity").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("master", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.dbzenith.style.MasterRoster.values()).map(com.dbzenith.style.MasterRoster::id), b))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, com.dbzenith.style.StyleLogic.MAX_AFFINITY)).executes(ctx -> {
                                            String m = StringArgumentType.getString(ctx, "master");
                                            int v = IntegerArgumentType.getInteger(ctx, "value");
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) ModCapabilities.getOrThrow(p).setAffinity(m, v);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Affinity with " + m + " set to " + v), true);
                                            return 1;
                                        })))))
                        .then(Commands.literal("training").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("master", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.dbzenith.style.MasterRoster.values()).map(com.dbzenith.style.MasterRoster::id), b))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                                            String m = StringArgumentType.getString(ctx, "master");
                                            int v = IntegerArgumentType.getInteger(ctx, "minutes");
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.getOrThrow(p);
                                                d.addTrainedSeconds(m, v * 60 - d.getTrainedSeconds(m));
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Training with " + m + " set to " + v + " min"), true);
                                            return 1;
                                        }))))))
                .then(Commands.literal("unlockform")                                // CX-20: unlock a form outright (past level, mastery, flags)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Forms.all().stream().map(Form::id), b))
                                        .executes(ctx -> unlockForm(ctx, true))
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(ctx -> unlockForm(ctx, BoolArgumentType.getBool(ctx, "on")))))))
                .then(Commands.literal("meter")                                     // the PvP meters (CX-20): set a bar's value
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("bar", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("form", "tech", "both"), b))
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100)).executes(ctx -> {
                                            String bar = StringArgumentType.getString(ctx, "bar");
                                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) ModCapabilities.get(p).ifPresent(d -> {
                                                if (!bar.equals("tech")) d.setFormMeter(v);
                                                if (!bar.equals("form")) d.setTechMeter(v);
                                            });
                                            ctx.getSource().sendSuccess(() -> Component.literal("Meter " + bar + " set to " + v), true);
                                            return 1;
                                        })))))
                .then(Commands.literal("netstats")                                  // netcode (CX-19 phase 7)
                        .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                long[] s = com.dbzenith.network.InputGuard.stats(p);
                                String line = String.format("%s: ping %d ms, lag compensation %d tick(s), inputs %d accepted / %d dropped, %d violation(s) this minute; %d entities with position history",
                                        p.getGameProfile().getName(), p.latency, com.dbzenith.combat.engine.LagComp.rewindTicks(p), s[0], s[1], s[2],
                                        com.dbzenith.combat.engine.LagComp.tracked());
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        })))
                .then(Commands.literal("lockon")                                    // lock-on (CX-19 phase 6): as if from the key
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                        com.dbzenith.combat.engine.Targeting.set(p, -1);
                                        com.dbzenith.network.ModNetwork.sendTo(p, new com.dbzenith.network.LockOnPacket(-1));
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Lock released"), true);
                                    return 1;
                                })
                                .then(Commands.argument("target", EntityArgument.entity()).executes(ctx -> {
                                    var target = EntityArgument.getEntity(ctx, "target");
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                        if (!com.dbzenith.combat.engine.Targeting.set(p, target.getId())) continue;
                                        com.dbzenith.network.ModNetwork.sendTo(p, new com.dbzenith.network.LockOnPacket(target.getId()));
                                        n++;
                                    }
                                    int locked = n;
                                    ctx.getSource().sendSuccess(() -> Component.literal(locked + " player(s) locked onto " + target.getName().getString()), true);
                                    return locked;
                                }))))
                .then(Commands.literal("kiblast")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("charge", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-1, 1)).executes(ctx -> {
                                    double charge = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "charge");
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) if (com.dbzenith.combat.engine.KiCombat.fireForTest(p, charge)) n++;
                                    int fired = n;
                                    ctx.getSource().sendSuccess(() -> Component.literal(fired + " blast(s) fired"), true);
                                    return fired;
                                }))))
                .then(Commands.literal("pvptag")                                    // CX-20: force a combat tag
                        .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) com.dbzenith.combat.PvpRules.tag(p, p.level().getGameTime());
                            ctx.getSource().sendSuccess(() -> Component.literal("Tagged"), true);
                            return 1;
                        })))
                .then(Commands.literal("pvpzone")
                        .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            var a = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "from");
                                            var b = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "to");
                                            String dim = ctx.getSource().getLevel().dimension().location().toString();
                                            com.dbzenith.combat.PvpZones.of(ctx.getSource().getServer()).add(name, dim, a, b);
                                            ctx.getSource().sendSuccess(() -> Component.literal("No-PvP zone " + name + " in " + dim + ": " + a.toShortString() + " to " + b.toShortString()), true);
                                            return 1;
                                        })))))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean gone = com.dbzenith.combat.PvpZones.of(ctx.getSource().getServer()).remove(name);
                            ctx.getSource().sendSuccess(() -> Component.literal(gone ? "Removed " + name : "No zone " + name), true);
                            return gone ? 1 : 0;
                        })))
                        .then(Commands.literal("list").executes(ctx -> {
                            var zones = com.dbzenith.combat.PvpZones.of(ctx.getSource().getServer()).zones();
                            StringBuilder sb = new StringBuilder(zones.size() + " no-PvP zone(s)");
                            for (var z : zones) sb.append("\n ").append(z.name()).append(" (").append(z.dimension()).append(") ")
                                    .append(z.x0()).append(' ').append(z.y0()).append(' ').append(z.z0()).append(" to ").append(z.x1()).append(' ').append(z.y1()).append(' ').append(z.z1());
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return zones.size();
                        })))
                .then(Commands.literal("tournament")
                        .then(Commands.literal("join").then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                            int n = 0;
                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) if (com.dbzenith.tournament.Tournament.join(p)) n++;
                            int count = n;
                            ctx.getSource().sendSuccess(() -> Component.literal("Entered " + count + " player(s)"), true);
                            return n;
                        })))
                        .then(Commands.literal("close").executes(ctx -> {
                            var t = com.dbzenith.tournament.Tournament.current();
                            if (t == null) return 0;
                            t.closeSignup();
                            return 1;
                        }))
                        .then(Commands.literal("result").then(Commands.argument("winner", IntegerArgumentType.integer(0, 1)).executes(ctx -> {
                            var t = com.dbzenith.tournament.Tournament.current();
                            if (t == null || t.phase() != com.dbzenith.tournament.Tournament.Phase.FIGHTING) return 0;
                            t.forceResult(IntegerArgumentType.getInteger(ctx, "winner"), "decision");
                            return 1;
                        })))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            com.dbzenith.tournament.Tournament.cancel();
                            return 1;
                        }))
                        .then(Commands.literal("build").then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
                            var at = com.dbzenith.tournament.TournamentGrounds.build(ctx.getSource().getServer().overworld(),
                                    net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
                            ctx.getSource().sendSuccess(() -> Component.literal("Tournament ring at " + at.toShortString()), true);
                            return 1;
                        }))))
                .then(Commands.literal("build")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.List.of("dojo", "outpost"), b))
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            String kind = StringArgumentType.getString(ctx, "kind");
                                            if (!kind.equals("dojo") && !kind.equals("outpost")) return 0;
                                            var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            com.dbzenith.npc.QuestNpcPlacement.buildWithNpc(ctx.getSource().getLevel(),
                                                    kind.equals("dojo") ? com.dbzenith.npc.NpcStructures.Kind.DOJO : com.dbzenith.npc.NpcStructures.Kind.OUTPOST, pos);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Built a " + kind + " at " + pos.toShortString()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("moon")
                        .executes(ctx -> {
                            var pos = ctx.getSource().getPosition();
                            GreatApe.spawnFalseMoon(ctx.getSource().getLevel(), pos.x, pos.y + 2, pos.z);
                            ctx.getSource().sendSuccess(() -> Component.literal("False moon rising"), true);
                            return 1;
                        }))
                .then(Commands.literal("cast")
                        .then(Commands.argument("caster", EntityArgument.entity())
                                .then(Commands.argument("technique", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Techniques.all().stream().map(Technique::id), b))
                                        .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "technique");
                                                    Technique t = Techniques.byId(id);
                                                    if (t == null) throw UNKNOWN_TECHNIQUE.create(id);
                                                    if (!(EntityArgument.getEntity(ctx, "caster") instanceof LivingEntity caster)) return 0;
                                                    TechniqueHandler.spawn(ctx.getSource().getLevel(), caster, t, DoubleArgumentType.getDouble(ctx, "damage"));
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Cast " + id), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("dash")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) if (DashHandler.dash(p, 1, 0)) n++;
                                    int dashed = n;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Dashed " + dashed + " player(s)"), true);
                                    return n;
                                })))
                .then(Commands.literal("heavy")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("chargeTicks", IntegerArgumentType.integer(0, 1200))
                                        .executes(ctx -> {
                                            int ticks = IntegerArgumentType.getInteger(ctx, "chargeTicks");
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.getOrThrow(p);
                                                d.startHeavyCharge();
                                                for (int i = 0; i < ticks; i++) d.tickHeavyCharge();
                                                double m = HeavyStrike.release(p, d);
                                                ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " heavy armed x" + m), true);
                                                n++;
                                            }
                                            return n;
                                        }))))
                .then(Commands.literal("variant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(com.dbzenith.race.Variant.values()).map(com.dbzenith.race.Variant::id), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "variant");
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                PlayerData d = ModCapabilities.get(p).orElse(null);
                                                if (d == null) continue;
                                                com.dbzenith.race.Variant v = com.dbzenith.race.Variant.byId(id, d.getRace());
                                                if (!v.id().equals(id)) continue;                  // not a variant of this race
                                                d.setVariant(v);
                                                d.setDestiny("");
                                                if (!com.dbzenith.transform.Forms.byId(d.getFormId()).allows(v)) d.setFormId(PlayerData.BASE_FORM);
                                                d.invalidateDerived();
                                                com.dbzenith.data.PlayerDataEvents.sync(p);
                                                n++;
                                            }
                                            int count = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal("Set variant " + id + " for " + count + " player(s)"), true);
                                            return n;
                                        }))))
                .then(Commands.literal("look")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("hair", StringArgumentType.word())
                                        .then(Commands.argument("skinTone", IntegerArgumentType.integer(-1, 0xFFFFFF))
                                                .then(Commands.argument("height", IntegerArgumentType.integer(PlayerData.MIN_HEIGHT, PlayerData.MAX_HEIGHT))
                                                        .executes(DBZCommand::look))))))
                .then(Commands.literal("bodytype")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("type", IntegerArgumentType.integer(0, 2))
                                        .executes(ctx -> {
                                            var targets = EntityArgument.getPlayers(ctx, "targets");
                                            PlayerData.BodyType type = PlayerData.BodyType.values()[IntegerArgumentType.getInteger(ctx, "type")];
                                            for (ServerPlayer p : targets) {
                                                ModCapabilities.get(p).ifPresent(d -> d.setBodyType(type));
                                                com.dbzenith.data.PlayerDataEvents.sync(p);
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Body type " + type + " for " + targets.size() + " player(s)"), true);
                                            return targets.size();
                                        }))))
                .then(Commands.literal("otherworld")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> otherworld(ctx, com.dbzenith.world.Otherworld.OTHERWORLD))))
                .then(Commands.literal("limbo")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> otherworld(ctx, com.dbzenith.world.Otherworld.LIMBO))))
                .then(Commands.literal("soul")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> apply(ctx, "Marked as dead", d -> d.setDead(true, ctx.getSource().getLevel().getGameTime(), false)))))
                .then(Commands.literal("revive")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                        PlayerData d = ModCapabilities.getOrThrow(p);
                                        d.setDead(true, -1_000_000_000L, false);
                                        if (com.dbzenith.world.Otherworld.returnToLife(p) || d.isDead()) d.setDead(false, 0, false);
                                        com.dbzenith.data.PlayerDataEvents.sync(p);
                                        n++;
                                    }
                                    int count = n;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Revived " + count + " player(s)"), true);
                                    return n;
                                })))
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

    /** /dbz look: hair (a preset name or a hair code), skin tone (-1 = own skin) and height in percent. */
    private static int look(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String hair = StringArgumentType.getString(ctx, "hair");
        String code;
        try {
            code = com.dbzenith.appearance.HairCode.Preset.valueOf(hair.toUpperCase(java.util.Locale.ROOT)).code();
        } catch (IllegalArgumentException e) {
            code = com.dbzenith.appearance.HairCode.sanitize(hair);
        }
        if (code == null) {
            ctx.getSource().sendFailure(Component.literal("Not a hair preset or code: " + hair));
            return 0;
        }
        String finalCode = code;
        int tone = IntegerArgumentType.getInteger(ctx, "skinTone");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        var targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            ModCapabilities.get(p).ifPresent(d -> {
                d.setHairCode(finalCode);
                d.setSkinTone(tone);
                d.setHeightPercent(height);
            });
            p.refreshDimensions();
            com.dbzenith.data.PlayerDataEvents.sync(p);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Restyled " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    /** Visits the other world (or Limbo), living or dead. */
    private static int otherworld(CommandContext<CommandSourceStack> ctx, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) throws CommandSyntaxException {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getServer().getLevel(dim);
        if (level == null) return 0;
        com.dbzenith.world.OtherworldBuilder.ensureBuilt(level);
        net.minecraft.world.phys.Vec3 at = dim == com.dbzenith.world.Otherworld.LIMBO ? com.dbzenith.world.Otherworld.LIMBO_ARRIVAL : com.dbzenith.world.Otherworld.ARRIVAL;
        var targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) p.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        ctx.getSource().sendSuccess(() -> Component.literal("Sent " + targets.size() + " player(s) to " + dim.location()), true);
        return targets.size();
    }

    /** Dev: a combat-engine press for players, as if from their keys (CX-19). */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestStyles(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        java.util.List<String> ids = new java.util.ArrayList<>(List.of("all", "default"));
        for (com.dbzenith.style.Styles.Style s : com.dbzenith.style.Styles.all()) ids.add(s.id());
        return SharedSuggestionProvider.suggest(ids, b);
    }

    /** /dbz style grant|revoke: one style, or all. */
    private static int styleGrant(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "style");
        java.util.List<String> ids = id.equals("all") ? com.dbzenith.style.Styles.all().stream().map(com.dbzenith.style.Styles.Style::id).toList() : List.of(id);
        if (!id.equals("all") && com.dbzenith.style.Styles.style(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown style: " + id));
            return 0;
        }
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            PlayerData d = ModCapabilities.getOrThrow(p);
            for (String s : ids) {
                if (on) com.dbzenith.style.StyleLogic.grant(d, s);
                else d.learnStyle(s, false);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal((on ? "Granted " : "Revoked ") + id), true);
        return 1;
    }

    /** /dbz unlockform: sets or clears a form's unlock flag (CX-20, for testing the form bar). */
    private static int unlockForm(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "form");
        if (!Forms.exists(id)) {
            ctx.getSource().sendFailure(Component.literal("Unknown form: " + id));
            return 0;
        }
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            ModCapabilities.get(p).ifPresent(d -> d.setFlag(com.dbzenith.transform.FormHandler.UNLOCK_FLAG + id, on));
        }
        ctx.getSource().sendSuccess(() -> Component.literal((on ? "Unlocked " : "Locked again ") + id), true);
        return 1;
    }

    private static int strike(CommandContext<CommandSourceStack> ctx, String direction) throws CommandSyntaxException {
        var button = "heavy".equals(StringArgumentType.getString(ctx, "button")) ? com.dbzenith.combat.engine.Move.Button.HEAVY : com.dbzenith.combat.engine.Move.Button.LIGHT;
        var push = switch (direction) {
            case "forward" -> com.dbzenith.combat.engine.Move.Dir.FORWARD;
            case "back" -> com.dbzenith.combat.engine.Move.Dir.BACK;
            case "side" -> com.dbzenith.combat.engine.Move.Dir.SIDE;
            default -> com.dbzenith.combat.engine.Move.Dir.NEUTRAL;
        };
        int n = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            boolean started = com.dbzenith.combat.engine.CombatEngine.press(p, new com.dbzenith.combat.engine.Moves.Input(button, push,
                    "up".equals(direction), "down".equals(direction), p.onGround()));
            String what = com.dbzenith.combat.engine.CombatEngine.describe(p);
            ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + (started ? " struck: " : " (buffered/refused): ") + what), false);
            n++;
        }
        return n;
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
