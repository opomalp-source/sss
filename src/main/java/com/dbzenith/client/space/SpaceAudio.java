package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class SpaceAudio {
    private static final List<Loop> loops=new ArrayList<>();private static boolean wasSpace;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();boolean space=mc.level!=null&&SpaceRuntime.isSpace(mc.level);
        if(mc.player==null){loops.clear();wasSpace=false;return;}
        if(space&&!wasSpace)mc.getSoundManager().stop(null,SoundSource.MUSIC);wasSpace=space;
        if((space||SpaceClient.entryIntensity()>0||PlanetExperience.weatherVolume()>0)&&loops.stream().noneMatch(l->!l.isStopped())){
            loops.clear();var sounds=List.of(ModSounds.SPACE_DRONE.get(),ModSounds.SPACE_BREATH.get(),ModSounds.SPACE_HEARTBEAT.get(),ModSounds.SPACE_ENTRY.get(),ModSounds.SPACE_MUSIC.get(),ModSounds.SPACE_ENTRY.get());
            for(int i=0;i<sounds.size();i++){var loop=new Loop(sounds.get(i),i);loops.add(loop);mc.getSoundManager().play(loop);}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void muffle(PlaySoundEvent e){
        var s=e.getSound();if(s==null||s instanceof Loop||s.getSource()==SoundSource.MASTER||s.getSource()==SoundSource.VOICE||s.isRelative())return;
        double vacuum=SpaceClient.vacuum();if(vacuum<.01)return;
        if(s.getSource()==SoundSource.MUSIC){if(vacuum>.9)e.setSound(null);return;}
        // Preserve identity/lifecycle of tickable engine sounds. Their wind loop has its own continuous altitude fade.
        if(s instanceof TickableSoundInstance)return;
        var p=Minecraft.getInstance().player;double distance=p==null?100:p.distanceToSqr(s.getX(),s.getY(),s.getZ());float gain=(float)(1-vacuum*(distance<4?.6:.965));
        e.setSound(new Muffled(s,gain,(float)(1-vacuum*.32)));
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final int kind;private final net.minecraft.world.level.Level level;
        Loop(SoundEvent event,int kind){super(event,kind==4?SoundSource.MUSIC:SoundSource.AMBIENT,SoundInstance.createUnseededRandom());this.kind=kind;this.level=Minecraft.getInstance().level;looping=true;relative=true;volume=.001f;attenuation=Attenuation.NONE;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){
            var mc=Minecraft.getInstance();if(mc.level!=level||mc.player==null){stop();return;}
            boolean space=SpaceRuntime.isSpace(level);float entry=SpaceClient.entryIntensity();if(!space&&entry<=0&&PlanetExperience.weatherVolume()<=0){stop();return;}
            double nearest=20000;for(var raw:SpaceClient.system().getList("bodies",net.minecraft.nbt.Tag.TAG_COMPOUND)){
                var b=(net.minecraft.nbt.CompoundTag)raw;nearest=Math.min(nearest,mc.player.position().distanceTo(SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")))-SpaceMath.radius(b.getInt("radius")));
            }
            float swell=(float)Math.max(0,1-nearest/6000);boolean sealed=com.dbzenith.item.SpaceSuitItem.sealed(mc.player)||mc.player.isPassenger();
            float target=switch(kind){case 0->space?.14f+swell*.10f:0;case 1->space&&sealed?.48f:0;case 2->space&&!mc.player.isCreative()?.14f:0;case 3->entry*.75f;case 5->PlanetExperience.weatherVolume();default->space?.12f+swell*.2f:0;};
            volume+=(target-volume)*.045f;pitch=kind==5?.55f:kind==3?.8f+entry*.25f:1;
        }
    }
    private record Muffled(SoundInstance original,float gain,float tone) implements SoundInstance {
        public ResourceLocation getLocation(){return original.getLocation();}public WeighedSoundEvents resolve(SoundManager m){return original.resolve(m);}public Sound getSound(){return original.getSound();}
        public SoundSource getSource(){return original.getSource();}public boolean isLooping(){return original.isLooping();}public boolean isRelative(){return original.isRelative();}public int getDelay(){return original.getDelay();}
        public float getVolume(){return original.getVolume()*gain;}public float getPitch(){return original.getPitch()*tone;}public double getX(){return original.getX();}public double getY(){return original.getY();}public double getZ(){return original.getZ();}
        public Attenuation getAttenuation(){return original.getAttenuation();}public boolean canStartSilent(){return original.canStartSilent();}public boolean canPlaySound(){return original.canPlaySound();}
    }
}
