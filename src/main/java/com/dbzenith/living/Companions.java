package com.dbzenith.living;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.Personality;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Companions (docs/LIVING_WORLD.md): a resident who travels with a player. Whether it will is its own decision, from
 * who it is and what it thinks of you ({@link #refusal}: civilians don't fight, a master has students, a guard a post,
 * the cowardly fear the road, the proud won't follow the weaker, nobody follows a stranger or someone who hurt their
 * town). Once it does, you tell it how ({@link Mode}) in conversation: follow, wait here, defend you, avoid fights,
 * fight alongside you, go home; train (spar) and talk as ever. It fights with the combat AI, never at you or your other
 * companions; it leaves if it comes to dislike you, goes home when you log out, when it is hurt badly enough to go
 * recover, or when its town needs its guard. Everything is kept on its character ({@code companionOf},
 * {@code companionMode}, {@code waitPos}).
 */
public final class Companions {
    public enum Mode {
        /** Walks with you; fights back if attacked. */
        FOLLOW,
        /** Stays where you left it. */
        WAIT,
        /** Goes for whoever attacks you. */
        DEFEND,
        /** Keeps out of fights altogether. */
        PASSIVE,
        /** Fights alongside you: what you attack, and threats around you. */
        AGGRESSIVE,
        /** Back to its own life for now (still your companion). */
        HOME;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode byId(String id) {
            for (Mode m : values()) if (m.id().equals(id)) return m;
            return null;
        }

        /** It walks with the player in this mode. */
        public boolean travels() {
            return this != WAIT && this != HOME;
        }
    }

    /** The affinity a resident needs before it travels with you, and (how many a player may have: living.maxCompanions). */
    static final double TRUST = 15;

    private Companions() {}

    public static Mode mode(NamedFighter c) {
        if (c == null || c.companionOf == null) return null;
        Mode m = Mode.byId(c.companionMode);
        return m == null ? Mode.FOLLOW : m;
    }

    public static boolean of(NamedFighter c, UUID player) {
        return c != null && player != null && player.equals(c.companionOf);
    }

    /** A player's companions. */
    public static List<NamedFighter> of(UUID player) {
        List<NamedFighter> out = new ArrayList<>();
        NemesisData d = Nemesis.data();
        if (d != null) for (NamedFighter c : d.all()) if (player.equals(c.companionOf) && c.status != NamedFighter.Status.DEAD) out.add(c);
        return out;
    }

    /**
     * Why it won't travel with this player, as a key the conversation data picks its line by, or "none" if it will:
     * no_fighter, wanted, taken, crowded, hostile, stranger, students, duty, afraid, proud, town_danger.
     */
    public static String refusal(NamedFighter c, Bond b, ServerPlayer p, Settlement town, long now) {
        Occupation o = Occupation.of(c);
        if (of(c, p.getUUID())) return "none";
        if (!o.fighter) return "no_fighter";
        if (town != null && town.wanted.contains(p.getUUID())) return "wanted";
        if (c.companionOf != null) return "taken";
        if (of(p.getUUID()).size() >= LivingConfig.maxCompanions()) return "crowded";
        if (b.affinity < 0 || b.state.name().equals("BITTER_RIVAL") || b.state.name().equals("SWORN_ENEMY")) return "hostile";
        if (b.affinity < TRUST) return "stranger";
        if (town != null && town.alarmed(now)) return "town_danger";
        if (o == Occupation.MASTER && b.affinity < 45) return "students";
        if (o == Occupation.GUARD && b.affinity < 30) return "duty";
        if (c.personality == Personality.COWARDLY && b.affinity < 50) return "afraid";
        if (c.personality == Personality.ARROGANT && c.level > playerLevel(p)) return "proud";
        return "none";
    }

    static int playerLevel(ServerPlayer p) {
        return KiFighter.levelFor(ModCapabilities.get(p).map(StatCalculator::fullPower).orElse(0L));
    }

    /** It joins the player (call only when {@link #refusal} said "none"). */
    public static void join(NamedFighter c, ServerPlayer p, long now) {
        c.companionOf = p.getUUID();
        c.companionMode = Mode.FOLLOW.id();
        c.remember(new Memory(Memory.Kind.BEFRIENDED, now, p.getUUID(), "companion"));
        p.sendSystemMessage(Component.translatable("living.dbzenith.companion.joined", c.name).withStyle(ChatFormatting.GREEN));
        Nemesis.data().setDirty();
    }

    /** Tells it how to travel (from a conversation). */
    public static void command(NamedFighter c, Resident r, Mode m) {
        c.companionMode = m.id();
        if (m == Mode.WAIT) c.waitPos = r.blockPosition().asLong();
        r.setTarget(null);
        Nemesis.data().setDirty();
    }

    /** It leaves the player ({@code why}: a translation key shown to them). */
    public static void leave(MinecraftServer server, NamedFighter c, String why, long now) {
        UUID p = c.companionOf;
        if (p == null) return;
        c.companionOf = null;
        c.companionMode = "";
        c.remember(new Memory(Memory.Kind.RELATION_CHANGED, now, p, "parted"));
        ServerPlayer sp = server.getPlayerList().getPlayer(p);
        if (sp != null) sp.sendSystemMessage(Component.translatable(why, c.name).withStyle(ChatFormatting.GRAY));
        Nemesis.data().setDirty();
    }

    /** Every two seconds: companions who have had enough leave; those whose player is gone, or whose town needs them, go home. */
    static void tick(MinecraftServer server, LivingData ld, long now) {
        NemesisData d = Nemesis.data();
        if (d == null) return;
        for (NamedFighter c : new ArrayList<>(d.all())) {
            if (c.companionOf == null) continue;
            if (c.status == NamedFighter.Status.DEAD || c.status == NamedFighter.Status.RETIRED) {
                leave(server, c, "living.dbzenith.companion.gone", now);
                continue;
            }
            Bond b = c.players.get(c.companionOf);
            if (b == null || b.affinity < 0) {
                leave(server, c, "living.dbzenith.companion.left_angry", now);
                continue;
            }
            Mode m = mode(c);
            ServerPlayer p = server.getPlayerList().getPlayer(c.companionOf);
            if (p == null && m != Mode.HOME) c.companionMode = Mode.HOME.id();              // you logged out: it went home
            if (c.status == NamedFighter.Status.RECOVERING && m != Mode.HOME) {
                c.companionMode = Mode.HOME.id();
                if (p != null) p.sendSystemMessage(Component.translatable("living.dbzenith.companion.recovering", c.name).withStyle(ChatFormatting.GRAY));
            }
            Settlement home = ld.settlement(c.settlement);
            Occupation o = Occupation.of(c);
            if (home != null && home.alarmed(now) && (o == Occupation.GUARD || o == Occupation.MASTER) && m != Mode.HOME) {
                c.companionMode = Mode.HOME.id();
                if (p != null) p.sendSystemMessage(Component.translatable("living.dbzenith.companion.town_needs", c.name, home.name).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Where a companion should be: by its player (null when it shouldn't follow now). */
    static ServerPlayer leader(Resident r, NamedFighter c) {
        Mode m = mode(c);
        if (m == null || !m.travels() || r.getServer() == null) return null;
        ServerPlayer p = r.getServer().getPlayerList().getPlayer(c.companionOf);
        return p == null || p.isSpectator() || !p.isAlive() ? null : p;
    }

    /** A spot beside a player a companion can stand on (or null). */
    static BlockPos besides(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        for (int[] o : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -2}, {2, 2}, {-1, 1}, {1, -1}})
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos q = at.offset(o[0], dy, o[1]);
                if (!p.level().getBlockState(q.below()).getCollisionShape(p.level(), q.below()).isEmpty()
                        && p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty() && p.level().getBlockState(q.above()).getCollisionShape(p.level(), q.above()).isEmpty()
                        && p.level().getFluidState(q).isEmpty()) return q;
            }
        return null;
    }
}
