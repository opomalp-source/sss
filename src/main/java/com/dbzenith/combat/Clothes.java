package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.network.ClothesPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clothes torn in battle stay torn until they are repaired (the knocked-out rework; the user: "when an armor or any
 * kind of clothing gets destroyed or ruined in battle, it stays that way until you repair it"). It used to follow
 * health and mend as health came back.
 * <ul>
 *   <li><b>A gi (or armour) from the mod</b> takes the blows in its durability: a hard blow (4% of health or more)
 *       wears each piece worn, more the harder it was. Torn, ripped, in tatters, by how worn the most worn piece is. It
 *       never wears right out: a ruined gi stays on, in rags, until it is mended. Mend it at an anvil with its
 *       material, or with a sewing kit.</li>
 *   <li><b>A fighter's own clothes</b> (no gi on: a player's outfit, an NPC fighter's uniform) wear the same way, the
 *       wear kept with the fighter. A sewing kit mends them.</li>
 * </ul>
 * Bruises, scratches and grime still come and go with health. A Senzu Bean heals the body, not the clothes.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Clothes {
    private static final String TAG = "dbzenith_wear";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Clothes() {}

    public static boolean eligible(LivingEntity e) {
        return e instanceof Player || e instanceof KiFighter;
    }

    /** How worn this fighter's own clothes are, 0 (whole) .. 1 (rags). */
    public static float wear(LivingEntity e) {
        return e.getPersistentData().getFloat(TAG);
    }

    /** 0 whole, 1 torn, 2 ripped, 3 in tatters. */
    public static int level(float wear) {
        return wear >= 0.8f ? 3 : wear >= 0.55f ? 2 : wear >= 0.3f ? 1 : 0;
    }

    /** How worn a gi piece is, 0..1. */
    public static float wear(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 0;
        return stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    /** The torn level shown: the most worn gi piece worn, or the fighter's own clothes if they wear no gi. */
    public static int shown(LivingEntity e) {
        int level = 0;
        boolean gi = false;
        for (EquipmentSlot s : SLOTS) {
            ItemStack stack = e.getItemBySlot(s);
            if (!(stack.getItem() instanceof GiArmorItem)) continue;
            gi = true;
            level = Math.max(level, level(wear(stack)));
        }
        return gi ? level : -1;
    }

    /** After every other rule: a hard blow wears the clothes. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !eligible(v) || event.getAmount() <= 0) return;
        if (v instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        float share = event.getAmount() / Math.max(1f, v.getMaxHealth());
        if (share < 0.04f) return;
        tear(v, share);
    }

    /** Wears what {@code v} has on by a blow of {@code share} of their health. */
    public static void tear(LivingEntity v, float share) {
        boolean gi = false;
        for (EquipmentSlot s : SLOTS) {
            ItemStack stack = v.getItemBySlot(s);
            if (!(stack.getItem() instanceof GiArmorItem) || !stack.isDamageableItem()) continue;
            gi = true;
            int max = stack.getMaxDamage();
            int add = Math.max(1, Math.round(max * share * 0.9f * (0.7f + v.getRandom().nextFloat() * 0.6f)));
            stack.setDamageValue(Math.min(max - 1, stack.getDamageValue() + add));   // it never wears right out
        }
        if (gi) return;                                                       // the gi took it
        float before = wear(v), after = Math.min(1f, before + share * 0.8f);
        v.getPersistentData().putFloat(TAG, after);
        if (level(after) != level(before)) broadcast(v);
    }

    /** Mends everything worn (a sewing kit): the fighter's own clothes whole, each gi piece {@code share} of the way. Returns whether anything needed it. */
    public static boolean mend(LivingEntity e, float share) {
        boolean any = false;
        if (wear(e) > 0) {
            e.getPersistentData().remove(TAG);
            broadcast(e);
            any = true;
        }
        for (EquipmentSlot s : SLOTS) {
            ItemStack stack = e.getItemBySlot(s);
            if (!(stack.getItem() instanceof GiArmorItem) || stack.getDamageValue() <= 0) continue;
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - Math.round(stack.getMaxDamage() * share)));
            any = true;
        }
        return any;
    }

    private static void broadcast(LivingEntity e) {
        ClothesPacket m = new ClothesPacket(e.getId(), level(wear(e)));
        if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, m);
        else ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> e), m);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity t && eligible(t) && wear(t) > 0 && event.getEntity() instanceof ServerPlayer p)
            ModNetwork.sendTo(p, new ClothesPacket(t.getId(), level(wear(t))));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && wear(p) > 0) broadcast(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) broadcast(p);
    }

    /** Your clothes come back with you from the Otherworld... as they were (the wear is kept over a death). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        float w = wear(event.getOriginal());
        if (w > 0) event.getEntity().getPersistentData().putFloat(TAG, w);
    }
}
