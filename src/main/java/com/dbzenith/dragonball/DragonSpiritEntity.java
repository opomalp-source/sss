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

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WISHES_LEFT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DragonSpiritEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private UUID summoner;

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

    /** Wishes it still grants (Porunga three, the others one; v0.167.0). */
    public int wishesLeft() {
        return entityData.get(WISHES_LEFT);
    }

    public void setWishesLeft(int n) {
        entityData.set(WISHES_LEFT, Math.max(0, n));
    }

    /** The wishes this dragon can grant. */
    public java.util.List<Wish> wishes() {
        return liftsCurse() ? java.util.List.of(Wish.LIFT_CURSE) : Wish.of(set());
    }

    public boolean isSummoner(Player p) {
        return summoner != null && summoner.equals(p.getUUID());
    }

    /**
     * Grants one wish to the summoner. Returns false if this dragon has no wishes left, the player is someone else, or the
     * wish can't come true now (they are told why). Porunga waits for the next wish until all three are made.
     */
    public boolean grant(ServerPlayer player, Wish wish) {
        if (wishesLeft() <= 0 || !isSummoner(player) || !wishes().contains(wish)) return false;
        if (!wish.canGrant(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.wish_impossible",
                    net.minecraft.network.chat.Component.translatable(wish.translationKey())), false);
            ModNetwork.sendTo(player, new OpenWishPacket(getId()));
            return false;
        }
        setWishesLeft(wish == Wish.SEND_TO_EARTH ? 0 : wishesLeft() - 1);                       // sent away, the summoner can wish no more
        wish.grant(player);
        if (set() == BallSet.BLACK_STAR && wish != Wish.LIFT_CURSE) DragonBalls.curse(player.server, player.level().getGameTime());
        if (wishesLeft() > 0) ModNetwork.sendTo(player, new OpenWishPacket(getId()));
        else DragonBalls.dragonDeparts(this);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > com.dbzenith.config.DBZConfig.SERVER.dragonWaitTicks.get()) DragonBalls.dragonDeparts(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && isSummoner(sp) && wishesLeft() > 0) {
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
        entityData.define(WISHES_LEFT, 1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("summoner")) summoner = tag.getUUID("summoner");
        setWishesLeft(tag.contains("wishesLeft") ? tag.getInt("wishesLeft") : tag.getBoolean("granted") ? 0 : 1);
        setSet(BallSet.byOrdinal(tag.getInt("set")));
        setLiftsCurse(tag.getBoolean("liftsCurse"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (summoner != null) tag.putUUID("summoner", summoner);
        tag.putInt("wishesLeft", wishesLeft());
        tag.putInt("set", set().ordinal());
        tag.putBoolean("liftsCurse", liftsCurse());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
