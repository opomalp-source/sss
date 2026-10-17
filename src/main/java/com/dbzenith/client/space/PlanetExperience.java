package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.client.KiSenseHud;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.galaxy.space.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dimension-bound presentation; server packets own every hazard, discovery and scan reading. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class PlanetExperience {
    private static CompoundTag state=new CompoundTag(),card=new CompoundTag();private static int title,age;private static float visual;
    public static void receive(CompoundTag t){if(t.getBoolean("landing")){card=t;title=220;}else{if(!state.getString("dimension").equals(t.getString("dimension")))visual=0;state=t;age=0;}}
    private static boolean valid(){var p=Minecraft.getInstance().player;return p!=null&&age<240&&state.getString("dimension").equals(p.level().dimension().location().toString());}
    public static double intensity(){return valid()?visual:0;}
    public static String weather(){return valid()?state.getString("weather"):"CLEAR";}
    public static float weatherVolume(){return (float)(intensity()*(state.getBoolean("sheltered")?.06:.22));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new CompoundTag();card=new CompoundTag();title=age=0;visual=0;}
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e){
        if(e.getType()!=net.minecraft.world.level.material.FogType.NONE||!weather().equals("DUST"))return;
        float far=(float)(e.getFarPlaneDistance()*(1-.72*intensity()));e.setFarPlaneDistance(Math.max(24,far));e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(),far*.12f));e.setCanceled(true);
    }
    @SubscribeEvent public static void fogColor(ViewportEvent.ComputeFogColor e){
        double a=intensity()*.55;if(a==0)return;String w=weather();int color=switch(w){case "DUST"->0xAD815B;case "ACID"->0x758650;case "ION"->0x70629D;case "COLD"->0xB6CFDE;default->0;};
        if(color==0)return;e.setRed((float)(e.getRed()*(1-a)+((color>>16)&255)/255d*a));e.setGreen((float)(e.getGreen()*(1-a)+((color>>8)&255)/255d*a));e.setBlue((float)(e.getBlue()*(1-a)+(color&255)/255d*a));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.isPaused())return;age++;if(title>0)title--;visual+=((valid()?state.getDouble("intensity"):0)-visual)*.05f;
        var p=mc.player;double strength=intensity();if(strength<.15||state.getBoolean("sheltered"))return;
        if(weather().equals("QUAKE")&&com.dbzenith.config.DBZConfig.CLIENT.flightCinematicCamera.get())CameraFx.rumble((float)(strength*.06));
        int budget=mc.options.particles().get()==net.minecraft.client.ParticleStatus.MINIMAL?1:4;
        for(int i=0;i<budget;i++){
            double x=p.getX()+(p.getRandom().nextDouble()-.5)*28,y=p.getY()+p.getRandom().nextDouble()*16,z=p.getZ()+(p.getRandom().nextDouble()-.5)*28;
            if(!mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(x,y,z))||!mc.level.canSeeSky(net.minecraft.core.BlockPos.containing(x,y,z)))continue;
            switch(weather()){
                case "DUST"->mc.level.addParticle(new DustParticleOptions(new org.joml.Vector3f(.65f,.43f,.24f),1.8f),x,y,z,.22,-.02,.07);
                case "ACID"->mc.level.addParticle(new DustParticleOptions(new org.joml.Vector3f(.4f,.72f,.15f),.65f),x,y,z,.02,-.5,0);
                case "COLD"->mc.level.addParticle(ParticleTypes.SNOWFLAKE,x,y,z,.06,-.06,0);
                case "ION"->mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,x,y,z,.04,-.03,.01);
                case "METEORS"->{if(p.tickCount%20==0)mc.level.addParticle(ParticleTypes.END_ROD,x,y+50,z,-1,-.7,.2);}
                case "HEAT"->mc.level.addParticle(ParticleTypes.WHITE_ASH,x,y,z,0,.01,0);
                default->{}
            }
        }
    }
    public static void scouter(GuiGraphics g,int w,int h,float pt){
        var mc=Minecraft.getInstance();if(!SpaceRuntime.isSpace(mc.level))return;var p=mc.player;
        var bodies=SpaceClient.system().getList("bodies",Tag.TAG_COMPOUND);CompoundTag nearest=null;double best=Double.MAX_VALUE;
        for(Tag raw:bodies){var b=(CompoundTag)raw;double d=p.position().distanceTo(SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")))-SpaceMath.radius(b.getInt("radius"));if(d<best){best=d;nearest=b;}}
        if(nearest==null)return;int x=Math.max(w/2+18,w-190),y=30,color=0xFF80F3AC;
        String scan=valid()&&state.getInt("scanBody")==nearest.getInt("index")?(state.getBoolean("scanLoaded")?state.getInt("scanCount")+" signals / max "+state.getLong("scanPower"):"Surface unscanned"):"Scanning...";
        boolean destroyed=nearest.getBoolean("destroyed");
        String[] lines=destroyed?new String[]{nearest.getString("name"),"DESTROYED / debris remnant","0g / vacuum","Surface sealed until restored"}:new String[]{nearest.getString("name"),String.format(java.util.Locale.ROOT,"%,.0f m  /  %s",Math.max(0,best),nearest.getString("type")),String.format(java.util.Locale.ROOT,"%.2fg / %s",nearest.getDouble("gravity"),nearest.getString("atmosphere")),scan};
        g.fill(x-6,y-5,w-6,y+46,0xA008201C);for(int i=0;i<lines.length;i++)g.drawString(mc.font,mc.font.plainSubstrByWidth(lines[i],w-x-12),x,y+i*11,color,false);
        if(valid()&&state.getBoolean("localWaypoint")){
            String ref=state.getString("waypoint");try{int index=Integer.parseInt(ref.substring(ref.lastIndexOf('/')+1));if(index<bodies.size()){
                var b=bodies.getCompound(index);Vec3 at=SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase"));Vec3 s=KiSenseHud.project(mc,at,w,h);
                if(s!=null&&s.x>8&&s.x<w-8&&s.y>8&&s.y<h-8){int xx=(int)s.x,yy=(int)s.y;g.drawCenteredString(mc.font,"◇",xx,yy,0xFFFFD78D);g.drawCenteredString(mc.font,"Waypoint",xx,yy+11,0xFFFFD78D);}
                else {float angle=KiSenseHud.angleTo(p,at,pt);KiSenseHud.arrow(g,w/2f+(float)Math.sin(angle)*70,h/2f-(float)Math.cos(angle)*70,angle,6,0xFFFFD78D);}
            }}catch(NumberFormatException ignored){}
        }
    }
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
            if(title<=0&&intensity()>.1){String label;try{label=PlanetConditions.label(PlanetConditions.Hazard.valueOf(weather()));}catch(IllegalArgumentException ex){label="";}
                label+=state.getBoolean("protected")?" / Protected":" / Seek shelter";g.drawCenteredString(mc.font,label,w/2,h-93,0xFFEACF9A);}
            if(title<=0||!card.getString("dimension").equals(mc.level.dimension().location().toString()))return;
            float a=Math.min(1,Math.min((220-title)/22f,title/35f));int alpha=(int)(a*255);if(alpha<4)return;
            int cy=(int)(h*.43);g.fill(w/2-115,cy-13,w/2+115,cy-12,(alpha<<24)|0xD2B884);
            var pose=g.pose();pose.pushPose();float scale=Math.min(1.65f,(w-32f)/Math.max(1,mc.font.width(card.getString("name"))));pose.translate(w/2f,cy,0);pose.scale(scale,scale,1);g.drawCenteredString(mc.font,card.getString("name"),0,0,(alpha<<24)|0xF1E9D7);pose.popPose();
            g.drawCenteredString(mc.font,String.format(java.util.Locale.ROOT,"%s  /  %.2fg",card.getString("type"),card.getDouble("gravity")),w/2,cy+23,(alpha<<24)|0xAAC6CE);
            g.drawCenteredString(mc.font,"Discovered by "+card.getString("discoverer"),w/2,cy+38,(alpha<<24)|0xD2B884);
        }
    }
}
