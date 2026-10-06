package com.dbzenith.fusion;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.registry.ModSounds;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Two fighters as one (12c). The Fusion Dance ({@link FusionDance}) or a pair of Potara earrings ({@link Potara}) fuses
 * two players for a while: the one who asked becomes the host, whose body the fused warrior uses, with a fused name,
 * full pools and every attribute multiplied by the fused power; the other rides along as a spectator watching through
 * the host's eyes, and gets their own body (and game mode) back when the fusion wears off, the host falls, or either one
 * leaves. A botched dance still fuses them, into something fat or scrawny and weaker than either alone.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Fusion {
    public static final int DANCE = 1, POTARA = 2, FAILED_FAT = 3, FAILED_THIN = 4;

    private static final Vector3f DANCE_GLOW = new Vector3f(1f, 0.92f, 0.55f);
    private static final Vector3f POTARA_GLOW = new Vector3f(0.75f, 0.95f, 1f);

    private Fusion() {}

    public static boolean failed(int kind) {
        return kind == FAILED_FAT || kind == FAILED_THIN;
    }

    /** The fused-look bits of a player's public state (only the host has a body to show it on). */
    public static int publicBits(PlayerData d) {
        if (!d.isFused() || !d.isFusionHost()) return 0;
        return switch (d.getFusionKind()) {
            case DANCE -> PublicStatePacket.FUSED_DANCE;
            case POTARA -> PublicStatePacket.FUSED_POTARA;
            case FAILED_FAT -> PublicStatePacket.FUSED_FAT;
            case FAILED_THIN -> PublicStatePacket.FUSED_THIN;
            default -> 0;
        };
    }

    /** A fighter's raw strength for the fused-power share: every attribute after forms and gear, whatever the release. */
    public static double strength(PlayerData d) {
        double sum = 0;
        for (Attribute a : Attribute.values()) sum += StatCalculator.effective(d, a);
        return sum;
    }

    /**
     * The fused body's multiplier over the host: bonus x (1 + share), the share being the partner's strength over the
     * host's, capped at 1 (so the stronger of two should be the one who asks). A botched dance: a flat, feeble factor.
     */
    public static double power(int kind, double host, double partner) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (failed(kind)) return c.failedFusionPower.get();
        double share = host <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, partner / host));
        return (kind == POTARA ? c.potaraBonus.get() : c.fusionDanceBonus.get()) * (1.0 + share);
    }

    /** How long a fusion of this kind lasts, in ticks. */
    public static long duration(int kind) {
        return (kind == POTARA ? DBZConfig.SERVER.potaraMinutes.get() : DBZConfig.SERVER.fusionDanceMinutes.get()) * 1200L;
    }

    /**
     * The fused name: the front of one name and the back of the other (the dance leads with the host, the Potara with the
     * partner, so the same pair gets two different names). Letters only, the first one capitalised.
     */
    public static String fusedName(String host, String partner, int kind) {
        String a = letters(kind == POTARA ? partner : host), b = letters(kind == POTARA ? host : partner);
        if (a.isEmpty() || b.isEmpty()) return (host + partner).substring(0, Math.min(16, host.length() + partner.length()));
        String front = a.substring(0, Math.max(1, (a.length() + 1) / 2));
        String back = b.substring(b.length() / 2);
        String name = (front + back).toLowerCase(java.util.Locale.ROOT);
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private static String letters(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) if (Character.isLetter(ch)) b.append(ch);
        return b.toString();
    }

    private static PlayerData data(ServerPlayer p) {
        return p == null ? null : ModCapabilities.get(p).orElse(null);
    }

    /** Why these two cannot fuse right now (a message key), or null if they can. */
    public static String problem(ServerPlayer a, ServerPlayer b) {
        PlayerData ad = data(a), bd = data(b);
        if (b == null || ad == null || bd == null || a == b) return "message.dbzenith.fusion_no_partner";
        if (ad.isFused() || bd.isFused()) return "message.dbzenith.fusion_already";
        if (a.isSpectator() || b.isSpectator() || !a.isAlive() || !b.isAlive()) return "message.dbzenith.fusion_no_partner";
        if (a.level() != b.level() || a.distanceToSqr(b) > 12 * 12) return "message.dbzenith.fusion_too_far";
        return null;
    }

    /** Fuses {@code partner} into {@code host}. Returns false if either is already fused. */
    public static boolean fuse(ServerPlayer host, ServerPlayer partner, int kind) {
        PlayerData hd = data(host), pd = data(partner);
        if (hd == null || pd == null || host == partner || hd.isFused() || pd.isFused()) return false;
        long until = host.level().getGameTime() + duration(kind);
        double power = power(kind, strength(hd), strength(pd));
        String name = fusedName(host.getGameProfile().getName(), partner.getGameProfile().getName(), kind);
        hd.setFusion(kind, true, partner.getStringUUID(), name, until, power, -1);
        pd.setFusion(kind, false, host.getStringUUID(), name, until, 1.0, partner.gameMode.getGameModeForPlayer().getId());
        partner.setGameMode(GameType.SPECTATOR);
        partner.teleportTo(host.serverLevel(), host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
        partner.setCamera(host);
        hd.refill();
        host.setHealth(host.getMaxHealth());
        host.refreshDisplayName();
        partner.refreshDisplayName();
        flash(host.serverLevel(), host.position(), kind);
        ModNetwork.sendToTrackingAndSelf(host, new AnimEventPacket(host.getId(), AnimEventPacket.FUSED, kind));
        Component fused = Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        String key = failed(kind) ? "message.dbzenith.fusion_botched" : "message.dbzenith.fusion_done";
        host.sendSystemMessage(Component.translatable(key, fused, partner.getDisplayName()));
        partner.sendSystemMessage(Component.translatable(key, fused, host.getDisplayName()));
        PlayerDataEvents.sync(host);
        PlayerDataEvents.sync(partner);
        return true;
    }

    /** Splits whichever fusion {@code player} is part of. Safe to call with the other half offline. */
    public static void unfuse(ServerPlayer player) {
        PlayerData d = data(player);
        if (d == null || !d.isFused()) return;
        ServerPlayer other = other(player.server, d);
        PlayerData od = data(other);
        if (od == null || !od.isFused() || !od.getFusedWith().equals(player.getStringUUID())) {
            other = null;                                       // offline, or not actually our other half
            od = null;
        }
        ServerPlayer host = d.isFusionHost() ? player : other, partner = d.isFusionHost() ? other : player;
        if (partner != null) release(partner, host);            // reads the saved game mode, so before the clear
        d.clearFusion();
        if (od != null) od.clearFusion();
        if (host != null) {
            host.refreshDisplayName();
            PlayerData hd = data(host);
            if (hd != null) hd.markDirty();
            ServerLevel level = host.serverLevel();
            level.sendParticles(ParticleTypes.END_ROD, host.getX(), host.getY() + 1, host.getZ(), 30, 0.6, 0.8, 0.6, 0.12);
            level.playSound(null, host.getX(), host.getY(), host.getZ(), ModSounds.POWER_DOWN.get(), SoundSource.PLAYERS, 1f, 0.9f);
            host.sendSystemMessage(Component.translatable("message.dbzenith.fusion_over").withStyle(ChatFormatting.YELLOW));
            PlayerDataEvents.sync(host);
        }
        if (partner != null) {
            partner.refreshDisplayName();
            partner.sendSystemMessage(Component.translatable("message.dbzenith.fusion_over").withStyle(ChatFormatting.YELLOW));
            PlayerDataEvents.sync(partner);
        }
    }

    /** The partner gets their own body back, beside the host (or where they are, if the host is gone). */
    private static void release(ServerPlayer partner, ServerPlayer host) {
        PlayerData pd = data(partner);
        int prev = pd == null ? -1 : pd.getFusionPrevMode();
        partner.setCamera(partner);
        GameType mode = prev >= 0 ? GameType.byId(prev) : partner.server.getDefaultGameType();
        partner.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
        if (host != null) {
            double yaw = Math.toRadians(host.getYRot());
            Vec3 at = host.position().add(-Math.cos(yaw) * 1.2, 0, -Math.sin(yaw) * 1.2);   // a step to the host's right
            partner.teleportTo(host.serverLevel(), at.x, host.getY(), at.z, host.getYRot(), 0);
        }
    }

    /** The other half, if online. */
    public static ServerPlayer other(MinecraftServer server, PlayerData d) {
        if (server == null || d.getFusedWith().isEmpty()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(d.getFusedWith()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The light of a fusion: a pillar of glow, a ring and a flash. */
    static void flash(ServerLevel level, Vec3 at, int kind) {
        Vector3f c = kind == POTARA ? POTARA_GLOW : DANCE_GLOW;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(new DustParticleOptions(c, 2.0f), at.x + Math.cos(a) * 2.2, at.y + 0.2, at.z + Math.sin(a) * 2.2, 2, 0.1, 0.05, 0.1, 0.02);
        }
        level.sendParticles(new DustParticleOptions(c, 2.4f), at.x, at.y + 3, at.z, 80, 0.35, 3.0, 0.35, 0.02);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 40, 0.4, 1.2, 0.4, 0.15);
        level.playSound(null, at.x, at.y, at.z, ModSounds.TRANSFORM.get(), SoundSource.PLAYERS, 1.4f, 1.1f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.5f);
    }

    // ------------------------------------------------------------------ upkeep

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        PlayerData d = data(player);
        if (d == null || !d.isFused()) return;
        ServerPlayer other = other(player.server, d);
        if (other == null || player.level().getGameTime() >= d.getFusionUntil()) {
            unfuse(player);
            return;
        }
        if (!d.isFusionHost()) {                                   // the partner sees through the host's eyes
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            if (player.level() != other.level()) {
                player.teleportTo(other.serverLevel(), other.getX(), other.getY(), other.getZ(), other.getYRot(), other.getXRot());
            }
            if (player.getCamera() != other) player.setCamera(other);
        } else if (player.tickCount % 20 == 0) {
            ServerLevel level = player.serverLevel();             // a faint shimmer of the two halves
            Vector3f c = d.getFusionKind() == POTARA ? POTARA_GLOW : DANCE_GLOW;
            level.sendParticles(new DustParticleOptions(c, 0.8f), player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FusionDance.cancel(player);
            unfuse(player);
        }
    }

    /** A fusion that outlived a server restart (or a crash) is undone on the next login. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData d = data(player);
        if (d == null || !d.isFused()) return;
        ServerPlayer other = other(player.server, d);
        PlayerData od = data(other);
        if (od != null && od.isFused() && od.getFusedWith().equals(player.getStringUUID())) return;
        if (!d.isFusionHost()) release(player, null);
        d.clearFusion();
        player.refreshDisplayName();
    }

    /** The fused body falls: the two split before death takes the host. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FusionDance.cancel(player);
            unfuse(player);
        }
    }

    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData d = data(player);
        if (d != null && d.isFused() && d.isFusionHost() && !d.getFusedName().isEmpty()) {
            event.setDisplayname(Component.literal(d.getFusedName()));
        }
    }
}
