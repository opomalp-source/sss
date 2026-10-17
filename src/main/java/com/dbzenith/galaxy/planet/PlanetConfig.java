package com.dbzenith.galaxy.planet;
import net.minecraftforge.common.ForgeConfigSpec;
public final class PlanetConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_LOADED,IDLE_TICKS,GRACE_TICKS;
    public static final ForgeConfigSpec.BooleanValue GRAVITY,ATMOSPHERE;
    public static final ForgeConfigSpec.IntValue MAX_ORBITS;
    public static final ForgeConfigSpec.BooleanValue SPACE;
    public static final ForgeConfigSpec.BooleanValue WEATHER,HAZARDS,INHABITANTS;
    public static final ForgeConfigSpec.BooleanValue HYPERSPEED;
    public static final ForgeConfigSpec.BooleanValue DOOM_ENABLED,DOOM_BASES,DOOM_SPAWNS,DOOM_EJECT;
    public static final ForgeConfigSpec.IntValue DOOM_SECONDS,DOOM_PERMISSION,DOOM_MAX;
    public static final ForgeConfigSpec.LongValue DOOM_POWER;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> DOOM_PROTECTED;
    static {var b=new ForgeConfigSpec.Builder();b.push("planets");
        MAX_LOADED=b.comment("Maximum simultaneously loaded procedural planets; occupied or forced worlds are never evicted").defineInRange("maxLoaded",4,1,16);
        IDLE_TICKS=b.comment("Empty worlds save and unload after this many ticks; region files remain").defineInRange("idleTicks",1200,100,72000);
        GRACE_TICKS=b.comment("Air protection after landing/reconnecting").defineInRange("landingGraceTicks",300,0,1200);
        MAX_ORBITS=b.comment("Maximum active orbital system instances, separate from surface planets").defineInRange("maxLoadedOrbits",4,1,16);
        SPACE=b.define("orbitalTravel",true);
        HYPERSPEED=b.define("hyperspeedTravel",true);
        WEATHER=b.define("planetWeather",true);HAZARDS=b.define("weatherDamage",true);INHABITANTS=b.define("landmarkInhabitants",true);
        GRAVITY=b.define("physicalGravity",true);ATMOSPHERE=b.define("atmosphereHazards",true);
        b.push("destruction");
        DOOM_ENABLED=b.define("enabled",true);DOOM_BASES=b.define("protectPlayerBases",true);DOOM_SPAWNS=b.define("protectSpawnPlanets",true);
        DOOM_EJECT=b.comment("Throw survivors into orbit; false uses lethal damage, with an evacuation fallback for immortals").define("ejectSurvivors",true);
        DOOM_SECONDS=b.defineInRange("countdownSeconds",60,30,90);DOOM_MAX=b.defineInRange("maxConcurrent",4,1,8);
        DOOM_POWER=b.defineInRange("powerThreshold",10000000L,1L,Long.MAX_VALUE);
        DOOM_PERMISSION=b.defineInRange("attackPermissionLevel",0,0,4);
        DOOM_PROTECTED=b.comment("Protected body addresses. Earth always remains protected as the server's safe return world.")
            .defineList("protectedPlanets",java.util.List.of("0:0:0:0/0","-9:4:2:0/0","40:8:-17:3/0"),v->v instanceof String s&&s.length()<48);
        b.pop();b.pop();SPEC=b.build();}
}
