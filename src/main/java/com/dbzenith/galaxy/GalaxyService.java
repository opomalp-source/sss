package com.dbzenith.galaxy;

import com.dbzenith.network.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** All survey results originate on the server. No world seed or terrain seed crosses the wire. */
public final class GalaxyService {
    public static final int OVERVIEW=0, SECTOR=1, SYSTEM=2, SEARCH=3, WAYPOINT=4, CLEAR_WAYPOINT=5;
    private GalaxyService() {}
    public static CompoundTag survey(ServerPlayer p,int view,GalaxyCatalog.Id center,int planet,String query,int cursor) {
        if(view<0 || view>SEARCH || query.length()>32 || cursor<0 || cursor>100000) throw new IllegalArgumentException("Invalid survey request");
        GalaxyData data=GalaxyData.get(p.server); long seed=p.server.overworld().getSeed();
        CompoundTag out=new CompoundTag(); out.putInt("view",view); out.putString("center",center.toString()); out.putInt("planet",planet);
        out.putString("current",GalaxyData.current(p).toString()); out.putInt("version",GalaxyCatalog.VERSION);
        var wp=data.waypoint(p.getUUID()); out.putString("waypoint",wp==null?"":wp.key());
        ListTag entries=new ListTag();
        Set<GalaxyCatalog.Id> known=knownSystems(data);
        if(view==OVERVIEW) {
            for(int dx=-2;dx<=2;dx++) for(int dy=-2;dy<=2;dy++) {
                int x=center.x()+dx,y=center.y()+dy;
                if(Math.abs(x)>GalaxyCatalog.LIMIT || Math.abs(y)>GalaxyCatalog.LIMIT) continue;
                for(int i=0;i<GalaxyCatalog.SYSTEMS;i++) entries.add(summary(GalaxyCatalog.system(seed,data.salt(),new GalaxyCatalog.Id(x,y,center.z(),i)),known));
            }
        } else if(view==SECTOR) {
            for(int i=0;i<GalaxyCatalog.SYSTEMS;i++) entries.add(summary(GalaxyCatalog.system(seed,data.salt(),new GalaxyCatalog.Id(center.x(),center.y(),center.z(),i)),known));
        } else if(view==SYSTEM) {
            var s=GalaxyCatalog.system(seed,data.salt(),center); out.put("system",detail(s,data));
        } else {
            String needle=query.strip().toLowerCase(Locale.ROOT);
            int skipped=0;
            var sorted=data.discoveries().entrySet();
            boolean more=false;
            for(var e:sorted) {
                var ref=e.getKey();
                String name=data.name(ref,e.getValue().catalogName());
                if(!name.toLowerCase(Locale.ROOT).contains(needle) && !ref.key().contains(needle)) continue;
                if(skipped++<cursor) continue;
                if(entries.size()>=32) { more=true; break; }
                var b=GalaxyCatalog.body(seed,data.salt(),ref);
                CompoundTag t=new CompoundTag(); t.putString("id",ref.system().toString()); t.putInt("planet",ref.planet()); t.putString("name",name);
                t.putString("type",GalaxyCatalog.label(b.kind())); t.putString("discoverer",e.getValue().discoverer()); t.putBoolean("destroyed",e.getValue().destroyed()); entries.add(t);
            }
            out.putBoolean("more",more); out.putInt("cursor",cursor); out.putString("query",query);
        }
        out.put("entries",entries); return out;
    }
    private static Set<GalaxyCatalog.Id> knownSystems(GalaxyData data) {
        Set<GalaxyCatalog.Id> result=new HashSet<>();
        for(var ref:data.discoveries().keySet())result.add(ref.system());
        return result;
    }
    private static CompoundTag summary(GalaxyCatalog.SystemSpec s,Set<GalaxyCatalog.Id> known) {
        CompoundTag t=new CompoundTag(); t.putString("id",s.id().toString()); t.putString("name",s.name());
        t.putDouble("x",s.x());t.putDouble("y",s.y());t.putDouble("z",s.z());
        t.putInt("color",s.stars().get(0).type().color); t.putInt("stars",s.stars().size()); t.putInt("planets",s.planets().size());
        t.putString("type",GalaxyCatalog.label(s.stars().get(0).type()));
        t.putBoolean("known",known.contains(s.id())); return t;
    }
    private static CompoundTag detail(GalaxyCatalog.SystemSpec s,GalaxyData data) {
        CompoundTag t=summary(s,knownSystems(data)); ListTag stars=new ListTag();
        for(var star:s.stars()) {
            CompoundTag st=new CompoundTag(); st.putString("type",GalaxyCatalog.label(star.type())); st.putInt("color",star.type().color);
            st.putDouble("radius",star.radius()); st.putDouble("light",star.luminosity()); st.putDouble("phase",star.phase()); stars.add(st);
        }
        t.put("starsDetail",stars); ListTag bodies=new ListTag();
        for(var b:s.planets()) {
            var ref=new GalaxyCatalog.Ref(s.id(),b.index()); var d=data.discovery(ref);
            CompoundTag body=new CompoundTag(); body.putInt("index",b.index()); body.putString("name",data.name(ref,b.name()));
            body.putString("type",GalaxyCatalog.label(b.kind())); body.putInt("color",b.kind().color); body.putInt("radius",b.radius());
            body.putDouble("gravity",b.gravity());body.putString("atmosphere",GalaxyCatalog.label(b.atmosphere()));body.putInt("temperature",b.temperature());
            body.putInt("day",b.dayTicks());body.putDouble("tilt",b.tilt());body.putBoolean("rings",b.rings());body.putDouble("orbit",b.orbit());body.putDouble("phase",b.phase());
            body.putBoolean("landable",b.kind().landable());body.putString("palette",b.kind().palette);body.putString("hazard",b.hazard());
            body.putString("landmark",b.landmark());body.putString("population",b.population());body.putBoolean("known",d!=null);
            body.putString("discoverer",d==null?"":d.discoverer()); body.putBoolean("destroyed",d!=null && d.destroyed());
            ListTag moons=new ListTag();
            for(var moon:b.moons()) {
                CompoundTag m=new CompoundTag();m.putString("name",moon.name());m.putInt("color",moon.kind().color);m.putInt("radius",moon.radius());
                m.putDouble("gravity",moon.gravity());m.putDouble("orbit",moon.orbit());m.putDouble("phase",moon.phase());m.putString("type",GalaxyCatalog.label(moon.kind()));moons.add(m);
            }
            body.put("moons",moons);bodies.add(body);
        }
        t.put("bodies",bodies);return t;
    }
    public static void request(ServerPlayer p,GalaxyPackets.Request r) {
        if(!InputGuard.allow(p,InputGuard.Kind.GALAXY)) return;
        try {
            var center=GalaxyCatalog.Id.parse(r.id());
            if(r.action()==WAYPOINT || r.action()==CLEAR_WAYPOINT) {
                var data=GalaxyData.get(p.server);
                if(r.action()==CLEAR_WAYPOINT) data.waypoint(p.getUUID(),null);
                else { var ref=new GalaxyCatalog.Ref(center,r.planet());GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref);data.waypoint(p.getUUID(),ref); }
                ModNetwork.sendTo(p,new GalaxyPackets.Data(r.sequence(),false,survey(p,SYSTEM,center,r.planet(),"",0)));return;
            }
            ModNetwork.sendTo(p,new GalaxyPackets.Data(r.sequence(),false,survey(p,r.action(),center,r.planet(),r.query(),r.cursor())));
        } catch(IllegalArgumentException ex) { InputGuard.sane(p,false,"galaxy coordinates"); }
    }
    public static void open(ServerPlayer p) {
        if(!InputGuard.allow(p,InputGuard.Kind.GALAXY))return;
        ModNetwork.sendTo(p,new GalaxyPackets.Data(0,true,survey(p,SECTOR,GalaxyData.current(p),-1,"",0)));
    }
}
