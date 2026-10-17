package com.dbzenith.client.screen;

import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.galaxy.*;
import com.dbzenith.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.util.Mth;
import java.util.*;

/** A bounded, server-fed chart. The client never generates a galaxy or learns the world seed. */
public final class GalaxyScreen extends Screen {
    private static final int TEXT=0xFFE3EDF5,DIM=0xFF8AABC0,GOLD=0xFFFFCB79,CYAN=0xFF75D8E8;
    private static int nextSequence;
    private int sequence,view=1,planet=-1,cursor,scroll,detailScroll,detailMax;
    private boolean waiting;
    private long requestedAt;
    private CompoundTag data;
    private GalaxyCatalog.Id center=GalaxyCatalog.EARTH;
    private EditBox search,coordinates;
    private Button back,home,go,searchButton,waypoint,land,previous,next,jump,transmit;
    private float yaw=.25f,tilt=.65f,zoom=1,targetZoom=1;
    private int mapX=14,mapY=65,mapW,mapH,sideX,sideW;
    private String selected="",status="",query="";
    private final List<Node> nodes=new ArrayList<>();
    private record Node(int x,int y,int radius,String id,int planet,String name,int color,boolean known) {}
    public GalaxyScreen(CompoundTag data) { super(Component.literal("Galaxy map")); this.data=data; }
    public static void receive(GalaxyPackets.Data reply) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null) return;
        if(reply.open()) { mc.setScreen(new GalaxyScreen(reply.data())); return; }
        if(mc.screen instanceof GalaxyScreen s && reply.sequence()==s.sequence) {
            s.data=reply.data();s.waiting=false;s.accept();
        }
    }
    @Override protected void init() {
        sideW=Math.min(210,Math.max(142,width/3));sideX=width-sideW-14;mapW=sideX-mapX-10;mapH=height-mapY-68;
        button("Places",14,11,52,b->minecraft.setScreen(new IconicScreen(this)));
        search=new EditBox(font,width-206,11,135,18,Component.literal("Search discovered planets"));search.setMaxLength(32);search.setHint(Component.literal("Discovered planet name"));search.setValue(query);addRenderableWidget(search);
        searchButton=button("Find",width-66,11,52,b->{query=search.getValue();cursor=0;request(3);});
        back=button("Back",14,37,49,b->back());
        home=button("Home",68,37,49,b->{center=GalaxyCatalog.Id.parse(data.getString("current"));planet=-1;selected="";request(1);});
        button("Galaxy",122,37,58,b->{planet=-1;request(0);});
        button("Sector",185,37,58,b->{planet=-1;request(1);});
        button("Close",width-66,37,52,b->onClose());
        coordinates=new EditBox(font,14,height-30,Math.max(100,mapW-58),18,Component.literal("Sector coordinates x:y:z:system"));coordinates.setMaxLength(40);coordinates.setHint(Component.literal("x:y:z:system"));addRenderableWidget(coordinates);
        go=button("Go",mapX+mapW-51,height-30,51,b->{
            try {center=GalaxyCatalog.Id.parse(coordinates.getValue());selected="";planet=-1;request(1);}
            catch(IllegalArgumentException ex){status="Use x:y:z:system; coordinates -1024 to 1024, system 0-7.";}
        });
        waypoint=button("Waypoint",sideX+8,height-30,(sideW-20)/2,b->{if(planet>=0)request(4);else{planet=0;request(4);}});
        land=button("Land",sideX+sideW/2+2,height-30,(sideW-20)/2,b->{if(planet<0)planet=0;request(6);onClose();});
        jump=button("Jump",sideX+8,height-52,(sideW-20)/2,b->{if(planet<0)planet=0;request(8);onClose();});
        transmit=button("Transmit",sideX+sideW/2+2,height-52,(sideW-20)/2,b->{if(planet<0)planet=0;request(9);onClose();});
        previous=button("Previous",sideX+8,height-30,70,b->{cursor=Math.max(0,cursor-32);request(3);});
        next=button("Next",sideX+sideW-78,height-30,70,b->{cursor+=32;request(3);});
        if(data!=null) accept();
    }
    private Button button(String name,int x,int y,int w,java.util.function.Consumer<Button> onClick) {
        return addRenderableWidget(ThemedButton.of(Component.literal(name),onClick::accept).bounds(x,y,w,18).build());
    }
    private void accept() {
        int beforeView=view;
        view=data.getInt("view");center=GalaxyCatalog.Id.parse(data.getString("center"));planet=data.getInt("planet");
        coordinates.setValue(center.toString());scroll=detailScroll=0;status="";zoom=beforeView==view?1:.7f;targetZoom=1;
        if(view!=2)selected="";
    }
    private void request(int action) {
        if(waiting) return;
        if(System.currentTimeMillis()-requestedAt<550) {status="Chart updating; try again in a moment.";return;}
        requestedAt=System.currentTimeMillis();waiting=true;sequence=++nextSequence;status="";
        ModNetwork.sendToServer(new GalaxyPackets.Request(sequence,action,center.toString(),planet,query,cursor));
    }
    @Override public void tick() {
        search.tick();coordinates.tick();
        if(waiting && System.currentTimeMillis()-requestedAt>3500){waiting=false;status="No chart response. Try again.";}
    }
    private void back() {
        if(view==3){planet=-1;request(1);}
        else if(view==2 && planet>=0){planet=-1;scroll=0;}
        else{planet=-1;request(Math.max(0,view-1));}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        if(data==null)return;
        zoom+=(targetZoom-zoom)*.2f;
        g.fillGradient(0,0,width,height,0xFF060D19,0xFF0B1927);
        g.drawString(font,font.plainSubstrByWidth("GALAXY ATLAS",Math.max(30,width-290)),75,13,GOLD,false);
        jump.visible=transmit.visible=view==2;jump.active=!waiting&&com.dbzenith.galaxy.space.SpaceRuntime.isSpace(minecraft.level);
        var selectedBody=data.getCompound("system").getList("bodies",Tag.TAG_COMPOUND);transmit.active=!waiting&&data.getBoolean("canTransmit")&&planet>=0&&planet<selectedBody.size()&&selectedBody.getCompound(planet).getBoolean("visited");
        frame(g,mapX,mapY,mapW,mapH);frame(g,sideX,mapY,sideW,mapH);
        nodes.clear();g.enableScissor(mapX+1,mapY+1,mapX+mapW-1,mapY+mapH-1);
        background(g);
        if(view==3)results(g,mx,my);else if(view==2)system(g,mx,my);else chart(g,mx,my);
        g.disableScissor();
        sidebar(g);
        previous.visible=next.visible=view==3;previous.active=cursor>0&&!waiting;next.active=data.getBoolean("more")&&!waiting;
        waypoint.visible=land.visible=view==2;waypoint.active=!waiting;
        var landBodies=data.getCompound("system").getList("bodies",Tag.TAG_COMPOUND);int landingIndex=Math.max(0,planet);
        land.active=!waiting&&landingIndex<landBodies.size()&&landBodies.getCompound(landingIndex).getBoolean("landable");
        go.active=searchButton.active=!waiting;
        super.render(g,mx,my,pt);
        if(waiting)g.drawString(font,"Receiving chart...",mapX+8,mapY+mapH-13,CYAN,false);
        else if(!status.isEmpty())g.drawString(font,font.plainSubstrByWidth(status,width-28),14,height-11,0xFFFFB17C,false);
    }
    private void frame(GuiGraphics g,int x,int y,int w,int h){g.fill(x-1,y-1,x+w+1,y+h+1,0xFF294354);g.fill(x,y,x+w,y+h,0xFF07101C);}
    private void background(GuiGraphics g){
        for(int i=0;i<150;i++){
            long h=GalaxyCatalog.mix(i+9417);int x=mapX+Math.floorMod((int)h,Math.max(1,mapW)),y=mapY+Math.floorMod((int)(h>>>32),Math.max(1,mapH));
            int a=30+(int)(h&63);g.fill(x,y,x+1,y+1,(a<<24)|0xA0CBDE);
        }
        for(int i=0;i<mapH;i+=4){int x=mapX+mapW/2+(int)(Math.sin(i*.008)*mapW*.3);g.fillGradient(x-35,mapY+i,x+35,mapY+i+4,0x083DA9CA,0x04456ED1);}
        g.drawString(font,view==0?"GALAXY / LOCAL SECTORS":view==1?"SECTOR "+center.x()+":"+center.y()+":"+center.z():view==3?"DISCOVERY ARCHIVE":"SYSTEM CHART",mapX+8,mapY+8,DIM,false);
    }
    private double[] project(double x,double y,double z){
        double xx=x*Math.cos(yaw)-z*Math.sin(yaw),depth=x*Math.sin(yaw)+z*Math.cos(yaw);
        return new double[]{mapX+mapW*.5+xx*zoom,mapY+mapH*.52+(y*Math.cos(tilt)-depth*Math.sin(tilt))*zoom};
    }
    private void chart(GuiGraphics g,int mx,int my){
        double scale=Math.min(mapW,mapH)*.75/(view==0?5000:1000);
        var list=data.getList("entries",Tag.TAG_COMPOUND);
        List<Node> paint=new ArrayList<>();
        for(Tag raw:list){CompoundTag t=(CompoundTag)raw;
            double[] at=project((t.getDouble("x")-(center.x()*1000d+500))*scale,
                    (t.getDouble("y")-(center.y()*1000d+500))*scale,(t.getDouble("z")-(center.z()*1000d+500))*scale);
            Node node=new Node((int)at[0],(int)at[1],view==0?1:3,t.getString("id"),-1,t.getString("name"),t.getInt("color"),t.getBoolean("known"));paint.add(node);
        }
        for(Node n:paint){
            boolean hover=distance(mx,my,n.x,n.y)<100; boolean chosen=n.id.equals(selected);
            dot(g,n.x,n.y,n.radius,n.color,n.known||hover||chosen);
            if(n.known||hover||chosen)g.drawString(font,font.plainSubstrByWidth(n.name,110),n.x+8,n.y-4,n.known?GOLD:TEXT,false);
            if(chosen || n.id.equals(data.getString("current")))ring(g,n.x,n.y,10,0xFF75D8E8);
            nodes.add(n);
        }
        g.drawString(font,"Drag to rotate  /  Scroll to zoom",mapX+8,mapY+mapH-25,DIM,false);
        g.drawString(font,"Click a star to select; click again to enter",mapX+8,mapY+mapH-14,DIM,false);
    }
    private void system(GuiGraphics g,int mx,int my){
        CompoundTag s=data.getCompound("system");ListTag bodies=s.getList("bodies",Tag.TAG_COMPOUND);
        if(planet>=0 && planet<bodies.size()){planet(g,bodies.getCompound(planet));return;}
        double unit=Math.max(12,Math.min(mapW,mapH)*.43)/Math.max(4,bodies.size()+1);
        for(int i=0;i<bodies.size();i++){
            var b=bodies.getCompound(i);double r=(i+1.7)*unit;
            ellipse(g,r,0xFF1B3547);
            double phase=b.getDouble("phase");double[] at=project(Math.cos(phase)*r,0,Math.sin(phase)*r);
            int color=b.getInt("color");sphere(g,(int)at[0],(int)at[1],b.getString("type").equals("Gas giant")?6:4,color,b.getBoolean("rings"));
            nodes.add(new Node((int)at[0],(int)at[1],8,center.toString(),i,b.getString("name"),color,b.getBoolean("known")));
            if(b.getBoolean("known")||distance(mx,my,at[0],at[1])<100)g.drawString(font,b.getString("name"),(int)at[0]+7,(int)at[1]-5,TEXT,false);
        }
        var stars=s.getList("starsDetail",Tag.TAG_COMPOUND);
        for(int i=0;i<stars.size();i++){var star=stars.getCompound(i);double[] at=project((i-(stars.size()-1)*.5)*14,0,0);dot(g,(int)at[0],(int)at[1],7,star.getInt("color"),true);}
        g.drawString(font,"Select a planet to inspect its survey",mapX+8,mapY+mapH-14,DIM,false);
    }
    private void planet(GuiGraphics g,CompoundTag b){
        int x=mapX+mapW/2,y=mapY+mapH/2;int r=Math.max(3,(int)(Math.min(mapW,mapH)/4*zoom));
        ring(g,x,y,r+4,0x244FAFD2);sphere(g,x,y,r,b.getInt("color"),b.getBoolean("rings"));
        var moons=b.getList("moons",Tag.TAG_COMPOUND);
        for(int i=0;i<moons.size();i++){
            var m=moons.getCompound(i);double a=m.getDouble("phase"),rr=r+20+(i%2)*8;
            int xx=x+(int)(Math.cos(a)*rr),yy=y+(int)(Math.sin(a)*rr*.55);
            sphere(g,xx,yy,3,m.getInt("color"),false);
        }
        g.drawString(font,font.plainSubstrByWidth(b.getString("name"),mapW-16),mapX+8,mapY+25,GOLD,false);
        g.drawString(font,b.getBoolean("landable")?"Land with a Space Pod":"Upper-cloud travel: next phase",mapX+8,mapY+mapH-14,DIM,false);
    }
    private void results(GuiGraphics g,int mx,int my){
        var entries=data.getList("entries",Tag.TAG_COMPOUND);
        if(entries.isEmpty()){g.drawString(font,"No discoveries match this name.",mapX+10,mapY+36,TEXT,false);return;}
        int visible=Math.max(1,(mapH-35)/24),start=Math.min(scroll,Math.max(0,entries.size()-visible));
        for(int i=start;i<Math.min(entries.size(),start+visible);i++){
            var t=entries.getCompound(i);int y=mapY+29+(i-start)*24;
            g.fill(mapX+5,y-3,mapX+mapW-5,y+19,0x99213649);
            g.drawString(font,font.plainSubstrByWidth(t.getString("name"),mapW-20),mapX+10,y,GOLD,false);
            g.drawString(font,font.plainSubstrByWidth(t.getString("id")+" / "+t.getInt("planet")+"  "+t.getString("type"),mapW-20),mapX+10,y+10,DIM,false);
            nodes.add(new Node(mapX+mapW/2,y+7,mapW/2,t.getString("id"),t.getInt("planet"),t.getString("name"),0,true));
        }
    }
    private void sidebar(GuiGraphics g){
        List<String> lines=new ArrayList<>();String heading="Chart controls";
        if(view==2){
            CompoundTag sys=data.getCompound("system");var bodies=sys.getList("bodies",Tag.TAG_COMPOUND);
            if(planet>=0 && planet<bodies.size()){
                var b=bodies.getCompound(planet);heading=b.getString("name");
                lines.add(b.getString("type")+(b.getBoolean("landable")?"":" / upper clouds only"));
                lines.add(String.format(Locale.ROOT,"Gravity %.2fg",b.getDouble("gravity")));lines.add("Air: "+b.getString("atmosphere"));
                lines.add("Surface radius: "+b.getInt("radius")+" blocks");lines.add("Temperature: "+b.getInt("temperature")+" C");
                lines.add(String.format(Locale.ROOT,"Day: %.0f min / tilt %.1f deg",b.getInt("day")/1200d,b.getDouble("tilt")));
                lines.add("Moons: "+b.getList("moons",Tag.TAG_COMPOUND).size()+(b.getBoolean("rings")?" / rings":""));
                lines.add("Palette: "+b.getString("palette"));lines.add("Hazards: "+b.getString("hazard"));lines.add("Landmark: "+b.getString("landmark"));lines.add("Population: "+b.getString("population"));
                lines.add(b.getBoolean("known")?"Discovered by "+b.getString("discoverer"):"Unvisited / survey estimate");
                lines.add("Jump: orbit + piloted pod or full ki.");lines.add("Transmit: learned skill and a personal landing.");
                lines.add(center+"/"+planet);if(b.getBoolean("destroyed"))lines.add("DESTROYED / remnant");
            }else{
                heading=sys.getString("name");lines.add(center.toString());lines.add(sys.getInt("stars")+" star(s) / "+bodies.size()+" planets");
                for(Tag raw:sys.getList("starsDetail",Tag.TAG_COMPOUND)){var s=(CompoundTag)raw;lines.add(String.format(Locale.ROOT,"%s / %.2f luminosity",s.getString("type"),s.getDouble("light")));}
                lines.add("Click a planet to inspect its atmosphere, gravity and moons.");lines.add("Waypoint defaults to planet index 0.");
            }
        }else if(view==3){
            heading="Discovery archive";lines.add("Search: "+(query.isEmpty()?"all discoveries":query));
            lines.add("Select a result to inspect its system and planet.");lines.add("Scroll the list for more rows. Use Next for the following page.");
            lines.add("Only visited planets appear here. Surveying does not claim discovery.");
        }else if(!selected.isEmpty()){
            for(Tag raw:data.getList("entries",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getString("id").equals(selected)){
                heading=t.getString("name");lines.add(t.getString("id"));lines.add(t.getString("type"));lines.add(t.getInt("stars")+" star(s) / "+t.getInt("planets")+" planets");lines.add(t.getBoolean("known")?"Known system":"Uncharted system");lines.add("Click the selected star again to enter its chart.");break;
            }}
        }else{
            lines.add("Drag the chart to rotate it.");lines.add("Scroll to zoom; scroll here for details.");lines.add("Stars brighten when selected. Known systems carry labels.");
            lines.add("Find searches discovered planets only. Enter coordinates below to chart a distant sector.");lines.add("Hold a Space Pod to land. Use /galaxy home to return to Earth.");
        }
        lines.add(" ");lines.add("Waypoint: "+(data.getString("waypoint").isEmpty()?"none":data.getString("waypoint")));
        g.drawString(font,font.plainSubstrByWidth(heading,sideW-16),sideX+8,mapY+8,GOLD,false);
        g.enableScissor(sideX+4,mapY+24,sideX+sideW-4,mapY+mapH-4);
        List<String> wrapped=new ArrayList<>();for(String line:lines){
            String rest=line;while(font.width(rest)>sideW-16){String part=font.plainSubstrByWidth(rest,sideW-16);int space=part.lastIndexOf(' ');if(space>8)part=part.substring(0,space);wrapped.add(part);rest=rest.substring(part.length()).stripLeading();}wrapped.add(rest);
        }
        detailMax=Math.max(0,wrapped.size()-(mapH-30)/11);detailScroll=Mth.clamp(detailScroll,0,detailMax);
        int yy=mapY+27-detailScroll*11;
        for(String line:wrapped){g.drawString(font,line,sideX+8,yy,TEXT,false);yy+=11;}
        g.disableScissor();
    }
    private void ellipse(GuiGraphics g,double r,int c){double[] before=project(r,0,0);for(int i=1;i<=64;i++){double a=i*Math.PI/32;double[] at=project(Math.cos(a)*r,0,Math.sin(a)*r);line(g,(int)before[0],(int)before[1],(int)at[0],(int)at[1],c);before=at;}}
    private static void line(GuiGraphics g,int x,int y,int xx,int yy,int c){int n=Math.max(Math.abs(xx-x),Math.abs(yy-y));for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;int px=(int)(x+(xx-x)*t),py=(int)(y+(yy-y)*t);g.fill(px,py,px+1,py+1,c);}}
    private static void ring(GuiGraphics g,int x,int y,int r,int color){for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;line(g,x+(int)(Math.cos(a)*r),y+(int)(Math.sin(a)*r),x+(int)(Math.cos(b)*r),y+(int)(Math.sin(b)*r),color);}}
    private static void dot(GuiGraphics g,int x,int y,int r,int color,boolean bright){if(bright)ring(g,x,y,r+4,0x30000000|color);sphere(g,x,y,bright?Math.max(2,r):r,bright?color:shade(color,.55),false);if(bright)g.fill(x-1,y-1,x+2,y+2,0xFFFFF1CF);}
    private static int shade(int c,double a){return 0xFF000000|((int)(((c>>16)&255)*a)<<16)|((int)(((c>>8)&255)*a)<<8)|(int)((c&255)*a);}
    private static void sphere(GuiGraphics g,int x,int y,int r,int color,boolean rings){
        for(int yy=-r;yy<=r;yy++){int w=(int)Math.sqrt(r*r-yy*yy);for(int strip=0;strip<8;strip++){
            int a=-w+2*w*strip/8,b=-w+2*w*(strip+1)/8;double light=.22+.73*(1-strip/8d);double band=1-.08*Math.sin(yy*.5);
            g.fill(x+a,y+yy,x+Math.max(a+1,b),y+yy+1,shade(color,light*band));
        }}
        if(rings){for(int i=0;i<100;i++){double a=i*Math.PI/50,b=(i+1)*Math.PI/50;line(g,x+(int)(Math.cos(a)*r*1.5),y+(int)(Math.sin(a)*r*.3),x+(int)(Math.cos(b)*r*1.5),y+(int)(Math.sin(b)*r*.3),0xFFB5A99F);}}
    }
    private static double distance(double x,double y,double a,double b){return (x-a)*(x-a)+(y-b)*(y-b);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(button!=0||x<mapX||x>mapX+mapW||y<mapY||y>mapY+mapH)return false;
        if(view==3){for(Node n:nodes)if(Math.abs(y-n.y)<12){center=GalaxyCatalog.Id.parse(n.id);planet=n.planet;request(2);return true;}}
        Node hit=null;double closest=160;
        for(Node n:nodes){double d=distance(x,y,n.x,n.y);if(d<closest){closest=d;hit=n;}}
        if(hit!=null){
            if(view==2){planet=hit.planet;scroll=0;zoom=.7f;targetZoom=1;}else if(selected.equals(hit.id)){center=GalaxyCatalog.Id.parse(hit.id);planet=-1;request(view==0?1:2);}else{selected=hit.id;scroll=0;}
            return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&x>=mapX&&x<mapX+mapW&&y>=mapY&&y<mapY+mapH){yaw+=dx*.012f;tilt=Mth.clamp(tilt+(float)dy*.009f,.15f,1.35f);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){
        if(x>=sideX){detailScroll=Mth.clamp(detailScroll-(int)Math.signum(amount)*2,0,detailMax);return true;}
        if(view==3){scroll=Mth.clamp(scroll-(int)Math.signum(amount)*2,0,32);return true;}
        targetZoom=Mth.clamp(targetZoom+(float)amount*.18f,.45f,2.6f);
        if(targetZoom>2.2f && !waiting) {
            if((view==0||view==1)&&!selected.isEmpty()) {center=GalaxyCatalog.Id.parse(selected);planet=-1;request(view+1);}
            else if(view==2&&planet<0) {
                Node closest=null;double best=225;
                for(Node n:nodes){double d=distance(x,y,n.x,n.y);if(d<best){best=d;closest=n;}}
                if(closest!=null){planet=closest.planet;zoom=.7f;targetZoom=1;scroll=0;}
            }
        } else if(targetZoom<.5f&&!waiting&&view>0){targetZoom=1;back();}
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==257 || key==335) {
            if(coordinates.isFocused()&&go.active){go.onPress();return true;}
            if(search.isFocused()&&searchButton.active){searchButton.onPress();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    /** Scripted QA uses the real request/reply path, never fabricated chart data. */
    public boolean devView(String name) {
        if(!Boolean.getBoolean("dbzenith.devAutomation"))return false;
        if(waiting)return true;
        int want=name.contains("overview")?0:name.contains("system")||name.contains("planet")?2:name.contains("search")?3:1;
        int body=name.contains("planet")?0:-1;
        if(view==want && (want!=2 || planet==body))return false;
        planet=body;query="";request(want);return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
