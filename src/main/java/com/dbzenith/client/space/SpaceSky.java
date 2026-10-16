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
    private static VertexBuffer stars;private static final Vec3 STAR=new Vec3(0,128,0);
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent e){
        e.register(new ResourceLocation(DBZenith.MOD_ID,"space"),new Effects());
        e.register(new ResourceLocation("minecraft:overworld"),new Ascent());
    }
    private static final class Effects extends DimensionSpecialEffects {
        Effects(){super(Float.NaN,false,SkyType.NONE,true,false);}
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 v,float f){return Vec3.ZERO;}
        @Override public boolean isFoggyAt(int x,int z){return false;}
        @Override public boolean renderSky(ClientLevel l,int ticks,float pt,PoseStack ps,Camera camera,Matrix4f projection,boolean foggy,Runnable setupFog){render(l,pt,ps,camera,projection,1,true);setupFog.run();return true;}
        @Override public boolean renderClouds(ClientLevel l,int t,float pt,PoseStack p,double x,double y,double z,Matrix4f m){return true;}
        @Override public boolean renderSnowAndRain(ClientLevel l,int t,float pt,LightTexture light,double x,double y,double z){return true;}
        @Override public boolean tickRain(ClientLevel l,int t,Camera c){return true;}
    }
    private static final class Ascent extends DimensionSpecialEffects.OverworldEffects {
        @Override public boolean renderSky(ClientLevel l,int t,float pt,PoseStack ps,Camera camera,Matrix4f projection,boolean foggy,Runnable setupFog){
            if(!SpaceClient.surface(l)||camera.getPosition().y<=180)return false;
            render(l,pt,ps,camera,projection,(float)SpaceMath.atmosphere(camera.getPosition().y),false);setupFog.run();return true;
        }
        @Override public boolean renderClouds(ClientLevel l,int t,float pt,PoseStack p,double x,double y,double z,Matrix4f m){return SpaceClient.surface(l)&&y>260;}
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
            for(var body:sorted)body(m,body,cameraPos,level.getGameTime()+pt);
            var suns=sys.getList("starsDetail",Tag.TAG_COMPOUND);for(int i=0;i<suns.size();i++){var s=suns.getCompound(i);Vec3 offset=new Vec3((i-(suns.size()-1)*.5)*650,0,0);sun(m,STAR.add(offset).subtract(cameraPos),s.getInt("color"),Math.min(450,200*s.getDouble("radius")),pt);}
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
        if(d<r*.98)return;double scale=100/d;Vec3 projected=relative.scale(scale);double radius=r*scale;
        int segments=radius>12?56:radius>3?32:radius>.5?16:8;
        Vec3 light=STAR.subtract(center).normalize();
        if(b.getBoolean("rings"))rings(m,projected,radius,relative.normalize(),false,b.getDouble("tilt"));
        sphere(m,projected,radius,b,light,segments,time,false,1);
        if(b.getString("type").equals("Temperate")||b.getString("type").equals("Ocean")||b.getString("type").equals("Jungle")){
            var clouds=b.copy();clouds.putBoolean("cloudLayer",true);sphere(m,projected,radius*1.009,clouds,light,segments,time,false,1);
        }
        if(!b.getString("atmosphere").equals("None"))sphere(m,projected,radius*1.018,b,light,segments,time,true,1);
        if(b.getBoolean("rings"))rings(m,projected,radius,relative.normalize(),true,b.getDouble("tilt"));
        var moons=b.getList("moons",Tag.TAG_COMPOUND);for(int i=0;i<moons.size();i++){
            var moon=moons.getCompound(i);double phase=moon.getDouble("phase")+time*.000002/(i+1),orbitRadius=r*(2+i*.65);
            Vec3 moonAt=center.add(Math.cos(phase)*orbitRadius,Math.sin(phase)*orbitRadius*.35,Math.sin(phase)*orbitRadius).subtract(camera);double md=moonAt.length();
            if(md>d&&moonAt.normalize().dot(relative.normalize())>Math.sqrt(Math.max(0,1-r*r/(d*d))))continue;
            CompoundTag mb=new CompoundTag();mb.putInt("color",moon.getInt("color"));mb.putString("type","Barren");mb.putBoolean("landable",true);
            sphere(m,moonAt.scale(100/Math.max(1,md)),Math.min(r*.18,SpaceMath.radius(moon.getInt("radius"))*.5)*100/Math.max(1,md),mb,light,12,time,false,1);
        }
    }
    private static void sphere(Matrix4f m,Vec3 c,double r,CompoundTag b,Vec3 light,int segments,double time,boolean atmosphere,float alpha){
        boolean clouds=b.getBoolean("cloudLayer"),horizon=b.getBoolean("horizon"),vertexColor=atmosphere||horizon;
        if(!vertexColor){RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,OrbitalTextures.get(b,r>5?(clouds?256:512):64));}
        var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,vertexColor?DefaultVertexFormat.POSITION_COLOR:DefaultVertexFormat.POSITION_TEX_COLOR);Vec3 view=c.scale(-1).normalize();int rows=Math.max(4,segments/2),base=b.getInt("color");
        for(int y=0;y<rows;y++)for(int x=0;x<segments;x++){
            double lon=x*Math.PI*2/segments,lat=-Math.PI/2+y*Math.PI/rows,dl=Math.PI*2/segments,dp=Math.PI/rows;Vec3 middle=normal(lon+dl*.5,lat+dp*.5);
            if(middle.dot(view)<-.03)continue;
            Vec3[] n={normal(lon,lat),normal(lon,lat+dp),normal(lon+dl,lat+dp),normal(lon+dl,lat)};
            int corner=0;for(Vec3 v:n){float lit=(float)(.055+.945*Math.pow(Math.max(0,v.dot(light)),.65));float a=alpha;int color=base;
                if(atmosphere){color=base==0?0x78BDE5:mix(base,0x92DFFF,.68);a*=(float)(Math.pow(1-Math.max(0,v.dot(view)),3)*.55);lit=.3f+.7f*lit;}
                else {double feature=Math.sin(v.x*13+Math.sin(v.z*9))*Math.cos(v.y*17+v.z*5)+Math.sin(v.z*24-v.x*12)*.23;
                    String type=b.getString("type");boolean wet=type.equals("Ocean")||type.equals("Temperate")||type.equals("Jungle");
                    if(wet){color=feature>(type.equals("Ocean")?.65:-.08)?base:0x245E91;if(Math.abs(v.y)>.88)color=0xCEE2E6;
                        double cloud=Math.sin(v.x*27+time*.0009+Math.sin(v.z*17))*Math.sin(v.y*31+v.z*13);if(cloud>.72)color=mix(color,0xF4F7F5,.68);}
                    else if(type.equals("Gas giant"))color=mix(base,0xF3DECB,.18+.2*Math.sin(v.y*42+Math.sin(v.x*8)));
                    else if(type.equals("Volcanic")&&Math.abs(feature)<.06)color=0xFF8A32;
                    else color=mix(base,feature>0?0xEEEEEE:0x1E2639,Math.abs(feature)*.22);
                }
                if(horizon){color=mix(0x5B98BC,base,Math.max(0,v.y));lit=1;a*=.65f;}
                if(vertexColor)vertex(buffer,m,c.add(v.scale(r)),color,lit,a);
                else {Vec3 at=c.add(v.scale(r));double u=(lon+(corner>=2?dl:0))/(Math.PI*2)+time/(clouds?160000:1200000d),vv=(lat+(corner==1||corner==2?dp:0))/Math.PI+.5;
                    buffer.vertex(m,(float)at.x,(float)at.y,(float)at.z).uv((float)u,(float)vv).color(lit,lit,lit,a).endVertex();}corner++;
            }
        }BufferUploader.drawWithShader(buffer.end());RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
    private static int mix(int a,int b,double t){t=Math.max(0,Math.min(1,t));return ((int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t)<<16)|((int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t)<<8)|(int)((a&255)*(1-t)+(b&255)*t);}
    private static void rings(Matrix4f m,Vec3 c,double radius,Vec3 view,boolean front,double tilt){
        var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);double a=Math.toRadians(18+tilt);
        for(int i=0;i<128;i++){
            double t=i*Math.PI/64,tt=(i+1)*Math.PI/64;Vec3 dir=new Vec3(Math.cos(t),Math.sin(t)*Math.sin(a),Math.sin(t)*Math.cos(a));if((dir.dot(view)<0)!=front)continue;
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
