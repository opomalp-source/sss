package com.dbzenith.living;

import com.dbzenith.DBZenith;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The living world's heart (docs/LIVING_WORLD.md): its clock and the world's events as townsfolk see them.
 * <ul>
 *   <li>the clock: conversations kept honest each second, social life every two, residents' comings and goings every
 *       five, the day's news from elsewhere once a day;</li>
 *   <li>an attack on a resident puts its town on alert (civilians run home, fighters defend, it is news); a player who
 *       did it is wanted there for a while and remembered for it;</li>
 *   <li>a resident killed is mourned; a player who drives off the town's attackers is thanked and talked about;</li>
 *   <li>a new world gets Ginger Town near spawn once; existing worlds only by command ({@code /dbz settlement build}).</li>
 * </ul>
 * Everything is server-side; the townsfolk are Nemesis characters, so their memories and bonds are that system's.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Living {
    /** How long a town stays on alert after the last blow (ticks), and a wanted player stays wanted after it. */
    static final long ALARM = 1200, WANTED = 6000;

    private Living() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        NamedFighter.onMemory = Rumors::fromMemory;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !LivingConfig.enabled()) return;
        MinecraftServer s = event.getServer();
        int t = s.getTickCount();
        long now = s.overworld().getGameTime();
        if (t % 20 == 0) Conversations.tick(s, now);
        if (t % 40 == 10) Social.tick(s, LivingData.get(s), now);
        if (t % 100 == 30) Residents.tick(s, LivingData.get(s), now);
        if (t % 200 == 70) housekeeping(s, LivingData.get(s), now);
    }

    /** Alerts that have passed; wanted players forgiven; the day's news. */
    static void housekeeping(MinecraftServer s, LivingData ld, long now) {
        for (Settlement x : ld.settlements()) if (!x.wanted.isEmpty() && now > x.alarmUntil + WANTED) x.wanted.clear();
        long day = Math.floorDiv(s.overworld().getDayTime(), 24000L);
        if (day != ld.lastNewsDay) {
            ld.lastNewsDay = day;
            Rumors.daily(ld, now);
            ld.setDirty();
        }
    }

    // ------------------------------------------------------------------ the town under attack

    /** Puts a town on alert; the first blow of an attack is news. */
    public static void alarm(LivingData ld, Settlement s, LivingEntity by, long now) {
        boolean fresh = !s.alarmed(now);
        s.alarmUntil = now + ALARM;
        ld.setDirty();
        if (!fresh || by == null) return;
        List<NamedFighter> people = Residents.of(s);
        for (NamedFighter c : people) Mood.set(c, Occupation.of(c).fighter ? Mood.ANGRY : Mood.WORRIED, now, ALARM * 2);
        String name = by instanceof KiFighter k && Nemesis.of(k) != null ? Nemesis.of(k).name : by.getName().getString();
        Rumors.add(ld, Rumor.Kind.TOWN_ATTACKED, by.getUUID(), name, null, "", s.name, s.id, now, people);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !LivingConfig.enabled() || !(event.getEntity() instanceof Resident r) || r.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity by) || by == r || by instanceof Resident) return;
        if (by.getUUID().equals(r.sparWith)) return;                                       // a spar, not an attack
        NamedFighter c = r.character();
        if (c == null) return;
        LivingData ld = LivingData.get(r.getServer());
        Settlement s = ld.settlement(c.settlement);
        long now = r.level().getGameTime();
        ServerPlayer talker = r.talkingTo == null ? null : r.getServer().getPlayerList().getPlayer(r.talkingTo);
        if (talker != null) Conversations.close(talker, true);
        if (by instanceof ServerPlayer p) {
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            b.shift(-12, 4);
            if (s != null) {
                s.wanted.add(p.getUUID());
                for (KiFighter k : Nemesis.bodies()) {                                     // those who saw it
                    if (!(k instanceof Resident w) || w == r || w.distanceToSqr(r) > 24 * 24) continue;
                    NamedFighter cw = w.character();
                    if (cw != null) cw.player(p.getUUID(), p.getGameProfile().getName()).shift(-6, 0);
                }
            }
        }
        if (s != null) alarm(ld, s, by, now);
        if (!r.aiMayFight()) r.fleeUntil = now + 200;
        Nemesis.data().setDirty();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !LivingConfig.enabled() || event.getEntity().level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        long now = dead.level().getGameTime();
        LivingData ld = LivingData.get(dead.getServer());
        if (dead instanceof Resident r) {                                                  // a townsperson is mourned
            NamedFighter c = r.character();
            Settlement s = c == null ? null : ld.settlement(c.settlement);
            if (s == null) return;
            for (NamedFighter o : Residents.of(s)) if (o != c) Mood.set(o, Mood.WORRIED, now, 12000);
            if (event.getSource().getEntity() instanceof ServerPlayer p) {
                s.wanted.add(p.getUUID());
                s.alarmUntil = Math.max(s.alarmUntil, now + ALARM);
                for (NamedFighter o : Residents.of(s)) o.player(p.getUUID(), p.getGameProfile().getName()).shift(-20, 8);
            }
            ld.setDirty();
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;            // a player drove off the town's attackers
        Settlement s = ld.at(dead.level().dimension().location().toString(), dead.blockPosition());
        if (s == null || !s.alarmed(now) || !hostile(dead)) return;
        Fame.add(p.server, p, 10, now);
        for (KiFighter k : Nemesis.bodies()) {
            if (!(k instanceof Resident w) || w.distanceToSqr(dead) > 32 * 32) continue;
            NamedFighter cw = w.character();
            if (cw != null) cw.player(p.getUUID(), p.getGameProfile().getName()).shift(8, 0);
        }
        Nemesis.data().setDirty();
    }

    /** A threat to townsfolk: monsters (not creepers: let them be), invaders' fighters, fighters after a resident. */
    public static boolean hostile(LivingEntity e) {
        if (e instanceof Resident || e instanceof Creeper || !e.isAlive()) return false;
        if (e instanceof KiFighter k) {
            Faction f = Faction.of(k);
            return f != null && f.enemyOf(Faction.TOWNSFOLK) || k.getTarget() instanceof Resident;
        }
        return e instanceof Enemy;
    }

    /** Whether a resident takes {@code e} on: its town's fighters go for threats inside the town (guards always, the rest when it is on alert). */
    public static boolean defends(Resident r, LivingEntity e) {
        if (!LivingConfig.enabled() || !r.aiMayFight() || !hostile(e)) return false;
        NamedFighter c = r.character();
        Settlement s = c == null ? null : LivingData.get(r.getServer()).settlement(c.settlement);
        if (s == null || !s.contains(e.level().dimension().location().toString(), e.blockPosition())) return false;
        return Occupation.of(c) == Occupation.GUARD || s.alarmed(r.level().getGameTime());
    }

    /** Whether a town's fighters go after this player (they attacked its people, lately). */
    public static boolean wanted(Resident r, LivingEntity e) {
        if (!LivingConfig.enabled() || !r.aiMayFight() || !(e instanceof Player p) || p.isCreative() || p.isSpectator()) return false;
        NamedFighter c = r.character();
        Settlement s = c == null ? null : LivingData.get(r.getServer()).settlement(c.settlement);
        return s != null && s.wanted.contains(p.getUUID()) && s.alarmed(r.level().getGameTime());
    }

    // ------------------------------------------------------------------ a town for a new world

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) placeIfNew(level.getServer());
    }

    /** Ginger Town near spawn, once, for a world that is new (an existing world is never built on without a command). */
    public static void placeIfNew(MinecraftServer server) {
        if (!LivingConfig.enabled()) return;
        LivingData ld = LivingData.get(server);
        if (ld.townPlaced) return;
        ld.townPlaced = true;
        ld.setDirty();
        ServerLevel overworld = server.overworld();
        if (!LivingConfig.autoTown() || overworld.getGameTime() > 6000 || !ld.settlements().isEmpty()) return;
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) return;     // never over the test grounds
        BlockPos spawn = overworld.getSharedSpawnPos();
        build(overworld, TownBuilder.findSite(overworld, spawn), TownBuilder.GINGER_TOWN);
    }

    /** Builds a town at {@code site}, registers it and peoples it. */
    public static Settlement build(ServerLevel level, BlockPos site, String name) {
        Settlement s = TownBuilder.build(level, site, name);
        LivingData ld = LivingData.get(level.getServer());
        ld.add(s);
        Residents.populate(level, s, LivingConfig.townPopulation());
        Nemesis.data().setDirty();
        com.mojang.logging.LogUtils.getLogger().info("Living world: built {} at {}", name, s.center);
        return s;
    }

    /** A resident summoned without anyone behind it becomes a citizen. */
    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Resident r) || r.nemesisId() != null || !LivingConfig.enabled()) return;
        if (Nemesis.data() == null) return;
        Residents.adopt(r, event.getLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Social.forget(event.getEntity().getUUID());
    }
}
