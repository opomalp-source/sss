package com.dbzenith.client.space;
import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.network.JourneyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Hyperspeed on the client (the space rework, phase 5: in the world, not a curtain over it). A short spool-up, then you
 * fly the real catalogue sky along the course to the star you chose: the stars stream past (StarSky draws them from
 * a point moving along the course), your own system falls away, the target star swells ahead and, at the end, is the
 * sun of the system you arrive in. Sneak disengages.
 */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class JourneyClient {
    private static boolean active,cancelSent;private static int elapsed,duration,heard,arrival;private static String name="";
    private static Vec3 course=Vec3.ZERO,holding=Vec3.ZERO;
    public static boolean active(){return active;}
    private static int spool(){return Math.min(50,duration/4);}
    /** How far along the course the view is (0..1): still during the spool-up, then speeding up, cruising and slowing. */
    public static double travelled(float pt){if(!active)return 0;double u=Math.max(0,Math.min(1,(elapsed+pt-spool())/Math.max(1,duration-spool())));return u*u*(3-2*u);}
    /** The view's offset from the departure system, light years: along the course, or held at its end until the new sky arrives. */
    public static Vec3 offset(float pt){return active?visual().scale(travelled(pt)):holding;}
    /** The course as the view flies it: long courses at a pace the eye can follow (8,000 ly in all), so the stars stream rather than flicker. */
    private static Vec3 visual(){return course.length()>5000?course.normalize().scale(8000):course;}
    /** The real course still to go, light years (the destination star's brightness follows it). */
    public static Vec3 remaining(float pt){return course.scale(1-travelled(pt));}
    /** How fast the view moves through the stars now (light years a tick), for streaks. */
    public static Vec3 motion(float pt){return active?visual().scale(travelled(pt+1)-travelled(pt)):Vec3.ZERO;}
    /** How far your own system has fallen away (0..1): gone a second into the travel. */
    public static float departed(float pt){if(!active)return 0;return (float)Math.max(0,Math.min(1,(elapsed+pt-spool())/20d));}
    /** How far your own system's bodies have fallen behind (orbital blocks, along the course): they shrink and are gone in a second. */
    public static Vec3 away(float pt){if(!active||course.lengthSqr()<1e-9)return Vec3.ZERO;float d=departed(pt);return course.normalize().scale(d*d*d*400000);}
    public static Vec3 course(){return active||holding.lengthSqr()>0?course:Vec3.ZERO;}
    /** The new system's sky has arrived: its stars are drawn from its own centre again. */
    public static void fieldArrived(){holding=Vec3.ZERO;}
    public static String name(){return name;}
    public static float progress(){return Math.min(1,elapsed/(float)Math.max(1,duration));}
    public static float remaining(){return Math.max(0,duration-elapsed)/20f;}
    public static boolean spooling(){return active&&elapsed<spool();}
    public static boolean stabilizing(){return active&&elapsed>=duration;}
    public static float strength(){if(!active)return 0;return Math.min(1,Math.min(elapsed/45f,(duration-elapsed+35)/60f));}
    public static void receive(JourneyPacket m){
        if(!active)cancelSent=false;
        boolean was=active;active=m.active();elapsed=m.elapsed();duration=Math.max(1,m.duration());heard=0;name=m.name();
        if(active){if(!was)SpaceAudio.cue(false);course=new Vec3(m.dx(),m.dy(),m.dz());holding=Vec3.ZERO;return;}
        if(was&&!name.isEmpty()){arrival=35;holding=visual();SpaceClient.arrive(null);SpaceAudio.cue(true);}   // arrived: the sky stays where the course ended until the new system's stars come
        else holding=Vec3.ZERO;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){active=false;arrival=0;holding=Vec3.ZERO;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END){if(active){elapsed=Math.min(duration,elapsed+1);if(++heard>80)active=false;if(!cancelSent&&Minecraft.getInstance().options.keyShift.isDown()){cancelSent=true;com.dbzenith.network.ModNetwork.sendToServer(new com.dbzenith.network.GalaxyPackets.Request(0,11,"0:0:0:0",0,"",0));}}if(arrival>0)arrival--;}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void input(MovementInputUpdateEvent e){if(!active)return;var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.jumping=i.shiftKeyDown=i.up=i.down=i.left=i.right=false;}
    /** The arrival's soft flash; the stars show the journey and the travel card ({@link NavigationHud}) its progress. */
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();if(mc.player==null)return;
            if(arrival>0)g.fill(0,0,w,h,((int)(arrival/35f*30*com.dbzenith.config.DBZConfig.CLIENT.screenFlash.get()*(CameraFx.calm()?.25:1))<<24)|0x639CBF);
        }
    }
}
