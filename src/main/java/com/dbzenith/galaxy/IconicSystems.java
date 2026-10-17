package com.dbzenith.galaxy;
import java.util.*;
import static com.dbzenith.galaxy.GalaxyCatalog.*;

/** Additive reserved addresses: no previously valid v1 address changes its catalogue or terrain. */
public final class IconicSystems {
    public static final Id VEGETA=new Id(27,-6,14,8),FRIEZA=new Id(-34,9,-21,8);
    public static boolean reserved(int x,int y,int z,int slot){return slot==8&&(x==27&&y==-6&&z==14||x==-34&&y==9&&z==-21);}
    public static boolean isNew(Id id){return reserved(id.x(),id.y(),id.z(),id.slot());}
    public static List<Id> all(){return List.of(EARTH,NAMEK,VEGETA,NORTH,FRIEZA,BEERUS);}
    public static SystemSpec system(long worldSeed,long salt,Id id){
        boolean vegeta=id.equals(VEGETA);long seed=seed(worldSeed,salt,id);String name=vegeta?"Vegeta system":"Frieza Planet 79 system";
        List<Body> planets=new ArrayList<>();
        for(int i=0;i<3;i++){
            boolean main=i==0;long s=mix(seed+i);Kind kind=main?(vegeta?Kind.DESERT:Kind.TEMPERATE):i==1?Kind.BARREN:Kind.ICE;
            String label=main?(vegeta?"Planet Vegeta":"Frieza Planet 79"):name+" / "+(i+1);
            planets.add(new Body(i,label,s,kind,main?4096:2048,main?(vegeta?10:1.2):.4,
                main?Atmosphere.BREATHABLE:i==1?Atmosphere.NONE:Atmosphere.THIN,main?(vegeta?40:18):i==1?-10:-80,
                32000,main?14:7,!main&&i==2,2+i*1.7,((s>>>11)*0x1.0p-53)*Math.PI*2,
                main?List.of(new Moon(label+" / Moon",mix(s+9),Kind.BARREN,1024,.2,1.4,1.2)):List.of(),
                vegeta?"Dust storms":"Occasional meteor showers",vegeta?"Ancient ruins":"Abandoned base","Hostile scouts"));
        }
        return new SystemSpec(id,name,id.x()*1000d+500,id.y()*1000d+500,id.z()*1000d+500,
            List.of(new Star(vegeta?StarType.RED_DWARF:StarType.YELLOW,vegeta?1.4:1.1,1,0)),List.copyOf(planets));
    }
}
