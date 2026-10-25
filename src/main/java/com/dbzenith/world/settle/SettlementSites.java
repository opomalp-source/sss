package com.dbzenith.world.settle;

import com.dbzenith.DBZenith;
import com.dbzenith.living.LivingConfig;
import com.dbzenith.living.LivingData;
import com.dbzenith.living.Residents;
import com.dbzenith.living.Settlement;
import com.dbzenith.world.landmark.Landmark;
import com.dbzenith.world.landmark.LandmarkPlan;
import com.dbzenith.world.landmark.LandmarkSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Brings world-generated settlements to life (docs/WORLD_STRUCTURES.md): every five seconds, for each player on
 * Earth, the nearest settlement landmark of each kind; once a player is within reach of one whose centre is loaded,
 * it is registered with the living world (a {@link Settlement} with all its places, its population and look) and
 * peopled ({@link Residents#settle}: residents in its books at once, their bodies appearing as their homes load).
 * Each site is registered once in a world's life (removing it by command does not bring it back).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SettlementSites {
    private SettlementSites() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 60 || !LivingConfig.enabled()) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) visit(level, p.blockPosition());
    }

    /** Registers any settlement landmark near {@code at} that is due. Returns the last one registered (tests), or null. */
    public static Settlement visit(ServerLevel level, BlockPos at) {
        Settlement made = null;
        LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
        for (Landmark type : Landmark.ALL) {
            LandmarkPlan plan = LandmarkSites.nearest(type, level.getSeed(), t, at, 1);
            if (!(plan instanceof SettlementPlan sp)) continue;
            double dx = sp.x - at.getX(), dz = sp.z - at.getZ();
            if (dx * dx + dz * dz > (double) (sp.reach() + 64) * (sp.reach() + 64)) continue;
            if (!level.isLoaded(sp.centre())) continue;
            Settlement s = register(level, sp);
            if (s != null) made = s;
        }
        return made;
    }

    /** Registers a settlement plan with the living world and peoples it, once. Returns it, or null if it was already known. */
    public static Settlement register(ServerLevel level, SettlementPlan sp) {
        LivingData ld = LivingData.get(level.getServer());
        if (!ld.firstVisit(sp.settlementId())) return null;
        Settlement s = new Settlement(sp.settlementId());
        s.name = sp.name();
        s.dimension = level.dimension().location().toString();
        s.center = sp.centre();
        s.radius = sp.reach();
        s.pois.addAll(sp.places());
        s.population.addAll(sp.people());
        s.ways.addAll(sp.ways());
        s.rooms.addAll(sp.rooms());
        s.style = sp.style();
        ld.add(s);
        int n = Residents.settle(s, level.getGameTime()).size();
        DBZenith.LOGGER.info("Settlement {} registered at {} with {} places and {} residents", s.name, s.center, s.pois.size(), n);
        return s;
    }
}
