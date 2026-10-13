package com.dbzenith.client.screen;
import com.dbzenith.DBZenith;
import com.dbzenith.nimbus.*;
import com.dbzenith.data.ModCapabilities;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Disposable client review; never included in the packaged mod. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public class NimbusReview {
    static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("dbzenith.devNimbusReview")||e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null) return;
        ticks++;
        if(ticks==40) {
            mc.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.fov().set(70);mc.options.hideGui=true;
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()-> {
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);if(p==null)return;
                p.serverLevel().setWeatherParameters(6000,0,false,false);p.serverLevel().setDayTime(6000);var d=ModCapabilities.getOrThrow(p);d.setAlignment(100);d.setFlying(false);
                p.stopRiding();p.teleportTo(7.5,88,5.5);
                var n=new NimbusEntity(NimbusContent.TYPE,p.level());n.moveTo(7.5,87,5.5,0,0);p.level().addFreshEntity(n);p.startRiding(n);
            });
        }
        if(ticks>60) { mc.player.setYRot(ticks>190?80:0);mc.player.setXRot(-8);mc.options.keyUp.setDown(ticks<220); }
        if(ticks==150) NimbusNetwork.CHANNEL.sendToServer(new NimbusNetwork.Control(1,0,false,false,true));
        if(ticks==130||ticks==210||ticks==260) {
            int frame=ticks;if(mc.player.getVehicle() instanceof NimbusEntity n) DBZenith.LOGGER.info("[nimbus-review] wake={} position={} sitting={}",n.wake.size(),n.position(),n.sitting());
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(()->Screenshot.grab(mc.gameDirectory,"nimbus-review-"+frame+".png",mc.getMainRenderTarget(),m->DBZenith.LOGGER.info("[nimbus-review] {}",m.getString())));
        }
        if(ticks==290) { mc.options.keyUp.setDown(false);mc.stop(); }
    }
}
