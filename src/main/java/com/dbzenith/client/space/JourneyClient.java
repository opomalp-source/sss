package com.dbzenith.client.space;
import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.network.JourneyPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class JourneyClient {
    private static boolean active,cancelSent;private static int elapsed,duration,heard,arrival;private static String name="";
    public static boolean active(){return active;}
    public static float strength(){if(!active)return 0;return Math.min(1,Math.min(elapsed/45f,(duration-elapsed+35)/60f));}
    public static void receive(JourneyPacket m){if(!active)cancelSent=false;active=m.active();elapsed=m.elapsed();duration=Math.max(1,m.duration());heard=0;name=m.name();if(!active&&!name.isEmpty())arrival=35;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){active=false;arrival=0;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END){if(active){elapsed=Math.min(duration,elapsed+1);if(++heard>80)active=false;if(!cancelSent&&Minecraft.getInstance().options.keyShift.isDown()){cancelSent=true;com.dbzenith.network.ModNetwork.sendToServer(new com.dbzenith.network.GalaxyPackets.Request(0,11,"0:0:0:0",0,"",0));}}if(arrival>0)arrival--;}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void input(MovementInputUpdateEvent e){if(!active)return;var i=e.getInput();i.forwardImpulse=i.leftImpulse=0;i.jumping=i.shiftKeyDown=i.up=i.down=i.left=i.right=false;}
    @SubscribeEvent public static void hotbar(RenderGuiOverlayEvent.Pre e){if(active&&e.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id()))e.setCanceled(true);}
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();if(mc.player==null)return;
            if(arrival>0)g.fill(0,0,w,h,((int)(arrival/35f*45*com.dbzenith.config.DBZConfig.CLIENT.screenFlash.get()*(CameraFx.calm()?.25:1))<<24)|0x639CBF);
            if(!active)return;float strength=strength(),calm=CameraFx.calm()?.28f:1;
            g.flush();g.fillGradient(0,0,w,h,0xFF050B20,0xFF08152E);int count=com.dbzenith.client.fx.KiParticles.quality()==0?32:96;
            float cx=w/2f,cy=h/2f;double time=(elapsed+pt)*(.012+.025*strength*calm);
            for(int i=0;i<count;i++){
                long hash=GalaxyCatalog.mix(i+0x4859504552L);double angle=(hash>>>11)*0x1.0p-53*Math.PI*2,depth=((hash&65535)/65536d-time)%1;if(depth<0)depth+=1;
                double near=1/(depth+.08),far=1/(depth+.13+.16*strength*calm);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle),scale=Math.max(w,h)*.085f;
                float x=cx+dx*(float)near*scale,y=cy+dy*(float)near*scale,xx=cx+dx*(float)far*scale,yy=cy+dy*(float)far*scale,width=.35f+strength*.5f;
                int color=((int)((1-depth)*160)<<24)|(i%4==0?0xB4A2F4:0xA7E4FF);
                DbzTheme.quad(g,x-dy*width,y+dx*width,x+dy*width,y-dx*width,xx+dy*width,yy-dx*width,xx-dy*width,yy+dx*width,color,color,0x0869BDEB,0x0869BDEB);
            }
            if(mc.options.hideGui)return;
            g.drawCenteredString(mc.font,"HYPERSPEED",w/2,h/2-14,0xFFD5EDFF);g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(name,w-32),w/2,h/2+3,0xFF92BDD5);
            int left=w/2-85;g.fill(left,h/2+22,left+170,h/2+24,0xFF243951);g.fill(left,h/2+22,left+Math.min(170,elapsed*170/duration),h/2+24,0xFF9FD9ED);
            g.drawCenteredString(mc.font,elapsed>=duration?"Stabilizing arrival...":String.format(java.util.Locale.ROOT,"%.1f s",Math.max(0,duration-elapsed)/20f),w/2,h/2+31,0xFF829AB3);
            g.drawCenteredString(mc.font,"Sneak: disengage",w/2,h/2+48,0xFF617D96);
        }
    }
}
