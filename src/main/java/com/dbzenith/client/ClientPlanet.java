package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.network.PlanetEnvironmentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.gui.overlay.*;
import java.util.Locale;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class ClientPlanet {
    private static PlanetEnvironmentPacket environment;
    public static void receive(PlanetEnvironmentPacket data){environment=data;}
    public static double gravity(Level level){return environment!=null&&environment.dimension().equals(level.dimension().location().toString())?environment.gravity():1;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){environment=null;}
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){
        var mc=Minecraft.getInstance();var d=environment;
        if(d==null||d.radius()==0||d.name().isEmpty()||mc.player==null||!mc.player.level().dimension().location().toString().equals(d.dimension())||mc.player.isUnderWater())return;
        double edge=d.radius()-Math.max(Math.abs(mc.player.getX()),Math.abs(mc.player.getZ()));
        if(edge<192){float far=(float)Math.max(20,Math.min(e.getFarPlaneDistance(),edge+20));e.setFarPlaneDistance(far);e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(),far*.2f));e.setCanceled(true);}
    }
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();var d=environment;
            if(d==null||d.name().isEmpty()||mc.player==null||mc.options.hideGui||com.dbzenith.client.space.JourneyClient.active()||!mc.player.level().dimension().location().toString().equals(d.dimension()))return;
            String air=d.atmosphere()>=0&&d.atmosphere()<GalaxyCatalog.Atmosphere.values().length?GalaxyCatalog.label(GalaxyCatalog.Atmosphere.values()[d.atmosphere()]):"Unknown";
            String title=String.format(Locale.ROOT,"%s  |  %.2fg  |  %s",d.name(),d.gravity(),air);
            title=mc.font.plainSubstrByWidth(title,Math.min(310,w-24));int x=(w-mc.font.width(title))/2;
            g.fill(x-5,h-77,x+mc.font.width(title)+5,h-62,0xB0081724);g.drawString(mc.font,title,x,h-74,0xFFC6E7F5,false);
            boolean hazard=d.atmosphere()!=GalaxyCatalog.Atmosphere.BREATHABLE.ordinal();
            if(hazard){int left=w/2-65;g.fill(left-1,h-59,left+131,h-52,0xC0081724);g.fill(left,h-58,left+Math.max(0,Math.min(130,d.air()*130/300)),h-53,d.air()<80?0xFFEF735A:0xFF6FC5DC);
                String protection=d.protection().isEmpty()?"Air reserve":d.protection();g.drawCenteredString(mc.font,protection,w/2,h-48,d.protection().isEmpty()?0xFFFFB781:0xFF9CE2B4);}
        }
    }
}
