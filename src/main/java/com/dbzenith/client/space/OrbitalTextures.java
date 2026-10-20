package com.dbzenith.client.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.space.PlanetMaps;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.network.PlanetMapPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/**
 * Orbital sphere textures. A landable planet shows its map from the server (the space rework, phase 4: drawn from the
 * ground it generates, {@link PlanetMaps}); until that arrives, and for gas giants, moons and fragments, an original
 * material generated from public identity (never terrain or world seeds). A planet's cloud layer is vanilla's own
 * cloud pattern at every surface point, drifting as vanilla's clouds do, so the clouds seen from space are the clouds
 * over the ground.
 */
public final class OrbitalTextures {
    private static final Map<String,ResourceLocation> CACHE=new LinkedHashMap<>(64,.75f,true);
    private static final Map<String,java.util.concurrent.CompletableFuture<NativeImage>> PENDING=new HashMap<>();
    private static final java.util.concurrent.Executor MATERIALS=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"DBZ-orbit-materials");t.setDaemon(true);return t;});
    private static final Set<String> READY=new HashSet<>();
    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT=new java.util.concurrent.atomic.AtomicInteger();
    /** Server maps by planet ("system/index/anchorX/anchorZ"), full size; the textures drawn are scaled from these. */
    private static final Map<String,NativeImage> MAPS=new LinkedHashMap<>(16,.75f,true);   // the last dozen planets drawn
    private static final Map<String,Long> ASKED=new HashMap<>();
    private static long nextUpload;private static int uploads,peakGenerating;
    /** Mirrors LevelRenderer's own tick counter (it drives vanilla's cloud drift; it is not the world's game time). */
    private static long cloudTicks;private static int[] cloudTable;private static int cloudSize;
    static void beginFrame(){uploads=0;}
    static void tick(){var mc=Minecraft.getInstance();if(mc.level!=null&&!mc.isPaused())cloudTicks++;}
    static ResourceLocation get(CompoundTag b,int resolution){
        if(!b.getBoolean("cloudLayer")&&mapped(b)){var map=MAPS.get(mapKey(b));
            if(map!=null){String key="map:"+mapKey(b)+":"+resolution;var found=CACHE.get(key);if(found==null){found=register(key,scaled(map,resolution,resolution/2));CACHE.put(key,found);READY.add(key);trim();}return found;}
            ask(b);
        }
        boolean drifting=b.getBoolean("cloudLayer")&&b.getBoolean("landable");String prefix="clouds:"+mapKey(b)+":";
        String identity=drifting?prefix+cloudStep(b,resolution):b.getString("type")+":"+b.getInt("color")+":"+b.getDouble("phase")+":"+b.getDouble("orbit")+":"+b.getBoolean("cloudLayer");String key=identity+":"+resolution;
        var manager=Minecraft.getInstance().getTextureManager();var found=CACHE.get(key);var future=PENDING.get(key);
        if(found!=null){
            if(future!=null&&future.isDone()&&uploads<1&&net.minecraft.Util.getMillis()>=nextUpload){
                try{var image=future.join();manager.release(found);found=register(key,image);CACHE.put(key,found);READY.add(key);uploads++;nextUpload=net.minecraft.Util.getMillis()+16;
                    if(drifting)for(var it=CACHE.entrySet().iterator();it.hasNext();){var e=it.next();if(!e.getKey().equals(key)&&e.getKey().startsWith(prefix)&&e.getKey().endsWith(":"+resolution)){manager.release(e.getValue());READY.remove(e.getKey());it.remove();}}}
                catch(java.util.concurrent.CompletionException ex){com.dbzenith.DBZenith.LOGGER.error("Orbital material failed",ex);}PENDING.remove(key);
            }
            if(!READY.contains(key)&&!PENDING.containsKey(key))request(key,b,resolution,identity);
            if(drifting&&!READY.contains(key))for(var e:CACHE.entrySet())if(e.getKey().startsWith(prefix)&&e.getKey().endsWith(":"+resolution)&&READY.contains(e.getKey()))return e.getValue();   // the last step until the next is drawn
            return found;
        }
        var placeholder=new NativeImage(1,1,false);int rgb=b.getInt("color");placeholder.setPixelRGBA(0,0,b.getBoolean("cloudLayer")?0:0xFF000000|((rgb&255)<<16)|(rgb&0xFF00)|((rgb>>>16)&255));
        found=register(key,placeholder);CACHE.put(key,found);
        request(key,b,resolution,identity);
        trim();
        return found;
    }
    private static void trim(){var manager=Minecraft.getInstance().getTextureManager();while(CACHE.size()>48){var it=CACHE.entrySet().iterator();var first=it.next();manager.release(first.getValue());READY.remove(first.getKey());var pending=PENDING.remove(first.getKey());if(pending!=null)pending.thenAccept(NativeImage::close);it.remove();}}
    /** Planets with a solid ground have a server map (gas giants and asteroid fields keep their materials). */
    private static boolean mapped(CompoundTag b){String type=b.getString("type");return b.getBoolean("landable")&&!type.equals("Gas giant")&&!type.equals("Asteroids")&&b.contains("index");}
    private static String mapKey(CompoundTag b){return SpaceClient.system().getString("id")+"/"+b.getInt("index")+"/"+b.getInt("anchorX")+"/"+b.getInt("anchorZ");}
    private static void ask(CompoundTag b){
        String key=mapKey(b);long now=net.minecraft.Util.getMillis();Long last=ASKED.get(key);if(last!=null&&now-last<30000)return;ASKED.put(key,now);
        com.dbzenith.network.ModNetwork.sendToServer(new PlanetMapPacket.Request(SpaceClient.system().getString("id"),b.getInt("index"),0,0,0));
    }
    /** A regional map's texture and the square of surface it covers. */
    public record Region(ResourceLocation texture,int x0,int z0,int span){
        public boolean covers(double x,double z,double margin){return x>=x0+margin&&z>=z0+margin&&x<x0+span-margin&&z<z0+span-margin;}
    }
    private static final Map<String,Region> REGIONS=new LinkedHashMap<>(8,.75f,true);
    /**
     * The regional map around a surface point of a planet (the approach terrain and the ground far below, phase 4):
     * the loaded one that best contains it, asking for one centred on it when the point nears its edge.
     */
    static Region region(CompoundTag b,double x,double z){
        if(!mapped(b))return null;String planet=SpaceClient.system().getString("id")+"/"+b.getInt("index")+"/";Region best=null;double bestEdge=-1;
        for(var e:REGIONS.entrySet()){if(!e.getKey().startsWith(planet))continue;var r=e.getValue();double edge=Math.min(Math.min(x-r.x0,r.x0+r.span-x),Math.min(z-r.z0,r.z0+r.span-z));if(edge>bestEdge){best=r;bestEdge=edge;}}
        if(best==null||bestEdge<PlanetMaps.SPAN/4d){int x0=(int)Math.round((x-PlanetMaps.SPAN/2d)/PlanetMaps.REGION_STEP)*PlanetMaps.REGION_STEP,z0=(int)Math.round((z-PlanetMaps.SPAN/2d)/PlanetMaps.REGION_STEP)*PlanetMaps.REGION_STEP;
            String key="region:"+planet+x0+"/"+z0;long now=net.minecraft.Util.getMillis();Long last=ASKED.get(key);
            if(!REGIONS.containsKey(planet+x0+"/"+z0)&&(last==null||now-last>30000)){ASKED.put(key,now);com.dbzenith.network.ModNetwork.sendToServer(new PlanetMapPacket.Request(SpaceClient.system().getString("id"),b.getInt("index"),x0,z0,PlanetMaps.SPAN));}}
        return best!=null&&bestEdge>0?best:null;
    }
    /** A planet's map arrived: keep it, and drop the textures drawn from the old one. */
    public static void receive(PlanetMapPacket m){
        int[] rgb;try{rgb=PlanetMaps.unpack(m.pixels(),m.width()*m.height());}catch(java.util.zip.DataFormatException ex){com.dbzenith.DBZenith.LOGGER.warn("Bad planet map for {}/{}",m.system(),m.index());return;}
        var image=new NativeImage(m.width(),m.height(),false);
        for(int y=0;y<m.height();y++)for(int x=0;x<m.width();x++){int c=rgb[y*m.width()+x];image.setPixelRGBA(x,y,0xFF000000|((c&255)<<16)|(c&0xFF00)|((c>>>16)&255));}
        String key=m.system()+"/"+m.index()+"/"+m.ax()+"/"+m.az();var manager=Minecraft.getInstance().getTextureManager();
        if(m.span()>0){var old=REGIONS.remove(key);if(old!=null)manager.release(old.texture());
            REGIONS.put(key,new Region(register("region:"+key,image),m.ax(),m.az(),m.span()));
            while(REGIONS.size()>6){var it=REGIONS.entrySet().iterator();manager.release(it.next().getValue().texture());it.remove();}return;}
        var old=MAPS.put(key,image);if(old!=null)old.close();
        while(MAPS.size()>12){var it=MAPS.entrySet().iterator();var eldest=it.next();eldest.getValue().close();it.remove();for(var c=CACHE.entrySet().iterator();c.hasNext();){var e=c.next();if(e.getKey().startsWith("map:"+eldest.getKey()+":")){manager.release(e.getValue());READY.remove(e.getKey());c.remove();}}}
        for(var it=CACHE.entrySet().iterator();it.hasNext();){var e=it.next();if(e.getKey().startsWith("map:"+key+":")){manager.release(e.getValue());READY.remove(e.getKey());it.remove();}}
    }
    /** A box-filtered copy at the drawn size (a 512-texel map on a small disc would shimmer). */
    private static NativeImage scaled(NativeImage map,int width,int height){
        if(width>=map.getWidth()){var copy=new NativeImage(map.getWidth(),map.getHeight(),false);copy.copyFrom(map);return copy;}
        var out=new NativeImage(width,height,false);int fx=map.getWidth()/width,fy=Math.max(1,map.getHeight()/height);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int r=0,g=0,bl=0;
            for(int j=0;j<fy;j++)for(int i=0;i<fx;i++){int c=map.getPixelRGBA(x*fx+i,Math.min(map.getHeight()-1,y*fy+j));r+=c&255;g+=(c>>8)&255;bl+=(c>>16)&255;}
            int k=fx*fy;out.setPixelRGBA(x,y,0xFF000000|((bl/k)<<16)|((g/k)<<8)|(r/k));}
        return out;
    }
    private static void request(String key,CompoundTag body,int resolution,String identity){
        if(IN_FLIGHT.get()>=4)return;var descriptor=body.copy();boolean clouds=body.getBoolean("cloudLayer")&&body.getBoolean("landable");
        SpaceFrames.Planet frame=clouds?ClientFrames.unturned(body):null;long ticks=cloudTicks;int[] table=clouds?cloudTable():null;int size=cloudSize;
        if(clouds&&table==null)return;
        peakGenerating=Math.max(peakGenerating,IN_FLIGHT.incrementAndGet());
        PENDING.put(key,java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return clouds?clouds(frame,table,size,ticks,resolution,resolution/2):generate(descriptor,resolution,resolution/2,identity);}finally{IN_FLIGHT.decrementAndGet();}},MATERIALS));
    }
    /** Which drift step the clouds are at: a new layer is drawn each time they have moved half a texel (at least six blocks). */
    private static long cloudStep(CompoundTag b,int resolution){double perTexel=2*ClientFrames.unturned(b).size()/resolution;return (long)Math.floor(cloudTicks*.03/Math.max(6,perTexel*.5));}
    /** Vanilla's cloud texture as a summed-area table of its cloud cells (loaded once). */
    private static int[] cloudTable(){
        if(cloudTable!=null)return cloudTable;
        try(var in=Minecraft.getInstance().getResourceManager().open(new ResourceLocation("textures/environment/clouds.png"));var image=NativeImage.read(in)){
            int n=image.getWidth();boolean[] cloud=new boolean[n*n];
            for(int z=0;z<n;z++)for(int x=0;x<n;x++)cloud[z*n+x]=(image.getPixelRGBA(x,z)>>>24)>16;
            cloudSize=n;return cloudTable=PlanetMaps.cloudTable(cloud,n);
        }catch(Exception ex){com.dbzenith.DBZenith.LOGGER.warn("No cloud texture for the orbital cloud layer",ex);return null;}
    }
    /** The cloud layer: at each texel's surface point, vanilla's cloud cell there (x drifts 0.03 blocks a tick, 12-block cells). */
    private static NativeImage clouds(SpaceFrames.Planet frame,int[] table,int n,long ticks,int width,int height){
        var image=new NativeImage(width,height,false);double drift=ticks*.03,mean=(double)table[n*(n+1)+n]/(n*n),contrast=Math.max(.35,Math.min(1,64/(2*frame.size()/width)));   // seen from far, vanilla's 3,072-block cloud tile would repeat as stripes: its contrast fades with the scale
        PlanetMaps.Ground cover=(x,z,footprint)->{double half=Math.max(6,footprint)/24,u=(x+drift)/12,v=z/12+.33;return (int)(PlanetMaps.cover(table,n,u-half,v-half,u+half,v+half)*255);};   // a texel's area, sampled like the ground's
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){double c=(PlanetMaps.texel(frame,cover,x,y,width,height)&255)/255d;c=mean+(c-mean)*contrast;
            int alpha=(int)(Math.pow(Math.max(0,c),.85)*210);image.setPixelRGBA(x,y,(alpha<<24)|0xF2EEE2);}
        return image;
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
    static void clear(){for(var id:CACHE.values())Minecraft.getInstance().getTextureManager().release(id);CACHE.clear();READY.clear();for(var future:PENDING.values())future.thenAccept(NativeImage::close);PENDING.clear();
        for(var map:MAPS.values())map.close();MAPS.clear();ASKED.clear();for(var r:REGIONS.values())Minecraft.getInstance().getTextureManager().release(r.texture());REGIONS.clear();}
    private static ResourceLocation register(String key,NativeImage image){var texture=new DynamicTexture(image);texture.setFilter(true,false);var id=new ResourceLocation("dbzenith","orbital/"+UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));Minecraft.getInstance().getTextureManager().register(id,texture);return id;}
    static String metrics(){return CACHE.size()+" materials / "+PENDING.size()+" pending / "+IN_FLIGHT.get()+" generating (peak "+peakGenerating+") / "+MAPS.size()+" maps / "+REGIONS.size()+" regions";}
    private static double hash(int x,int y,int z,long seed){return (GalaxyCatalog.mix(seed^x*0x9e3779b97f4a7c15L^y*0x632be59bd9b4e019L^z*0x94d049bb133111ebL)>>>11)*0x1.0p-53*2-1;}
    private static double smooth(double v){return v*v*v*(v*(v*6-15)+10);}
    private static double noise(double x,double y,double z,long seed){int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);double fx=smooth(x-ix),fy=smooth(y-iy),fz=smooth(z-iz),v=0;
        for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++)v+=hash(ix+a,iy+b,iz+c,seed)*(a==0?1-fx:fx)*(b==0?1-fy:fy)*(c==0?1-fz:fz);return v;}
    private static double fbm(double x,double y,double z,long seed){double value=0,weight=.57;for(int i=0;i<4;i++){value+=noise(x,y,z,seed+i*13)*weight;weight*=.48;x*=2.07;y*=2.07;z*=2.07;}return value;}
    private static int mix(int a,int b,double t){t=Math.max(0,Math.min(1,t));return ((int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t)<<16)|((int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t)<<8)|(int)((a&255)*(1-t)+(b&255)*t);}
}
