package com.dbzenith.client.render;

import com.dbzenith.transform.Form;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Rectangular, stepped locks. Every block has six closed planar faces and a blunt end. */
public final class HairGeometry {
    public enum Cap { FULL, PRINCE, SHORT, LONG, BUZZ, MOHAWK, SIDECUT }
    private HairGeometry() {}
    public static double hairline(Cap cap,double angle) { return -7.9; }
    public static final class Builder {
        private final List<HairMesh.Segment> faces=new ArrayList<>();
        private final Form.HairStyle style, targetStyle;
        private final float blend;
        public double growth=1;
        public int mirror=1;
        public Builder(Form.HairStyle style) { this(style,style,1); }
        public Builder(Form.HairStyle style,Form.HairStyle target,float blend) { this.style=style;this.targetStyle=target;this.blend=blend; }
        public List<HairMesh.Segment> build() { return List.copyOf(faces); }

        /** Overlapping root blocks form the actual haircut, with staggered side and nape ends. */
        public void cap(Cap profile) {
            if(profile==Cap.MOHAWK) { box(-.8,-8.7,-4.55,.8,-7.8,4.55);return; }
            box(-4.57,-8.85,-4.57,4.57,-7.8,4.57);
            if(profile==Cap.BUZZ) {
                box(-4.58,-8.5,-3.6,-3.95,-4.9,4.6);
                box(3.95,-8.5,-3.6,4.58,-4.9,4.6);
                box(-4.58,-8.5,3.95,4.58,-2.7,4.6);
                return;
            }
            for(int s:new int[]{-1,1}) for(int i=0;i<4;i++) {
                double z=-2.9+i*1.95;
                double end=profile==Cap.PRINCE?-6.6:profile==Cap.SHORT?-6.1:-5.7;
                if(profile==Cap.LONG) end+=.6;
                if(profile==Cap.SIDECUT&&s<0) end=-7.8;
                double x=s*4.35;
                box(x-.42,-8.65,z-1.08,x+.42,end+(i%2)*.45,z+1.08);
            }
            for(int i=0;i<5;i++) {
                double x=-3.68+i*1.84;
                box(x-.97,-8.6,3.98,x+.97,-4.4+(i%2)*.5,4.85);
            }
        }
        public void box(double x0,double y0,double z0,double x1,double y1,double z1) {
            Vec3 a=new Vec3((x0+x1)*.5,y0,(z0+z1)*.5),b=new Vec3(a.x,y1,a.z);
            block(a,b,x1-x0,z1-z0,new Vec3(1,0,0),new HairMesh.Root(a,new Vec3(0,1,0),new Vec3(0,-1,0),0,0),0,0,0,0);
        }
        /** Knots contain x,y,z,width,depth. Width stays constant per section; steps expose shoulders. */
        public void tuft(double flex,double... data) {
            if(data.length<10||data.length%5!=0) throw new IllegalArgumentException("Invalid block lock");
            int n=data.length/5; Vec3[] p=new Vec3[n];
            Vec3 origin=new Vec3(data[0]*mirror,data[1],data[2]);
            for(int i=0;i<n;i++) {
                int k=i*5;p[i]=new Vec3(data[k]*mirror,data[k+1],data[k+2]);
                if(i>0) {
                    Vec3 delta=p[i].subtract(origin);
                    Vec3 start=raised(delta,style),end=raised(delta,targetStyle);
                    p[i]=origin.add(start.lerp(end,blend).scale(growth));
                }
            }
            double total=0;for(int i=1;i<n;i++) total+=p[i].distanceTo(p[i-1]);
            if(total<.001) return;
            Vec3 direction=p[n-1].subtract(origin).normalize();
            HairMesh.Root root=new HairMesh.Root(origin,direction,new Vec3(0,-1,0),(float)flex,direction.y>0&&flex>0?.35f:0);
            double distance=0; Vec3 side=null;
            for(int i=0;i<n-1;i++) {
                Vec3 tangent=p[i+1].subtract(p[i]).normalize();double length=p[i+1].distanceTo(p[i]);
                if(length<.001) continue;
                if(side==null) side=tangent.cross(new Vec3(0,0,1));
                else side=side.subtract(tangent.scale(side.dot(tangent)));
                if(side.lengthSqr()<.001) side=new Vec3(1,0,0);
                side=side.normalize();
                double w=Math.max(.35,data[i*5+3])*growth,d=Math.max(.35,data[i*5+4])*growth;
                // A buried overlap seals bends; the smaller next block leaves a visible square shoulder.
                double overlap=Math.min(.38,length*.2);
                block(p[i].subtract(tangent.scale(i==0?0:overlap)),p[i+1],w,d,side,root,
                    (float)distance,(float)(distance+length),(float)(distance/total),(float)((distance+length)/total));
                distance+=length;
            }
        }
        private static Vec3 raised(Vec3 d,Form.HairStyle style) {
            if(style!=Form.HairStyle.SPIKY&&style!=Form.HairStyle.SPIKY_TALL&&style!=Form.HairStyle.LONG) return d;
            return new Vec3(d.x*.78,-(Math.abs(d.y)*.93+Math.hypot(d.x,d.z)*.33)*(style==Form.HairStyle.SPIKY_TALL?1.16:1),d.z*.83);
        }
        private void block(Vec3 a,Vec3 b,double width,double depth,Vec3 side,HairMesh.Root root,float d0,float d1,float t0,float t1) {
            Vec3 tangent=b.subtract(a).normalize(),across=tangent.cross(side).normalize();
            Vec3[] v=new Vec3[8];
            for(int i=0;i<8;i++) v[i]=(i<4?a:b).add(side.scale(((i%4==0||i%4==3)?-1:1)*width*.5)).add(across.scale((i%4<2?-1:1)*depth*.5));
            Vec3 center=a.add(b).scale(.5);
            for(int[] q:new int[][]{{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}}) {
                Vec3 normal=v[q[1]].subtract(v[q[0]]).cross(v[q[2]].subtract(v[q[0]]));
                Vec3 outward=v[q[0]].add(v[q[1]]).add(v[q[2]]).add(v[q[3]]).scale(.25).subtract(center);
                if(normal.dot(outward)<0) q=new int[]{q[3],q[2],q[1],q[0]};
                float[][] coords=new float[4][3],uv=new float[4][2];float[] ds=new float[4],ts=new float[4];
                for(int j=0;j<4;j++) { Vec3 p=v[q[j]];coords[j]=new float[]{(float)p.x,(float)p.y,(float)p.z};ds[j]=q[j]<4?d0:d1;ts[j]=q[j]<4?t0:t1;uv[j]=new float[]{.5f,.5f}; }
                faces.add(new HairMesh.Segment(coords,root,ds,ts,false,uv));
            }
        }
    }
}
