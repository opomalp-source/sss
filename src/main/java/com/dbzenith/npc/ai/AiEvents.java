package com.dbzenith.npc.ai;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.npc.KiFighter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What fighters see happen, delivered as it happens instead of being searched for every tick: blows landed (and
 * whether they were guarded or dodged), moves started, ki fired, transformations, dodges, deaths. The combat code
 * calls in here with one line each; this passes each event to the fighters watching the one who did it (those
 * whose target it is, and allies of a victim). It also keeps who last hit whom (for protecting allies) and the
 * registry of thinking fighters.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AiEvents {
    private static final Set<KiFighter> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<KiFighter> SCRATCH = new ArrayList<>();
    /** The last blow on each entity: who dealt it and when. */
    private record Blow(int attacker, long tick) {}
    private static final Map<Integer, Blow> LAST_HIT = new HashMap<>();
    /** Players watching the AI debug readout (/dbz ai watch). */
    static final Set<UUID> WATCHERS = new java.util.HashSet<>();

    private AiEvents() {}

    static void activate(KiFighter k) {
        ACTIVE.add(k);
    }

    static void deactivate(KiFighter k) {
        ACTIVE.remove(k);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** The thinking fighters whose target is {@code e}. */
    private static List<KiFighter> watching(Entity e) {
        SCRATCH.clear();
        for (KiFighter k : ACTIVE) if (k.isAlive() && k.getTarget() == e && k.brainOrNull() != null) SCRATCH.add(k);
        return SCRATCH;
    }

    /** Who last hit {@code victim} within {@code ticks} (an entity id, or -1). */
    static int lastAttacker(LivingEntity victim, long now, int ticks) {
        Blow b = LAST_HIT.get(victim.getId());
        return b != null && now - b.tick <= ticks ? b.attacker : -1;
    }

    // ------------------------------------------------------------------ hooks (one line each in the combat code)

    /** A blow landed (CombatEvents): {@code share} of the victim's lasting health. */
    public static void onBlow(Entity attacker, LivingEntity victim, double share, boolean guarded, boolean evaded, boolean ki) {
        if (attacker == null || attacker == victim || victim.level().isClientSide) return;
        long now = victim.level().getGameTime();
        if (!evaded) LAST_HIT.put(victim.getId(), new Blow(attacker.getId(), now));
        if (LAST_HIT.size() > 512) LAST_HIT.entrySet().removeIf(e -> now - e.getValue().tick > 200);
        if (victim instanceof KiFighter k && k.brainOrNull() != null && !evaded) k.brainOrNull().onHurt(attacker, guarded ? 0 : share, now);
        if (attacker instanceof KiFighter k && k.brainOrNull() != null && !evaded && !guarded) k.brainOrNull().log.dealt(share);
        if (evaded && attacker instanceof KiFighter k && k.brainOrNull() != null && k.getTarget() == victim) k.brainOrNull().foe.onDodge();
    }

    /** A melee move started (CombatEngine). */
    public static void onMove(LivingEntity e, Move m) {
        if (e.level().isClientSide) return;
        for (KiFighter k : watching(e)) k.brainOrNull().foe.onMelee();
    }

    /** Ki was fired (a player's technique or ki blast, or an NPC's). */
    public static void onKi(LivingEntity e, String technique) {
        if (e.level().isClientSide) return;
        long now = e.level().getGameTime();
        for (KiFighter k : watching(e)) k.brainOrNull().observedKi(technique, now);
    }

    /** Someone took a form (players through FormHandler, NPCs through their own). */
    public static void onTransform(LivingEntity e, String form) {
        if (e.level().isClientSide) return;
        long now = e.level().getGameTime();
        for (KiFighter k : watching(e)) k.brainOrNull().observedTransform(form, now);
    }

    /** {@code victim} vanished out of {@code attacker}'s blow (Evasion). */
    public static void onDodge(LivingEntity victim, LivingEntity attacker) {
        if (victim.level().isClientSide) return;
        for (KiFighter k : watching(victim)) k.brainOrNull().foe.onDodge();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        long now = dead.level().getGameTime();
        if (dead instanceof KiFighter k && k.brainOrNull() != null) k.brainOrNull().endFight(NpcBrain.Outcome.LOST, now);
        for (KiFighter k : new ArrayList<>(ACTIVE)) {
            NpcBrain b = k.brainOrNull();
            if (b == null || !k.isAlive() || k == dead) continue;
            if (k.getTarget() == dead) b.endFight(NpcBrain.Outcome.WON, now);
            else if (Faction.allied(k, dead) && k.distanceToSqr(dead) < 24 * 24) b.allyFell(now);
        }
        LAST_HIT.remove(dead.getId());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE.removeIf(k -> k.isRemoved() || !k.isAlive());
        if (WATCHERS.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;
        for (UUID id : WATCHERS) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            KiFighter near = AiCommands.nearest(p, 32);
            if (near != null && near.brainOrNull() != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(near.brainOrNull().line()), true);
        }
    }
}
