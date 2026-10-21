package com.dbzenith.galaxy;
import java.util.*;
import static com.dbzenith.galaxy.GalaxyCatalog.*;

/**
 * The series' own worlds that the procedural catalogue doesn't make: additive reserved addresses (slot 8), so no
 * previously valid v1 address changes its catalogue or terrain. Lore from the series (Dragon Ball Z and Super):
 * Planet Vegeta as it was before Frieza destroyed it, Planet Frieza No. 79, Yardrat and Cereal.
 */
public final class IconicSystems {
    public static final Id VEGETA=new Id(27,-6,14,8),FRIEZA=new Id(-34,9,-21,8),
            YARDRAT=new Id(7,-2,9,8),          // about ten days from Earth and a week from (New) Namek
            CEREAL=new Id(-46,14,31,8);        // far out; the Saiyans and the Frieza Force still reached it
    /** Plain coordinates: creating the reserved ids themselves asks this, before any static list exists. */
    public static boolean reserved(int x,int y,int z,int slot){return slot==8&&(x==27&&y==-6&&z==14||x==-34&&y==9&&z==-21||x==7&&y==-2&&z==9||x==-46&&y==14&&z==31);}
    public static boolean isNew(Id id){return reserved(id.x(),id.y(),id.z(),id.slot());}
    public static List<Id> all(){return List.of(EARTH,NAMEK,VEGETA,NORTH,FRIEZA,BEERUS,YARDRAT,CEREAL);}

    /**
     * A world's lore, as the atlas and a landing tell it (by planet; empty for the rest). Facts from the series:
     * Planet Vegeta (formerly Planet Plant, the Tuffles' world) with at least ten times Earth's gravity; Planet Frieza
     * No. 79, a Frieza Force base with Medical Machines where Vegeta recovered after Earth; Yardrat under a yellow sky,
     * where Goku learned Instant Transmission from Elder Pybara; Cereal, rebuilt by the Heeters and sold to the
     * Sugarians after the Saiyans and the Frieza Force laid it waste; Namek under three suns and a green sky.
     */
    public static String lore(Ref ref){
        if(ref.planet()!=0)return "";
        if(ref.system().equals(EARTH))return "Home of Goku, Gohan and their friends, defended against every threat that has come.";
        if(ref.system().equals(NAMEK))return "Green skies, dark green seas and three suns that never set. The Namekians' Dragon Balls summon Porunga.";
        if(ref.system().equals(VEGETA))return "Once Planet Plant, world of the Tuffles, taken by the Saiyans. Ten times Earth's gravity. Here as it was before Frieza destroyed it.";
        if(ref.system().equals(FRIEZA))return "A Frieza Force base: landing pads, a control room and Medical Machines. Vegeta recovered here after Earth.";
        if(ref.system().equals(YARDRAT))return "Under a yellow sky the Yardratians master Spirit Control. Elder Pybara taught Goku Instant Transmission here.";
        if(ref.system().equals(CEREAL))return "Laid waste by the Saiyans and the Frieza Force, rebuilt by the Heeters and sold to the Sugarians. Granolah's home.";
        if(ref.system().equals(NORTH))return "King Kai's tiny world at the end of Snake Way, with ten times Earth's gravity.";
        if(ref.system().equals(BEERUS))return "The God of Destruction's own world, beyond reach without god ki.";
        return "";
    }

    /** A series world's own landscape ({@code PlanetTerrain} style; 0 for every other world, Planet Vegeta's and Frieza 79's included, so visited ground never changes). */
    public static int style(Ref ref){return ref.planet()!=0?0:ref.system().equals(YARDRAT)?com.dbzenith.galaxy.planet.PlanetTerrain.YARDRAT:ref.system().equals(CEREAL)?com.dbzenith.galaxy.planet.PlanetTerrain.CEREAL:0;}

    /** A reserved world's sky, grass and water (its own biome), or null for its kind's. */
    public static String biome(Ref ref){
        if(ref.planet()!=0||!isNew(ref.system()))return null;
        return ref.system().equals(VEGETA)?"planet_vegeta":ref.system().equals(FRIEZA)?"planet_frieza79":ref.system().equals(YARDRAT)?"planet_yardrat":"planet_cereal";
    }

    public static SystemSpec system(long worldSeed,long salt,Id id){
        long seed=seed(worldSeed,salt,id);
        String system,main,hazard,landmark,population;Kind kind;double gravity,sunSize;int temperature,moons;StarType sun;
        if(id.equals(VEGETA)){system="Vegeta system";main="Planet Vegeta";kind=Kind.DESERT;gravity=10;temperature=40;sun=StarType.RED_DWARF;sunSize=1.4;
            hazard="Dust storms";landmark="Saiyan city";population="Saiyan warriors";moons=2;}
        else if(id.equals(FRIEZA)){system="Frieza Planet 79 system";main="Planet Frieza No. 79";kind=Kind.TEMPERATE;gravity=1.2;temperature=18;sun=StarType.YELLOW;sunSize=1.1;
            hazard="Occasional meteor showers";landmark="Frieza Force base";population="Frieza Force soldiers";moons=1;}
        else if(id.equals(YARDRAT)){system="Yardrat system";main="Yardrat";kind=Kind.TEMPERATE;gravity=.9;temperature=22;sun=StarType.YELLOW;sunSize=1.3;
            hazard="Mild seasons";landmark="Yardratian spires";population="Yardratians";moons=1;}
        else{system="Cereal system";main="Cereal";kind=Kind.TEMPERATE;gravity=1.1;temperature=16;sun=StarType.YELLOW;sunSize=1;
            hazard="Scarred highlands";landmark="Sugarian dome colony";population="Sugarians";moons=0;}
        List<Body> planets=new ArrayList<>();
        for(int i=0;i<3;i++){
            boolean first=i==0;long s=mix(seed+i);
            String label=first?main:system+" / "+(i+1);
            List<Moon> sats=new ArrayList<>();
            if(first)for(int m=0;m<moons;m++)sats.add(new Moon(label+" / Moon"+(moons>1?" "+(m+1):""),mix(s+9+m),Kind.BARREN,1024-m*256,.2,1.4+m*.5,1.2+m*2.1));
            planets.add(new Body(i,label,s,first?kind:i==1?Kind.BARREN:Kind.ICE,first?4096:2048,first?gravity:.4,
                first?Atmosphere.BREATHABLE:i==1?Atmosphere.NONE:Atmosphere.THIN,first?temperature:i==1?-10:-80,
                32000,first?14:7,!first&&i==2,2+i*1.7,((s>>>11)*0x1.0p-53)*Math.PI*2,List.copyOf(sats),
                first?hazard:"None",first?landmark:"None",first?population:"None"));
        }
        return new SystemSpec(id,system,id.x()*1000d+500,id.y()*1000d+500,id.z()*1000d+500,
            List.of(new Star(sun,sunSize,1,0)),List.copyOf(planets));
    }
}
