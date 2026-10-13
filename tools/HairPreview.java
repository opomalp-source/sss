package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Offline orthographic previews of the actual HairMesh vertices; no Minecraft window is required. */
public final class HairPreview {
    private static final int[][] FACES = {{1,3,7,5}, {0,4,6,2}, {2,6,7,3}, {0,1,5,4}, {4,5,7,6}, {0,2,3,1}};
    private record Surface(float[][] corners, int rgb) {}
    private static final int W = 280, H = 340;

    public static void main(String[] args) throws Exception {
        File output = new File(args[0]);
        output.mkdirs();
        HashSet<String> codes = new HashSet<>();
        int shapes = 0;
        StringBuilder report = new StringBuilder("Hair geometry validation\n");
        for (Preset p : Preset.values()) {
            var strands = HairCode.strands(p);
            check(strands.size() <= HairCode.MAX_STRANDS, "strand limit: " + p);
            check(strands.equals(HairCode.decode(p.code())), "round trip: " + p);
            check(codes.add(p.code()), "distinct preset: " + p);
            for (Form.HairStyle style : Form.HairStyle.values()) {
                Form f = Form.builder("preview_" + style).hair(style).build();
                String grown = HairCode.forForm(p.code(), f);
                var decoded = HairCode.decode(grown);
                check(decoded != null && decoded.size() <= HairCode.MAX_STRANDS, "transformation: " + p + "/" + style);
                for (boolean chunky : new boolean[]{true, false}) {
                    var mesh = HairMesh.buildCode(grown, chunky);
                    if (!grown.isEmpty()) check(SculptedHair.isStock(grown), "stock hair uses authored geometry: " + p + "/" + style);
                    for (var seg : mesh) for (float[] c : seg.corners()) for (float v : c)
                        check(Float.isFinite(v), "finite mesh: " + p);
                    for (var seg : mesh) {
                        for(float[] uv:seg.uv()) check(Float.isFinite(uv[0])&&Float.isFinite(uv[1]),"finite material coordinates: "+p);

                        float[] a = seg.corners()[0], b = seg.corners()[1], c = seg.corners()[2];
                        double nx = (b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
                        double ny = (b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
                        double nz = (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
                        check(nx*nx+ny*ny+nz*nz > 1e-12, "nondegenerate face: " + p);
                    }
                    for (HairWind.State wind : new HairWind.State[]{HairWind.State.STILL,
                            new HairWind.State(new Vec3(.6,-.3,.8),new Vec3(.1,.95,.2),1f)}) {
                        ValidatingConsumer consumer = new ValidatingConsumer();
                        HairMesh.render(new PoseStack(),consumer,mesh,wind,15728880,0,.04f,.04f,.05f);
                        check(consumer.count == mesh.size()*4, "complete render: " + p);
                    }
                    shapes++;
                }
            }
            report.append(p).append(": ").append(HairMesh.buildCode(p.code(),true).size()).append(" mesh faces, all five form styles render correctly\n");
        }
        verifyTransformation();
        verifyDynamics();
        int coverage=verifyCoverage();
        check(HairMesh.MATERIAL.getPath().equals("textures/entity/aura_white.png"),"flat material selected");
        try(var input=HairCode.class.getResourceAsStream("/assets/dbzenith/textures/entity/aura_white.png")) {
            check(input!=null,"flat material exists");
            BufferedImage material=ImageIO.read(input);
            check(material.getRGB(material.getWidth()/2,material.getHeight()/2)==0xFFFFFFFF,"sampled material is opaque neutral white");
        }
        check(HairCode.decode("garbage") == null, "reject garbage");
        check(HairMesh.buildCode("garbage", true).isEmpty(), "invalid hair safely produces no geometry");
        check(HairCode.decode(HairCode.PREFIX + "BwAAAA") == null, "reject invalid face");
        check(HairCode.sanitize("").equals(""), "bald remains bald");
        java.util.Properties legacy = new java.util.Properties();
        try (var input = HairCode.class.getResourceAsStream("/assets/dbzenith/hair/legacy-presets.properties")) {
            check(input != null, "migration resource present");
            legacy.load(input);
        }
        for (String code : legacy.stringPropertyNames()) {
            Preset preset = Preset.valueOf(legacy.getProperty(code));
            check(HairCode.decode(code).equals(HairCode.strands(preset)), "saved preset upgrades: " + preset);
            check(HairCode.sanitize(code).equals(preset.code()), "upgraded code canonical: " + preset);
        }
        var custom = List.of(new HairCode.Strand(HairCode.Face.TOP, 5, 2, 2, -1, 9, 2, HairCode.Bend.LIFT));
        check(HairCode.decode(HairCode.encode(custom)).equals(custom), "custom hair unchanged");
        check(!SculptedHair.isStock(HairCode.encode(custom)), "custom hair uses editable renderer");
        BufferedImage sheet = new BufferedImage(W * 3, 100 + H * Preset.values().length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x121923)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setColor(new Color(0xF0ECE3)); g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString("Dragon Block Zenith / rebuilt hair presets", 20, 35);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Actual mesh geometry / front, side and back / offline preview", 20, 65);
        int row = 0;
        for (Preset p : Preset.values()) {
            for (int view = 0; view < 3; view++) {
                BufferedImage tile = render(p, null, view * Math.PI / 2, 0.07);
                g.drawImage(tile, view * W, 100 + row * H, null);
            }
            row++;
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(output, "hair-presets.png"));
        BufferedImage overview = new BufferedImage(W*4,H*5,BufferedImage.TYPE_INT_RGB);
        g = overview.createGraphics(); g.setColor(new Color(0x121923)); g.fillRect(0,0,overview.getWidth(),overview.getHeight());
        for (int i=0;i<Preset.values().length;i++) g.drawImage(render(Preset.values()[i],null,-Math.PI/6,0.08),(i%4)*W,(i/4)*H,null);
        g.dispose(); ImageIO.write(overview,"png",new File(output,"hair-overview.png"));
        BufferedImage heroes = new BufferedImage(W * 4, H * 4 + 90, BufferedImage.TYPE_INT_RGB);
        g = heroes.createGraphics();
        g.setColor(new Color(0x121923)); g.fillRect(0, 0, heroes.getWidth(), heroes.getHeight());
        g.setColor(new Color(0xF0ECE3)); g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("Goku / Vegeta / Teen Gohan / Adult Gohan — sculpted geometry", 20, 34);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Offline mesh preview. In-game lighting and hair textures differ.", 20, 62);
        Preset[] main = {Preset.SPIKY, Preset.PRINCE, Preset.TEEN, Preset.GOHAN_ADULT};
        for (int i = 0; i < main.length; i++) for (int view = 0; view < 4; view++)
            g.drawImage(render(main[i], null, new double[]{0, Math.PI/2, Math.PI, -Math.PI/3}[view], 0.07), view*W, 90+i*H, null);
        g.dispose();
        ImageIO.write(heroes, "png", new File(output, "hair-heroes.png"));
        BufferedImage forms = new BufferedImage(W * 5, H * main.length, BufferedImage.TYPE_INT_RGB);
        g = forms.createGraphics();
        for (int i = 0; i < main.length; i++) for (int j = 0; j < 5; j++)
            g.drawImage(render(main[i], Form.HairStyle.values()[j], -Math.PI/5, 0.1), j*W, i*H, null);
        g.dispose();
        ImageIO.write(forms, "png", new File(output, "hair-transformations.png"));
        report.append("PASS: ").append(shapes).append(" preset/form/render-style combinations, rendered still and in wind; ").append(coverage).append(" scalp coverage rays; ").append(legacy.size()).append(" legacy preset migrations; custom codes preserved.\n");
        Files.writeString(new File(output, "hair-validation.txt").toPath(), report);
        System.out.print(report);
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    private static int verifyCoverage() {
        int rays=0;
        for(HairGeometry.Cap cap:HairGeometry.Cap.values()) {
            if(cap==HairGeometry.Cap.MOHAWK) continue; // A deliberately shaved style.
            HairGeometry.Builder builder=new HairGeometry.Builder(Form.HairStyle.NONE);builder.cap(cap);
            List<HairMesh.Segment> shell=builder.build();
            if(cap!=HairGeometry.Cap.BUZZ) check(shell.stream().noneMatch(HairMesh.Segment::scalp),"no skull shell under non-buzz hair: "+cap);
            java.util.Map<String,Integer> edges=new java.util.HashMap<>();
            for(var face:shell) {
                var v=face.corners();
                for(int[] tri:new int[][]{{0,1,2},{0,2,3}}) {
                    if(Arrays.equals(v[tri[0]],v[tri[1]])||Arrays.equals(v[tri[1]],v[tri[2]])||Arrays.equals(v[tri[0]],v[tri[2]])) continue;
                    for(int i=0;i<3;i++) {
                        String a=pointKey(v[tri[i]]),b=pointKey(v[tri[(i+1)%3]]);
                        String key=a.compareTo(b)<0?a+"|"+b:b+"|"+a;
                        edges.merge(key,1,Integer::sum);
                    }
                }
            }
            for(int uses:edges.values()) check(uses==2,"watertight scalp topology: "+cap);
            for(double x=-4.5;x<=4.5;x+=.5) for(double z=-4.5;z<=4.5;z+=.5) {
                check(rayHit(shell,new Vec3(x,-8.50,z),new Vec3(0,-1,0)),"top scalp coverage: "+cap+" "+x+","+z);rays++;
            }
            for(int i=0;i<96;i++) {
                double angle=i*2*Math.PI/96+.013,sx=Math.sin(angle),sz=-Math.cos(angle);
                if(cap!=HairGeometry.Cap.BUZZ&&Math.cos(angle)>.55) continue; // Forehead is defined by each preset's bangs.
                double radius=4.5/Math.max(Math.abs(sx),Math.abs(sz));
                double boundary=cap==HairGeometry.Cap.BUZZ?HairGeometry.hairline(cap,angle)-.25:-7.85;
                for(int row=0;row<5;row++) {
                    double y=-8.53+(boundary+8.53)*row/4;
                    check(rayHit(shell,new Vec3(sx*radius,y,sz*radius),new Vec3(sx,0,sz)),"side scalp coverage: "+cap+" theta="+angle+" y="+y);rays++;
                }
            }
        }
        return rays;
    }
    private static String pointKey(float[] v) { return Math.round(v[0]*100000)+","+Math.round(v[1]*100000)+","+Math.round(v[2]*100000); }
    private static Vec3 vec(float[] v) { return new Vec3(v[0],v[1],v[2]); }
    private static boolean rayHit(List<HairMesh.Segment> faces,Vec3 origin,Vec3 dir) {
        for(var face:faces) for(int[] tri:new int[][]{{0,1,2},{0,2,3}}) {
            var p=face.corners();Vec3 a=vec(p[tri[0]]),e1=vec(p[tri[1]]).subtract(a),e2=vec(p[tri[2]]).subtract(a);
            Vec3 h=dir.cross(e2);double det=e1.dot(h);if(Math.abs(det)<1e-8) continue;
            Vec3 s=origin.subtract(a);double u=s.dot(h)/det;if(u<-.0001||u>1.0001) continue;
            Vec3 q=s.cross(e1);double v=dir.dot(q)/det;if(v<-.0001||u+v>1.0001) continue;
            double t=e2.dot(q)/det;if(t>=0&&t<1.2) return true;
        }
        return false;
    }

    private static void verifyTransformation() {
        String baseCode=Preset.SPIKY.code();
        String raisedCode=HairCode.forForm(baseCode,Form.builder("test").hair(Form.HairStyle.SPIKY).build());
        var base=HairMesh.of(baseCode);
        var raised=HairMesh.of(HairCode.forForm(Preset.SPIKY.code(),Form.builder("test").hair(Form.HairStyle.SPIKY).build()));
        check(HairMesh.transition(baseCode,raisedCode,0)==base,"transformation starts with current shape");
        check(HairMesh.transition(baseCode,raisedCode,1)==raised,"transformation finishes at target shape");
        double previous=Double.POSITIVE_INFINITY;
        for(float t:new float[]{0,.25f,.5f,.75f,1}) {
            var shape=HairMesh.transition(baseCode,raisedCode,t);
            double y=0;for(var face:shape) for(var v:face.corners()) y+=v[1];
            check(y<=previous+.001,"hair rises continuously with progress");previous=y;
            ValidatingConsumer consumer=new ValidatingConsumer();
            HairMesh.render(new PoseStack(),consumer,shape,HairWind.State.STILL,15728880,0,0,0,0);
            check(consumer.count==shape.size()*4,"transition faces render without collapse");
        }
        for(Preset preset:Preset.values()) for(Form.HairStyle style:Form.HairStyle.values()) {
            String targetCode=HairCode.forForm(preset.code(),Form.builder("morph").hair(style).build());
            for(float p:new float[]{.25f,.5f,.75f}) {
                var mesh=HairMesh.transition(preset.code(),targetCode,p);
                ValidatingConsumer vc=new ValidatingConsumer();
                HairMesh.render(new PoseStack(),vc,mesh,HairWind.State.STILL,15728880,0,0,0,0);
                check(vc.count==mesh.size()*4,"all preset transitions retain valid surfaces: "+preset+"/"+style);
            }
        }
        com.dbzenith.config.DBZConfig.SERVER_SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        var data=new com.dbzenith.data.PlayerData();
        var form=com.dbzenith.transform.Forms.SUPER_SAIYAN;
        double full=com.dbzenith.config.DBZConfig.SERVER.transformTimeBase.get()+com.dbzenith.config.DBZConfig.SERVER.transformTimePerTier.get()*form.tier();
        int fresh=com.dbzenith.transform.FormHandler.transformTime(data,form);
                check(fresh>=340,"first Super Saiyan charge lasts at least 17 seconds");
        check(com.dbzenith.transform.FormHandler.transformTime(data,form)==fresh,"an unfinished attempt does not consume first-use duration");
        com.dbzenith.transform.TransformationExperience.completed(data,form);
        int repeat=com.dbzenith.transform.FormHandler.transformTime(data,form);
        check(repeat==2*Math.max(10,(int)Math.round(full)),"repeat duration keeps normal mastery timing");
        var restored=new com.dbzenith.data.PlayerData();restored.load(data.save());
        check(com.dbzenith.transform.FormHandler.transformTime(restored,form)==repeat,"experience survives save/load even at zero mastery");
        check(com.dbzenith.transform.FormHandler.transformTime(data,com.dbzenith.transform.Forms.SUPER_SAIYAN_2)>=380,"first-use history is per form");
        for(float progress:new float[]{0,.5f,1}) {
            java.util.Set<Integer> colors=new java.util.HashSet<>();
            for(int t=0;t<32;t++) colors.add(HairTransformation.chargeColor(0x172534,0xFFD34A,progress,t));
            check(colors.equals(java.util.Set.of(0x172534,0xFFD34A)),"gold and actual normal hair colour both flash throughout charge");
        }
        check(HairTransformation.goldTarget("super_saiyan_2")&&!HairTransformation.goldTarget("super_saiyan_blue"),"gold flashing limited to early golden forms");
        data.setMastery(form.id(),40);
        check(com.dbzenith.transform.FormHandler.transformTime(data,form)<fresh,"mastery still speeds up transformation");
        data.setMastery(form.id(),100);
        check(com.dbzenith.transform.FormHandler.transformTime(data,form)==0,"mastered instant timing preserved");
        System.out.println("PASS: progressive hair lift, target endpoints, first-use timing, persistent per-form history, normal/gold flicker and mastery scaling");
    }
    private static void verifyDynamics() {
        HairWind.Spring coarse=new HairWind.Spring(),fine=new HairWind.Spring();
        HairWind.advance(coarse,0,0,0,0);HairWind.advance(fine,0,0,0,0);
        for(int i=1;i<=20;i++) HairWind.advance(coarse,i,0,-1,1);
        for(int i=1;i<=80;i++) HairWind.advance(fine,i*.25f,0,-1,1);
        check(Math.abs(coarse.y-fine.y)<1e-8&&Math.abs(coarse.z-fine.z)<1e-8,"frame-rate independent spring");
        check(coarse.y<-.8&&coarse.z>.8,"falling lifts / forward movement pushes back");
        for(int i=21;i<=80;i++) HairWind.advance(coarse,i,0,0,0);
        check(Math.abs(coarse.y)+Math.abs(coarse.z)<.0001,"hair settles after stopping");
        HairWind.advance(coarse,200,0,0,0);
        check(coarse.vy==0&&coarse.vz==0,"stale spring resets without snapping");
        for(Preset preset:new Preset[]{Preset.SPIKY,Preset.MANE,Preset.PRINCE}) {
            var mesh=HairMesh.buildCode(preset.code(),true);
            PositionConsumer still=new PositionConsumer(),moving=new PositionConsumer(),stiff=new PositionConsumer();
            HairMesh.render(new PoseStack(),still,mesh,HairWind.State.STILL,15728880,0,0,0,0);
            HairMesh.render(new PoseStack(),moving,mesh,new HairWind.State(new Vec3(.4,-.6,1),new Vec3(0,1,0),1),15728880,0,0,0,0);
            HairMesh.render(new PoseStack(),stiff,mesh,new HairWind.State(new Vec3(.4,-.6,1),new Vec3(0,1,0),.45f),15728880,0,0,0,0);
            double max=0,total=0,stiffTotal=0;
            for(int i=0;i<still.positions.size();i++) {
                double distance=still.positions.get(i).distanceTo(moving.positions.get(i))*16;
                if(mesh.get(i/4).distances()[i%4]==0) check(distance<.00001,"roots remain anchored: "+preset);
                max=Math.max(max,distance);total+=distance;
                stiffTotal+=still.positions.get(i).distanceTo(stiff.positions.get(i))*16;
            }
            check(max>.15&&max<2.3,"visible but bounded tip movement: "+preset);
            check(stiffTotal<total*.8,"transformed stiffness reduces motion: "+preset);
        }
        System.out.println("PASS: dynamics direction, settling, frame-rate independence, anchored roots, bounded tip motion and transformed stiffness");
    }
    private static final class PositionConsumer extends ValidatingConsumer {
        final List<Vec3> positions=new ArrayList<>();
        @Override public VertexConsumer vertex(double x,double y,double z) {
            super.vertex(x,y,z);positions.add(new Vec3(x,y,z));return this;
        }
    }
    private static class ValidatingConsumer implements VertexConsumer {
        int count;
        @Override public VertexConsumer vertex(double x,double y,double z) {
            check(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z),"finite rendered position");
            return this;
        }
        @Override public VertexConsumer color(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer uv(float u,float v) { check(Float.isFinite(u)&&Float.isFinite(v),"finite UV"); return this; }
        @Override public VertexConsumer overlayCoords(int u,int v) { return this; }
        @Override public VertexConsumer uv2(int u,int v) { return this; }
        @Override public VertexConsumer normal(float x,float y,float z) {
            float length=x*x+y*y+z*z;
            check(Float.isFinite(length)&&Math.abs(length-1)<.01,"normalized rendered surface normal");
            return this;
        }
        @Override public void endVertex() { count++; }
        @Override public void defaultColor(int r,int g,int b,int a) { }
        @Override public void unsetDefaultColor() { }
    }

    private static List<HairMesh.Segment> mesh(List<HairCode.Strand> strands, boolean chunky) {
        List<HairMesh.Segment> out = new ArrayList<>();
        for (var strand : strands) HairMesh.build(strand, out, chunky);
        return out;
    }

    private static void cube(List<Surface> out, float x, float y, float z, float w, float h, float d, int color) {
        float[][] c = new float[8][];
        for (int i=0; i<8; i++) c[i] = new float[]{x+((i&1)!=0?w:0), y+((i&2)!=0?h:0), z+((i&4)!=0?d:0)};
        out.add(new Surface(c, color));
    }

    private static BufferedImage render(Preset p, Form.HairStyle style, double yaw, double pitch) {
        List<Surface> geometry = new ArrayList<>();
        cube(geometry, -4,-8,-4,8,8,8,0xCDA67E);
        cube(geometry,-4.1f,-4.3f,-4.1f,1,1.3f,0.3f,0xCDA67E);
        cube(geometry,-2.7f,-4.1f,-4.08f,1.8f,1.2f,0.1f,0xEEEAE1);
        cube(geometry,0.9f,-4.1f,-4.08f,1.8f,1.2f,0.1f,0xEEEAE1);
        cube(geometry,-1.7f,-4.05f,-4.2f,0.7f,0.9f,0.1f,0x28272B);
        cube(geometry,1.0f,-4.05f,-4.2f,0.7f,0.9f,0.1f,0x28272B);
        cube(geometry,-4,0,-2,8,7,4,0xDF9126);
        String code = style == null ? p.code() : HairCode.forForm(p.code(), Form.builder("preview").hair(style).build());
        int color = style == null || style == Form.HairStyle.NONE ? 0x17181D : style == Form.HairStyle.SLIM ? 0x2878B6 : 0xEAC74E;
        if (!code.isEmpty()) {
            for (var seg : HairMesh.buildCode(code, true)) {
                float lift = seg.scalp() ? .025f : .035f+.025f*(seg.progress()[0]+seg.progress()[2])*.5f;
                int r = (color>>16)&255, b=color&255, green=(color>>8)&255;
                int rgb = ((int)(r+(255-r)*lift)<<16)|((int)(green+(255-green)*lift)<<8)|(int)(b+(255-b)*lift);
                geometry.add(new Surface(seg.corners(),rgb));
            }
        }
        BufferedImage img = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[W*H]; Arrays.fill(pixels,0xADB5C1);
        double[] depth = new double[W*H]; Arrays.fill(depth,Double.POSITIVE_INFINITY);
        double scale = style == Form.HairStyle.SPIKY_TALL || style == Form.HairStyle.LONG || p == Preset.MANE || p == Preset.LONG || p == Preset.SAGE ? 8 : 11.5;
        double cy=Math.cos(yaw), sy=Math.sin(yaw), cp=Math.cos(pitch), sp=Math.sin(pitch);
        for (Surface surface : geometry) {
            double[][] projected = new double[surface.corners.length][];
            for (int i=0;i<surface.corners.length;i++) {
                float[] v=surface.corners[i]; double x=v[0]*cy-v[2]*sy, z=v[0]*sy+v[2]*cy;
                double y=v[1]*cp-z*sp; z=v[1]*sp+z*cp;
                projected[i]=new double[]{W/2.0+x*scale,245+y*scale,z};
            }
            for(int[] face:surface.corners.length==4?new int[][]{{0,1,2,3}}:FACES) {
                double[] a=projected[face[0]],b=projected[face[1]],c=projected[face[2]];
                double ax=b[0]-a[0],ay=b[1]-a[1],az=(b[2]-a[2])*scale;
                double bx=c[0]-a[0],by=c[1]-a[1],bz=(c[2]-a[2])*scale;
                double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
                double lighting=0.62+0.38*Math.max(0,(-0.3*nx-0.65*ny-0.70*nz)/Math.max(1e-9,len));
                int rgb=shade(surface.rgb,lighting);
                triangle(pixels,depth,a,b,c,rgb); triangle(pixels,depth,a,c,projected[face[3]],rgb);
            }
        }
        img.setRGB(0,0,W,H,pixels,0,W);
        Graphics2D g=img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x172131)); g.setFont(new Font("SansSerif",Font.BOLD,16));
        g.drawString(p.name()+(style==null?"":" / "+style),12,25);
        g.dispose(); return img;
    }

    private static int shade(int c,double f) { return ((int)(((c>>16)&255)*f)<<16)|((int)(((c>>8)&255)*f)<<8)|(int)((c&255)*f); }
    private static void triangle(int[] pixels,double[] depth,double[] a,double[] b,double[] c,int color) {
        double det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);
        if(Math.abs(det)<1e-8) return;
        int x0=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(b[0],c[0])))),x1=Math.min(W-1,(int)Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
        int y0=Math.max(0,(int)Math.floor(Math.min(a[1],Math.min(b[1],c[1])))),y1=Math.min(H-1,(int)Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
            double u=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/det;
            double v=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/det;
            double w=1-u-v;
            if(u<0||v<0||w<0) continue;
            double z=u*a[2]+v*b[2]+w*c[2]; int at=y*W+x;
            if(z<depth[at]) {depth[at]=z;pixels[at]=color;}
        }
    }
}
