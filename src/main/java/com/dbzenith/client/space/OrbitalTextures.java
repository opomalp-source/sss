package com.dbzenith.client.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Original spherical material maps, generated once on demand from public identity (never terrain/world seeds). */
final class OrbitalTextures {
    private static final Map<String,ResourceLocation> CACHE=new LinkedHashMap<>(64,.75f,true);
    private static final Map<String,java.util.concurrent.CompletableFuture<NativeImage>> PENDING=new HashMap<>();
    private static final java.util.concurrent.Executor MATERIALS=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"DBZ-orbit-materials");t.setDaemon(true);return t;});
    private static final Set<String> READY=new HashSet<>();
    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT=new java.util.concurrent.atomic.AtomicInteger();
    private static long nextUpload;private static int uploads,peakGenerating;
    static void beginFrame(){uploads=0;}
    static ResourceLocation get(CompoundTag b,int resolution){
        String identity=b.getString("type")+":"+b.getInt("color")+":"+b.getDouble("phase")+":"+b.getDouble("orbit")+":"+b.getBoolean("cloudLayer");String key=identity+":"+resolution;
        var manager=Minecraft.getInstance().getTextureManager();var found=CACHE.get(key);var future=PENDING.get(key);
        if(found!=null){
            if(future!=null&&future.isDone()&&uploads<1&&net.minecraft.Util.getMillis()>=nextUpload){
                try{var image=future.join();manager.release(found);found=register(key,image);CACHE.put(key,found);READY.add(key);uploads++;nextUpload=net.minecraft.Util.getMillis()+16;}
                catch(java.util.concurrent.CompletionException ex){com.dbzenith.DBZenith.LOGGER.error("Orbital material failed",ex);}PENDING.remove(key);
            }
            if(!READY.contains(key)&&!PENDING.containsKey(key))request(key,b,resolution,identity);
            return found;
        }
        var placeholder=new NativeImage(1,1,false);int rgb=b.getInt("color");placeholder.setPixelRGBA(0,0,b.getBoolean("cloudLayer")?0:0xFF000000|((rgb&255)<<16)|(rgb&0xFF00)|((rgb>>>16)&255));
        found=register(key,placeholder);CACHE.put(key,found);
        request(key,b,resolution,identity);
        if(CACHE.size()>48){var it=CACHE.entrySet().iterator();var first=it.next();manager.release(first.getValue());READY.remove(first.getKey());var pending=PENDING.remove(first.getKey());if(pending!=null)pending.thenAccept(NativeImage::close);it.remove();}
        return found;
    }
    private static void request(String key,CompoundTag body,int resolution,String identity){
        if(IN_FLIGHT.get()>=4)return;peakGenerating=Math.max(peakGenerating,IN_FLIGHT.incrementAndGet());var descriptor=body.copy();
        PENDING.put(key,java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return generate(descriptor,resolution,resolution/2,identity);}finally{IN_FLIGHT.decrementAndGet();}},MATERIALS));
    }
    private static NativeImage generate(CompoundTag b,int width,int height,String key){
        var image=new NativeImage(width,height,false);long seed=GalaxyCatalog.mix(key.hashCode());boolean cloud=b.getBoolean("cloudLayer");
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            double lon=x*Math.PI*2/width,lat=-Math.PI/2+y*Math.PI/height,nx=Math.cos(lat)*Math.cos(lon),ny=Math.sin(lat),nz=Math.cos(lat)*Math.sin(lon);
            double terrain=fbm(nx*3,ny*3,nz*3,seed),fine=fbm(nx*24,ny*24,nz*24,seed+1);int color=b.getInt("color"),alpha=255;String type=b.getString("type");
            if(cloud){double amount=fbm(nx*9+ny*2,ny*14,nz*9,seed+7);alpha=(int)(Math.max(0,Math.min(1,(amount-.05)*4))*205);color=0xE2EEF2;}
            else if(type.equals("Temperate")||type.equals("Jungle")||type.equals("Ocean")){
                double threshold=type.equals("Ocean")?.19:-.03;
                if(terrain<threshold)color=mix(0x102A56,0x3983B0,Math.max(0,Math.min(1,(terrain+.5)*1.25)));
                else {color=mix(type.equals("Jungle")?0x245142:color,0xB0A36B,(terrain-threshold)*1.2);if(terrain>threshold+.24)color=mix(color,0x7B7C83,(terrain-threshold-.24)*3);}
                if(Math.abs(ny)>.91+fine*.04)color=mix(color,0xE5EAF0,.88);
            }else if(type.equals("Gas giant")){
                double bands=.5+.5*Math.sin(ny*52+terrain*4+Math.sin(lon*3)*.6);color=mix(mix(color,0x3C3049,.35),0xEAD5BB,bands*.55);
                double storm=Math.pow((nx-.7)*2,2)+Math.pow((ny+.22)*8,2)+Math.pow((nz-.67)*2,2);if(storm<.32)color=mix(color,0x8E594E,(1-storm/.32)*.6);
            }else if(type.equals("Volcanic")){
                color=mix(0x202329,0x70665B,terrain+.4);if(Math.abs(terrain+.06+fine*.12)<.018)color=mix(0xC33D16,0xFFBC59,(fine+.5));
            }else if(type.equals("Ice"))color=mix(0x557E9A,0xE7EEF2,terrain+.65);
            else if(type.equals("Crystal"))color=mix(0x574B83,0xDEDAE9,terrain+.6);
            else if(type.equals("Desert"))color=color==0xB54B3B?mix(0x632A30,0xD87A60,terrain+.6):mix(0x8C4E37,0xE4BE7F,terrain+.6);
            else color=mix(color,terrain<0?0x34363E:0xD0C8BC,Math.abs(terrain)*.8);
            if(!cloud)color=mix(color,fine>0?0xD6D3CB:0x161C26,Math.abs(fine)*.18);
            int abgr=(alpha<<24)|((color&255)<<16)|(color&0xFF00)|((color>>>16)&255);image.setPixelRGBA(x,y,abgr);
        }
        return image;
    }
    static void clear(){for(var id:CACHE.values())Minecraft.getInstance().getTextureManager().release(id);CACHE.clear();READY.clear();for(var future:PENDING.values())future.thenAccept(NativeImage::close);PENDING.clear();}
    private static ResourceLocation register(String key,NativeImage image){var texture=new DynamicTexture(image);texture.setFilter(true,false);var id=new ResourceLocation("dbzenith","orbital/"+UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));Minecraft.getInstance().getTextureManager().register(id,texture);return id;}
    static String metrics(){return CACHE.size()+" materials / "+PENDING.size()+" pending / "+IN_FLIGHT.get()+" generating (peak "+peakGenerating+")";}
    private static double hash(int x,int y,int z,long seed){return (GalaxyCatalog.mix(seed^x*0x9e3779b97f4a7c15L^y*0x632be59bd9b4e019L^z*0x94d049bb133111ebL)>>>11)*0x1.0p-53*2-1;}
    private static double smooth(double v){return v*v*v*(v*(v*6-15)+10);}
    private static double noise(double x,double y,double z,long seed){int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);double fx=smooth(x-ix),fy=smooth(y-iy),fz=smooth(z-iz),v=0;
        for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++)v+=hash(ix+a,iy+b,iz+c,seed)*(a==0?1-fx:fx)*(b==0?1-fy:fy)*(c==0?1-fz:fz);return v;}
    private static double fbm(double x,double y,double z,long seed){double value=0,weight=.57;for(int i=0;i<4;i++){value+=noise(x,y,z,seed+i*13)*weight;weight*=.48;x*=2.07;y*=2.07;z*=2.07;}return value;}
    private static int mix(int a,int b,double t){t=Math.max(0,Math.min(1,t));return ((int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t)<<16)|((int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t)<<8)|(int)((a&255)*(1-t)+(b&255)*t);}
}
