package com.dbzenith.galaxy.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import net.minecraft.world.phys.Vec3;

/** Public orbital geometry, deliberately independent of terrain seeds. Units are local blocks, never galaxy coordinates. */
public final class SpaceMath {
    public static final double ASCENT=512, ENTRY=364;
    public static double radius(int surfaceRadius){return Math.max(80,Math.min(600,surfaceRadius/12d));}
    public static Vec3 center(double orbit,double phase){return new Vec3(Math.cos(phase)*orbit*6000,128,Math.sin(phase)*orbit*6000);}
    public static Vec3 center(GalaxyCatalog.Body b){return center(b.orbit(),b.phase());}
    public static Vec3 star(int index,int count){
        if(count==3){double a=index*Math.PI*2/3;return new Vec3(Math.cos(a)*30000,128,Math.sin(a)*30000);}
        return new Vec3((index-(count-1)*.5)*650,128,0);
    }
    public static Vec3 normal(double x,double z,int radius){
        double longitude=Math.max(-.999,Math.min(.999,x/radius))*Math.PI,latitude=Math.max(-.999,Math.min(.999,z/radius))*Math.PI/2;
        return new Vec3(Math.sin(longitude)*Math.cos(latitude),Math.cos(longitude)*Math.cos(latitude),Math.sin(latitude));
    }
    public static double[] surface(Vec3 normal,int radius){
        Vec3 n=normal.normalize();return new double[]{Math.atan2(n.x,n.y)/Math.PI*radius,Math.asin(Math.max(-1,Math.min(1,n.z)))*2/Math.PI*radius};
    }
    public static double atmosphere(double height){double t=Math.max(0,Math.min(1,(height-180)/300));return t*t*(3-2*t);}
    /** No coast drag. Opposing thrust and sneak brake; boost remains bounded for vanilla movement validation. */
    public static Vec3 step(Vec3 velocity,double forward,double strafe,double up,boolean boost,boolean brake,float yaw,float pitch,boolean powered){
        return step(velocity,forward,strafe,up,boost,brake,Vec3.directionFromRotation(pitch,yaw),new Vec3(Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw))),new Vec3(0,1,0),powered);
    }
    /** The same, thrusting along an explicit frame (the space camera's: where you look, your left and your up). */
    public static Vec3 step(Vec3 velocity,double forward,double strafe,double up,boolean boost,boolean brake,Vec3 look,Vec3 left,Vec3 upward,boolean powered){
        Vec3 v=velocity;if(brake&&powered)v=v.scale(.82);
        if(powered){
            Vec3 input=look.scale(forward).add(left.scale(strafe)).add(upward.scale(up*.7));if(input.lengthSqr()>1)input=input.normalize();
            v=v.add(input.scale(boost?.10:.045));}
        double cap=!powered||boost?5.5:Math.max(3,Math.min(5.5,velocity.length()));return v.lengthSqr()>cap*cap?v.normalize().scale(cap):v;
    }
}
