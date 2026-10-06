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

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DragonSpiritEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LIFTS_CURSE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DragonSpiritEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

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

    /** Which set of balls raised this dragon (its looks and its wishes). */
    public BallSet set() {
        return BallSet.byOrdinal(entityData.get(SET));
    }

    public void setSet(BallSet set) {
        entityData.set(SET, set.ordinal());
    }

    /** A Black Star dragon raised while the curse is on: its one wish is to lift it. */
    public boolean liftsCurse() {
        return entityData.get(LIFTS_CURSE);
    }

    public void setLiftsCurse(boolean lifts) {
        entityData.set(LIFTS_CURSE, lifts);
    }

    /** The wishes this dragon can grant. */
    public java.util.List<Wish> wishes() {
        return liftsCurse() ? java.util.List.of(Wish.LIFT_CURSE) : Wish.of(set());
    }

    public boolean isSummoner(Player p) {
        return summoner != null && summoner.equals(p.getUUID());
    }

    /** Grants one wish to the summoner. Returns false if this dragon already granted it or the player is someone else. */
    public boolean grant(ServerPlayer player, Wish wish) {
        if (granted || !isSummoner(player) || !wishes().contains(wish)) return false;
        granted = true;
        wish.grant(player);
        if (set() == BallSet.BLACK_STAR && wish != Wish.LIFT_CURSE) DragonBalls.curse(player.server, player.level().getGameTime());
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

    /** The Eternal Dragon, the Black Star dragon, the Super Dragon. */
    @Override
    protected net.minecraft.network.chat.Component getTypeName() {
        return set() == BallSet.EARTH ? super.getTypeName() : net.minecraft.network.chat.Component.translatable("entity.dbzenith.dragon_spirit." + set().id());
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
        entityData.define(SET, 0);
        entityData.define(LIFTS_CURSE, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("summoner")) summoner = tag.getUUID("summoner");
        granted = tag.getBoolean("granted");
        setSet(BallSet.byOrdinal(tag.getInt("set")));
        setLiftsCurse(tag.getBoolean("liftsCurse"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (summoner != null) tag.putUUID("summoner", summoner);
        tag.putBoolean("granted", granted);
        tag.putInt("set", set().ordinal());
        tag.putBoolean("liftsCurse", liftsCurse());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
