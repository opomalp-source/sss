package com.dbzenith.galaxy.space;
import net.minecraft.world.phys.Vec3;
/** The same system coordinates as the orbital renderer, projected through the rotating surface frame. */
public final class PlanetSkyMath {
    public static Vec3 local(Vec3 direction,Vec3 surfaceNormal,double turns){
        double a=turns*Math.PI*2,c=Math.cos(a),s=Math.sin(a);
        Vec3 d=new Vec3(direction.x*c-direction.y*s,direction.x*s+direction.y*c,direction.z);
        Vec3 east=new Vec3(surfaceNormal.y,-surfaceNormal.x,0).normalize();if(east.lengthSqr()<.1)east=new Vec3(1,0,0);
        return new Vec3(d.dot(east),d.dot(surfaceNormal),d.dot(east.cross(surfaceNormal).normalize()));
    }
    public static double daylight(double sunAltitude,int stars){return Math.max(stars>=3?.42:.055,Math.min(1,.08+Math.max(0,sunAltitude)*.92));}
    public static double eclipse(Vec3 sun,double sunRadius,Vec3 moon,double moonRadius){
        double sep=Math.acos(Math.max(-1,Math.min(1,sun.normalize().dot(moon.normalize()))));
        double sr=Math.atan2(sunRadius,sun.length()),mr=Math.atan2(moonRadius,moon.length());
        return Math.max(0,Math.min(1,(sr+mr-sep)/Math.max(.00001,2*sr)))*Math.min(1,mr*mr/(sr*sr));
    }
}
