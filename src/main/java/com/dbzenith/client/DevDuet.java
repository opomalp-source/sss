package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.fusion.FusionDance;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Dev automation only: a client-side stand-in partner for reviewing two-player animations (the fusion dance, the
 * Potara) frame by frame with one test client. It wears the local player's look and stands where the real partner
 * would: on the right for the dance, facing them for the Potara.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class DevDuet {
    private static final int ID = -42_042;
    private static RemotePlayer partner;
    private static boolean facing;

    private DevDuet() {}

    public static AbstractClientPlayer spawn(boolean faceToFace) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        clear();
        facing = faceToFace;
        partner = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes("dbz-duet".getBytes()), "Partner"));
        partner.setId(ID);
        place(mc);
        mc.level.addPlayer(ID, partner);
        PublicStatePacket s = ClientPublicStates.get(mc.player.getId());
        if (s != null) ClientPublicStates.put(s.withEntity(ID));
        return partner;
    }

    public static boolean faceToFace() {
        return facing;
    }

    public static AbstractClientPlayer partner() {
        return partner;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (partner != null && mc.level != null) mc.level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        partner = null;
    }

    private static void place(Minecraft mc) {
        float yaw = mc.player.getYRot();
        double r = Math.toRadians(yaw);
        double x, z;
        float face;
        if (facing) {                                                       // a couple of steps in front, turned round
            x = mc.player.getX() - Math.sin(r) * 2.0;
            z = mc.player.getZ() + Math.cos(r) * 2.0;
            face = yaw + 180;
        } else {                                                            // on the right, the dance's distance away
            x = mc.player.getX() - Math.cos(r) * FusionDance.SEPARATION;
            z = mc.player.getZ() - Math.sin(r) * FusionDance.SEPARATION;
            face = yaw;
        }
        partner.moveTo(x, mc.player.getY(), z, face, 0);
        partner.setOldPosAndRot();
        partner.setYBodyRot(face);
        partner.yBodyRotO = face;
        partner.setYHeadRot(face);
        partner.yHeadRotO = face;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || partner == null) return;
        if (mc.level == null || mc.player == null || partner.level() != mc.level) {
            partner = null;
            return;
        }
        place(mc);
    }
}
