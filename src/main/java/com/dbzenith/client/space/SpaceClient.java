package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.client.*;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.galaxy.space.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT)
public final class SpaceClient {
    private static CompoundTag scene=new CompoundTag();private static Vec3 velocity=Vec3.ZERO,start=Vec3.ZERO;
    private static ResourceKey<Level> movementLevel;private static double forward,strafe,up;private static boolean brake;
    private static int fade,entry,preparing,hintTicks;private static float atmosphere;
    /** Input counts while the window has focus (and always for the scripted dev client, which runs unfocused). */
    private static boolean focused(Minecraft mc){return mc.isWindowActive()||Boolean.getBoolean("dbzenith.devAutomation");}
    public static CompoundTag system(){return scene.getCompound("system");}
    public static float entryIntensity(){var p=Minecraft.getInstance().player;return p==null||!surface(p.level())?0:Math.min(1,entry/30f)*(float)Math.max(0,Math.min(1,(p.getY()-175)/100));}
    /** Held at the edge of an atmosphere while its terrain is prepared (phase 3): the body's index and the entry altitude, or -1. */
    private static int holdBody=-1;private static double holdAltitude;private static boolean intoOrbit;
    public static void receive(CompoundTag data){
        if(data.getBoolean("preparing")){preparing=600;intoOrbit=!SpaceRuntime.isSpace(Minecraft.getInstance().level);return;}   // until the transfer or a cancel
        if(data.contains("hold")){holdBody=data.getBoolean("hold")?data.getInt("holdBody"):-1;holdAltitude=data.getDouble("holdAltitude");if(!data.getBoolean("hold"))preparing=0;if(!data.contains("system"))return;}
        holdBody=-1;
        if(!scene.getString("dimension").equals(data.getString("dimension"))&&data.getString("dimension").contains("orbit/v1/"))hintTicks=200;
        scene=data;
        if(data.getBoolean("transition")){SpaceCamera.reset();preparing=0;fade=24;velocity=new Vec3(data.getDouble("vx"),data.getDouble("vy"),data.getDouble("vz"));
            var p=Minecraft.getInstance().player;if(p!=null)p.setDeltaMovement(velocity);
            if(!data.getString("dimension").contains("orbit/v1/"))entry=140;
        }
    }
    public static boolean surface(Level l){if(l==null||!com.dbzenith.galaxy.planet.PlanetConfig.SPACE.get())return false;String d=l.dimension().location().toString();return d.equals("minecraft:overworld")||d.equals("dbzenith:namek")||d.equals("dbzenith:northern_planet")||d.equals("dbzenith:beerus_planet")||d.startsWith("dbzenith:planet/v1/");}
    public static double vacuum(){var p=Minecraft.getInstance().player;return p==null?0:SpaceRuntime.isSpace(p.level())?1:surface(p.level())?SpaceMath.atmosphere(p.getY()):0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){scene=new CompoundTag();movementLevel=null;velocity=Vec3.ZERO;fade=entry=preparing=hintTicks=0;OrbitalTextures.clear();SpaceSky.clearCaches();}
    public static CompoundTag surfaceBody(){var l=Minecraft.getInstance().level;if(l==null||!scene.contains("surfacePlanet")||!scene.getString("dimension").equals(l.dimension().location().toString()))return new CompoundTag();var list=system().getList("bodies",Tag.TAG_COMPOUND);int index=scene.getInt("surfacePlanet");return index>=0&&index<list.size()?list.getCompound(index):new CompoundTag();}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){var mc=Minecraft.getInstance();if(mc.player!=null&&SpaceRuntime.isSpace(mc.level)&&com.dbzenith.config.DBZConfig.CLIENT.flightCinematicCamera.get())e.setRoll(e.getRoll()+(float)Math.sin((mc.player.tickCount+e.getPartialTick())*.012)*.22f);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void input(MovementInputUpdateEvent e){
        var mc=Minecraft.getInstance();
        if(!SpaceRuntime.isSpace(e.getEntity().level())||e.getEntity().isPassenger()||mc.screen!=null||!focused(mc)){forward=strafe=up=0;brake=false;return;}
        var i=e.getInput();forward=i.forwardImpulse;strafe=i.leftImpulse;up=i.jumping?1:0;brake=i.shiftKeyDown;
        i.forwardImpulse=i.leftImpulse=0;i.jumping=i.shiftKeyDown=false;i.up=i.down=i.left=i.right=false;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void movement(TickEvent.PlayerTickEvent e){
        if(!(e.player instanceof LocalPlayer p))return;
        var mc=Minecraft.getInstance();if(mc.screen!=null||!focused(mc)){forward=strafe=up=0;brake=false;}
        if(JourneyClient.active()){velocity=Vec3.ZERO;p.setDeltaMovement(Vec3.ZERO);movementLevel=null;return;}
        if(!SpaceRuntime.isSpace(p.level())||p.isPassenger()){movementLevel=null;forward=strafe=up=0;brake=false;return;}
        if(e.phase==TickEvent.Phase.START){
            if(movementLevel!=p.level().dimension()){movementLevel=p.level().dimension();velocity=p.getDeltaMovement();}
            start=p.position();return;
        }
        boolean powered=p.isCreative()||p.isSpectator()||ClientPlayerData.hasData()&&ClientPlayerData.get().isFlying();
        if(p.hurtTime>0)velocity=p.getDeltaMovement();
        if(p.horizontalCollision)velocity=new Vec3(p.getX()-start.x,velocity.y,p.getZ()-start.z);
        if(p.verticalCollision)velocity=new Vec3(velocity.x,p.getY()-start.y,velocity.z);
        boolean boost=mc.screen==null&&focused(mc)&&mc.options.keySprint.isDown();
        if(SpaceCamera.active()){var f=SpaceCamera.basis();velocity=SpaceMath.step(velocity,forward,strafe,up,boost,brake,f[0],f[1],f[2],powered);}   // along the space camera's frame (phase 3)
        else velocity=SpaceMath.step(velocity,forward,strafe,up,boost,brake,p.getYRot(),p.getXRot(),powered);
        Vec3 drag=Vec3.ZERO;var anchor=SpaceCamera.anchor(p.position());                       // near a planet you turn with its ground: stopping keeps you over the same land (phase 3)
        if(anchor.body()!=null&&anchor.weight()>0){var now=ClientFrames.spin(anchor.body(),0);var next=ClientFrames.spin(anchor.body(),1);Vec3 rel=p.position().subtract(ClientFrames.center(anchor.body()));
            drag=next.apply(now.invert(rel)).subtract(rel).scale(anchor.weight());SpaceCamera.carry(now,next,anchor.weight());}
        if(holdBody>=0){var bodies=system().getList("bodies",Tag.TAG_COMPOUND);                // held just inside the atmosphere's edge until its ground is ready: the crossing goes the moment it is
            for(Tag raw:bodies){var b=(CompoundTag)raw;if(b.getInt("index")!=holdBody)continue;Vec3 c=ClientFrames.center(b);Vec3 n=p.position().subtract(c).normalize();
                double alt=p.position().distanceTo(c)-SpaceMath.radius(b.getInt("radius")),out=velocity.dot(n),top=Math.max(1,holdAltitude-2);
                if(alt<holdAltitude*.4)velocity=n.scale(Math.min(.1,Math.max(0,out)+.02));                         // too deep: a gentle lift, never a launch
                else if(alt<=top)velocity=Vec3.ZERO;else if(out>0)velocity=velocity.subtract(n.scale(out));}}   // inside the band: hover over the same ground (the spin drag still applies); above it: no further out
        p.setDeltaMovement(velocity.add(drag));p.fallDistance=0;
    }
    /** Space transfers change dimension without vanilla's loading screen: the destination was prepared, the sky carries on (phase 3). */
    @SubscribeEvent public static void noLoadingScreen(net.minecraftforge.client.event.ScreenEvent.Opening e){
        if(e.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen&&(preparing>0||fade>0)&&com.dbzenith.galaxy.planet.PlanetConfig.SPACE.get())e.setNewScreen(null);   // closes vanilla's "Joining world" too (cancelling would leave it up)
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.START)OrbitalTextures.beginFrame();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;OrbitalTextures.tick();var mc=Minecraft.getInstance();if(mc.player==null||mc.isPaused())return;
        if(hintTicks>0&&mc.screen==null)hintTicks--;if(fade>0)fade--;if(entry>0)entry--;if(preparing>0)preparing--;atmosphere=(float)vacuum();
        if((fade>0||preparing>0)&&mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen&&mc.level.hasChunkAt(mc.player.blockPosition()))mc.setScreen(null);
        if(Boolean.getBoolean("dbzenith.devAutomation")&&mc.player.tickCount%100==0)DBZenith.LOGGER.info("[space-profile] {} / {} fps / {}",mc.level.dimension().location(),mc.getFps(),OrbitalTextures.metrics()+" / "+SpaceProfile.metrics());
        if(mc.player.tickCount%10==0&&(SpaceRuntime.isSpace(mc.level)||surface(mc.level)&&mc.player.getY()>450)){
            Vec3 v=mc.player.isPassenger()?mc.player.getVehicle().getDeltaMovement():mc.player.getDeltaMovement();if(v.lengthSqr()>30.25)v=v.normalize().scale(5.5);
            com.dbzenith.network.ModNetwork.sendToServer(new com.dbzenith.network.SpaceVelocityPacket((float)v.x,(float)v.y,(float)v.z));
        }
        if(entry>0&&!SpaceRuntime.isSpace(mc.level)&&mc.player.getY()>175){
            CameraFx.rumble(CameraFx.calm()?.02f:.09f);if(mc.player.tickCount%2==0){Vec3 at=mc.player.position().add((mc.player.getRandom().nextDouble()-.5)*1.2,1,(mc.player.getRandom().nextDouble()-.5)*1.2);
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,at.x,at.y,at.z,0,.06,0);}
        }
    }
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor e){
        float f=(float)vacuum();if(f<=0)return;e.setRed(e.getRed()*(1-f));e.setGreen(e.getGreen()*(1-f));e.setBlue(e.getBlue()*(1-f));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fog(ViewportEvent.RenderFog e){
        double f=vacuum();if(f<=.01)return;
        if(f>.999&&SpaceRuntime.isSpace(Minecraft.getInstance().level)){e.setNearPlaneDistance(90000);e.setFarPlaneDistance(100000);}
        else {e.setNearPlaneDistance((float)(e.getNearPlaneDistance()*(1+f*3)));e.setFarPlaneDistance((float)(e.getFarPlaneDistance()*(1+f*2)));}
        e.setCanceled(true);
    }
    public static final class Overlay implements IGuiOverlay {
        @Override public void render(ForgeGui gui,net.minecraft.client.gui.GuiGraphics g,float pt,int w,int h){
            var mc=Minecraft.getInstance();var p=mc.player;if(p==null||JourneyClient.active())return;
            float alpha=fade>0?.35f*Math.min(1,fade/12f):0;                       // a light veil in the sky's colour, never a black screen (phase 3)
            if(alpha>0){int tint=SpaceRuntime.isSpace(mc.level)?0x05070F:0x3A1E0C;g.fill(0,0,w,h,((int)(alpha*255)<<24)|tint);}
            if(preparing>0&&600-preparing>30&&!mc.options.hideGui)g.drawCenteredString(mc.font,intoOrbit?"Leaving the atmosphere...":holdBody>=0?"Holding at the atmosphere: preparing the ground below...":"Preparing the ground below...",w/2,h-62,0xFF9FB4C4);
            if(entry>0&&surface(p.level())&&p.getY()>175){float intensity=Math.min(1,entry/30f)*(float)Math.min(1,(p.getY()-175)/100);
                for(int i=0;i<12;i++){int a=(int)((12-i)*1.4f*intensity);int color=(a<<24)|0xFF652B;g.fill(i,i,w-i,i+2,color);g.fill(i,h-i-2,w-i,h-i,color);g.fill(i,i,i+2,h-i,color);g.fill(w-i-2,i,w-i,h-i,color);}}
            if(!SpaceRuntime.isSpace(p.level())||mc.options.hideGui)return;
            var bodies=system().getList("bodies",Tag.TAG_COMPOUND);CompoundTag nearest=null;double best=Double.MAX_VALUE;
            for(Tag raw:bodies){var b=(CompoundTag)raw;double d=p.position().distanceTo(SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")))-SpaceMath.radius(b.getInt("radius"));if(d<best){best=d;nearest=b;}}
            if(nearest!=null){String text=mc.font.plainSubstrByWidth(nearest.getString("name")+"  /  "+String.format(java.util.Locale.ROOT,"%,.0f m",Math.max(0,best)),w/2-16);g.drawString(mc.font,text,w-mc.font.width(text)-10,14,0xFFC6E4EF,false);}
            if(hintTicks>0&&!DoomClient.warning())g.drawCenteredString(mc.font,"W/S thrust  /  Space rise  /  Sneak brake  /  V ki flight",w/2,h-103,0xFF819DAF);
        }
    }
}
