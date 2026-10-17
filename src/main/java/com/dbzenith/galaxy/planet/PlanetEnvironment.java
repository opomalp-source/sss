package com.dbzenith.galaxy.planet;

import com.dbzenith.DBZenith;
import com.dbzenith.data.*;
import com.dbzenith.galaxy.*;
import com.dbzenith.item.SpaceSuitItem;
import com.dbzenith.network.*;
import com.dbzenith.race.*;
import com.dbzenith.world.SpacePodEntity;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Physics uses Forge's synced gravity attribute for living entities; input/aim remain vanilla-authoritative. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class PlanetEnvironment {
    private static final UUID GRAVITY=UUID.fromString("aa300aba-14ae-4204-a384-3c555f6e9041");
    private static final String AIR="dbzenithPlanetAir",GRACE="dbzenithPlanetGrace";
    public static double gravity(Level l){
        if(com.dbzenith.galaxy.space.SpaceRuntime.isSpace(l))return 0;
        if(!PlanetConfig.GRAVITY.get())return 1;
        if(l.isClientSide)return com.dbzenith.client.ClientPlanet.gravity(l);
        var b=PlanetRuntime.body((ServerLevel)l);return (b==null?1:b.gravity())*PlanetDestruction.gravity((ServerLevel)l);
    }
    public static double flightFactor(Level l){return Math.max(.35,Math.min(5,Math.sqrt(gravity(l))));}
    public static void applyGravity(LivingEntity entity,double g){
        var a=entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());if(a==null)return;
        var old=a.getModifier(GRAVITY);double amount=g-1;
        if(old!=null&&Math.abs(old.getAmount()-amount)<1e-6)return;
        if(old!=null)a.removeModifier(GRAVITY);
        if(Math.abs(amount)>1e-6)a.addTransientModifier(new AttributeModifier(GRAVITY,"dbzenith planet gravity",amount,AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static String protection(ServerPlayer p,PlayerData d){
        if(p.isCreative()||p.isSpectator())return "Creative";
        if(p.getVehicle() instanceof SpacePodEntity)return "Sealed pod";
        if(SpaceSuitItem.sealed(p))return "Sealed space suit";
        if(Races.of(d.getRace()).breathless()||RacialSkills.has(d,"vacuum_breathing"))return "Vacuum adaptation";
        if(d.getRacialActive().contains("ki_barrier")||d.getRacialActive().contains("sacred_barrier"))return "Ki barrier";
        return "";
    }
    public static int drain(GalaxyCatalog.Atmosphere air,long tick){return switch(air){case NONE->1;case TOXIC,SCORCHING->2;case THIN->tick%2==0?1:0;default->0;};}
    public static void enter(ServerPlayer p){
        p.getPersistentData().putInt(AIR,300);p.getPersistentData().putLong(GRACE,p.server.overworld().getGameTime()+PlanetConfig.GRACE_TICKS.get());
        // Vanilla sendLevelInfo sends Earth's border even for other dimensions.
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(p.serverLevel().getWorldBorder()));
        sync(p);
    }
    public static void sync(ServerPlayer p){
        var b=PlanetRuntime.body(p.serverLevel());String name="";int air=300,radius=0,atmos=GalaxyCatalog.Atmosphere.BREATHABLE.ordinal();double g=1;String protectedBy="";
        if(b!=null){var data=GalaxyData.get(p.server);var generator=(PlanetGenerator)p.serverLevel().getChunkSource().getGenerator();name=data.name(generator.ref,b.name());radius=b.radius();atmos=b.atmosphere().ordinal();g=gravity(p.level());
            air=p.getPersistentData().contains(AIR)?p.getPersistentData().getInt(AIR):300;protectedBy=protection(p,ModCapabilities.getOrThrow(p));
            if(p.server.overworld().getGameTime()<p.getPersistentData().getLong(GRACE))protectedBy="Landing grace";
        }
        if(com.dbzenith.galaxy.space.SpaceRuntime.isSpace(p.level())){name=com.dbzenith.galaxy.space.SpaceRuntime.system(p.serverLevel()).name()+" / Space";g=0;atmos=GalaxyCatalog.Atmosphere.NONE.ordinal();air=p.getPersistentData().getInt(AIR);protectedBy=protection(p,ModCapabilities.getOrThrow(p));}
        ModNetwork.sendTo(p,new PlanetEnvironmentPacket(p.level().dimension().location().toString(),name,g,atmos,radius,air,protectedBy));
    }
    @SubscribeEvent public static void living(LivingEvent.LivingTickEvent e){if(e.getEntity().level() instanceof ServerLevel l)applyGravity(e.getEntity(),gravity(l));}
    @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity().level() instanceof ServerLevel l)e.setDamageMultiplier((float)(e.getDamageMultiplier()*gravity(l)));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        var b=PlanetRuntime.body(p.serverLevel());boolean orbit=com.dbzenith.galaxy.space.SpaceRuntime.isSpace(p.level());if(b==null&&!orbit)return;
        var atmosphere=orbit?GalaxyCatalog.Atmosphere.NONE:b.atmosphere();
        var tag=p.getPersistentData();long now=p.server.overworld().getGameTime();
        if(!tag.contains(AIR))enter(p);
        boolean shield=!PlanetConfig.ATMOSPHERE.get()||!protection(p,ModCapabilities.getOrThrow(p)).isEmpty()||now<tag.getLong(GRACE);
        int air=tag.getInt(AIR);
        if(shield||atmosphere==GalaxyCatalog.Atmosphere.BREATHABLE)air=Math.min(300,air+4);
        else {air=Math.max(0,air-drain(atmosphere,now));p.setAirSupply(air);if(air==0&&now%20==0)p.hurt(p.damageSources().drown(),2f);}
        tag.putInt(AIR,air);if(now%20==0)sync(p);
    }
    @SubscribeEvent public static void arcs(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;double g=gravity(e.level);if(Math.abs(g-1)<1e-6)return;
        Iterable<Entity> all=e.level instanceof ServerLevel s?s.getAllEntities():((net.minecraft.client.multiplayer.ClientLevel)e.level).entitiesForRendering();
        for(Entity entity:all){
            applyArc(entity,g);
        }
    }
    public static void applyArc(Entity entity,double g){
        if(entity.isNoGravity()||entity.onGround()||entity.isInWaterOrBubble())return;
        if(entity instanceof AbstractArrow && !entity.level().getBlockState(entity.blockPosition()).getCollisionShape(entity.level(),entity.blockPosition()).isEmpty())return;
        double base=entity instanceof ItemEntity?.04:entity instanceof AbstractArrow?.05:entity instanceof ThrowableProjectile?.03:0;
        if(base!=0)entity.setDeltaMovement(entity.getDeltaMovement().add(0,-base*(g-1),0));
    }
}
