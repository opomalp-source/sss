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
    public static final KeyMapping POSTURE=new KeyMapping("key.dbzenith.nimbus_posture",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_M,"key.categories.dbzenith");
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(NimbusContent.TYPE,NimbusRenderer::new); }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(POSTURE); }
    @Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
    public static final class Controls {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
            if(e.phase!=TickEvent.Phase.END) return;
            Minecraft mc=Minecraft.getInstance();boolean toggle=false;
            while(POSTURE.consumeClick()) toggle=true;
            if(mc.player==null||!(mc.player.getVehicle() instanceof NimbusEntity)) return;
            if(mc.player.tickCount%2!=0&&!toggle) return;
            boolean active=mc.screen==null;
            float f=active?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
            float s=active?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
            NimbusNetwork.CHANNEL.sendToServer(new NimbusNetwork.Control(f,s,active&&mc.options.keyJump.isDown(),active&&mc.options.keySprint.isDown(),active&&toggle));
        }
    }
}
