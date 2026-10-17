package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.*;
import com.dbzenith.galaxy.planet.DoomData;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.network.DoomPacket;
import com.dbzenith.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Small, dimension-bound event snapshots. Countdown extrapolation never authorizes a detonation. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class DoomClient {
    private static DoomPacket packet=new DoomPacket("","",List.of());private static int age;
    private static final Set<Integer> sounded=new HashSet<>();
    public static void receive(DoomPacket p){
        if(!p.dimension().equals(packet.dimension()))sounded.clear();
        packet=p;age=0;
        for(var b:p.bodies())if(b.phase()==DoomData.ACTIVE)sounded.remove(b.index());
    }
    private static boolean valid(){var mc=Minecraft.getInstance();return mc.level!=null&&age<80&&mc.level.dimension().location().toString().equals(packet.dimension());}
    public static DoomPacket.Body body(int index){if(!valid())return null;for(var b:packet.bodies())if(b.index()==index)return b;return null;}
    public static boolean warning(){if(!valid())return false;var mc=Minecraft.getInstance();for(var b:packet.bodies())if(b.phase()==DoomData.ACTIVE&&(SpaceRuntime.isSpace(mc.level)||!SpaceClient.surfaceBody().isEmpty()&&SpaceClient.surfaceBody().getInt("index")==b.index()))return true;return false;}
    public static float progress(DoomPacket.Body b){return Math.min(1,(b.elapsed()+age)/(float)b.duration());}
    public static float aftermath(DoomPacket.Body b){return b.age()+age;}
    public static float intensity(){
        if(!valid()||SpaceRuntime.isSpace(Minecraft.getInstance().level)||SpaceClient.surfaceBody().isEmpty())return 0;
        var b=body(SpaceClient.surfaceBody().getInt("index"));return b!=null&&b.phase()==DoomData.ACTIVE?.15f+progress(b)*.85f:0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){packet=new DoomPacket("","",List.of());sounded.clear();age=0;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fog(ViewportEvent.ComputeFogColor e){
        float a=intensity()*.65f;e.setRed(e.getRed()*(1-a)+.7f*a);e.setGreen(e.getGreen()*(1-a)+.12f*a);e.setBlue(e.getBlue()*(1-a)+.06f*a);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();if(e.phase!=TickEvent.Phase.END||mc.player==null||mc.isPaused())return;age++;
        float strength=intensity();
        if(strength>0){
            CameraFx.rumble(strength*(CameraFx.calm()?.012f:.065f));
            if(mc.player.tickCount%10==0&&strength>.35){
                var p=mc.player;var at=p.blockPosition().offset(p.getRandom().nextInt(17)-8,-1,p.getRandom().nextInt(17)-8);
                if(mc.level.hasChunkAt(at)){var block=mc.level.getBlockState(at);if(!block.isAir())mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,block),at.getX()+.5,at.getY()+1.2,at.getZ()+.5,0,.3+strength*.3,0);}
            }
        }
        if(valid()&&SpaceRuntime.isSpace(mc.level))for(var b:packet.bodies()){
            if(b.phase()!=DoomData.DESTROYED||aftermath(b)>100||!sounded.add(b.index()))continue;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EXPLOSION_BIG.get(),.5f,.5f));
            CameraFx.shakeAt(mc.player.position(),CameraFx.calm()?.1f:.35f,120);
        }
    }
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();if(!valid()||mc.player==null||mc.screen!=null)return;
            for(var b:packet.bodies())if(b.phase()==DoomData.DESTROYED&&aftermath(b)<30&&SpaceRuntime.isSpace(mc.level)){
                float fade=(1-aftermath(b)/30f)*com.dbzenith.config.DBZConfig.CLIENT.screenFlash.get().floatValue()*(CameraFx.calm()?.08f:.3f);
                g.fill(0,0,w,h,((int)(fade*255)<<24)|0xFFE3BC);
            }
            if(mc.options.hideGui)return;
            DoomPacket.Body warning=null;double nearest=Double.MAX_VALUE;
            for(var b:packet.bodies())if(b.phase()==DoomData.ACTIVE){
                if(!SpaceRuntime.isSpace(mc.level)&&SpaceClient.surfaceBody().getInt("index")!=b.index())continue;
                double dist=0;var bodies=SpaceClient.system().getList("bodies",net.minecraft.nbt.Tag.TAG_COMPOUND);
                if(SpaceRuntime.isSpace(mc.level)&&b.index()<bodies.size()){var desc=bodies.getCompound(b.index());dist=mc.player.position().distanceToSqr(SpaceMath.center(desc.getDouble("orbit"),desc.getDouble("phase")));}
                if(dist<nearest){nearest=dist;warning=b;}
            }
            if(warning==null)return;int x=w-65,y=28;
            g.fill(w-124,y-5,w-6,y+36,0xD02C0F10);g.fill(w-124,y-5,w-122,y+36,0xFFFF8450);
            g.drawCenteredString(mc.font,"CORE INSTABILITY",x,y,0xFFFFBA82);
            g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(warning.name(),108),x,y+11,0xFFE9B397);
            g.drawCenteredString(mc.font,String.format(java.util.Locale.ROOT,"EVACUATE / %.1f s",Math.max(0,warning.duration()-warning.elapsed()-age)/20f),x,y+24,0xFFFFE3C7);
        }
    }
}
