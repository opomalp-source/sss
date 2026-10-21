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
        if(view<0 || view>SEARCH || query.length()>32 || cursor<0) throw new IllegalArgumentException("Invalid survey request");
        GalaxyData data=GalaxyData.get(p.server); long seed=p.server.overworld().getSeed();
        CompoundTag out=new CompoundTag(); out.putInt("view",view); out.putString("center",center.toString()); out.putInt("planet",planet);
        out.putString("current",GalaxyData.current(p).toString()); out.putInt("version",GalaxyCatalog.VERSION);
        out.putBoolean("canTransmit",com.dbzenith.race.RacialSkills.has(com.dbzenith.data.ModCapabilities.getOrThrow(p),"instant_transmission"));
        var wp=data.waypoint(p.getUUID()); out.putString("waypoint",wp==null?"":wp.key());
        ListTag entries=new ListTag();

        if(view==OVERVIEW) {
            for(int dx=-2;dx<=2;dx++) for(int dy=-2;dy<=2;dy++) {
                int x=center.x()+dx,y=center.y()+dy;
                if(Math.abs(x)>GalaxyCatalog.LIMIT || Math.abs(y)>GalaxyCatalog.LIMIT) continue;
                for(int i=0;i<GalaxyCatalog.SYSTEMS;i++) entries.add(seen(p,data,summary(GalaxyCatalog.system(seed,data.salt(),new GalaxyCatalog.Id(x,y,center.z(),i)),data)));
            }
        } else if(view==SECTOR) {
            for(int i=0;i<GalaxyCatalog.SYSTEMS;i++) entries.add(seen(p,data,summary(GalaxyCatalog.system(seed,data.salt(),new GalaxyCatalog.Id(center.x(),center.y(),center.z(),i)),data)));
        } else if(view==SYSTEM) {
            var s=GalaxyCatalog.system(seed,data.salt(),center);var system=seen(p,data,detail(s,data));for(Tag raw:system.getList("bodies",Tag.TAG_COMPOUND)){var b=(CompoundTag)raw;b.putBoolean("visited",com.dbzenith.data.ModCapabilities.getOrThrow(p).hasFlag("planet_landing:"+new GalaxyCatalog.Ref(center,b.getInt("index")).key()));}out.put("system",system);
        } else {
            var page=searchPage(data,seed,query,cursor);out.merge(page);entries=page.getList("entries",Tag.TAG_COMPOUND);
        }
        out.put("entries",entries); return out;
    }
    public static CompoundTag searchPage(GalaxyData data,long seed,String query,int cursor){
        if(query.length()>32||cursor<0)throw new IllegalArgumentException("Invalid discovery search");
        var out=new CompoundTag();var entries=new ListTag();
        String needle=query.strip().toLowerCase(Locale.ROOT);
        int examined=0,next=Math.min(cursor,data.discoveryCount());
        while(next<data.discoveryCount()&&examined<512&&entries.size()<32){
            var ref=data.discoveryAt(next++);examined++;var discovery=data.discovery(ref);
            String name=data.name(ref,discovery.catalogName());
            if(!name.toLowerCase(Locale.ROOT).contains(needle)&&!ref.key().contains(needle))continue;
            var body=GalaxyCatalog.body(seed,data.salt(),ref);CompoundTag t=new CompoundTag();t.putString("id",ref.system().toString());t.putInt("planet",ref.planet());t.putString("name",name);
            t.putString("type",GalaxyCatalog.label(body.kind()));t.putString("discoverer",discovery.discoverer());t.putBoolean("destroyed",data.destroyed(ref));entries.add(t);
        }
        out.putBoolean("more",next<data.discoveryCount());out.putInt("nextCursor",next);out.putInt("examined",examined);out.putInt("cursor",cursor);out.putString("query",query);
        out.put("entries",entries);return out;
    }
    private static CompoundTag summary(GalaxyCatalog.SystemSpec s,GalaxyData data) {
        CompoundTag t=new CompoundTag(); t.putString("id",s.id().toString()); t.putString("name",s.name());
        t.putDouble("x",s.x());t.putDouble("y",s.y());t.putDouble("z",s.z());
        t.putInt("color",s.stars().get(0).type().color); t.putInt("stars",s.stars().size()); t.putInt("planets",s.planets().size());
        t.putString("type",GalaxyCatalog.label(s.stars().get(0).type()));
        t.putBoolean("known",data.knownSystem(s.id())); return t;
    }
    private static CompoundTag detail(GalaxyCatalog.SystemSpec s,GalaxyData data) {
        CompoundTag t=summary(s,data); ListTag stars=new ListTag();
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
            if(s.id().equals(IconicSystems.VEGETA)&&b.index()==0)body.putInt("color",0xB54B3B);
            body.putInt("day",b.dayTicks());body.putDouble("tilt",b.tilt());body.putBoolean("rings",b.rings());body.putDouble("orbit",b.orbit());body.putDouble("phase",b.phase());
            body.putBoolean("landable",b.kind().landable()&&!data.destroyed(ref));body.putString("palette",b.kind().palette);body.putString("hazard",b.hazard());
            body.putString("landmark",b.landmark());body.putString("population",b.population());body.putBoolean("known",d!=null);body.putString("lore",IconicSystems.lore(ref));
            body.putString("discoverer",d==null?"":d.discoverer()); body.putBoolean("destroyed",data.destroyed(ref));
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
            if(r.action()==11){com.dbzenith.galaxy.space.Interstellar.cancel(p);return;}
            if(r.action()==10){if(IconicSystems.all().contains(center))ModNetwork.sendTo(p,new GalaxyPackets.Data(0,true,survey(p,SYSTEM,center,0,"",0)));return;}
            if(r.action()==8){com.dbzenith.galaxy.space.Interstellar.begin(p,new GalaxyCatalog.Ref(center,r.planet()));return;}
            if(r.action()==9){com.dbzenith.galaxy.space.Interstellar.transmit(p,new GalaxyCatalog.Ref(center,r.planet()));return;}
            if(r.action()==7){if(p.getVehicle() instanceof com.dbzenith.world.SpacePodEntity pod)pod.launchOrbit(p);return;}
            if(r.action()==6){com.dbzenith.galaxy.planet.PlanetRuntime.travel(p,new GalaxyCatalog.Ref(center,r.planet()));return;}
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
    /** Opens the atlas on a star's sector with that star selected (star navigation's map shortcut; the caller checked the star). */
    public static void openAt(ServerPlayer p,GalaxyCatalog.Id id){
        var t=survey(p,SECTOR,id,-1,"",0);t.putString("focus",id.toString());ModNetwork.sendTo(p,new GalaxyPackets.Data(0,true,t));
    }

    /** A system's state for a player (the space rework, phase 2): its planets are known only once it is charted. */
    public static final String UNOBSERVED="unobserved",OBSERVED="observed",CHARTED="charted";
    public static String state(ServerPlayer p,GalaxyData data,GalaxyCatalog.Id id){
        if(data.knownSystem(id)||data.charted(p.getUUID(),id)||id.equals(GalaxyData.current(p))||IconicSystems.all().contains(id)||p.isCreative())return CHARTED;
        return data.observed(p.getUUID(),id)?OBSERVED:UNOBSERVED;
    }
    /**
     * Marks a survey entry with the player's state for its system. An uncharted system shows its stars (anyone can see
     * those) but neither its planets nor their count: those wait until someone has been there.
     */
    private static CompoundTag seen(ServerPlayer p,GalaxyData data,CompoundTag t){
        var id=GalaxyCatalog.Id.parse(t.getString("id"));String state=state(p,data,id);t.putString("state",state);
        boolean charted=CHARTED.equals(state);t.putBoolean("charted",charted);
        if(!charted){t.putInt("planets",-1);if(t.contains("bodies"))t.put("bodies",new ListTag());}
        return t;
    }
}
