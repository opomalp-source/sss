package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Badly hurt (CX-52, the user: "when our health is low the player moves slower, does ki blasts slower, with their own
 * animations, and stumbles a lot"). Below 35% health a fighter is wounded, the more so the lower it gets
 * ({@link #wounded}, 0..1, worked out the same on both sides from the synced health):
 * <ul>
 *   <li>they move up to 45% slower on foot and fly up to 40% slower;</li>
 *   <li>their ki comes out slower: blasts fly up to 35% slower and every technique takes up to 80% longer to come
 *       round again; they fire one-handed, clutching their side (client);</li>
 *   <li>moving, they stumble now and then, more often the worse it is: a lurch, a moment's stall, a stumbling
 *       animation;</li>
 *   <li>they hunch over, a hand pressed to the wound (client).</li>
 * </ul>
 * Players and NPC fighters alike. Knocked out is its own state ({@link KnockOut}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Wounds {
    /** Below this share of health a fighter starts to show it. */
    public static final float FROM = 0.35f;
    private static final UUID SLOW = UUID.fromString("c52d7a10-3e1b-4c5a-9e1d-0c52ab7e0001");

    private Wounds() {}

    /** How badly hurt: 0 above {@link #FROM} of their health, 1 at none left. */
    public static float wounded(LivingEntity e) {
        float max = e.getMaxHealth();
        if (max <= 0 || !e.isAlive()) return 0;
        return Mth.clamp((FROM - e.getHealth() / max) / FROM, 0, 1);
    }

    /** Ki fired this badly hurt flies this much slower (a factor). */
    public static float kiSpeed(LivingEntity e) {
        return 1 - 0.35f * wounded(e);
    }

    /** Techniques take this much longer to come round again (a factor). */
    public static double cooldown(LivingEntity e) {
        return 1 + 0.8 * wounded(e);
    }

    /** Flying this badly hurt is this much slower (a factor). */
    public static float flight(LivingEntity e) {
        return 1 - 0.4f * wounded(e);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof Player || e instanceof KiFighter)) return;
        update(e);
    }

    /** One tick of being wounded: the slowdown, and the chance of a stumble. */
    public static void update(LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        boolean down = KnockOut.isDown(e);
        float w = down ? 0 : wounded(e);
        slow(e, -0.45 * w);
        if (w <= 0 || e.isPassenger() || !e.onGround()) return;
        Vec3 moved = e.position().subtract(e.xo, e.yo, e.zo);
        double speed = moved.horizontalDistance();
        if (speed > 0.04 && e.getRandom().nextFloat() < 0.004f + 0.022f * w) stumble(e, moved, w);
    }

    /** A stumble: a lurch forward, a moment's stall, the animation for everyone watching. */
    public static void stumble(LivingEntity e, Vec3 moved, float w) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12 + (int) (10 * w), w > 0.6f ? 3 : 2, false, false, false));
        Vec3 ahead = moved.horizontalDistanceSqr() > 1e-6 ? moved.multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, e.getYRot());
        e.setDeltaMovement(e.getDeltaMovement().add(ahead.scale(0.18)));
        e.hurtMarked = true;
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 0.7f);
        AnimEventPacket m = new AnimEventPacket(e.getId(), AnimEventPacket.STUMBLE, 0);
        if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, m);
        else ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> e), m);
    }

    private static void slow(LivingEntity e, double amount) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        AttributeModifier now = a.getModifier(SLOW);
        if (now != null && Math.abs(now.getAmount() - amount) < 0.02) return;
        if (now != null) a.removeModifier(SLOW);
        if (amount < -0.005) a.addTransientModifier(new AttributeModifier(SLOW, "dbzenith wounded", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
