package com.dbzenith.galaxy.planet;
import net.minecraftforge.common.ForgeConfigSpec;
public final class PlanetConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_LOADED,IDLE_TICKS,GRACE_TICKS;
    public static final ForgeConfigSpec.BooleanValue GRAVITY,ATMOSPHERE;
    static {var b=new ForgeConfigSpec.Builder();b.push("planets");
        MAX_LOADED=b.comment("Maximum simultaneously loaded procedural planets; occupied or forced worlds are never evicted").defineInRange("maxLoaded",4,1,16);
        IDLE_TICKS=b.comment("Empty worlds save and unload after this many ticks; region files remain").defineInRange("idleTicks",1200,100,72000);
        GRACE_TICKS=b.comment("Air protection after landing/reconnecting").defineInRange("landingGraceTicks",300,0,1200);
        GRAVITY=b.define("physicalGravity",true);ATMOSPHERE=b.define("atmosphereHazards",true);b.pop();SPEC=b.build();}
}
