package com.dbzenith.dragonball;

import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.OpenWishPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** The Eternal Dragon: a towering coil of light that waits for its summoner's wish, then vanishes. */
public class DragonSpiritEntity extends Entity {
    public static final int LIFETIME = 2400;

    private UUID summoner;
    private boolean granted;

    public DragonSpiritEntity(EntityType<? extends DragonSpiritEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setSummoner(UUID id) {
        summoner = id;
    }

    public boolean isSummoner(Player p) {
        return summoner != null && summoner.equals(p.getUUID());
    }

    /** Grants one wish to the summoner. Returns false if this dragon already granted it or the player is someone else. */
    public boolean grant(ServerPlayer player, Wish wish) {
        if (granted || !isSummoner(player)) return false;
        granted = true;
        wish.grant(player);
        DragonBalls.dragonDeparts(this);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > com.dbzenith.config.DBZConfig.SERVER.dragonWaitTicks.get()) DragonBalls.dragonDeparts(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && isSummoner(sp) && !granted) {
            ModNetwork.sendTo(sp, new OpenWishPacket(getId()));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        return distanceSq < 256 * 256;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("summoner")) summoner = tag.getUUID("summoner");
        granted = tag.getBoolean("granted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (summoner != null) tag.putUUID("summoner", summoner);
        tag.putBoolean("granted", granted);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
