package com.dbzenith.data;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.network.SyncPlayerDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lifecycle of {@link PlayerData}: attach, survive death/dimension change, initialize, and sync to the owner.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerDataEvents {
    private static final ResourceLocation KEY = new ResourceLocation(DBZenith.MOD_ID, "player_data");

    private PlayerDataEvents() {}

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            // No invalidate listener: invalidating the LazyOptional is permanent, and Clone needs the
            // old player's capability after removal (reviveCaps). Caught by PlayerDataTests.survivesDeathRespawn.
            event.addCapability(KEY, new PlayerDataProvider());
        }
    }

    /** Data persists through death and returning from the End. Pools refill on death. */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        ModCapabilities.get(original).ifPresent(oldData ->
                ModCapabilities.get(event.getEntity()).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    if (event.isWasDeath()) {
                        newData.refill();
                        newData.setFlying(false); // vanilla resets abilities on death
                        newData.setFormId(PlayerData.BASE_FORM);
                    }
                }));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModCapabilities.get(player).ifPresent(PlayerData::initDefaultsIfNeeded);
            player.refreshDimensions(); // height (appearance.Stature) is only known once the data has loaded
            sync(player);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.refreshDimensions();
            sync(player);
        }
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ModCapabilities.get(player).ifPresent(data -> {
            KiTicker.tick(player, data);
            PublicStatePacket state = PublicStatePacket.of(player.getId(), data);
            if (state.stateHash() != data.getLastPublicStateHash()) {
                data.setLastPublicStateHash(state.stateHash());
                ModNetwork.sendToTrackingAndSelf(player, state);
            }
            if (data.tickSyncTimer(DBZConfig.SERVER.syncIntervalTicks.get())) {
                ModNetwork.sendTo(player, new SyncPlayerDataPacket(data.writeSyncTag()));
            }
        });
    }

    /** A client starts seeing another player: send that player's visible state. */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModCapabilities.get(target).ifPresent(d -> ModNetwork.sendTo(viewer, PublicStatePacket.of(target.getId(), d)));
        }
    }

    /** Sends the full state to the owning client immediately. */
    public static void sync(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(data -> ModNetwork.sendTo(player, new SyncPlayerDataPacket(data.writeSyncTag())));
    }
}
