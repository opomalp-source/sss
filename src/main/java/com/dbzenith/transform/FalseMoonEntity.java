package com.dbzenith.transform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A thrown ball of light that rises into the sky and acts as a full moon for nearby Saiyans. */
public class FalseMoonEntity extends Entity {
    public static final int RISE_TICKS = 40;
    public static final float RISE_HEIGHT = 25f;
    public static final int LIFETIME = 1200;

    private double startY;

    public FalseMoonEntity(EntityType<? extends FalseMoonEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    void setStartY(double y) {
        startY = y;
    }

    public boolean isRisen() {
        return tickCount >= RISE_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > com.dbzenith.config.DBZConfig.SERVER.falseMoonTicks.get()) {
            discard();
            return;
        }
        if (tickCount <= RISE_TICKS) {
            double t = tickCount / (double) RISE_TICKS;
            setPos(getX(), startY + RISE_HEIGHT * (1 - (1 - t) * (1 - t)), getZ()); // ease out
        }
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
        startY = tag.getDouble("startY");
        tickCount = tag.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("startY", startY);
        tag.putInt("age", tickCount);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
