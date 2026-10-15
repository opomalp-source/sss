package com.dbzenith.combat;

import com.dbzenith.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A limb cut off in battle (the knocked-out rework): it flies off spinning, bounces and comes to rest, in the fighter's
 * own skin tone with the cut end in their blood's colour, and it bleeds a little where it lies. It is gone after a
 * minute. Just to look at; nothing can be done with it.
 */
public class SeveredLimb extends Entity {
    private static final EntityDataAccessor<Integer> LIMB = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BLOOD = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLEEVE = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
    private static final int LIFE = 1200;

    /** Spin while flying (degrees a tick), on the client. */
    public float spin, spinO, roll;

    public SeveredLimb(EntityType<? extends SeveredLimb> type, Level level) {
        super(type, level);
    }

    /** Throws {@code limb} off {@code from} at {@code at}, out along {@code out}. */
    public static void throwOff(LivingEntity from, Limbs.Limb limb, Vec3 at, Vec3 out) {
        SeveredLimb e = new SeveredLimb(ModEntities.SEVERED_LIMB.get(), from.level());
        e.entityData.set(LIMB, limb.ordinal());
        e.entityData.set(SKIN, skinTone(from));
        e.entityData.set(BLOOD, Blood.color(from));
        e.entityData.set(SLEEVE, -1);
        e.setPos(at.x, at.y - 0.2, at.z);
        e.setYRot(from.getRandom().nextFloat() * 360);
        e.setDeltaMovement(out.x * 0.35 + (from.getRandom().nextDouble() - 0.5) * 0.1, 0.35 + from.getRandom().nextDouble() * 0.15,
                out.z * 0.35 + (from.getRandom().nextDouble() - 0.5) * 0.1);
        from.level().addFreshEntity(e);
    }

    private static int skinTone(LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player p)
            return com.dbzenith.data.ModCapabilities.get(p).map(d -> d.getSkinTone() < 0 ? 0xD9A87A : d.getSkinTone()).orElse(0xD9A87A);
        return switch (Blood.race(e)) {
            case NAMEKIAN -> 0x6FB04A;
            case FROST_DEMON -> 0xE8E4F0;
            case MAJIN -> 0xF2A0C8;
            default -> 0xD9A87A;
        };
    }

    public Limbs.Limb limb() {
        return Limbs.Limb.values()[Math.max(0, Math.min(3, entityData.get(LIMB)))];
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public int blood() {
        return entityData.get(BLOOD);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LIMB, 0);
        entityData.define(SKIN, 0xD9A87A);
        entityData.define(BLOOD, 0x8E0B0B);
        entityData.define(SLEEVE, -1);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!isNoGravity()) v = v.add(0, -0.04, 0);
        boolean wasOnGround = onGround();
        move(MoverType.SELF, v);
        v = getDeltaMovement();
        if (onGround()) {
            if (!wasOnGround && v.y < -0.15) setDeltaMovement(v.x * 0.5, -v.y * 0.3, v.z * 0.5);   // a bounce
            else setDeltaMovement(v.x * 0.6, v.y, v.z * 0.6);
        } else setDeltaMovement(v.scale(0.98));
        if (level().isClientSide) {
            spinO = spin;
            double speed = getDeltaMovement().length();
            if (!onGround() || speed > 0.02) spin += (float) (speed * 60 + (onGround() ? 0 : 12));
            if (onGround()) roll += (90 - roll) * 0.3f;                          // it settles on its side
            if (tickCount < 60 && tickCount % 4 == 0) com.dbzenith.client.fx.BloodFx.drip(this);   // it bleeds a little where it lies
        } else if (tickCount > LIFE) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LIMB, tag.getInt("Limb"));
        entityData.set(SKIN, tag.getInt("Skin"));
        entityData.set(BLOOD, tag.getInt("Blood"));
        tickCount = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Limb", entityData.get(LIMB));
        tag.putInt("Skin", entityData.get(SKIN));
        tag.putInt("Blood", entityData.get(BLOOD));
        tag.putInt("Age", tickCount);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }
}
