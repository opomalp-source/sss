package com.dbzenith.client.screen;
import com.dbzenith.DBZenith;
import com.dbzenith.social.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated UI inspection, excluded from release packaging. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public class SocialReview {
    static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("dbzenith.devSocialReview")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ticks++;
        if(ticks==30){
            mc.options.hideGui=false;mc.options.guiScale().set(3);mc.resizeDisplay();
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.stopRiding();p.teleportTo(7.5,90,5.5);p.getAbilities().flying=true;
                for(boolean clan:new boolean[]{false,true}) {var g=SocialData.get(p.server).membership(id,clan);if(g!=null)SocialService.act(p,g.owner.equals(id)?"disband":"leave",clan,"");}
            });
            mc.setScreen(new StatScreen());
        }
        if(ticks==80)mc.setScreen(new SocialScreen(false));
        if(ticks==110)SocialNetwork.action("create",false,"");
        if(ticks==160)mc.setScreen(new SocialScreen(true));
        if(ticks==190)SocialNetwork.action("create",true,"Capsule Corps");
        if(ticks==230)SocialNetwork.action("access",true,"");
        if(ticks==260){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var d=SocialData.get(p.server);var g=d.membership(id,true);for(int i=1;i<=12;i++)g.members.put(java.util.UUID.randomUUID(),"Member"+i);d.setDirty();SocialService.refresh(p.server);});}
        if(ticks==270){mc.options.guiScale().set(4);mc.resizeDisplay();}
        if(ticks==60||ticks==100||ticks==150||ticks==180||ticks==220||ticks==250||ticks==290){int frame=ticks;com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(()->Screenshot.grab(mc.gameDirectory,"social-review-"+frame+".png",mc.getMainRenderTarget(),m->DBZenith.LOGGER.info("[social-review] {}",m.getString())));}
        if(ticks==300){
            var button=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equalsIgnoreCase("Stats")).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();button.onPress();
            if(!(mc.screen instanceof StatScreen))throw new IllegalStateException("Stats tab failed to navigate from social screen");DBZenith.LOGGER.info("[social-review] PASS: Stats tab navigation");
        }
        if(ticks==310){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);for(boolean clan:new boolean[]{false,true})SocialService.act(p,"disband",clan,"");});}
        if(ticks==330)mc.stop();
    }
}
