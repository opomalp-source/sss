package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.transform.Form;
import java.util.ArrayList;
import java.util.List;

/** Authored block-lock layouts. Head pixels: -Z is the face, -Y is up. */
public final class HairPresetLibrary {
    private HairPresetLibrary() {}
    public static List<HairMesh.Segment> create(Preset preset,Form.HairStyle style) {
        return create(preset,style,style,1);
    }
    static List<HairMesh.Segment> create(Preset preset,Form.HairStyle style,Form.HairStyle target,float progress) {
        if(preset==Preset.BALD) return List.of();
        HairGeometry.Builder h=new HairGeometry.Builder(style,target,progress),longs=new HairGeometry.Builder(Form.HairStyle.NONE);
        switch(preset) {
            case SPIKY -> goku(h);
            case PRINCE -> vegeta(h);
            case TEEN -> gohan(h,false);
            case GOHAN_ADULT -> gohan(h,true);
            case WILD -> broly(h);
            case MANE -> { raditz(h);mane(longs,18,1); }
            case CURTAINS -> curtains(h,false);
            case SAGE -> { curtains(h,true);mane(longs,13,.8); }
            case LONG -> { curtains(h,true);drape(longs,0,11,5); }
            case SWEPT -> swept(h,false);
            case SIDECUT -> swept(h,true);
            case SLICK -> slick(h);
            case BOWL -> bowl(h);
            case BUZZ -> h.cap(HairGeometry.Cap.BUZZ);
            case PUFF -> puff(h);
            case MOHAWK -> mohawk(h);
            case PONYTAIL -> { slick(h);drape(longs,0,12,3); }
            case TWINTAILS -> { curtains(h,false);drape(longs,-5,11,2);drape(longs,5,11,2); }
            case TOPKNOT -> { slick(h);lock(h,0,-9,2,0,-12,3,3.4,3,3); }
            default -> {}
        }
        float growth=(style==Form.HairStyle.LONG?1:0)*(1-progress)+(target==Form.HairStyle.LONG?1:0)*progress;
        if(growth>0&&preset!=Preset.MANE) {
            HairGeometry.Builder extra=new HairGeometry.Builder(Form.HairStyle.NONE);extra.growth=growth;
            mane(extra,21,1.1);
            List<HairMesh.Segment> out=new ArrayList<>(h.build());out.addAll(longs.build());out.addAll(extra.build());return List.copyOf(out);
        }
        List<HairMesh.Segment> out=new ArrayList<>(h.build());out.addAll(longs.build());return List.copyOf(out);
    }
    /** A stepped lock with a slight backward bow and a square final block, never a zero-area tip. */
    private static void lock(HairGeometry.Builder h,double x,double y,double z,double ex,double ey,double ez,double w,double d,int steps) {
        double[] data=new double[(steps+1)*5];
        for(int i=0;i<=steps;i++) {
            double t=i/(double)steps,section=1-.68*t;
            int k=i*5;data[k]=x+(ex-x)*t;data[k+1]=y+(ey-y)*t;
            data[k+2]=z+(ez-z)*t+.24*Math.sin(Math.PI*t);
            data[k+3]=w*section;data[k+4]=d*(1-.52*t);
        }
        h.tuft(.12,data);
    }
    private static void bang(HairGeometry.Builder h,double x,double endX,double endY,double width) {
        h.tuft(.08,x,-8.9,-4.05,width,1.65,x+.15,-7.7,-4.85,width*.88,1.45,
            endX,-6.55,-5.12,width*.64,1.12,endX,endY,-5.18,width*.4,.8);
    }
    /** Goku reference: large left fan, thin vertical crest, lower right wing, and a separate rear fan. */
    private static void goku(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.FULL);
        // Outward-bending shoulders carry the broad fan in the supplied front reference.
        h.tuft(.12,-.65,-8.8,-1.7,4.9,3.5,-1.7,-11.3,-1.45,4.8,3.4,
            -3.85,-13.8,-.95,3.9,2.9,-6.15,-15.35,-.4,2.85,2.3,-8.2,-16.15,.15,1.8,1.7);
        h.tuft(.12,-2.4,-8.5,-.9,4.1,3.2,-4.15,-10.1,-.65,4.05,3.1,
            -6.55,-11.35,-.25,3.3,2.6,-8.25,-11.95,.1,2.65,2.15,-10.2,-12.4,.4,1.75,1.7);
        h.tuft(.1,1.15,-8.8,.15,2.8,2.6,1.55,-11.4,.3,2.55,2.45,
            1.4,-13.9,.65,2.05,2.05,.8,-16.2,.95,1.4,1.55,.2,-17.65,1.2,.85,1.05);
        h.tuft(.12,2,-8.6,.5,3.9,3,4.2,-9.35,.9,3.75,2.85,
            6.2,-9.85,1.3,3,2.45,8,-10.3,1.65,2.15,1.85,9,-10.8,1.9,1.25,1.3);
        h.tuft(.12,-.8,-8.8,2.65,4.1,3.5,-1.8,-10.9,3.35,3.85,3.15,
            -3.55,-12.6,4.35,2.9,2.65,-5.4,-13.65,5.5,1.9,1.85);
        lock(h,1.65,-8.35,3.1,7.1,-10.55,6.8,3.55,2.8,4);
        lock(h,-2.5,-7.6,3.1,-8.5,-8.55,5.6,3.1,2.5,4);
        h.tuft(.08,-.35,-9,-3.65,3.45,2.2,-.8,-7.85,-4.65,3.15,1.8,
            -1.85,-6.75,-5.05,2.4,1.35,-2.75,-5.6,-5.1,1.35,.95);
        h.tuft(.08,2.5,-8.8,-3.8,2.65,1.95,2.75,-7.55,-4.75,2.3,1.6,
            2.5,-6.25,-5.1,1.55,1.1,2,-5.05,-5.1,.8,.75);
        h.tuft(.08,-2.7,-8.6,-3.6,2.1,1.65,-3.55,-7.15,-4.3,1.65,1.25,
            -3.9,-5.6,-4.55,.95,.8);
        sides(h,1.15,false);
    }
    /** Short, staggered side fans and nape shingles give depth without a second buzz haircut. */
    private static void sides(HairGeometry.Builder h,double size,boolean upright) {
        for(int s:new int[]{-1,1}) {
            for(int tier=0;tier<3;tier++) {
                double y=-7.2+tier*1.25;
                lock(h,s*3.9,y,.5+tier*.95,s*(4.8+size*(2.5-tier*.4)),y+(upright?-2.3:-.1),3+tier*.95,2.05,1.85,3);
            }
            lock(h,s*3.9,-7.7,-2.65,s*4.75,-4.8,-2.4,1.65,1.45,3);
        }
        for(int i=-2;i<=2;i++) {
            double x=i*1.8;
            lock(h,x,-5.3,4.4,x+i*.35,-3.05+(Math.abs(i)%2)*.45,6.25,2.2,1.5,3);
        }
    }
    private static void vegeta(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.PRINCE);
        h.tuft(.05,0,-8.65,-4.1,3.2,1.5,0,-7.5,-4.7,2.2,1.25,0,-6.45,-4.85,1.15,.9,0,-5.9,-4.85,.6,.6);
        // Converging flame silhouette instead of parallel upright columns.
        h.tuft(.1,0,-8.7,-1.55,4,3.45,.15,-11.8,-1.1,3.5,3,
            .4,-14.45,-.25,2.65,2.45,.45,-16.65,.8,1.7,1.7,.15,-18.2,1.65,.7,.9);
        for(int s:new int[]{-1,1}) {
            h.tuft(.1,s*2.4,-8.65,-1.65,3.3,2.75,s*3.1,-10.85,-.95,3.05,2.6,
                s*2.9,-13.1,.05,2.4,2.15,s*2.1,-15.15,1.05,1.5,1.4,s*1.5,-16.6,1.85,.65,.8);
            h.tuft(.1,s*3.6,-8.15,.85,2.7,2.45,s*4.2,-10.2,1.65,2.4,2.2,
                s*3.9,-12.05,2.45,1.7,1.65,s*3.15,-13.7,3.35,.8,.95);
            lock(h,s*2.1,-8.3,3,s*1.7,-15.3,5,2.85,2.55,5);
            for(int i=0;i<3;i++) lock(h,s*4.1,-6.1-i*.65,-1+i*1.5,s*4.3,-8.6-i*.7,2.2+i*1.3,1.75,1.35,3);
        }
        lock(h,0,-8.7,2.7,0,-16,4.4,2.9,2.55,5);
        for(int i=-2;i<=2;i++) lock(h,i*1.7,-5.1,4.4,i*1.65,-6.1,5.6,1.9,1.25,3);
    }
    private static void gohan(HairGeometry.Builder h,boolean adult) {
        h.cap(adult?HairGeometry.Cap.SHORT:HairGeometry.Cap.FULL);
        if(adult) {
            h.tuft(.1,-1.1,-8.8,-2,3.85,3,-1.1,-10.65,-.7,3.25,2.75,
                -1.65,-12.05,1,2.35,2.05,-2.7,-13,2.4,1.1,1.1);
            lock(h,1.55,-8.7,-.9,2.2,-12.05,3.3,3.2,2.65,4);
            lock(h,-.3,-8.6,2.8,-.9,-11.35,5.8,2.9,2.5,4);
            for(int s:new int[]{-1,1}) {
                lock(h,s*3.2,-8.45,.5,s*4.05,-10,3.8,2.3,2,3);
                lock(h,s*4,-6.25,1.4,s*4.55,-7.1,4.2,1.8,1.3,3);
            }
            h.tuft(.08,-1.5,-8.85,-3.7,2.65,1.9,-2,-7.5,-4.7,2.1,1.5,
                -1.85,-6.1,-5.05,1.3,.95,-1.05,-4.85,-5.1,.6,.65);
        } else {
            h.tuft(.1,-.8,-8.7,-.9,4.2,3.4,-1.4,-10.4,-.4,3.8,3,
                -2.9,-11.7,.5,2.8,2.3,-4.55,-12.4,1.4,1.35,1.3);
            lock(h,1.55,-8.75,.35,2.4,-12.2,3.1,3.5,2.9,4);
            lock(h,-2.8,-8.4,1.4,-6.25,-9.9,3.1,3.1,2.5,3);
            lock(h,2.85,-8.4,1.1,5.8,-10.2,3.45,3,2.5,3);
            lock(h,0,-8.6,3.1,-.8,-10.5,6,3.25,2.6,3);
            bang(h,-2.3,-2.7,-4.9,2.4);bang(h,.15,-.5,-5.8,2.65);bang(h,2.5,2,-5,2.25);
            sides(h,.45,false);
        }
    }
    private static void broly(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.LONG);
        for(int s:new int[]{-1,1}) {
            lock(h,s*1.2,-8.6,-.5,s*3.3,-14.5,1.8,4.2,3.5,4);
            lock(h,s*3,-8.1,.8,s*8.8,-11.8,3.6,3.8,3.1,4);
            lock(h,s*2.4,-8.3,3,s*5.5,-11.5,7.5,3.7,3,4);
            bang(h,s*2,s*2.45,-4.75,2.9);
        }
        bang(h,0,-.6,-5.5,2.45);sides(h,1.4,false);
        for(int i=-2;i<=2;i++) lock(h,i*1.8,-4.6,4.3,i*2.8,1.1-Math.abs(i)*.45,7,2.8,2.3,4);
    }
    private static void raditz(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.PRINCE);
        lock(h,0,-8.7,-2,0,-13.8,4.8,4,3.6,4);
        for(int s:new int[]{-1,1}) {
            lock(h,s*2.5,-8.6,-1.8,s*5,-12.7,4.25,3.4,2.9,4);
            lock(h,s*3.5,-8,1.5,s*7.1,-10,7.1,3.4,2.9,4);
        }
        sides(h,.8,false);
    }
    private static void mane(HairGeometry.Builder h,double length,double spread) {
        for(int layer=0;layer<3;layer++) for(int i=-4;i<=4;i++) {
            double stagger=((i+4)%3)*.8,edge=Math.abs(i)/4.0;
            double x=(i*1.48+(layer%2==1?.55:0))*spread;
            double end=length*(1-.24*layer-.19*edge)-stagger;
            double z=5+layer*1.45+.55*Math.cos(i*1.7+layer);
            double flare=1.13+layer*.13;
            h.tuft(.3,i*.9,-7.8,3.6,2.5,2.2,x*.83,-2,z,2.45,2.05,
                x*flare,1+stagger*.3,z+1.4,1.95,1.65,
                x*flare*.98,end*.55,z+2.0,1.5,1.25,
                x*flare*.85,end*.8,z+2.25,1.05,.95,
                x*flare*.76,end*.93,z+2.5,.65,.7,
                x*flare*.70,end,z+2.55,.45,.5);
        }
        for(int s:new int[]{-1,1}) for(int t=0;t<4;t++)
            lock(h,s*3.9,-6+t*.7,2+t*.8,s*(6.8+t*.15)*spread,2+t*2.0,6.6+t*.7,2.5,2,4);
    }
    private static void curtains(HairGeometry.Builder h,boolean longHair) {
        h.cap(longHair?HairGeometry.Cap.LONG:HairGeometry.Cap.FULL);
        for(int s:new int[]{-1,1}) {
            h.tuft(.1,s*.65,-8.8,-2.9,3.15,2.6,s*2.35,-9.05,-3.7,3.35,2.3,
                s*3.55,-7.5,-4.55,2.8,1.85,s*3.2,-5.55,-4.95,2,1.35,s*2.75,-3.8,-4.95,1.1,.8);
            for(int i=0;i<3;i++) lock(h,s*2.4,-8.9,-.7+i*1.85,s*4.85,longHair?-1.5:-3.45,1+i*1.95,3.2,2.55,4);
        }
        for(int i=-1;i<=1;i++) lock(h,i*2.4,-8.5,3.5,i*2.9,-2.6,5.2,3,2,4);
    }
    private static void drape(HairGeometry.Builder h,double center,double length,int count) {
        for(int i=0;i<count;i++) {
            double x=center+(i-(count-1)*.5)*1.4;
            h.tuft(.3,x*.65,-7,4,2.35,2.15,x,-2,6,2.4,2,x+.3,4,7.2,2,1.6,x-.25,length-2,7.8,1.55,1.2,x-.5,length,7.8,.7,.7);
        }
    }
    private static void swept(HairGeometry.Builder h,boolean shaved) {
        h.cap(shaved?HairGeometry.Cap.SIDECUT:HairGeometry.Cap.SHORT);
        for(int i=0;i<4;i++) {
            double z=-3+i*2;
            h.tuft(.12,-2.9,-8.7,z,3,2.4,-1.2,-10.1,z+.15,3.1,2.4,2,-10.2,z+.4,2.7,2.1,4.6,-8.6,z+.8,2,1.65,5.4,shaved?-3.4:-7,z+1.1,1.1,1);
        }
    }
    private static void slick(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.SHORT);
        for(int i=-2;i<=2;i++) lock(h,i*1.7,-8.95,-3.9,i*1.95,-9.3,5.6,2.25,1.85,4);
    }
    private static void bowl(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.FULL);
        for(int i=-2;i<=2;i++) lock(h,i*1.85,-8.9,-3.65,i*1.88,-5.25,-4.85,2.15,1.65,3);
        for(int s:new int[]{-1,1}) for(int i=0;i<4;i++) lock(h,s*3.6,-8.7,-2+i*1.9,s*4.7,-4.8,-1.7+i*1.9,2.45,1.8,3);
    }
    private static void puff(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.FULL);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) lock(h,x*2.7,-8.65,z*2.6,x*4,-11.8+(Math.abs(x)+Math.abs(z))*.65,z*4,4.1,3.8,2);
        sides(h,.5,false);
    }
    private static void mohawk(HairGeometry.Builder h) {
        h.cap(HairGeometry.Cap.MOHAWK);
        for(int i=0;i<5;i++) lock(h,0,-8.7,-3.5+i*1.7,0,-13.6,-3+i*1.8,1.55,2.25,4);
    }
}
