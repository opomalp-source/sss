package com.dbzenith.tournament;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.BodyHealth;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.TournamentPackets;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The World Martial Arts Tournament (CX-17c). Talk to the Announcer to enter. Registration stays open for thirty
 * seconds so others can join; then an eight-fighter bracket is drawn (players, then the roster, Mr. Satan in the far
 * half) and fought one match at a time on the ring: quarterfinals, semifinals, the final. When two roster fighters meet
 * with no one watching, the result is simply announced.
 * <p>
 * A match is won by a ring-out (touching anything below the tiles), a knockout (what would have killed you only knocks
 * you down) or, after three minutes, by whoever has more of their health left. No flying. Fighters match themselves to
 * the player they face, a little stronger every round. Places pay out training points and Senzu Beans; the champion
 * earns the title. One entry a day.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Tournament {
    public enum Phase { SIGNUP, INTRO, FIGHTING, AFTER }

    public static final int SIGNUP_TICKS = 600, INTRO_TICKS = 80, MATCH_TICKS = 3600, AFTER_TICKS = 60, NPC_MATCH_TICKS = 40;
    public static final int COOLDOWN_TICKS = 24000;
    public static final String COOLDOWN = "tournament";
    public static final String CHAMPION_FLAG = "tournament_champion";
    /** How strong the roster fights you, by round (of your full power). */
    static final double[] ROUND_POWER = {0.75, 0.95, 1.15};
    static final long[] PLACE_TP = {300, 800, 1500, 3000};
    static final int[] PLACE_SENZU = {0, 1, 2, 4};

    /** One place in the bracket: a player, or one of the roster. */
    public record Entrant(UUID player, String playerName, Roster npc) {
        public Component name() {
            return player != null ? Component.literal(playerName) : Component.translatable(npc.nameKey());
        }

        public boolean isPlayer() {
            return player != null;
        }
    }

    private static Tournament current;

    final List<Entrant> signedUp = new ArrayList<>();
    final List<Entrant> slots = new ArrayList<>();
    /** Winner's slot for each of the seven matches (0-3 quarterfinals, 4-5 semifinals, 6 the final), -1 until fought. */
    final int[] winners = {-1, -1, -1, -1, -1, -1, -1};
    Phase phase = Phase.SIGNUP;
    int timer = SIGNUP_TICKS;
    int match;
    final LivingEntity[] fighters = new LivingEntity[2];
    final ServerLevel level;
    final BlockPos ring;

    private Tournament(ServerLevel level, BlockPos ring) {
        this.level = level;
        this.ring = ring;
    }

    public static Tournament current() {
        return current;
    }

    public Phase phase() {
        return phase;
    }

    public int timer() {
        return timer;
    }

    public int match() {
        return match;
    }

    public List<Entrant> slots() {
        return slots;
    }

    public List<Entrant> signedUp() {
        return signedUp;
    }

    public int winner(int m) {
        return winners[m];
    }

    public LivingEntity fighter(int side) {
        return fighters[side];
    }

    /** Whether this fighter is in the match being fought. */
    public static boolean isFighter(LivingEntity e) {
        return current != null && (current.fighters[0] == e || current.fighters[1] == e);
    }

    // ------------------------------------------------------------------ entering

    /** Why {@code p} cannot enter now (a lang key), or null. */
    public static String problem(ServerPlayer p) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return "tournament.dbzenith.why.no";
        if (current != null && current.has(p.getUUID())) return "tournament.dbzenith.why.entered";
        if (current != null && current.phase != Phase.SIGNUP) return "tournament.dbzenith.why.running";
        if (current != null && current.signedUp.size() >= 7) return "tournament.dbzenith.why.full";
        if (p.level() != p.server.overworld() || TournamentGrounds.ring(p.server.overworld()) == null) return "tournament.dbzenith.why.elsewhere";
        if (d.isOnCooldown(COOLDOWN, p.server.overworld().getGameTime()) && !p.getAbilities().instabuild) return "tournament.dbzenith.why.cooldown";
        return null;
    }

    /** Signs {@code p} up (opening registration if nobody has yet). Returns whether they are in. */
    public static boolean join(ServerPlayer p) {
        String why = problem(p);
        if (why != null) {
            p.displayClientMessage(Component.translatable(why), true);
            return false;
        }
        ServerLevel ow = p.server.overworld();
        if (current == null) {
            current = new Tournament(ow, TournamentGrounds.ring(ow));
            current.announce(Component.translatable("tournament.dbzenith.say.open", SIGNUP_TICKS / 20));
        }
        current.signedUp.add(new Entrant(p.getUUID(), p.getGameProfile().getName(), null));
        ModCapabilities.get(p).ifPresent(d -> d.setCooldown(COOLDOWN, ow.getGameTime() + COOLDOWN_TICKS));
        current.announce(Component.translatable("tournament.dbzenith.say.entered", p.getDisplayName(), current.signedUp.size()));
        current.broadcastState();
        return true;
    }

    boolean has(UUID id) {
        for (Entrant e : signedUp) if (id.equals(e.player())) return true;
        return false;
    }

    /** Ends registration now (tests, or the Announcer's word) and draws the bracket. */
    public void closeSignup() {
        if (phase == Phase.SIGNUP) draw();
    }

    // ------------------------------------------------------------------ the bracket

    void draw() {
        List<Entrant> players = new ArrayList<>(signedUp);
        Collections.shuffle(players, new java.util.Random(level.random.nextLong()));
        List<Roster> roster = new ArrayList<>(List.of(Roster.values()));
        roster.remove(Roster.MR_SATAN);
        Collections.shuffle(roster, new java.util.Random(level.random.nextLong()));
        Entrant[] s = new Entrant[8];
        s[7] = new Entrant(null, null, Roster.MR_SATAN);                       // the champion waits in the far half
        int[] order = {0, 2, 4, 6, 1, 3, 5};                                    // players spread out, so they meet late
        int r = 0;
        for (int i = 0; i < order.length; i++) {
            s[order[i]] = i < players.size() ? players.get(i) : new Entrant(null, null, roster.get(r++));
        }
        slots.clear();
        slots.addAll(List.of(s));
        match = 0;
        announce(Component.translatable("tournament.dbzenith.say.drawn"));
        phase = Phase.AFTER;
        timer = AFTER_TICKS;
        broadcastState();
    }

    /** The two slots that meet in match {@code m}. */
    public int[] sides(int m) {
        if (m < 4) return new int[]{2 * m, 2 * m + 1};
        int base = (m - 4) * 2;                                                 // semis take the quarterfinal winners, the final the semis'
        return m < 6 ? new int[]{winners[base], winners[base + 1]} : new int[]{winners[4], winners[5]};
    }

    public static int round(int m) {
        return m < 4 ? 0 : m < 6 ? 1 : 2;
    }

    // ------------------------------------------------------------------ the server's heartbeat

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) return;
        current.tick();
    }

    void tick() {
        timer--;
        switch (phase) {
            case SIGNUP -> {
                if (timer == 200) announce(Component.translatable("tournament.dbzenith.say.closing", 10));
                if (timer <= 0) draw();
            }
            case AFTER -> {
                if (timer <= 0) next();
            }
            case INTRO -> tickIntro();
            case FIGHTING -> tickFight();
        }
    }

    /** Starts the next match (or ends the tournament after the final). */
    void next() {
        if (match >= 7) {
            finish();
            return;
        }
        int[] s = sides(match);
        Entrant a = slots.get(s[0]), b = slots.get(s[1]);
        if (!a.isPlayer() && !b.isPlayer()) {                                   // two of the roster: announced, not shown
            double pa = a.npc().seed() / (double) (a.npc().seed() + b.npc().seed());
            decide(level.random.nextDouble() < pa ? 0 : 1, "simulated");
            timer = NPC_MATCH_TICKS;
            return;
        }
        for (int i = 0; i < 2; i++) {                                           // a player who is not here forfeits
            Entrant e = slots.get(s[i]);
            if (e.isPlayer() && present(e) == null) {
                decide(1 - i, "forfeit");
                return;
            }
        }
        ServerPlayer facing = a.isPlayer() ? present(a) : present(b);
        for (int i = 0; i < 2; i++) {
            Entrant e = slots.get(s[i]);
            Vec3 at = TournamentGrounds.corner(ring, i);
            float yaw = i == 0 ? -90f : 90f;
            if (e.isPlayer()) {
                ServerPlayer p = present(e);
                p.teleportTo(level, at.x, at.y, at.z, yaw, 0f);
                restore(p);
                fighters[i] = p;
            } else {
                fighters[i] = spawn(e.npc(), at, yaw, facing);
            }
        }
        announce(Component.translatable("tournament.dbzenith.say.match." + round(match), a.name(), b.name()));
        phase = Phase.INTRO;
        timer = INTRO_TICKS;
        broadcastState();
    }

    private TournamentFighter spawn(Roster who, Vec3 at, float yaw, ServerPlayer facing) {
        EntityType<TournamentFighter> type = com.dbzenith.npc.ModNpcs.tournamentFighter(who);
        TournamentFighter f = type.create(level);
        if (f == null) return null;
        f.moveTo(at.x, at.y, at.z, yaw, 0);
        f.setYHeadRot(yaw);
        f.setYBodyRot(yaw);
        f.finalizeSpawn(level, level.getCurrentDifficultyAt(f.blockPosition()), MobSpawnType.EVENT, null, null);
        long power = facing == null ? 0 : ModCapabilities.get(facing).map(StatCalculator::fullPower).orElse(0L);
        f.setFighterLevel(KiFighter.levelFor((long) (power * ROUND_POWER[round(match)])));
        f.setNoAi(true);
        level.addFreshEntity(f);
        return f;
    }

    void tickIntro() {
        int left = timer / 20 + 1;
        if (timer % 20 == 0 && timer > 0) {
            actionbar(Component.literal(String.valueOf(left)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            level.playSound(null, ring, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 0.8f);
        }
        if (timer > 0) return;
        for (int i = 0; i < 2; i++) {
            if (fighters[i] instanceof TournamentFighter f) {
                f.setNoAi(false);
                f.setOpponent(fighters[1 - i]);
            }
            if (fighters[i] instanceof net.minecraft.server.level.ServerPlayer sp) com.dbzenith.combat.PvpRules.set(sp, true, true);   // a match is a fight (CX-20)
        }
        actionbar(Component.translatable("tournament.dbzenith.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        level.playSound(null, ring, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.4f, 1.4f);
        phase = Phase.FIGHTING;
        timer = MATCH_TICKS;
    }

    void tickFight() {
        for (int i = 0; i < 2; i++) {
            LivingEntity e = fighters[i];
            if (e == null || e.isRemoved() || !e.isAlive() || (e instanceof ServerPlayer p && p.hasDisconnected())) {
                decide(1 - i, "forfeit");
                return;
            }
            if (e instanceof ServerPlayer p) ModCapabilities.get(p).ifPresent(d -> {
                if (d.isFlying()) com.dbzenith.ki.FlightHandler.stop(p, d);  // no flying in the ring
            });
            if (TournamentGrounds.isOut(e, ring)) {
                decide(1 - i, "ring_out");
                return;
            }
        }
        if (timer % 20 == 0) {
            int s = timer / 20;
            actionbar(Component.translatable("tournament.dbzenith.clock", Component.translatable("tournament.dbzenith.round." + round(match)),
                    s / 60, String.format("%02d", s % 60)));
        }
        if (timer <= 0) decide(share(fighters[0]) >= share(fighters[1]) ? 0 : 1, "decision");
    }

    /** How much of their health a fighter has left (0..1). */
    static double share(LivingEntity e) {
        if (e instanceof ServerPlayer p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            return d == null ? 0 : d.getBody() / Math.max(1, d.getDerived().maxBody());
        }
        return e == null ? 0 : e.getHealth() / Math.max(1f, e.getMaxHealth());
    }

    /** Ends match {@code match}: side {@code winner} (0 or 1) goes through. */
    void decide(int winner, String how) {
        int[] s = sides(match);
        Entrant w = slots.get(s[winner]), l = slots.get(s[1 - winner]);
        winners[match] = s[winner];
        announce(Component.translatable("tournament.dbzenith.say." + how, w.name(), l.name()));
        if (l.isPlayer()) {                                                     // out: paid for how far they came
            ServerPlayer p = online(l);
            if (p != null) reward(p, round(match));
            if (l.npc() == null && w.npc() == Roster.MR_SATAN) announce(Component.translatable("tournament.dbzenith.satan.win"));
        }
        if (w.isPlayer() && l.npc() == Roster.MR_SATAN) announce(Component.translatable("tournament.dbzenith.satan.lose"));
        for (int i = 0; i < 2; i++) {
            LivingEntity e = fighters[i];
            if (e instanceof TournamentFighter f) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, f.getX(), f.getY() + 1, f.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
                f.discard();
            } else if (e instanceof ServerPlayer p && !p.hasDisconnected()) {
                Vec3 side = TournamentGrounds.sideline(ring);
                if (p.level() == level) p.teleportTo(level, side.x + (i == 0 ? -2 : 2), side.y, side.z, 180f, 0f);
                restore(p);
            }
            fighters[i] = null;
        }
        match++;
        phase = Phase.AFTER;
        timer = AFTER_TICKS;
        broadcastState();
    }

    void finish() {
        Entrant champ = slots.get(winners[6]);
        announce(Component.translatable("tournament.dbzenith.say.champion", champ.name()).withStyle(ChatFormatting.GOLD));
        ServerPlayer p = online(champ);
        if (p != null) reward(p, 3);
        broadcastState();
        current = null;
    }

    /** Pays a player for reaching {@code place} (0 out in the quarterfinals ... 3 champion). */
    static void reward(ServerPlayer p, int place) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        d.addTrainingPoints(PLACE_TP[place]);
        if (PLACE_SENZU[place] > 0) {
            ItemStack beans = new ItemStack(com.dbzenith.registry.ModItems.SENZU_BEAN.get(), PLACE_SENZU[place]);
            if (!p.getInventory().add(beans)) p.drop(beans, false);
        }
        if (place == 3) {
            d.setFlag(CHAMPION_FLAG, true);
        }
        p.sendSystemMessage(Component.translatable("tournament.dbzenith.reward." + place, PLACE_TP[place], PLACE_SENZU[place]).withStyle(ChatFormatting.YELLOW));
    }

    /** Full body, ki and stamina between matches. */
    static void restore(ServerPlayer p) {
        ModCapabilities.get(p).ifPresent(d -> {
            d.setBody(d.getDerived().maxBody());
            d.setKi(d.getDerived().maxKi());
            d.setStamina(d.getDerived().maxStamina());
            BodyHealth.mirror(p, d);
        });
        p.clearFire();
    }

    // ------------------------------------------------------------------ knockouts and leaving

    /** What would kill a fighter in the ring only knocks them out. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (current == null || current.phase != Phase.FIGHTING) return;
        LivingEntity e = event.getEntity();
        for (int i = 0; i < 2; i++) {
            if (current.fighters[i] != e) continue;
            event.setCanceled(true);
            e.setHealth(Math.max(1f, e.getMaxHealth() * 0.1f));
            if (e instanceof ServerPlayer p) ModCapabilities.get(p).ifPresent(d -> {
                d.setBody(d.getDerived().maxBody() * 0.1);
                BodyHealth.mirror(p, d);
            });
            current.decide(1 - i, "knockout");
            return;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (current == null || current.phase != Phase.SIGNUP) return;
        current.signedUp.removeIf(e -> event.getEntity().getUUID().equals(e.player()));
        if (current.signedUp.isEmpty()) current = null;
    }

    /** Ends everything at once (server stop, /dbz). Fighters vanish; nobody is paid. */
    public static void cancel() {
        if (current == null) return;
        for (LivingEntity e : current.fighters) if (e instanceof TournamentFighter f) f.discard();
        current = null;
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        cancel();
    }

    // ------------------------------------------------------------------ helpers

    ServerPlayer online(Entrant e) {
        return e.player() == null ? null : level.getServer().getPlayerList().getPlayer(e.player());
    }

    /** The player of an entrant if they can fight now: online, alive, in this world and near the ring. */
    ServerPlayer present(Entrant e) {
        ServerPlayer p = online(e);
        if (p == null || !p.isAlive() || p.isSpectator() || p.level() != level) return null;
        return p.position().distanceToSqr(Vec3.atCenterOf(ring)) < 160 * 160 ? p : null;
    }

    /** The Announcer's voice: everyone in the world near the grounds, and every entrant. */
    void announce(Component text) {
        Component line = Component.translatable("tournament.dbzenith.announcer", text).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            boolean near = p.level() == level && p.position().distanceToSqr(Vec3.atCenterOf(ring)) < 128 * 128;
            boolean entrant = has(p.getUUID());
            if (near || entrant) p.sendSystemMessage(line);
        }
    }

    void actionbar(Component text) {
        for (LivingEntity e : fighters) if (e instanceof ServerPlayer p) p.displayClientMessage(text, true);
    }

    /** Sends the bracket to every entrant and anyone near the grounds (their open screen follows it). */
    void broadcastState() {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (has(p.getUUID()) || p.level() == level && p.position().distanceToSqr(Vec3.atCenterOf(ring)) < 64 * 64) {
                ModNetwork.sendTo(p, TournamentPackets.State.of(p, false));
            }
        }
    }

    /** The Announcer: opens the bracket for {@code p}. */
    public static void open(ServerPlayer p) {
        ModNetwork.sendTo(p, TournamentPackets.State.of(p, true));
    }

    /** Test hook: forces the current match's result. */
    public void forceResult(int winnerSide, String how) {
        decide(winnerSide, how);
    }
}
