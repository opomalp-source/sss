package com.dbzenith.duel;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.CombatLog;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.combat.BodyHealth;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.DuelCamPacket;
import com.dbzenith.network.DuelResultPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duels (CX-19 phase 9). A challenge ({@code /duel <player>}) waits for an Accept; then:
 * <ul>
 *   <li>both are set five blocks either side of the spot between them, facing each other, healed, the special meter
 *       empty; 3, 2, 1, FIGHT, while they are held in place;</li>
 *   <li>the arena is a circle round that spot ({@code duelArenaRadius}), ringed with light; out of it for
 *       {@code duelRingOutSeconds} loses the round;</li>
 *   <li>a blow that would kill knocks out instead (the round); the clock ({@code duelTimeLimit}) gives the round to the
 *       one with more health left; best of 1, 3 or 5;</li>
 *   <li>arena rules: duelists fight only each other (no PvP mode needed, and nobody else may hurt them or be hurt by
 *       them); under melee rules, no ki blasts or techniques;</li>
 *   <li>at the end each side gets the results screen (stats, rounds, rating change), the ladder is updated, and
 *       watchers ({@code /duel watch}) are put back as they were.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Duels {
    private static final int COUNTDOWN = 60, BETWEEN = 60, SPACING = 5;

    private record Challenge(UUID from, String fromName, int bestOf, Duel.Rules rules, long expires) {}

    private static final Map<UUID, List<Challenge>> CHALLENGES = new HashMap<>();
    private static final List<Duel> ACTIVE = new ArrayList<>();

    private Duels() {}

    // ------------------------------------------------------------------ asking

    public static Duel of(LivingEntity e) {
        if (!(e instanceof ServerPlayer)) return null;
        for (Duel d : ACTIVE) if (d.has(e.getUUID()) && d.phase != Duel.Phase.OVER) return d;
        return null;
    }

    public static Duel watching(ServerPlayer p) {
        for (Duel d : ACTIVE) if (d.watchers.containsKey(p.getUUID())) return d;
        return null;
    }

    public static boolean inDuel(LivingEntity e) {
        return of(e) != null;
    }

    /** Opponents in the same live duel. */
    public static boolean opponents(LivingEntity a, LivingEntity b) {
        Duel d = of(a);
        return d != null && d.has(b.getUUID()) && a != b;
    }

    /** Ki attacks allowed (not under melee rules, and not before the fight). */
    public static boolean kiAllowed(LivingEntity p) {
        Duel d = of(p);
        return d == null || d.rules == Duel.Rules.FULL && d.phase == Duel.Phase.FIGHTING;
    }

    static int radius() {
        return DBZConfig.SERVER.duelArenaRadius.get();
    }

    /** Why a challenge can't happen, or null. */
    static String problem(ServerPlayer a, ServerPlayer b) {
        if (a == b) return "message.dbzenith.duel_self";
        if (inDuel(a) || inDuel(b)) return "message.dbzenith.duel_busy";
        if (com.dbzenith.tournament.Tournament.isFighter(a) || com.dbzenith.tournament.Tournament.isFighter(b)) return "message.dbzenith.duel_busy";
        if (a.level() != b.level() || a.distanceTo(b) > 64) return "message.dbzenith.duel_far";
        if (!a.isAlive() || !b.isAlive() || a.isSpectator() || b.isSpectator()) return "message.dbzenith.duel_busy";
        return null;
    }

    public static boolean challenge(ServerPlayer from, ServerPlayer to, int bestOf, Duel.Rules rules) {
        String why = problem(from, to);
        if (why != null) {
            from.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), false);
            return false;
        }
        long expires = from.level().getGameTime() + DBZConfig.SERVER.duelChallengeSeconds.get() * 20L;
        List<Challenge> list = CHALLENGES.computeIfAbsent(to.getUUID(), k -> new ArrayList<>());
        list.removeIf(c -> c.from.equals(from.getUUID()));
        list.add(new Challenge(from.getUUID(), from.getGameProfile().getName(), bestOf, rules, expires));
        String name = from.getGameProfile().getName();
        Component accept = Component.translatable("message.dbzenith.duel_accept_button").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.dbzenith.duel_accept_hover"))));
        Component decline = Component.translatable("message.dbzenith.duel_decline_button").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel decline " + name)));
        to.sendSystemMessage(Component.translatable("message.dbzenith.duel_challenged", from.getDisplayName(), bestOf,
                Component.translatable("duel.dbzenith.rules." + rules.name().toLowerCase())).withStyle(ChatFormatting.GOLD)
                .append(" ").append(accept).append(" ").append(decline));
        from.displayClientMessage(Component.translatable("message.dbzenith.duel_sent", to.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** {@code to} answers a challenge (from {@code fromName}, or the latest). */
    public static boolean answer(ServerPlayer to, String fromName, boolean accept) {
        List<Challenge> list = CHALLENGES.get(to.getUUID());
        long now = to.level().getGameTime();
        if (list != null) list.removeIf(c -> c.expires < now);
        Challenge c = null;
        if (list != null) for (Challenge x : list) if (fromName == null || x.fromName.equalsIgnoreCase(fromName)) c = x;
        if (c == null) {
            to.displayClientMessage(Component.translatable("message.dbzenith.duel_no_challenge").withStyle(ChatFormatting.RED), false);
            return false;
        }
        list.remove(c);
        ServerPlayer from = to.server.getPlayerList().getPlayer(c.from);
        if (from == null) {
            to.displayClientMessage(Component.translatable("message.dbzenith.duel_gone").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!accept) {
            from.displayClientMessage(Component.translatable("message.dbzenith.duel_declined", to.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            to.displayClientMessage(Component.translatable("message.dbzenith.duel_you_declined").withStyle(ChatFormatting.GRAY), false);
            return true;
        }
        String why = problem(from, to);
        if (why != null) {
            to.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), false);
            return false;
        }
        start(from, to, c.bestOf, c.rules);
        return true;
    }

    /** Starts a duel at once (accepted, or tests). */
    public static Duel start(ServerPlayer a, ServerPlayer b, int bestOf, Duel.Rules rules) {
        Duel d = new Duel(a, b, Math.max(1, bestOf | 1), rules);
        Vec3 dir = b.position().subtract(a.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
        d.starts[0] = new Vec3(d.center.x - dir.x * SPACING, a.getY(), d.center.z - dir.z * SPACING);
        d.starts[1] = new Vec3(d.center.x + dir.x * SPACING, b.getY(), d.center.z + dir.z * SPACING);
        ACTIVE.add(d);
        com.dbzenith.combat.PvpRules.set(a, true, true);                        // a duel is a fight: both in PvP mode (CX-20)
        com.dbzenith.combat.PvpRules.set(b, true, true);
        CombatLog.near(d.level, d.center, Component.translatable("log.dbzenith.duel_start", a.getDisplayName(), b.getDisplayName(), d.bestOf));
        beginRound(d);
        return d;
    }

    private static void beginRound(Duel d) {
        d.phase = Duel.Phase.COUNTDOWN;
        d.phaseStart = d.level.getGameTime();
        d.outSince[0] = d.outSince[1] = -1;
        for (int i = 0; i < 2; i++) {
            ServerPlayer p = d.player(i);
            if (p == null) continue;
            ModCapabilities.get(p).ifPresent(pd -> {
                pd.refill();
                pd.setSpecial(0);
                BodyHealth.mirror(p, pd);
            });
            p.removeAllEffects();
            p.clearFire();
            Vec3 s = d.starts[i];
            p.teleportTo(d.level, s.x, s.y, s.z, p.getYRot(), 0);
            ServerPlayer other = d.player(1 - i);
            if (other != null) p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(d.starts[1 - i].x, d.starts[1 - i].y + 1.6, d.starts[1 - i].z));
        }
        Component sub = d.bestOf > 1 ? Component.translatable("duel.dbzenith.round", d.round, d.wins[0], d.wins[1]) : Component.empty();
        titleAll(d, Component.literal("3").withStyle(ChatFormatting.GOLD), sub);
    }

    // ------------------------------------------------------------------ running

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        for (Duel d : new ArrayList<>(ACTIVE)) tick(d, event.getServer());
        ACTIVE.removeIf(d -> d.phase == Duel.Phase.OVER);
        long now = event.getServer().overworld().getGameTime();
        CHALLENGES.values().forEach(l -> l.removeIf(c -> c.expires < now));
    }

    static void tick(Duel d, MinecraftServer server) {
        if (d.phase == Duel.Phase.OVER) return;
        long now = d.level.getGameTime(), age = now - d.phaseStart;
        for (int i = 0; i < 2; i++) {
            ServerPlayer p = d.player(i);
            if (p == null || p.level() != d.level) {                            // left: the other wins
                finish(d, 1 - i, "disconnect");
                return;
            }
        }
        switch (d.phase) {
            case COUNTDOWN -> {
                for (int i = 0; i < 2; i++) hold(d.player(i), d.starts[i]);
                if (age == 20) titleAll(d, Component.literal("2").withStyle(ChatFormatting.GOLD), Component.empty());
                if (age == 40) titleAll(d, Component.literal("1").withStyle(ChatFormatting.GOLD), Component.empty());
                if (age >= COUNTDOWN) {
                    d.phase = Duel.Phase.FIGHTING;
                    d.phaseStart = d.roundStart = now;
                    titleAll(d, Component.translatable("duel.dbzenith.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty());
                    for (int i = 0; i < 2; i++) {
                        ServerPlayer p = d.player(i);
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
                    }
                }
            }
            case FIGHTING -> {
                ring(d, now);
                trackCombos(d);
                for (int i = 0; i < 2; i++) {
                    ServerPlayer p = d.player(i);
                    double off = Math.hypot(p.getX() - d.center.x, p.getZ() - d.center.z);
                    if (off > radius()) {
                        if (d.outSince[i] < 0) d.outSince[i] = now;
                        long left = DBZConfig.SERVER.duelRingOutSeconds.get() * 20L - (now - d.outSince[i]);
                        if (left <= 0) {
                            decide(d, 1 - i, "ring_out");
                            return;
                        }
                        if ((now - d.outSince[i]) % 20 == 0) {
                            p.displayClientMessage(Component.translatable("message.dbzenith.duel_return", (left + 19) / 20).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        }
                    } else d.outSince[i] = -1;
                }
                if (now - d.roundStart >= DBZConfig.SERVER.duelTimeLimit.get() * 20L) {   // time: more health left wins the round
                    double h0 = healthFraction(d.player(0)), h1 = healthFraction(d.player(1));
                    decide(d, Math.abs(h0 - h1) < 0.01 ? -1 : h0 > h1 ? 0 : 1, "time");
                }
            }
            case BETWEEN -> {
                if (age >= BETWEEN) {
                    d.round++;
                    beginRound(d);
                }
            }
            default -> { }
        }
    }

    /** Keeps a duelist on their mark during the countdown. */
    static void hold(ServerPlayer p, Vec3 at) {
        if (p == null) return;
        if (p.position().distanceToSqr(at) > 0.09) p.teleportTo(at.x, at.y, at.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
    }

    /** The arena's edge, in light, every half second. */
    static void ring(Duel d, long now) {
        if (now % 10 != 0) return;
        int r = radius(), n = Math.max(24, r * 3);
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            d.level.sendParticles(ParticleTypes.END_ROD, d.center.x + Math.cos(a) * r, d.center.y + 0.3, d.center.z + Math.sin(a) * r, 1, 0, 0.15, 0, 0);
        }
    }

    /** The longest combo each side has put on the other. */
    static void trackCombos(Duel d) {
        for (int i = 0; i < 2; i++) {
            ServerPlayer victim = d.player(i), attacker = d.player(1 - i);
            com.dbzenith.combat.engine.Fighter g = com.dbzenith.combat.engine.CombatEngine.peek(victim);
            if (g != null && attacker != null && g.comboFrom() == attacker.getId()) {
                d.stats[1 - i].maxCombo = Math.max(d.stats[1 - i].maxCombo, g.comboHits());
            }
        }
    }

    static double healthFraction(ServerPlayer p) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        return d == null ? p.getHealth() / p.getMaxHealth() : d.getBody() / Math.max(1, d.getDerived().maxBody());
    }

    /** A round is over: {@code winner} 0 or 1, or -1 for a drawn round. */
    static void decide(Duel d, int winner, String reason) {
        if (d.phase != Duel.Phase.FIGHTING) return;
        if (winner >= 0) d.wins[winner]++;
        int need = d.bestOf / 2 + 1;
        boolean last = winner >= 0 && d.wins[winner] >= need || d.round >= d.bestOf + 2;   // drawn rounds can't go on forever
        if (d.bestOf == 1 && winner < 0) last = true;
        if (last) {
            finish(d, d.wins[0] == d.wins[1] ? -1 : d.wins[0] > d.wins[1] ? 0 : 1, reason);
            return;
        }
        d.phase = Duel.Phase.BETWEEN;
        d.phaseStart = d.level.getGameTime();
        Component who = winner < 0 ? Component.translatable("duel.dbzenith.round_draw") : Component.translatable("duel.dbzenith.round_to", d.names[winner]);
        titleAll(d, who.copy().withStyle(ChatFormatting.GOLD), Component.translatable("duel.dbzenith.reason." + reason)
                .append("  " + d.wins[0] + " - " + d.wins[1]));
    }

    /** The duel is over: results, the ladder, watchers back where they were. */
    static void finish(Duel d, int winner, String reason) {
        if (d.phase == Duel.Phase.OVER) return;
        d.phase = Duel.Phase.OVER;
        MinecraftServer server = d.level.getServer();
        int[] delta = DuelLadder.get(server).result(d.ids[0], d.names[0], d.ids[1], d.names[1], winner);
        DuelLadder ladder = DuelLadder.get(server);
        for (int i = 0; i < 2; i++) {
            ServerPlayer p = d.player(i);
            if (p == null) continue;
            ModCapabilities.get(p).ifPresent(pd -> {                             // nobody leaves a duel half dead
                pd.refill();
                BodyHealth.mirror(p, pd);
            });
            p.removeAllEffects();
            ModNetwork.sendTo(p, new DuelResultPacket(winner < 0 ? 0 : winner == i ? 1 : 2, reason, d.names[i], d.names[1 - i], d.wins[i], d.wins[1 - i],
                    d.stats[i].toArray(), d.stats[1 - i].toArray(), ladder.record(d.ids[i], d.names[i]).rating, delta[i], d.bestOf, d.rules.name().toLowerCase(), false));
        }
        for (Duel.Watcher w : new ArrayList<>(d.watchers.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(w.id());
            if (p != null) ModNetwork.sendTo(p, new DuelResultPacket(winner < 0 ? 0 : 3, reason, d.names[0], d.names[1], d.wins[0], d.wins[1],
                    d.stats[0].toArray(), d.stats[1].toArray(), 0, 0, d.bestOf, d.rules.name().toLowerCase(), true));
            unwatch(p, d, w);
        }
        d.watchers.clear();
        Component line = winner < 0 ? Component.translatable("log.dbzenith.duel_draw", d.names[0], d.names[1])
                : Component.translatable("log.dbzenith.duel_won", d.names[winner], d.names[1 - winner], d.wins[winner] + "-" + d.wins[1 - winner],
                Component.translatable("duel.dbzenith.reason." + reason));
        CombatLog.near(d.level, d.center, line);
    }

    /** Gives up the duel {@code p} is in. */
    public static boolean forfeit(ServerPlayer p) {
        Duel d = of(p);
        if (d == null) return false;
        int i = d.indexOf(p.getUUID());
        if (d.phase == Duel.Phase.FIGHTING || d.phase == Duel.Phase.BETWEEN || d.phase == Duel.Phase.COUNTDOWN) finish(d, 1 - i, "forfeit");
        return true;
    }

    // ------------------------------------------------------------------ watching

    public static boolean watch(ServerPlayer w, ServerPlayer duelist) {
        Duel d = of(duelist);
        if (d == null || d.has(w.getUUID()) || inDuel(w)) return false;
        Duel already = watching(w);
        if (already != null) unwatch(w, already, already.watchers.remove(w.getUUID()));
        d.watchers.put(w.getUUID(), new Duel.Watcher(w.getUUID(), w.gameMode.getGameModeForPlayer(), w.position(), w.getYRot(), w.getXRot()));
        w.setGameMode(GameType.SPECTATOR);
        w.teleportTo(d.level, d.center.x, d.center.y + 6, d.center.z + radius() * 0.6, w.getYRot(), 20);
        ServerPlayer a = d.player(0), b = d.player(1);
        ModNetwork.sendTo(w, new DuelCamPacket(a == null ? -1 : a.getId(), b == null ? -1 : b.getId()));
        return true;
    }

    public static boolean leaveWatching(ServerPlayer w) {
        Duel d = watching(w);
        if (d == null) return false;
        unwatch(w, d, d.watchers.remove(w.getUUID()));
        return true;
    }

    static void unwatch(ServerPlayer p, Duel d, Duel.Watcher w) {
        if (p == null || w == null) return;
        ModNetwork.sendTo(p, new DuelCamPacket(-1, -1));
        p.setGameMode(w.mode());
        p.teleportTo(d.level, w.pos().x, w.pos().y, w.pos().z, w.yaw(), w.pitch());
    }

    // ------------------------------------------------------------------ hooks

    /** What would kill a duelist knocks them out (the round); nobody dies in the countdown or between rounds. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        Duel d = of(event.getEntity());
        if (d == null) return;
        ServerPlayer p = (ServerPlayer) event.getEntity();
        event.setCanceled(true);
        ModCapabilities.get(p).ifPresent(pd -> {
            pd.setBody(Math.max(1, pd.getDerived().maxBody() * 0.1));
            BodyHealth.mirror(p, pd);
        });
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.1f));
        if (d.phase == Duel.Phase.FIGHTING) {
            int i = d.indexOf(p.getUUID());
            ServerPlayer winner = d.player(1 - i);
            if (winner != null) CombatLog.near(d.level, d.center, Component.translatable("log.dbzenith.knockout", winner.getDisplayName(), p.getDisplayName()));
            decide(d, 1 - i, "knockout");
        }
    }

    /** Before the bell and between rounds, nothing hurts a duelist. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        Duel d = of(event.getEntity());
        if (d != null && d.phase != Duel.Phase.FIGHTING) event.setCanceled(true);
    }

    /** A blow between duelists landed (from CombatEvents): the numbers for the results. */
    public static void onBlow(LivingEntity attacker, LivingEntity victim, double dealt) {
        Duel d = of(attacker);
        if (d == null || !d.has(victim.getUUID())) return;
        Duel.Stats s = d.stats[d.indexOf(attacker.getUUID())];
        s.damage += dealt;
        s.hits++;
    }

    /** A perfect guard or vanish by a duelist. */
    public static void onDefense(LivingEntity who, boolean perfectGuard) {
        Duel d = of(who);
        if (d == null) return;
        Duel.Stats s = d.stats[d.indexOf(who.getUUID())];
        if (perfectGuard) s.perfectGuards++;
        else s.vanishes++;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Duel w = watching(p);
        if (w != null) unwatch(p, w, w.watchers.remove(p.getUUID()));
        Duel d = of(p);
        if (d != null) finish(d, 1 - d.indexOf(p.getUUID()), "disconnect");
        CHALLENGES.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (Duel d : new ArrayList<>(ACTIVE)) {
            for (Duel.Watcher w : new ArrayList<>(d.watchers.values())) unwatch(event.getServer().getPlayerList().getPlayer(w.id()), d, w);
            d.phase = Duel.Phase.OVER;
        }
        ACTIVE.clear();
        CHALLENGES.clear();
    }

    // ------------------------------------------------------------------ helpers

    static void titleAll(Duel d, Component title, Component sub) {
        List<ServerPlayer> to = new ArrayList<>();
        for (int i = 0; i < 2; i++) if (d.player(i) != null) to.add(d.player(i));
        for (UUID w : d.watchers.keySet()) {
            ServerPlayer p = d.level.getServer().getPlayerList().getPlayer(w);
            if (p != null) to.add(p);
        }
        for (ServerPlayer p : to) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 6));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    /** Tests: every duel at once. */
    public static List<Duel> active() {
        return ACTIVE;
    }

    /** Tests: run one duel's tick now. */
    public static void tickForTest(Duel d) {
        tick(d, d.level.getServer());
    }

    /** Tests: straight to the fight. */
    public static void skipCountdown(Duel d) {
        d.phase = Duel.Phase.FIGHTING;
        d.phaseStart = d.roundStart = d.level.getGameTime();
    }

    /** Tests: a round decided now. */
    public static void decideForTest(Duel d, int winner, String reason) {
        decide(d, winner, reason);
    }
}
