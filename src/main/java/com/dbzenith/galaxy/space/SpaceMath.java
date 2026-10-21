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
    /** Flying speeds (blocks a tick): cruising, and sprinting (within vanilla's movement checks). */
    public static final double CRUISE=1.8,SPRINT=5.0;
    /**
     * Ki flight in space (the space flight rework): you go where you look. WASD sets the direction (forward, strafe),
     * Space rises and Sneak sinks along your up; speed eases towards {@link #CRUISE} (Sprint: {@link #SPRINT}) and,
     * with nothing pressed, eases out to a stop relative to the local frame. Without flight (no ki flight, no pod) you
     * only drift: space keeps your momentum.
     */
    public static Vec3 step(Vec3 velocity,double forward,double strafe,double up,boolean boost,boolean brake,float yaw,float pitch,boolean powered){
        return step(velocity,forward,strafe,up,boost,brake,Vec3.directionFromRotation(pitch,yaw),new Vec3(Math.cos(Math.toRadians(yaw)),0,Math.sin(Math.toRadians(yaw))),new Vec3(0,1,0),powered);
    }
    /** The same along an explicit frame (the space camera's: where you look, your left and your up); {@code brake} is Sneak, sinking. */
    public static Vec3 step(Vec3 velocity,double forward,double strafe,double up,boolean boost,boolean brake,Vec3 look,Vec3 left,Vec3 upward,boolean powered){
        if(!powered)return velocity;
        double rise=up-(brake?1:0);
        Vec3 input=look.scale(forward).add(left.scale(strafe)).add(upward.scale(rise));
        boolean moving=input.lengthSqr()>1e-6;
        Vec3 target=moving?input.normalize().scale(boost?SPRINT:CRUISE):Vec3.ZERO;
        double ease=moving?(boost?.07:.12):.09;                                   // speeding up is brisk, sprinting builds, letting go settles in about two seconds
        Vec3 v=velocity.add(target.subtract(velocity).scale(ease));
        if(!moving&&v.lengthSqr()<1e-4)v=Vec3.ZERO;
        return v.lengthSqr()>SPRINT*SPRINT?v.normalize().scale(SPRINT):v;
    }
}
