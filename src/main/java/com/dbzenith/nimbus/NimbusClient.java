package com.dbzenith.nimbus;

import com.dbzenith.DBZenith;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.settings.KeyConflictContext;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NimbusClient {
    /** Calls your soul-bound Flying Nimbus down to you, or sends it away (CX-40). */
    public static final KeyMapping CALL=new KeyMapping("key.dbzenith.nimbus_call",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_M,"key.categories.dbzenith");
    /** Sit or stand while riding (a new id so the old M binding does not clash with the call key). */
    public static final KeyMapping POSTURE=new KeyMapping("key.dbzenith.nimbus_sit",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA,"key.categories.dbzenith");
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(NimbusContent.TYPE,NimbusRenderer::new); }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(CALL);e.register(POSTURE); }
    /** The wind while riding, louder and higher the faster the cloud goes. */
    private static final class Wind extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        Wind() {
            super(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, net.minecraft.sounds.SoundSource.PLAYERS,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = .01f;
            relative = true;
        }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(mc.player.getVehicle() instanceof NimbusEntity n)) { stop(); return; }
            float f = net.minecraft.util.Mth.clamp((n.speed - .1f) / .8f, 0, 1);
            volume = Math.max(.01f, f * f * .8f);
            pitch = .65f + .55f * f;
        }

        @Override public boolean canStartSilent() { return true; }
    }
    private static Wind wind;
    /** The rush of a called cloud swooping in, heard where it is, louder the faster it comes. */
    private static final class Rush extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final NimbusEntity cloud;

        Rush(NimbusEntity cloud) {
            super(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, net.minecraft.sounds.SoundSource.NEUTRAL,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.cloud = cloud;
            looping = true;
            delay = 0;
            volume = .01f;
            x = cloud.getX(); y = cloud.getY(); z = cloud.getZ();
        }

        @Override public void tick() {
            if (cloud.isRemoved() || cloud.state() != NimbusEntity.ARRIVING) { stop(); return; }
            x = cloud.getX(); y = cloud.getY(); z = cloud.getZ();
            float f = net.minecraft.util.Mth.clamp(cloud.speed / 1.4f, 0, 1);
            volume = Math.max(.01f, f * 1.2f);
            pitch = .7f + .5f * f;
        }

        @Override public boolean canStartSilent() { return true; }
    }
    /** A called cloud has appeared: hear it coming. */
    static void incoming(NimbusEntity cloud) {
        Minecraft.getInstance().getSoundManager().play(new Rush(cloud));
    }
    @Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
    public static final class Controls {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
            if(e.phase!=TickEvent.Phase.END) return;
            Minecraft mc=Minecraft.getInstance();boolean toggle=false;
            while(POSTURE.consumeClick()) toggle=true;
            boolean call=false;
            while(CALL.consumeClick()) call=true;
            if(call&&mc.player!=null&&mc.screen==null) NimbusNetwork.CHANNEL.sendToServer(new NimbusNetwork.Call());
            if(mc.player==null||!(mc.player.getVehicle() instanceof NimbusEntity)) return;
            if(wind==null||wind.isStopped()) { wind=new Wind();mc.getSoundManager().play(wind); }
            if(mc.player.tickCount%2!=0&&!toggle) return;
            boolean active=mc.screen==null;
            float f=active?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
            float s=active?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
            NimbusNetwork.CHANNEL.sendToServer(new NimbusNetwork.Control(f,s,active&&mc.options.keyJump.isDown(),active&&mc.options.keySprint.isDown(),active&&toggle));
        }
    }
}
