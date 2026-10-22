package com.dbzenith.nemesis;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Power;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Special encounters: characters coming back into a player's life, at a believable pace. Every half minute (every five
 * seconds for help in a fight) one online player is considered; an encounter needs:
 * <ul>
 *   <li>the player alive, in survival, not in a safe zone, duel or tournament, not dead in the other world, not in
 *       space, the Hyperbolic Time Chamber or the other world;</li>
 *   <li>the player's cooldown over ({@code nemesis.encounterCooldownMinutes}), fewer bodies out than
 *       {@code nemesis.maxPresent}, none already out for that player, and the chance ({@code encounterChance});</li>
 *   <li>a character that knows them, is away and due back ({@code returnAt}), and has a reason (below);</li>
 *   <li>safe ground in a loaded chunk 14-22 blocks off (8-12 behind them for an ambush).</li>
 * </ul>
 * Kinds: <b>revenge</b> (it lost last and burns for it), <b>rival challenge</b> (a rival wants a rematch),
 * <b>ambush</b> (a serious grudge; config), <b>training</b> (you come upon it training; it fights only if provoked),
 * and in a fight <b>intervention</b> (a rival who won't let anyone else beat you takes on your foe) and <b>alliance</b>
 * (a friend comes to help when you are hurt; config). The body leaves again when its business is done.
 */
public final class Encounters {
    public enum Kind { REVENGE, RIVAL_CHALLENGE, AMBUSH, TRAINING, INTERVENTION, ALLIANCE }

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<KiFighter, UUID> OUT = new WeakHashMap<>();
    private static int cursor;

    private Encounters() {}

    static void left(KiFighter k) {
        OUT.remove(k);
    }

    static void forget(UUID player) {
        OUT.forEach((k, p) -> {
            if (p.equals(player)) k.leaveAt = Math.min(k.leaveAt == 0 ? Long.MAX_VALUE : k.leaveAt, k.level().getGameTime());
        });
    }

    public static int out() {
        OUT.keySet().removeIf(k -> k.isRemoved() || !k.isAlive());
        return OUT.size();
    }

    /** Every half minute: one player is considered for an encounter. */
    static void tick(MinecraftServer s) {
        List<ServerPlayer> players = s.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer p = players.get(Math.floorMod(cursor++, players.size()));
        long now = s.overworld().getGameTime();
        if (!eligible(p) || now - LAST.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < NemesisConfig.cooldownTicks()) return;
        if (out() >= NemesisConfig.maxPresent() || OUT.containsValue(p.getUUID())) return;
        if (!AiRandom.chance(p.getUUID().getMostSignificantBits(), now / 600, 81, NemesisConfig.encounterChance())) return;
        Choice ch = choose(p, now, false);
        if (ch != null) start(ch, p, now);
    }

    /** A chosen character and why it comes. */
    public record Choice(NamedFighter c, Kind kind, LivingEntity foe) {}

    /** Which character comes for {@code p} now, and why (null: nobody). {@code inFight}: only help or interventions. */
    public static Choice choose(ServerPlayer p, long now, boolean inFight) {
        NemesisData d = Nemesis.data();
        LivingEntity attacker = null;
        int a = com.dbzenith.npc.ai.AiEvents.lastAttacker(p, now, 100);
        if (a >= 0 && p.level().getEntity(a) instanceof LivingEntity le && le.isAlive()) attacker = le;
        Choice best = null;
        double bestScore = 0;
        for (NamedFighter c : d.knowing(p.getUUID())) {
            if (!c.active() || c.resident() || c.status == NamedFighter.Status.PRESENT || c.returnAt > now || c.body != null) continue;
            if (type(c) == null || c.type.contains("mr_satan") || ROSTER_TYPES.contains(path(c))) continue;
            Bond b = c.players.get(p.getUUID());
            var t = c.temper();
            double tie = AiRandom.unit(c.id.getMostSignificantBits(), now / 600, 83) * 0.3;
            if (attacker != null && !(attacker instanceof KiFighter k && k.nemesisId() != null && k.nemesisId().equals(c.id))) {
                if (b.state.friendly() && NemesisConfig.alliances() && Power.health(p) < 0.5) {
                    double s = 4 + b.affinity / 50 + tie;
                    if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.ALLIANCE, attacker); }
                }
                if (b.rivalry >= 50 && b.affinity >= -20 && !b.state.friendly()) {
                    double s = 3.5 + b.rivalry / 50 + tie;
                    if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.INTERVENTION, attacker); }
                }
                continue;
            }
            if (inFight) continue;
            if (b.lastResult == -1 && (c.has(Trait.VENGEFUL) || t.pride > 0.8 || c.goal == NamedFighter.Goal.REVENGE && p.getUUID().equals(c.goalTarget))) {
                double s = 3 + b.rivalry / 30 + tie;
                if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.REVENGE, null); }
            }
            if (b.state.rival() || b.rivalry >= 35) {
                double s = 2 + b.rivalry / 40 + tie;
                if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.RIVAL_CHALLENGE, null); }
            }
            if (NemesisConfig.ambushes() && b.affinity <= -50 && (c.has(Trait.VENGEFUL) || t.caution > 0.6 || t.honor < 0.35)) {
                double s = 2.5 + tie;
                if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.AMBUSH, null); }
            }
            if (b.affinity >= -10) {
                double s = 1 + tie + (b.state.friendly() ? 1 : 0);
                if (s > bestScore) { bestScore = s; best = new Choice(c, Kind.TRAINING, null); }
            }
        }
        return best;
    }

    private static final java.util.Set<String> ROSTER_TYPES = java.util.Set.of("mr_satan", "spopovich", "pintar", "jewel", "nam", "ranfan", "yamu");

    static String path(NamedFighter c) {
        ResourceLocation r = ResourceLocation.tryParse(c.type);
        return r == null ? "" : r.getPath();
    }

    static EntityType<?> type(NamedFighter c) {
        ResourceLocation r = ResourceLocation.tryParse(c.type);
        return r == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(r);
    }

    /** Whether a player can be met now (alive, playing, somewhere a fight can happen). */
    public static boolean eligible(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null || d.isDead()) return false;
        if (com.dbzenith.combat.PvpRules.inSafeZone(p) || com.dbzenith.duel.Duels.inDuel(p) || com.dbzenith.tournament.Tournament.isFighter(p)) return false;
        if (com.dbzenith.galaxy.space.SpaceRuntime.isSpace(p.level())) return false;
        var dim = p.level().dimension();
        if (dim == com.dbzenith.world.Otherworld.OTHERWORLD || dim == com.dbzenith.world.Otherworld.LIMBO || dim == com.dbzenith.world.TimeChamber.KEY) return false;
        return !p.isPassenger();
    }

    /** Brings the character in. Returns its body, or null if there was no safe place. */
    public static KiFighter start(Choice ch, ServerPlayer p, long now) {
        NamedFighter c = ch.c();
        EntityType<?> type = type(c);
        if (type == null || !(p.level() instanceof ServerLevel level)) return null;
        Vec3 at = spot(level, p, ch.kind() == Kind.AMBUSH, now);
        if (at == null) return null;
        if (!(type.create(level) instanceof KiFighter k)) return null;
        k.moveTo(at.x, at.y, at.z, (float) (Math.atan2(p.getZ() - at.z, p.getX() - at.x) * 180 / Math.PI) - 90f, 0);
        k.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null, null);
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        c.body = null;
        Nemesis.bind(c, k, true);
        PlayerData pd = ModCapabilities.get(p).orElse(null);
        int playerLevel = KiFighter.levelFor(pd == null ? 0 : StatCalculator.fullPower(pd));
        k.showAs(c.fullName(), Math.max(1, Math.min(c.level, (int) Math.ceil(playerLevel * 1.3) + 1)), true);   // never unfairly far ahead
        k.leaveAt = now + 2400;
        k.greeted = true;
        level.addFreshEntity(k);
        OUT.put(k, p.getUUID());
        LAST.put(p.getUUID(), now);
        Dialogue.Context ctx = Nemesis.context(c, b, p);
        ctx.days = b.lastEncounter == Long.MIN_VALUE / 2 ? -1 : (now - b.lastEncounter) / 24000.0;
        switch (ch.kind()) {
            case REVENGE -> {
                k.setTarget(p);
                Dialogue.say(k, Dialogue.On.RETURN_REVENGE, ctx, p);
            }
            case RIVAL_CHALLENGE -> {
                k.setTarget(p);
                Dialogue.say(k, Dialogue.On.RETURN_RIVAL, ctx, p);
            }
            case AMBUSH -> {
                k.setTarget(p);
                Dialogue.say(k, Dialogue.On.AMBUSH, ctx, p);
            }
            case TRAINING -> {
                k.training = true;
                k.spared = p.getUUID();                                               // it leaves you be, unless you start something
                k.greeted = false;
            }
            case INTERVENTION -> {
                k.protecting = p.getUUID();
                k.spared = p.getUUID();
                k.setTarget(ch.foe());
                Dialogue.say(k, Dialogue.On.INTERVENE, ctx, p);
            }
            case ALLIANCE -> {
                k.protecting = p.getUUID();
                k.spared = p.getUUID();
                k.setTarget(ch.foe());
                Dialogue.say(k, Dialogue.On.ALLY_ARRIVES, ctx, p);
            }
        }
        if (ch.kind() != Kind.TRAINING && ch.kind() != Kind.AMBUSH) {
            com.dbzenith.network.CalloutPacket.send(p, ch.kind() == Kind.ALLIANCE ? "message.dbzenith.nemesis_ally" : "message.dbzenith.nemesis_arrives", 0xFFC24A);
        }
        c.remember(new Memory(Memory.Kind.FOUGHT, now, p.getUUID(), "encounter:" + ch.kind().name().toLowerCase(java.util.Locale.ROOT)));
        Nemesis.data().setDirty();
        return k;
    }

    /** Safe ground near the player in a loaded chunk: 14-22 blocks off (8-12 behind them for an ambush), or null. */
    static Vec3 spot(ServerLevel level, ServerPlayer p, boolean behind, long now) {
        for (int i = 0; i < 12; i++) {
            double ang = behind ? Math.toRadians(p.getYRot() + 90 + (AiRandom.unit(now, i, 85) - 0.5) * 60)
                    : AiRandom.unit(p.getUUID().getLeastSignificantBits(), now + i, 86) * Math.PI * 2;
            double dist = behind ? 8 + 4 * AiRandom.unit(now, i, 87) : 14 + 8 * AiRandom.unit(now, i, 88);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            BlockPos column = new BlockPos(x, (int) p.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getY()) > 12) continue;
            BlockPos feet = new BlockPos(x, y, z);
            var below = level.getBlockState(feet.below());
            if (!below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()) continue;
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (level.getNearestPlayer(x + 0.5, y, z + 0.5, 6, false) != null) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /** A protecting character (an ally, an intervening rival) takes on whoever attacks its player; idle a while, it leaves. */
    static void guard(NamedFighter c, KiFighter k, MinecraftServer s) {
        ServerPlayer p = s.getPlayerList().getPlayer(k.protecting);
        long now = k.level().getGameTime();
        if (p == null || p.level() != k.level() || p.distanceTo(k) > 48) {
            k.protecting = null;
            k.leaveAt = Math.min(k.leaveAt == 0 ? Long.MAX_VALUE : k.leaveAt, now + 60);
            return;
        }
        if (k.getTarget() != null && k.getTarget().isAlive() && k.getTarget() != p) return;
        int a = com.dbzenith.npc.ai.AiEvents.lastAttacker(p, now, 100);
        if (a >= 0 && p.level().getEntity(a) instanceof LivingEntity foe && foe.isAlive() && foe != k && !(foe instanceof ServerPlayer)) {
            k.setTarget(foe);
            return;
        }
        if (k.getTarget() == null && k.leaveAt > now + 200) k.leaveAt = now + 200;     // nothing left to do here
    }

    /** Every five seconds: a player in a fight may get help, or a rival who wants them for itself. */
    static void tickFights(MinecraftServer s) {
        if (out() >= NemesisConfig.maxPresent()) return;
        long now = s.overworld().getGameTime();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (OUT.containsValue(p.getUUID()) || !eligible(p)) continue;
            if (now - LAST.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < NemesisConfig.cooldownTicks() / 2) continue;
            if (com.dbzenith.npc.ai.AiEvents.lastAttacker(p, now, 100) < 0) continue;
            if (!AiRandom.chance(p.getUUID().getLeastSignificantBits(), now / 100, 89, NemesisConfig.encounterChance())) continue;
            Choice ch = choose(p, now, true);
            if (ch != null) start(ch, p, now);
            return;
        }
    }

    /** Tests and the debug command: the bodies out, and whom for. */
    public static Map<KiFighter, UUID> outNow() {
        return new HashMap<>(OUT);
    }

    /** Tests: clears a player's cooldown. */
    public static void clearCooldown(UUID player) {
        LAST.remove(player);
    }

    static List<NamedFighter> due(UUID player, long now) {
        List<NamedFighter> out = new ArrayList<>();
        for (NamedFighter c : Nemesis.data().knowing(player)) if (c.active() && !c.resident() && c.body == null && c.returnAt <= now) out.add(c);
        return out;
    }
}
