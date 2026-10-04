package com.dbzenith.data;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

/**
 * Capability handles. 1.20.1 predates NeoForge data attachments (added in 1.20.3), so per-player state
 * is a Forge capability. Keep all access behind {@link #get} so a later port only touches this file.
 */
public final class ModCapabilities {
    public static final Capability<PlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private ModCapabilities() {}

    /** Mod bus listener. */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerData.class);
    }

    public static Optional<PlayerData> get(Player player) {
        return player.getCapability(PLAYER_DATA).resolve();
    }

    /** For call sites where the capability must exist (it is attached to every Player). */
    public static PlayerData getOrThrow(Player player) {
        return get(player).orElseThrow(() -> new IllegalStateException("Missing dbzenith player data on " + player.getName().getString()));
    }
}
