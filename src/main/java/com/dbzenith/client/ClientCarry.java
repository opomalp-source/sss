package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Carry;
import com.dbzenith.network.CarryPacket;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Who is carrying whom, on this client (the knocked-out rework). Every tick the carried fighter is put on their carrier's
 * shoulder by the same rule the server uses ({@link Carry#shoulder}), the previous tick's spot from the carrier's previous
 * position, so they ride along smoothly on every screen. If you are the one carried, your own game puts you there too
 * (it used to be left out, so nothing showed on your screen).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientCarry {
    private static final Int2IntOpenHashMap CARRIER = new Int2IntOpenHashMap();   // carried -> carrier

    private ClientCarry() {}

    public static void set(CarryPacket m) {
        if (m.on()) {
            CARRIER.put(m.carriedId(), m.carrierId());
            com.dbzenith.client.anim.AnimController.playOn(m.carrierId(), com.dbzenith.client.anim.Anims.custom("pick_up", com.dbzenith.client.anim.Anims.PICK_UP));
        } else CARRIER.remove(m.carriedId());
    }

    public static boolean isCarried(int id) {
        return CARRIER.containsKey(id);
    }

    public static boolean isCarrying(int id) {
        return CARRIER.containsValue(id);
    }

    /** The carrier's entity id, or -1. */
    public static int carrierOf(int id) {
        return CARRIER.getOrDefault(id, -1);
    }

    /** The carried one's entity id, or -1. */
    public static int carriedBy(int carrierId) {
        for (var e : CARRIER.int2IntEntrySet()) if (e.getIntValue() == carrierId) return e.getIntKey();
        return -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CARRIER.clear();
            return;
        }
        if (CARRIER.isEmpty()) return;
        for (var e : CARRIER.int2IntEntrySet()) {
            Entity v = mc.level.getEntity(e.getIntKey()), c = mc.level.getEntity(e.getIntValue());
            if (!(v instanceof LivingEntity carried) || !(c instanceof LivingEntity carrier)) continue;
            place(carried, carrier);
        }
    }

    private static void place(LivingEntity v, LivingEntity c) {
        Vec3 now = Carry.shoulder(c.getX(), c.getY(), c.getZ(), c.getYRot(), c.getBbHeight());
        Vec3 was = Carry.shoulder(c.xo, c.yo, c.zo, c.yRotO, c.getBbHeight());
        v.setPos(now.x, now.y, now.z);
        v.xo = v.xOld = was.x;
        v.yo = v.yOld = was.y;
        v.zo = v.zOld = was.z;
        v.setDeltaMovement(Vec3.ZERO);
        v.yBodyRot = c.getYRot() + 180;                                       // head down the carrier's back, legs in front
        v.yBodyRotO = c.yRotO + 180;
        if (v != Minecraft.getInstance().player) {                            // the carried player still looks round on their own
            v.setYRot(c.getYRot() + 180);
            v.yRotO = c.yRotO + 180;
            v.setYHeadRot(c.getYRot() + 180);
        }
        v.fallDistance = 0;
    }
}
