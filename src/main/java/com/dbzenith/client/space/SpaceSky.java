package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.space.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Original mesh sky. No texture downloads, no per-body render targets, bounded tessellation and a cached star VBO. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceSky {
    private static final Map<String,List<Remnants.Fragment>> DEBRIS=new LinkedHashMap<>(16,.75f,true);
    public static void clearCaches(){DEBRIS.clear();}
    private static VertexBuffer stars;private static final Vec3 STAR=new Vec3(0,128,0);
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent e){
        e.register(new ResourceLocation(DBZenith.MOD_ID,"space"),new Effects());
        e.register(new ResourceLocation("minecraft:overworld"),new Ascent());
    }
    private static final class Effects extends DimensionSpecialEffects {
        Effects(){super(Float.NaN,false,SkyType.NONE,true,false);}
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 v,float f){return Vec3.ZERO;}
        @Override public boolean isFoggyAt(int x,int z){return false;}
        @Override public boolean renderSky(ClientLevel l,int ticks,float pt,PoseStack ps,Camera camera,Matrix4f projection,boolean foggy,Runnable setupFog){if(SpaceProfile.bypass){setupFog.run();return true;}long began=System.nanoTime();try{render(l,pt,ps,camera,projection,1,true);}finally{SpaceProfile.sky(System.nanoTime()-began);}setupFog.run();return true;}
        @Override public boolean renderClouds(ClientLevel l,int t,float pt,PoseStack p,double x,double y,double z,Matrix4f m){return true;}
        @Override public boolean renderSnowAndRain(ClientLevel l,int t,float pt,LightTexture light,double x,double y,double z){return true;}
        @Override public boolean tickRain(ClientLevel l,int t,Camera c){return true;}
    }
    private static final class Ascent extends DimensionSpecialEffects.OverworldEffects {
        @Override public void adjustLightmapColors(ClientLevel l,float pt,float dark,float flicker,float skyLight,int block,int sky,org.joml.Vector3f color){
            if(l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)||SpaceClient.surfaceBody().isEmpty()||sky==0)return;
            var b=SpaceClient.surfaceBody();var p=Minecraft.getInstance().player;if(p==null)return;
            int count=SpaceClient.system().getList("starsDetail",Tag.TAG_COMPOUND).size();Vec3 sun=SpaceMath.star(0,count).subtract(SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")));
            double daylight=PlanetSkyMath.daylight(PlanetSkyMath.local(sun,SpaceMath.normal(p.getX(),p.getZ(),b.getInt("radius")),(l.getGameTime()+pt)/Math.max(1,b.getInt("day"))).normalize().y,count);
            float weight=sky/15f*(1-block/15f);color.mul(1-weight*(float)(1-daylight)*.75f);if(daylight>.4)color.lerp(new org.joml.Vector3f((float)daylight),weight*.55f);
        }
        @Override public boolean renderSky(ClientLevel l,int t,float pt,PoseStack ps,Camera camera,Matrix4f projection,boolean foggy,Runnable setupFog){
            if(SpaceProfile.bypass)return false;
            if(SpaceClient.surface(l)&&!l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&!SpaceClient.surfaceBody().isEmpty()){long began=System.nanoTime();try{surface(l,pt,ps,camera,projection);}finally{SpaceProfile.sky(System.nanoTime()-began);}setupFog.run();return true;}
            if(!SpaceClient.surface(l)||camera.getPosition().y<=180)return false;
            long began=System.nanoTime();try{render(l,pt,ps,camera,projection,(float)SpaceMath.atmosphere(camera.getPosition().y),false);}finally{SpaceProfile.sky(System.nanoTime()-began);}setupFog.run();return true;
        }
        @Override public boolean renderClouds(ClientLevel l,int t,float pt,PoseStack p,double x,double y,double z,Matrix4f m){return SpaceClient.surface(l)&&(y>260||SpaceClient.surfaceBody().getString("atmosphere").equals("None"));}
        @Override public boolean renderSnowAndRain(ClientLevel l,int t,float pt,LightTexture light,double x,double y,double z){return !l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&SpaceClient.surface(l);}
        @Override public boolean tickRain(ClientLevel l,int t,Camera camera){return !l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&SpaceClient.surface(l);}
    }
    private static void surface(ClientLevel level,float pt,PoseStack ps,Camera camera,Matrix4f projection){
        var b=SpaceClient.surfaceBody();var sys=SpaceClient.system();var suns=sys.getList("starsDetail",Tag.TAG_COMPOUND);if(suns.isEmpty())return;
        Vec3 center=SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")),normal=SpaceMath.normal(camera.getPosition().x,camera.getPosition().z,b.getInt("radius"));
        double tilt=Math.toRadians(b.getDouble("tilt"));normal=new Vec3(normal.x,normal.y*Math.cos(tilt)-normal.z*Math.sin(tilt),normal.y*Math.sin(tilt)+normal.z*Math.cos(tilt));
        double turns=(level.getGameTime()+pt)/Math.max(1,b.getInt("day")),vacuum=b.getString("atmosphere").equals("None")?1:SpaceMath.atmosphere(camera.getPosition().y);
        Vec3 primary=PlanetSkyMath.local(SpaceMath.star(0,suns.size()).subtract(center),normal,turns);double daylight=PlanetSkyMath.daylight(primary.normalize().y,suns.size());
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();FogRenderer.setupNoFog();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m=ps.last().pose();var buffer=Tesselator.getInstance().getBuilder();
        int skyColor=level.getBiome(camera.getBlockPosition()).value().getSkyColor();Vec3 sky=new Vec3((skyColor>>16&255)/255d,(skyColor>>8&255)/255d,(skyColor&255)/255d).scale(daylight*(1-vacuum));int rgb=((int)(sky.x*255)<<16)|((int)(sky.y*255)<<8)|(int)(sky.z*255);
        rgb=mix(rgb,0x8B281A,DoomClient.intensity()*.75);
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int face=0;face<6;face++){Vec3 n=switch(face){case 0->new Vec3(1,0,0);case 1->new Vec3(-1,0,0);case 2->new Vec3(0,1,0);case 3->new Vec3(0,-1,0);case 4->new Vec3(0,0,1);default->new Vec3(0,0,-1);};Vec3 side=Math.abs(n.y)>.5?new Vec3(1,0,0):n.cross(new Vec3(0,1,0));quad(buffer,m,n.scale(190),side.scale(190),n.cross(side).scale(190),rgb,1);}
        BufferUploader.drawWithShader(buffer.end());buildStars();RenderSystem.setShaderColor(1,1,1,(float)Math.max(vacuum,(1-daylight)*.8));stars.bind();stars.drawWithShader(m,projection,GameRenderer.getPositionColorShader());VertexBuffer.unbind();RenderSystem.setShaderColor(1,1,1,1);
        var moons=b.getList("moons",Tag.TAG_COMPOUND);double radius=SpaceMath.radius(b.getInt("radius"));
        for(int i=0;i<suns.size();i++){var star=suns.getCompound(i);Vec3 direction=SpaceMath.star(i,suns.size()).subtract(center);double size=Math.min(450,200*star.getDouble("radius")),cover=0;
            for(int j=0;j<moons.size();j++){var moon=moons.getCompound(j);double phase=moon.getDouble("phase")+(level.getGameTime()+pt)*.000002/(j+1);Vec3 md=new Vec3(Math.cos(phase),Math.sin(phase)*.35,Math.sin(phase)).scale(radius*(2+j*.65));cover=Math.max(cover,PlanetSkyMath.eclipse(direction,size,md,Math.min(radius*.18,SpaceMath.radius(moon.getInt("radius"))*.5)));}
            sun(m,PlanetSkyMath.local(direction,normal,turns),mix(star.getInt("color"),0x15141D,cover),size,pt);
        }
        for(Tag raw:sys.getList("bodies",Tag.TAG_COMPOUND)){var other=(CompoundTag)raw;if(other.getInt("index")==b.getInt("index"))continue;Vec3 rel=SpaceMath.center(other.getDouble("orbit"),other.getDouble("phase")).subtract(center);double d=rel.length();
            sphere(m,PlanetSkyMath.local(rel,normal,turns).scale(100/d),SpaceMath.radius(other.getInt("radius"))*100/d,other,PlanetSkyMath.local(SpaceMath.star(0,suns.size()).subtract(SpaceMath.center(other.getDouble("orbit"),other.getDouble("phase"))),normal,turns).normalize(),16,level.getGameTime()+pt,false,1);}
        for(int j=0;j<moons.size();j++){var moon=moons.getCompound(j);double phase=moon.getDouble("phase")+(level.getGameTime()+pt)*.000002/(j+1);Vec3 md=new Vec3(Math.cos(phase),Math.sin(phase)*.35,Math.sin(phase)).scale(radius*(2+j*.65));double d=md.length();
            sphere(m,PlanetSkyMath.local(md,normal,turns).scale(100/d),Math.min(radius*.18,SpaceMath.radius(moon.getInt("radius"))*.5)*100/d,moon,primary.normalize(),24,level.getGameTime()+pt,false,1);}
        if(b.getBoolean("rings")){ // translucent near-side arc of the planet's own equatorial ring
            var ring=Tesselator.getInstance().getBuilder();ring.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int i=0;i<96;i++){double a=i*Math.PI/48,aa=(i+1)*Math.PI/48;Vec3 n=new Vec3(Math.cos(a),.22,Math.sin(a)),nn=new Vec3(Math.cos(aa),.22,Math.sin(aa));vertex(ring,m,n.scale(140),0xB6ADA5,1,.26f);vertex(ring,m,n.add(0,.16,0).scale(140),0xD8CABC,1,.08f);vertex(ring,m,nn.add(0,.16,0).scale(140),0xD8CABC,1,.08f);vertex(ring,m,nn.scale(140),0xB6ADA5,1,.26f);}BufferUploader.drawWithShader(ring.end());
        }
        RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 p,int rgb,float light,float alpha){b.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(Math.min(1,((rgb>>16)&255)/255f*light),Math.min(1,((rgb>>8)&255)/255f*light),Math.min(1,(rgb&255)/255f*light),alpha).endVertex();}
    private static void quad(BufferBuilder b,Matrix4f m,Vec3 c,Vec3 a,Vec3 d,int color,float alpha){vertex(b,m,c.subtract(a).subtract(d),color,1,alpha);vertex(b,m,c.subtract(a).add(d),color,1,alpha);vertex(b,m,c.add(a).add(d),color,1,alpha);vertex(b,m,c.add(a).subtract(d),color,1,alpha);}
    private static Vec3 normal(double lon,double lat){return new Vec3(Math.cos(lat)*Math.cos(lon),Math.sin(lat),Math.cos(lat)*Math.sin(lon));}
    private static void buildStars(){
        if(stars!=null)return;var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);Random random=new Random(0x44425A5350414345L);Matrix4f identity=new Matrix4f();
        for(int i=0;i<3100;i++){
            double lon=random.nextDouble()*Math.PI*2,lat=Math.asin(random.nextDouble()*2-1);Vec3 n=normal(lon,lat),c=n.scale(170);
            Vec3 side=n.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.5)side=new Vec3(1,0,0);Vec3 up=n.cross(side).normalize();
            double size=.055+Math.pow(random.nextDouble(),6)*.22;int[] colors={0x8CB6FF,0xCADFFF,0xFFF3D0,0xFFC88D,0xFF998A};int color=colors[random.nextInt(colors.length)];
            quad(b,identity,c,side.scale(size),up.scale(size),color,.45f+random.nextFloat()*.55f);
            if(size>.15)quad(b,identity,c,side.scale(size*3),up.scale(size*.16),color,.11f);
        }
        // The galactic band is diffuse layered light with irregular seeded knots, rather than a flat texture strip.
        for(int i=0;i<1700;i++){
            double lon=random.nextDouble()*Math.PI*2,lat=random.nextGaussian()*.065+.18*Math.sin(lon);Vec3 n=normal(lon,lat),side=n.cross(new Vec3(0,1,0)).normalize(),up=n.cross(side).normalize();
            double size=.2+random.nextDouble()*1.7;int color=i%3==0?0x7B668F:0x49738F;
            Vec3 center=n.scale(168);float opacity=.018f+random.nextFloat()*.027f;
            for(int j=0;j<12;j++){double a=j*Math.PI/6,aa=(j+1)*Math.PI/6;vertex(b,identity,center,color,1,opacity);vertex(b,identity,center.add(side.scale(Math.cos(a)*size*1.6)).add(up.scale(Math.sin(a)*size)),color,1,0);vertex(b,identity,center.add(side.scale(Math.cos(aa)*size*1.6)).add(up.scale(Math.sin(aa)*size)),color,1,0);vertex(b,identity,center,color,1,opacity);}
        }
        for(int group=0;group<5;group++)for(int i=0;i<95;i++){
            double lon=group*1.39+.4+random.nextGaussian()*.06,lat=.14*Math.sin(lon)+random.nextGaussian()*.035;
            Vec3 n=normal(lon,lat),side=n.cross(new Vec3(0,1,0)).normalize(),up=n.cross(side).normalize(),center=n.scale(167);double size=.8+random.nextDouble()*3.5;
            int color=group%2==0?0x77608A:0x416F86;
            for(int j=0;j<12;j++){double a=j*Math.PI/6,aa=(j+1)*Math.PI/6;vertex(b,identity,center,color,1,.028f);vertex(b,identity,center.add(side.scale(Math.cos(a)*size*2)).add(up.scale(Math.sin(a)*size)),color,1,0);vertex(b,identity,center.add(side.scale(Math.cos(aa)*size*2)).add(up.scale(Math.sin(aa)*size)),color,1,0);vertex(b,identity,center,color,1,.028f);}
        }
        stars=new VertexBuffer(VertexBuffer.Usage.STATIC);stars.bind();stars.upload(b.end());VertexBuffer.unbind();
    }
    private static void render(ClientLevel level,float pt,PoseStack ps,Camera camera,Matrix4f projection,float vacuum,boolean orbit){
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();FogRenderer.setupNoFog();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m=ps.last().pose();var builder=Tesselator.getInstance().getBuilder();
        if(!orbit){
            Vec3 sky=level.getSkyColor(camera.getPosition(),pt).scale(1-vacuum);int rgb=((int)(sky.x*255)<<16)|((int)(sky.y*255)<<8)|(int)(sky.z*255);
            builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            Vec3[] corners={new Vec3(-190,-190,-190),new Vec3(190,-190,-190),new Vec3(190,190,-190),new Vec3(-190,190,-190),new Vec3(-190,-190,190),new Vec3(190,-190,190),new Vec3(190,190,190),new Vec3(-190,190,190)};
            int[][] faces={{0,1,2,3},{4,5,6,7},{0,4,7,3},{1,5,6,2},{3,2,6,7},{0,1,5,4}};
            for(var face:faces)for(int k:face)vertex(builder,m,corners[k],rgb,1,1);BufferUploader.drawWithShader(builder.end());
        }
        buildStars();RenderSystem.setShaderColor(1,1,1,orbit?1:vacuum*vacuum);stars.bind();stars.drawWithShader(m,projection,GameRenderer.getPositionColorShader());VertexBuffer.unbind();RenderSystem.setShaderColor(1,1,1,1);
        if(orbit){
            Vec3 cameraPos=camera.getPosition();var sys=SpaceClient.system();var bodies=sys.getList("bodies",Tag.TAG_COMPOUND);List<CompoundTag> sorted=new ArrayList<>();for(Tag t:bodies)sorted.add((CompoundTag)t);
            sorted.sort(Comparator.comparingDouble((CompoundTag b)->SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")).distanceToSqr(cameraPos)).reversed());
            Vec3 look=new Vec3(camera.getLookVector().x(),camera.getLookVector().y(),camera.getLookVector().z());
            for(var body:sorted){double bound=SpaceMath.radius(body.getInt("radius"))*Math.max(4,3+body.getList("moons",Tag.TAG_COMPOUND).size()*.65);var event=DoomClient.body(body.getInt("index"));if(event!=null&&event.phase()==com.dbzenith.galaxy.planet.DoomData.DESTROYED&&DoomClient.aftermath(event)<180)bound*=4;
                if(OrbitalLod.visible(SpaceMath.center(body.getDouble("orbit"),body.getDouble("phase")).subtract(cameraPos),bound,look))body(m,body,cameraPos,level.getGameTime()+pt);
            }
            var suns=sys.getList("starsDetail",Tag.TAG_COMPOUND);for(int i=0;i<suns.size();i++){var s=suns.getCompound(i);sun(m,SpaceMath.star(i,suns.size()).subtract(cameraPos),s.getInt("color"),Math.min(450,200*s.getDouble("radius")),pt);}
        }else if(vacuum>.1){
            // A distant curved limb meets the real chunks below; close terrain always draws over this sky pass.
            var body=SpaceClient.surfaceBody().copy();if(body.isEmpty()){body.putInt("color",0x477EAA);body.putString("type","Temperate");body.putString("atmosphere","Breathable");}
            body.putBoolean("horizon",true);body.putInt("color",0x427A97);
            sphere(m,new Vec3(0,-330,0),270,body,new Vec3(.7,.5,.2),24,0,false,.25f+(vacuum*.75f));
        }
        RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);
    }
    private static void body(Matrix4f m,CompoundTag b,Vec3 camera,double time){
        Vec3 center=SpaceMath.center(b.getDouble("orbit"),b.getDouble("phase")),relative=center.subtract(camera);double d=Math.max(1,relative.length()),r=SpaceMath.radius(b.getInt("radius"));
        double scale=100/d;Vec3 projected=relative.scale(scale);double radius=r*scale;
        int segments=OrbitalLod.segments(radius,com.dbzenith.config.DBZConfig.CLIENT.spaceQuality.get());
        var doom=DoomClient.body(b.getInt("index"));
        if(b.getBoolean("destroyed")||doom!=null&&doom.phase()==com.dbzenith.galaxy.planet.DoomData.DESTROYED){remnant(m,b,camera,time,doom);return;}
        if(d<r*.98)return;
        if(radius<.7){impostor(m,projected,radius,b.getInt("color"));return;}
        Vec3 light=SpaceMath.star(0,SpaceClient.system().getList("starsDetail",Tag.TAG_COMPOUND).size()).subtract(center).normalize();
        if(b.getBoolean("rings"))rings(m,projected,radius,relative.normalize(),false,b.getDouble("tilt"));
        sphere(m,projected,radius,b,light,segments,time,false,1);
        if(doom!=null&&doom.phase()==com.dbzenith.galaxy.planet.DoomData.ACTIVE)seams(m,projected,radius,DoomClient.progress(doom),b.getInt("index"));
        if(b.getString("type").equals("Temperate")||b.getString("type").equals("Ocean")||b.getString("type").equals("Jungle")){
            var clouds=b.copy();clouds.putBoolean("cloudLayer",true);sphere(m,projected,radius*1.009,clouds,light,segments,time,false,1);
        }
        if(!b.getString("atmosphere").equals("None"))halo(m,projected,radius,b.getInt("color"),light);
        if(b.getBoolean("rings"))rings(m,projected,radius,relative.normalize(),true,b.getDouble("tilt"));
        var moons=b.getList("moons",Tag.TAG_COMPOUND);for(int i=0;i<moons.size();i++){
            var moon=moons.getCompound(i);double phase=moon.getDouble("phase")+time*.000002/(i+1),orbitRadius=r*(2+i*.65);
            Vec3 moonAt=center.add(Math.cos(phase)*orbitRadius,Math.sin(phase)*orbitRadius*.35,Math.sin(phase)*orbitRadius).subtract(camera);double md=moonAt.length();
            if(md>d&&moonAt.normalize().dot(relative.normalize())>Math.sqrt(Math.max(0,1-r*r/(d*d))))continue;
            CompoundTag mb=new CompoundTag();mb.putInt("color",moon.getInt("color"));mb.putString("type","Barren");mb.putBoolean("landable",true);
            double moonRadius=Math.min(r*.18,SpaceMath.radius(moon.getInt("radius"))*.5)*100/Math.max(1,md);
            if(moonRadius<.7)impostor(m,moonAt.scale(100/Math.max(1,md)),moonRadius,mb.getInt("color"));else sphere(m,moonAt.scale(100/Math.max(1,md)),moonRadius,mb,light,12,time,false,1);
        }
    }
    private static void seams(Matrix4f m,Vec3 c,double r,float progress,int identity){
        if(progress<.15)return;var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int path=0;path<7;path++)for(int i=0;i<18;i++){
            double lat=-1.3+i*.14,lon=path*.9+Math.sin(i*1.9+identity+path)*.055,width=.002+progress*.012,nextLon=path*.9+Math.sin((i+1)*1.9+identity+path)*.055;
            if(normal(lon,lat).dot(c.scale(-1).normalize())<0)continue;
            vertex(b,m,c.add(normal(lon-width,lat).scale(r*1.012)),0xFF471A,1,progress);
            vertex(b,m,c.add(normal(lon+width,lat).scale(r*1.012)),0xFFE48C,1,progress);
            vertex(b,m,c.add(normal(nextLon+width,lat+.14).scale(r*1.012)),0xFF6E27,1,progress);
            vertex(b,m,c.add(normal(nextLon-width,lat+.14).scale(r*1.012)),0xFF471A,1,progress);
        }BufferUploader.drawWithShader(b.end());
    }
    private static void remnant(Matrix4f m,CompoundTag body,Vec3 camera,double time,com.dbzenith.network.DoomPacket.Body event){
        Vec3 center=SpaceMath.center(body.getDouble("orbit"),body.getDouble("phase"));double r=SpaceMath.radius(body.getInt("radius"));
        float age=event==null?1201:DoomClient.aftermath(event);
        if(age<180){
            Vec3 rel=center.subtract(camera);double d=Math.max(1,rel.length()),scale=100/d;Vec3 c=rel.scale(scale),view=rel.normalize(),side=view.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.1)side=new Vec3(1,0,0);Vec3 up=view.cross(side).normalize();
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);double rr=r*scale*(1+age*.045);
            for(int i=0;i<96;i++){double a=i*Math.PI/48,aa=(i+1)*Math.PI/48;Vec3 n=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),nn=side.scale(Math.cos(aa)).add(up.scale(Math.sin(aa)));
                vertex(b,m,c.add(n.scale(rr)),0xFFE3A8,1,1-age/180);vertex(b,m,c.add(n.scale(rr*1.08)),0xFF7A3C,1,0);vertex(b,m,c.add(nn.scale(rr*1.08)),0xFF7A3C,1,0);vertex(b,m,c.add(nn.scale(rr)),0xFFE3A8,1,1-age/180);
            }BufferUploader.drawWithShader(b.end());
            if(age<55){var flash=body.copy();flash.putBoolean("horizon",true);flash.putBoolean("detonation",true);flash.putInt("color",0xFFE3AF);sphere(m,c,r*scale*(1+age*.025),flash,new Vec3(0,1,0),24,time,false,(1-age/55)*com.dbzenith.config.DBZConfig.CLIENT.screenFlash.get().floatValue()*(com.dbzenith.client.fx.CameraFx.calm()?.18f:1));}
        }
        var identity=SpaceClient.system().getString("id")+"/"+body.getInt("index");
        var fragments=DEBRIS.computeIfAbsent(identity+":"+body.getDouble("orbit")+":"+body.getDouble("phase")+":"+r,k->Remnants.fragments(identity,body.getInt("index"),center,r));
        if(DEBRIS.size()>12)DEBRIS.remove(DEBRIS.keySet().iterator().next());
        for(var f:fragments){
            double expansion=Math.min(1,.12+age/90);Vec3 at=center.add(f.center().subtract(center).scale(expansion)).subtract(camera);double distance=Math.max(1,at.length());
            var rock=new CompoundTag();rock.putString("type","Barren");rock.putInt("color",0x76615B);rock.putDouble("phase",Math.floorMod(f.seed(),3)/3d);
            sphere(m,at.scale(100/distance),(f.radius()+Math.max(0,r*.065-f.radius())*Math.min(1,Math.max(0,(distance-200)/1800)))*100/distance,rock,new Vec3(.5,.7,.2),8,time,false,Math.min(1,age/25f));
        }
    }
    private static void sphere(Matrix4f m,Vec3 c,double r,CompoundTag b,Vec3 light,int segments,double time,boolean atmosphere,float alpha){
        boolean clouds=b.getBoolean("cloudLayer"),horizon=b.getBoolean("horizon"),vertexColor=atmosphere||horizon;
        if(!vertexColor){RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,OrbitalTextures.get(b,OrbitalLod.texture(r,clouds,com.dbzenith.config.DBZConfig.CLIENT.spaceQuality.get())));}
        var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,vertexColor?DefaultVertexFormat.POSITION_COLOR:DefaultVertexFormat.POSITION_TEX_COLOR);Vec3 view=c.scale(-1).normalize();int base=b.getInt("color"),verts=0;
        for(var patch:OrbitalLod.mesh(segments)){
            if(patch.middle().dot(view)<-.03)continue;
            int corner=0;for(Vec3 v:patch.corners()){
                float lit=(float)(.055+.945*Math.pow(Math.max(0,v.dot(light)),.65)),a=alpha;int color=base;
                if(atmosphere){color=base==0?0x78BDE5:mix(base,0x92DFFF,.68);a*=(float)(Math.pow(1-Math.max(0,v.dot(view)),3)*.55);lit=.3f+.7f*lit;}
                if(horizon){color=b.getBoolean("detonation")?0xFFF0C8:mix(0x5B98BC,base,Math.max(0,v.y));lit=1;a*=b.getBoolean("detonation")?.95f:.65f;}
                float x=(float)(c.x+v.x*r),y=(float)(c.y+v.y*r),z=(float)(c.z+v.z*r);
                if(vertexColor)buffer.vertex(m,x,y,z).color(((color>>16)&255)/255f*lit,((color>>8)&255)/255f*lit,(color&255)/255f*lit,a).endVertex();
                else {float u=patch.u()+(corner>=2?patch.du():0)+(float)(time/(clouds?160000:1200000d)),vv=patch.v()+(corner==1||corner==2?patch.dv():0);
                    buffer.vertex(m,x,y,z).uv(u,vv).color(lit,lit,lit,a).endVertex();}corner++;verts++;
            }
        }BufferUploader.drawWithShader(buffer.end());SpaceProfile.draw(verts);RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
    private static void halo(Matrix4f m,Vec3 center,double radius,int color,Vec3 light){
        Vec3 n=center.normalize(),side=n.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.5)side=new Vec3(1,0,0);Vec3 up=n.cross(side).normalize();int rgb=mix(color,0x84CBFF,.86),pieces=64;
        var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int i=0;i<pieces;i++){
            double a=i*Math.PI*2/pieces,b=(i+1)*Math.PI*2/pieces;Vec3 p=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),q=side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));float lit=(float)(.25+.75*Math.max(0,p.dot(light)));
            for(int band=0;band<2;band++){double inner=band==0?.985:1.006,outer=band==0?1.006:1.045;float ai=band==0?0:.3f,ao=band==0?.3f:0;
                vertex(buffer,m,center.add(p.scale(radius*inner)),rgb,lit,ai);vertex(buffer,m,center.add(p.scale(radius*outer)),rgb,lit,ao);vertex(buffer,m,center.add(q.scale(radius*outer)),rgb,lit,ao);vertex(buffer,m,center.add(q.scale(radius*inner)),rgb,lit,ai);
            }
        }BufferUploader.drawWithShader(buffer.end());SpaceProfile.draw(pieces*8);
    }
    private static void impostor(Matrix4f m,Vec3 center,double radius,int color){
        Vec3 n=center.normalize(),side=n.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.5)side=new Vec3(1,0,0);Vec3 up=n.cross(side).normalize();
        var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;Vec3 p=center.add(side.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius)),q=center.add(side.scale(Math.cos(b)*radius)).add(up.scale(Math.sin(b)*radius));
            vertex(buffer,m,center,color,.9f,1);vertex(buffer,m,p,color,.55f,1);vertex(buffer,m,q,color,.55f,1);vertex(buffer,m,center,color,.9f,1);
        }BufferUploader.drawWithShader(buffer.end());SpaceProfile.draw(48);
    }
    private static int mix(int a,int b,double t){t=Math.max(0,Math.min(1,t));return ((int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t)<<16)|((int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t)<<8)|(int)((a&255)*(1-t)+(b&255)*t);}
    private static void rings(Matrix4f m,Vec3 c,double radius,Vec3 view,boolean front,double tilt){
        var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);double a=Math.toRadians(18+tilt);
        int pieces=com.dbzenith.config.DBZConfig.CLIENT.spaceQuality.get()==0?64:128;
        for(int i=0;i<pieces;i++){
            double t=i*Math.PI*2/pieces,tt=(i+1)*Math.PI*2/pieces;Vec3 dir=new Vec3(Math.cos(t),Math.sin(t)*Math.sin(a),Math.sin(t)*Math.cos(a));if((dir.dot(view)<0)!=front)continue;
            Vec3 next=new Vec3(Math.cos(tt),Math.sin(tt)*Math.sin(a),Math.sin(tt)*Math.cos(a));float opacity=.35f+(float)Math.sin(i*.7)*.1f;
            vertex(b,m,c.add(dir.scale(radius*1.25)),0xB8AEA1,1,opacity);vertex(b,m,c.add(dir.scale(radius*1.95)),0x887C77,1,.08f);vertex(b,m,c.add(next.scale(radius*1.95)),0x887C77,1,.08f);vertex(b,m,c.add(next.scale(radius*1.25)),0xB8AEA1,1,opacity);
        }BufferUploader.drawWithShader(b.end());
    }
    private static void sun(Matrix4f m,Vec3 relative,int color,double radius,float pt){
        double distance=Math.max(1,relative.length());Vec3 n=relative.normalize(),c=n.scale(99);Vec3 side=n.cross(new Vec3(0,1,0)).normalize();if(side.lengthSqr()<.5)side=new Vec3(1,0,0);Vec3 up=n.cross(side).normalize();double r=Math.max(.06,radius*99/distance);
        var b=Tesselator.getInstance().getBuilder();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int i=15;i>=0;i--){double rr=r*(1+i*.19);float alpha=i==0?1:.015f+(15-i)*.001f;for(int j=0;j<48;j++){
            double a=j*Math.PI/24,aa=(j+1)*Math.PI/24;Vec3 edge=side.scale(Math.cos(a)*rr).add(up.scale(Math.sin(a)*rr)),next=side.scale(Math.cos(aa)*rr).add(up.scale(Math.sin(aa)*rr));
            vertex(b,m,c,color,1,alpha);vertex(b,m,c.add(edge),i==0?0xFFF1D6:color,1,alpha);vertex(b,m,c.add(next),i==0?0xFFF1D6:color,1,alpha);vertex(b,m,c,color,1,alpha);
        }}
        quad(b,m,c,side.scale(r*7),up.scale(r*.12),color,.08f);BufferUploader.drawWithShader(b.end());RenderSystem.defaultBlendFunc();
    }
}
