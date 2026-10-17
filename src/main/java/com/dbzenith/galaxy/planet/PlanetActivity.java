package com.dbzenith.galaxy.planet;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.item.ScouterItem;
import com.dbzenith.network.*;
import com.dbzenith.npc.ModNpcs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Bounded player-local environmental work. Never loads terrain for hazards, scanning or inhabitants. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class PlanetActivity {
    private record Preview(PlanetConditions.Hazard hazard,long until){}
    private static final Map<ServerLevel,Preview> PREVIEWS=new WeakHashMap<>();
    private record Meteor(BlockPos at,long tick){}
    private static final Map<ServerLevel,List<Meteor>> METEORS=new WeakHashMap<>();
    public static void preview(ServerLevel l,PlanetConditions.Hazard hazard){PREVIEWS.put(l,new Preview(hazard,l.getGameTime()+1200));}
    public static final class Visits extends SavedData {
        private final Set<String> sites=new HashSet<>();
        public boolean contains(String key){return sites.contains(key);}
        public boolean claim(String key){boolean added=sites.add(key);if(added)setDirty();return added;}
        @Override public CompoundTag save(CompoundTag t){ListTag list=new ListTag();sites.stream().sorted().forEach(s->list.add(StringTag.valueOf(s)));t.put("sites",list);return t;}
        public static Visits load(CompoundTag t){var v=new Visits();for(Tag s:t.getList("sites",Tag.TAG_STRING))v.sites.add(s.getAsString());return v;}
    }
    public static boolean sheltered(ServerPlayer p){return p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING,p.getBlockX(),p.getBlockZ())>p.getY()+1;}
    public static boolean protectedFromWeather(ServerPlayer p){return p.server.overworld().getGameTime()<p.getPersistentData().getLong("dbzenithPlanetGrace")||!PlanetEnvironment.protection(p,ModCapabilities.getOrThrow(p)).isEmpty()||sheltered(p);}
    public static PlanetConditions.Weather weather(ServerLevel l,GalaxyCatalog.Body b){
        var preview=PREVIEWS.get(l);
        if(preview!=null&&preview.until>l.getGameTime())return new PlanetConditions.Weather(preview.hazard,preview.hazard==PlanetConditions.Hazard.CLEAR?0:1,(int)(preview.until-l.getGameTime()));
        return PlanetConfig.WEATHER.get()?PlanetConditions.sample(b,l.getGameTime()):new PlanetConditions.Weather(PlanetConditions.Hazard.CLEAR,0,0);
    }
    public static boolean landing(ServerPlayer p){
        var ref=SpaceRuntime.surfaceRef(p.serverLevel());if(ref==null||!p.onGround()||p.isSpectator()||!p.isAlive())return false;
        if(!p.serverLevel().noCollision(p,p.getBoundingBox())||p.serverLevel().noCollision(p,p.getBoundingBox().move(0,-.08,0)))return false;
        var d=ModCapabilities.getOrThrow(p);String flag="planet_landing:"+ref.key();if(d.hasFlag(flag))return false;
        var data=GalaxyData.get(p.server);var b=GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref);
        data.discover(ref,p.getUUID(),p.getGameProfile().getName(),p.server.overworld().getGameTime(),b.name());d.setFlag(flag,true);
        var found=data.discovery(ref);CompoundTag t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());t.putBoolean("landing",true);
        t.putString("name",data.name(ref,b.name()));t.putString("type",GalaxyCatalog.label(b.kind()));t.putDouble("gravity",b.gravity());t.putString("discoverer",found.discoverer());
        ModNetwork.sendTo(p,new PlanetActivityPacket(t));SpaceRuntime.scene(p,false,p.getDeltaMovement());
        if(found.owner().equals(p.getUUID())&&!(ref.planet()==0&&Set.of(GalaxyCatalog.EARTH,GalaxyCatalog.NAMEK,GalaxyCatalog.NORTH,GalaxyCatalog.BEERUS).contains(ref.system())))
            p.sendSystemMessage(Component.literal("You discovered this planet. [Name it]").withStyle(s->s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/galaxy namehere "))));
        return true;
    }
    public static void applyHazard(ServerPlayer p,PlanetConditions.Weather w){
        if(!PlanetConfig.HAZARDS.get()||w.intensity()<.2||protectedFromWeather(p))return;
        switch(w.hazard()){
            case ACID,COLD,HEAT -> p.hurt(p.damageSources().magic(),(float)(w.intensity()*1.5));
            case DUST -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,65,0,false,false));
            case ION -> {var d=ModCapabilities.getOrThrow(p);d.setKi(Math.max(0,d.getKi()-w.intensity()*2));}
            default -> {}
        }
    }
    public static CompoundTag status(ServerPlayer p){
        CompoundTag t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());
        var b=PlanetRuntime.body(p.serverLevel());
        if(b!=null){var w=weather(p.serverLevel(),b);t.putString("weather",w.hazard().name());t.putDouble("intensity",w.intensity());t.putInt("remaining",w.remaining());t.putBoolean("sheltered",sheltered(p));t.putBoolean("protected",!PlanetConfig.HAZARDS.get()||protectedFromWeather(p));}
        var wp=GalaxyData.get(p.server).waypoint(p.getUUID());if(wp!=null){t.putString("waypoint",wp.key());t.putBoolean("localWaypoint",wp.system().equals(GalaxyData.current(p)));}
        if(SpaceRuntime.isSpace(p.level())&&ScouterItem.wears(p)){
            var sys=SpaceRuntime.system(p.serverLevel());GalaxyCatalog.Body nearest=null;double best=Double.MAX_VALUE;
            for(var body:sys.planets()){double d=p.position().distanceTo(SpaceMath.center(body))-SpaceMath.radius(body.radius());if(d<best){best=d;nearest=body;}}
            if(nearest!=null){var ref=new GalaxyCatalog.Ref(sys.id(),nearest.index());var target=p.server.getLevel(Optional.ofNullable(PlanetRuntime.legacy(ref)).orElse(PlanetRuntime.key(ref)));
                t.putInt("scanBody",nearest.index());t.putBoolean("scanLoaded",target!=null);long max=0;int count=0;
                if(target!=null){int seen=0;for(var e:target.getAllEntities()){
                    if(++seen>2048)break;if(e instanceof LivingEntity living&&living.isAlive()){long power=ScouterItem.powerOf(living);if(power>0){max=Math.max(max,power);count++;}}
                }}t.putLong("scanPower",max);t.putInt("scanCount",count);
            }
        }return t;
    }
    private static void populate(ServerPlayer p,PlanetGenerator g){
        if(!PlanetConfig.INHABITANTS.get())return;var l=p.serverLevel();var s=PlanetLandmarks.near(g,p.getBlockX(),p.getBlockZ());
        if(s==null||p.distanceToSqr(s.x()+.5,s.y(),s.z()+.5)>96*96||!l.hasChunkAt(new BlockPos(s.x(),s.y(),s.z())))return;
        if(!l.getBlockState(new BlockPos(s.x(),s.y(),s.z())).is(Blocks.LODESTONE))return;
        var data=l.getServer().overworld().getDataStorage().computeIfAbsent(Visits::load,Visits::new,"dbzenith_planet_sites");
        String key=g.ref.key()+"/"+s.key();if(data.contains(key))return;
        var body=PlanetRuntime.body(l);EntityType<? extends Mob> type=s.kind()==3?ModNpcs.MASTER.get():switch(body.population()){
            case "Wildlife"->ModNpcs.SPROUTLING.get();case "Hostile scouts"->ModNpcs.KI_SOLDIER.get();case "Remote settlers"->ModNpcs.NAMEKIAN_WARRIOR.get();default->null;};
        if(type==null){data.claim(key);return;}if(l.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL&&type.getCategory()==MobCategory.MONSTER)return;
        int sx=s.x()+(s.kind()==3?2:9),sz=s.z();if(!l.hasChunk(sx>>4,sz>>4))return;
        var mob=type.create(l);if(mob==null)return;mob.moveTo(sx+.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz),sz+.5,0,0);
        if(!l.noCollision(mob))return;mob.setPersistenceRequired();if(l.addFreshEntity(mob))data.claim(key);
    }
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        if(p.tickCount%20==0)landing(p);
        if(p.tickCount%100==0&&(SpaceRuntime.surfaceRef(p.serverLevel())!=null||SpaceRuntime.isSpace(p.level())))ModNetwork.sendTo(p,new PlanetActivityPacket(status(p)));
        var b=PlanetRuntime.body(p.serverLevel());if(b==null)return;
        if(p.tickCount%40==0)applyHazard(p,weather(p.serverLevel(),b));
        if(p.tickCount%200==0)populate(p,(PlanetGenerator)p.serverLevel().getChunkSource().getGenerator());
        var w=weather(p.serverLevel(),b);
        if(PlanetConfig.HAZARDS.get()&&p.tickCount%100==0&&w.hazard()==PlanetConditions.Hazard.METEORS&&w.intensity()>.3){
            int x=p.getBlockX()+p.getRandom().nextInt(49)-24,z=p.getBlockZ()+p.getRandom().nextInt(49)-24;var l=p.serverLevel();
            if(!l.hasChunk(x>>4,z>>4))return;var at=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z));
            var pending=METEORS.computeIfAbsent(l,k->new ArrayList<>());if(pending.size()<8){pending.add(new Meteor(at,l.getGameTime()+40));l.sendParticles(ParticleTypes.FLAME,at.getX(),at.getY()+20,at.getZ(),16,.15,4,.15,.02);}
        }
    }
    @SubscribeEvent public static void meteors(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel l))return;var pending=METEORS.get(l);if(pending==null)return;
        for(var it=pending.iterator();it.hasNext();){var meteor=it.next();if(l.getGameTime()<meteor.tick)continue;it.remove();var at=meteor.at;
            if(!PlanetConfig.HAZARDS.get()||!l.hasChunkAt(at))continue;
            l.sendParticles(ParticleTypes.LAVA,at.getX(),at.getY()+1,at.getZ(),12,1,.5,1,.05);l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,at.getX(),at.getY()+1,at.getZ(),12,.8,1,.8,.02);
            for(var victim:l.getEntitiesOfClass(ServerPlayer.class,new AABB(at).inflate(3)))if(!protectedFromWeather(victim))victim.hurt(l.damageSources().magic(),2);
        }
    }
}
