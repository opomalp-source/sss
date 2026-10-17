package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;

/** Stateless weather calendar. Sampling a world never creates chunks or persistent records. */
public final class PlanetConditions {
    public enum Hazard { CLEAR, DUST, ACID, METEORS, ION, COLD, HEAT, QUAKE }
    public record Weather(Hazard hazard,double intensity,int remaining) {}
    public static Weather sample(GalaxyCatalog.Body b,long time){
        long offset=GalaxyCatalog.mix(b.seed())&0x7fffffffL;
        int t=(int)Math.floorMod(time+offset,9600L);
        Hazard hazard=switch(b.kind()){
            case DESERT->Hazard.DUST;case TOXIC_SWAMP->Hazard.ACID;
            case VOLCANIC->Hazard.QUAKE;case ICE->Hazard.COLD;case GAS_GIANT,CRYSTAL->Hazard.ION;
            default->Hazard.METEORS;
        };
        double strength=t<1800?Math.min(1,Math.min(t/180d,(1800-t)/240d)):0;
        if(strength==0&&b.temperature()>=50)return new Weather(Hazard.HEAT,.35,0);
        if(strength==0&&b.temperature()<=-50)return new Weather(Hazard.COLD,.35,0);
        return new Weather(strength>0?hazard:Hazard.CLEAR,strength,Math.max(0,1800-t));
    }
    public static String label(Hazard h){return switch(h){case CLEAR->"Clear skies";case DUST->"Dust storm";case ACID->"Acid rain";
        case METEORS->"Meteor shower";case ION->"Ion storm";case COLD->"Extreme cold";case HEAT->"Extreme heat";case QUAKE->"Seismic activity";};}
}
