package com.dbzenith.nimbus;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Flying Nimbus bound to a player's soul (CX-40). Using the item binds it (the item is absorbed); from then on the
 * call key ("Nimbus!") brings the cloud swooping down out of the sky to you, and pressing it again sends the cloud away,
 * dissolving. The bond lives in the player's persisted data, so it survives death and relogging.
 */
public final class NimbusBond {
    private static final String KEY = "dbzenith_nimbus_bound";
    /** How near your cloud must be for the key to send it away rather than call a new one. */
    private static final double NEAR = 64;
    private static final Map<UUID, Long> lastPress = new HashMap<>();

    private NimbusBond() {}

    public static boolean bound(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    public static void setBound(Player p, boolean bound) {
        CompoundTag root = p.getPersistentData();
        CompoundTag kept = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (bound) kept.putBoolean(KEY, true);
        else kept.remove(KEY);
        root.put(Player.PERSISTED_NBT_TAG, kept);
    }

    /** Binds the cloud to the player's soul, with a golden flash. */
    public static void bind(ServerPlayer p) {
        setBound(p, true);
        ServerLevel level = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, .5, .8, .5, .08);
        level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 30, .6, .9, .6, .5);
        level.playSound(null, p.blockPosition(), com.dbzenith.registry.ModSounds.NIMBUS_BIND.get(), SoundSource.PLAYERS, 1f, 1f);
        p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.bound", Component.keybind("key.dbzenith.nimbus_call")), true);
    }

    /** The call key: your cloud near you goes away; otherwise one comes. */
    public static void press(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = lastPress.get(p.getUUID());
        if (last != null && now - last < 8 && now >= last) return;
        lastPress.put(p.getUUID(), now);
        if (!bound(p)) {
            p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.unbound"), true);
            return;
        }
        var mine = owned(p);
        var near = mine.stream().filter(n -> n.distanceToSqr(p) < NEAR * NEAR).min(Comparator.comparingDouble(n -> n.distanceToSqr(p)));
        if (near.isPresent()) {
            mine.forEach(NimbusEntity::leave);
            return;
        }
        if (!NimbusEntity.eligible(p)) {
            p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.good"), true);
            return;
        }
        mine.forEach(NimbusEntity::leave);
        call(p);
    }

    /** Your clouds in this world that are not already leaving. */
    public static java.util.List<? extends NimbusEntity> owned(ServerPlayer p) {
        String id = p.getUUID().toString();
        return p.serverLevel().getEntities(NimbusContent.TYPE, n -> !n.leaving() && id.equals(n.owner()));
    }

    /** "Nimbus!": a cloud appears high in the sky ahead of you and swoops down to you. */
    public static NimbusEntity call(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 ahead = p.getLookAngle().multiply(1, 0, 1);
        if (ahead.lengthSqr() < 1e-4) ahead = Vec3.directionFromRotation(0, p.getYRot());
        ahead = ahead.normalize().yRot((float) Math.toRadians((p.getRandom().nextFloat() - .5f) * 80));
        boolean open = level.canSeeSky(p.blockPosition().above());
        Vec3 from = p.position().add(ahead.scale(open ? 22 : 10)).add(0, open ? 26 : 7, 0);
        from = new Vec3(from.x, Math.min(from.y, level.getMaxBuildHeight() - 3), from.z);
        NimbusEntity cloud = new NimbusEntity(NimbusContent.TYPE, level);
        Vec3 to = p.position().subtract(from);
        cloud.moveTo(from.x, from.y, from.z, (float) Math.toDegrees(Math.atan2(-to.x, to.z)), 0);
        cloud.setOwner(p);
        cloud.arrive();
        level.addFreshEntity(cloud);
        p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.call"), true);
        level.playSound(null, p.blockPosition(), com.dbzenith.registry.ModSounds.NIMBUS_CALL.get(), SoundSource.PLAYERS, 1f, 1f);
        return cloud;
    }
}
