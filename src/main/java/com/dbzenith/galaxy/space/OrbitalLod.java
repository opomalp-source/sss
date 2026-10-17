package com.dbzenith.galaxy.space;

import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Small shared unit meshes: geometry depends only on detail, never on a planet's identity or world seed. */
public final class OrbitalLod {
    public record Patch(Vec3 middle,List<Vec3> corners,float u,float v,float du,float dv){}
    private static final Set<Integer> DETAILS=Set.of(8,12,16,24,32,40,56);
    private static final Map<Integer,List<Patch>> MESHES=new ConcurrentHashMap<>();
    public static boolean visible(Vec3 relative,double bound,Vec3 look){return relative.dot(look)>=-bound;}
    public static int segments(double radius,int quality){return radius>12?(quality==0?24:quality==1?40:56):radius>3?(quality==0?16:quality==1?24:32):radius>.7?16:8;}
    public static int texture(double radius,boolean clouds,int quality){return radius<=5?64:quality==0?128:clouds?256:512;}
    public static Vec3 normal(double lon,double lat){return new Vec3(Math.cos(lat)*Math.cos(lon),Math.sin(lat),Math.cos(lat)*Math.sin(lon));}
    public static List<Patch> mesh(int segments){
        if(!DETAILS.contains(segments))throw new IllegalArgumentException("Unbounded orbital mesh");
        return MESHES.computeIfAbsent(segments,n->{var list=new ArrayList<Patch>();int rows=Math.max(4,n/2);double dl=Math.PI*2/n,dp=Math.PI/rows;
            for(int y=0;y<rows;y++)for(int x=0;x<n;x++){double lon=x*dl,lat=-Math.PI/2+y*dp;
                list.add(new Patch(normal(lon+dl*.5,lat+dp*.5),List.of(normal(lon,lat),normal(lon,lat+dp),normal(lon+dl,lat+dp),normal(lon+dl,lat)),(float)x/n,(float)y/rows,1f/n,1f/rows));
            }return List.copyOf(list);
        });
    }
}
