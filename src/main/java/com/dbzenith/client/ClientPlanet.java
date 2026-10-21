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
    /** The world you are on (name, gravity, air), when the server has said. */
    public static PlanetEnvironmentPacket environment(Level level){return environment!=null&&level!=null&&environment.dimension().equals(level.dimension().location().toString())?environment:null;}
    public static double gravity(Level level){return environment!=null&&environment.dimension().equals(level.dimension().location().toString())?environment.gravity():1;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){environment=null;}
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){
        var mc=Minecraft.getInstance();var d=environment;
        if(d==null||d.radius()==0||d.name().isEmpty()||mc.player==null||!mc.player.level().dimension().location().toString().equals(d.dimension())||mc.player.isUnderWater())return;
        double edge=d.radius()-Math.max(Math.abs(mc.player.getX()),Math.abs(mc.player.getZ()));
        if(edge<192){float far=(float)Math.max(20,Math.min(e.getFarPlaneDistance(),edge+20));e.setFarPlaneDistance(far);e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(),far*.2f));e.setCanceled(true);}
    }
    /**
     * The world you stand on, in the space HUD's language ({@link com.dbzenith.client.space.SpaceUi}): its name, gravity
     * and air on a glass card for a few seconds after you arrive, and the air reserve while the air can't be breathed.
     */
    public static final class Overlay implements IGuiOverlay {
        private static String shownFor = "";
        private static long arrivedAt;
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();var d=environment;
            boolean here=d!=null&&!d.name().isEmpty()&&mc.player!=null&&!mc.options.hideGui&&!com.dbzenith.client.space.JourneyClient.active()
                    &&mc.player.level().dimension().location().toString().equals(d.dimension())&&!com.dbzenith.galaxy.space.SpaceRuntime.isSpace(mc.level);
            long now=System.currentTimeMillis();
            if(here&&!d.dimension().equals(shownFor)){shownFor=d.dimension();arrivedAt=now;}
            if(!here&&d==null)shownFor="";
            float a=com.dbzenith.client.space.SpaceUi.fade("planet-card",here&&now-arrivedAt<9000);
            boolean hazard=d!=null&&d.atmosphere()!=GalaxyCatalog.Atmosphere.BREATHABLE.ordinal();
            float air=com.dbzenith.client.space.SpaceUi.fade("planet-air",here&&hazard);
            if((a<=.01f&&air<=.01f)||d==null)return;
            String atmosphere=d.atmosphere()>=0&&d.atmosphere()<GalaxyCatalog.Atmosphere.values().length?GalaxyCatalog.label(GalaxyCatalog.Atmosphere.values()[d.atmosphere()]):"Unknown";
            String detail=String.format(Locale.ROOT,"%.2f g  ·  %s",d.gravity(),atmosphere);
            int cw=Math.max(150,Math.max(com.dbzenith.client.space.SpaceUi.valueWidth(d.name(),1f),com.dbzenith.client.space.SpaceUi.capsWidth(detail))+16),x=w/2-cw/2,y=h-82;
            if(a>.01f){
                com.dbzenith.client.space.SpaceUi.glass(g,x,y,cw,hazard?30:22,a,com.dbzenith.client.space.SpaceUi.ACCENT);
                com.dbzenith.client.space.SpaceUi.value(g,mc.font.plainSubstrByWidth(d.name(),cw-16),w/2f-Math.min(cw-16,com.dbzenith.client.space.SpaceUi.valueWidth(d.name(),1f))/2f,y+4,com.dbzenith.client.space.SpaceUi.VALUE,a,1f);
                com.dbzenith.client.space.SpaceUi.caps(g,detail,w/2f-com.dbzenith.client.space.SpaceUi.capsWidth(detail)/2f,y+14,com.dbzenith.client.space.SpaceUi.LABEL,a);
            }
            if(air>.01f){
                boolean safe=!d.protection().isEmpty();int c=safe?0x9CE2B4:d.air()<80?com.dbzenith.client.space.SpaceUi.DANGER:com.dbzenith.client.space.SpaceUi.CAUTION;
                String label=safe?d.protection():"Air reserve  "+Math.round(d.air()/3f)+"%";
                int gw=120,gy=h-52;
                com.dbzenith.client.space.SpaceUi.gauge(g,w/2-gw/2,gy,gw,safe?1:d.air()/300f,c,air);
                com.dbzenith.client.space.SpaceUi.caps(g,label,w/2f-com.dbzenith.client.space.SpaceUi.capsWidth(label)/2f,gy+3,c,air);
            }
        }
    }
}
