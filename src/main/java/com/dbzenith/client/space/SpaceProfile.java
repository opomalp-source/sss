package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in dev diagnostics only: frame intervals include presentation; sky timings measure CPU/driver submission. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class SpaceProfile {
    public static final boolean ENABLED=Boolean.getBoolean("dbzenith.devAutomation");
    public static boolean bypass;
    private static final double[] frames=new double[600],skies=new double[600];
    private static int count,cursor,warm,vertices,draws;private static long last,sky;
    private static String dimension="",label="default";
    public static void reset(String name){count=cursor=0;warm=120;last=0;label=name;}
    public static void sky(long nanos){if(ENABLED)sky+=nanos;}
    public static void draw(int verts){if(ENABLED){vertices+=verts;draws++;}}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(e.phase==TickEvent.Phase.START){sky=0;vertices=draws=0;return;}
        String id=mc.level.dimension().location().toString();if(!id.equals(dimension)){dimension=id;reset(label);}
        long now=System.nanoTime();if(last!=0&&warm--<=0){frames[cursor]=(now-last)/1e6;skies[cursor]=sky/1e6;cursor=(cursor+1)%frames.length;count=Math.min(frames.length,count+1);}last=now;
    }
    public static String metrics(){
        if(count==0)return label+" / warming";var sample=Arrays.copyOf(frames,count);Arrays.sort(sample);double mean=Arrays.stream(sample).average().orElse(0),skyMean=Arrays.stream(skies,0,count).average().orElse(0);
        return String.format(Locale.ROOT,"%s / n=%d / frame mean=%.3f p95=%.3fms / sky mean=%.3fms / mesh=%d verts %d draws",label,count,mean,sample[(int)((count-1)*.95)],skyMean,vertices,draws);
    }
}
