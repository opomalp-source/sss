package com.dbzenith.galaxy;

import java.util.*;

/** Version 1 is a save contract. Never change its output; add another generator version instead. */
public final class GalaxyCatalog {
    public static final int VERSION = 1, LIMIT = 1024, SYSTEMS = 8;
    public static final long SALT = 0x44425A47414C4158L;
    private GalaxyCatalog() {}

    public record Id(int x, int y, int z, int slot) {
        public Id {
            if (Math.abs((long)x) > LIMIT || Math.abs((long)y) > LIMIT || Math.abs((long)z) > LIMIT || slot < 0 || slot >= SYSTEMS&&!IconicSystems.reserved(x,y,z,slot))
                throw new IllegalArgumentException("Galaxy coordinates out of range");
        }
        public static Id parse(String text) {
            String[] p = text.split(":", -1);
            if (p.length != 4) throw new IllegalArgumentException("Use x:y:z:system (system 0-7)");
            return new Id(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        }
        @Override public String toString() { return x + ":" + y + ":" + z + ":" + slot; }
    }
    public record Ref(Id system, int planet) {
        public Ref { if (planet < 0 || planet >= 12) throw new IllegalArgumentException("Planet index out of range"); }
        public String key() { return system + "/" + planet; }
        public static Ref parse(String s) {
            String[] p = s.split("/", -1);
            if (p.length != 2) throw new IllegalArgumentException("Use x:y:z:system/planet");
            return new Ref(Id.parse(p[0]), Integer.parseInt(p[1]));
        }
    }
    public enum StarType {
        RED_DWARF(0xFF8067, .45, .2), YELLOW(0xFFE6A3, 1, 1), BLUE_GIANT(0x8BC8FF, 5, 18),
        WHITE_DWARF(0xD8E8FF, .25, .5), NEUTRON_STAR(0xBCA8FF, .12, 3);
        public final int color; public final double size, light;
        StarType(int color, double size, double light) { this.color=color; this.size=size; this.light=light; }
    }
    public enum Atmosphere { NONE, THIN, BREATHABLE, TOXIC, SCORCHING }
    public enum Kind {
        TEMPERATE(0x5CBA8B, "grass / stone / water"), OCEAN(0x428BC9, "sand / coral / water"),
        DESERT(0xDFAB69, "sand / sandstone"), ICE(0xA2DBEF, "snow / packed ice"),
        VOLCANIC(0xE66F43, "basalt / ash / lava"), JUNGLE(0x418E60, "moss / jungle / water"),
        BARREN(0x9A8A80, "stone / gravel / dust"), CRYSTAL(0xAF8DEB, "calcite / crystal"),
        TOXIC_SWAMP(0x97AD46, "mud / dark stone / brine"), GAS_GIANT(0xC894B4, "upper cloud bands"),
        TRAINING(0xD19764, "dense stone / sparse grass"), ASTEROIDS(0x999DA9, "rock / iron / ice");
        public final int color; public final String palette;
        Kind(int color, String palette) { this.color=color; this.palette=palette; }
        public boolean landable() { return this != GAS_GIANT; }
    }
    public record Star(StarType type, double radius, double luminosity, double phase) {}
    public record Moon(String name, long seed, Kind kind, int radius, double gravity, double orbit, double phase) {}
    public record Body(int index, String name, long seed, Kind kind, int radius, double gravity, Atmosphere atmosphere,
                       int temperature, int dayTicks, double tilt, boolean rings, double orbit, double phase,
                       List<Moon> moons, String hazard, String landmark, String population) {}
    public record SystemSpec(Id id, String name, double x, double y, double z, List<Star> stars, List<Body> planets) {}
    public static final Id EARTH = new Id(0,0,0,0), NAMEK = new Id(12,-3,6,1), NORTH = new Id(-9,4,2,0), BEERUS = new Id(40,8,-17,3);
    private static final String[] START={"Al","Ve","Ka","Or","Shi","Na","Te","Zu","Io","Ar","Ma","Ry"};
    private static final String[] END={"ria","dor","nus","tha","ion","ara","esh","ora","vik","eus","une","ath"};
    public static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    public static long seed(long worldSeed, long salt, Id id) {
        return mix(mix(worldSeed ^ salt) ^ mix(id.x * 0x9E3779B97F4A7C15L) ^ Long.rotateLeft(mix(id.y),21)
                ^ Long.rotateLeft(mix(id.z),42) ^ mix(id.slot + 0x53544152L));
    }
    private static String name(Random r) { return START[r.nextInt(START.length)] + END[r.nextInt(END.length)]; }
    /**
     * A system's star part (the space rework, phase 2): its name, galactic position and stars, exactly as
     * {@link #system} draws them, without generating planets. What the sky and star selection use.
     */
    public record Head(Id id, String name, double x, double y, double z, List<Star> stars) {}
    public static Head head(long worldSeed, long salt, Id id) {
        if(IconicSystems.isNew(id)){var s=IconicSystems.system(worldSeed,salt,id);return new Head(id,s.name(),s.x(),s.y(),s.z(),s.stars());}
        Head h=head(new Random(seed(worldSeed, salt, id)), id);
        String shown=id.equals(EARTH)?"Earth system":id.equals(NAMEK)?"Namek system":id.equals(NORTH)?"North Galaxy outpost":id.equals(BEERUS)?"Beerus's system":h.name;
        return new Head(id,shown,h.x,h.y,h.z,h.stars);
    }
    /** The first draws of a system's Random: name, position, stars (shared by {@link #system} and {@link #head}; v1 contract). */
    private static Head head(Random r, Id id) {
        String name = name(r) + " " + (100 + r.nextInt(900));
        double x=id.x * 1000d + r.nextDouble()*800+100, y=id.y*1000d+r.nextDouble()*800+100, z=id.z*1000d+r.nextDouble()*800+100;
        int count = r.nextInt(12) == 0 ? 3 : r.nextInt(4) == 0 ? 2 : 1;
        if (id.equals(NAMEK)) count=3;
        if (id.equals(EARTH)) count=1;
        List<Star> stars=new ArrayList<>();
        for(int i=0;i<count;i++) {
            StarType t=StarType.values()[r.nextInt(StarType.values().length)];
            if(id.equals(EARTH) && i==0) t=StarType.YELLOW;
            double size=t.size*(.8+r.nextDouble()*.4);
            stars.add(new Star(t, id.equals(EARTH)?1:size, t.light, r.nextDouble()*Math.PI*2));
        }
        return new Head(id,name,x,y,z,List.copyOf(stars));
    }
    public static SystemSpec system(long worldSeed, long salt, Id id) {
        if(IconicSystems.isNew(id))return IconicSystems.system(worldSeed,salt,id);
        Random r = new Random(seed(worldSeed, salt, id));
        Head head=head(r,id);
        String name=head.name;double x=head.x,y=head.y,z=head.z;List<Star> stars=head.stars;
        int n=3+r.nextInt(7);
        List<Body> bodies=new ArrayList<>();
        for(int i=0;i<n;i++) {
            long bodySeed=mix(seed(worldSeed,salt,id) ^ (0x504C414E4554L+i));
            Random b=new Random(bodySeed);
            Kind k=Kind.values()[b.nextInt(Kind.values().length)];
            String bodyName=name + " " + (i+1);
            if(i==0 && id.equals(EARTH)) { k=Kind.TEMPERATE; bodyName="Earth"; name="Earth system"; }
            if(i==0 && id.equals(NAMEK)) { k=Kind.OCEAN; bodyName="Namek"; name="Namek system"; }
            if(i==0 && id.equals(NORTH)) { k=Kind.TRAINING; bodyName="King Kai's Planet"; name="North Galaxy outpost"; }
            if(i==0 && id.equals(BEERUS)) { k=Kind.TRAINING; bodyName="Beerus's Planet"; name="Beerus's system"; }
            Atmosphere air=switch(k) {
                case BARREN, ASTEROIDS -> Atmosphere.NONE;
                case TOXIC_SWAMP, GAS_GIANT -> Atmosphere.TOXIC;
                case VOLCANIC -> Atmosphere.SCORCHING;
                case ICE, DESERT, CRYSTAL -> b.nextBoolean()? Atmosphere.THIN:Atmosphere.BREATHABLE;
                default -> Atmosphere.BREATHABLE;
            };
            int radius=k==Kind.TRAINING?256+b.nextInt(4)*128:2048+b.nextInt(15)*1024;
            double gravity=k==Kind.TRAINING? 5+b.nextInt(16): k==Kind.ASTEROIDS?.05:Math.round((.25+b.nextDouble()*2.25)*100)/100d;
            if(i==0 && (id.equals(EARTH)||id.equals(NAMEK)||id.equals(BEERUS))) { gravity=1; air=Atmosphere.BREATHABLE; }
            if(i==0 && id.equals(NORTH)) gravity=10;
            int temp=switch(k) { case ICE->-90; case VOLCANIC->350; case DESERT->55; case GAS_GIANT->-120; default->-15+b.nextInt(55); };
            List<Moon> moons=new ArrayList<>();
            int m=k==Kind.ASTEROIDS?0:b.nextInt(6);
            for(int j=0;j<m;j++) moons.add(new Moon(bodyName+" / "+(char)('A'+j), mix(bodySeed+j+1),
                    j%3==0?Kind.ICE:Kind.BARREN,256+b.nextInt(8)*128,.08+b.nextDouble()*.45,1.4+j*.6,b.nextDouble()*Math.PI*2));
            if(i==0 && id.equals(EARTH)) {
                radius=29999984; temp=15;
                moons=List.of(new Moon("Moon",mix(bodySeed+1),Kind.BARREN,1737,.165,1.4,0));
            }
            String hazard=switch(k) { case VOLCANIC->"Lava fissures / quakes"; case TOXIC_SWAMP->"Acid rain"; case ICE->"Extreme cold";
                case DESERT->"Dust storms"; case ASTEROIDS->"Meteor debris"; case GAS_GIANT->"Ion storms"; default->"Occasional meteor showers"; };
            int day=12000+b.nextInt(12)*4000; double tilt=b.nextDouble()*40;boolean rings=b.nextInt(5)==0;
            if(i==0 && id.equals(EARTH)) { day=24000;tilt=23.4;rings=false; }
            bodies.add(new Body(i,bodyName,bodySeed,k,radius,gravity,air,temp,day,tilt,
                    rings,2+i*1.7,b.nextDouble()*Math.PI*2,List.copyOf(moons),hazard,
                    new String[]{"Ancient ruins","Crashed ship","Crystal spires","Remote master","Abandoned base"}[b.nextInt(5)],
                    new String[]{"Wildlife","Hostile scouts","Remote settlers","Uninhabited"}[b.nextInt(4)]));
        }
        return new SystemSpec(id,name,x,y,z,List.copyOf(stars),List.copyOf(bodies));
    }
    public static Body body(long seed,long salt,Ref ref) {
        var s=system(seed,salt,ref.system);
        if(ref.planet>=s.planets.size()) throw new IllegalArgumentException("That planet does not exist");
        return s.planets.get(ref.planet);
    }
    public static String label(Enum<?> e) {
        String s=e.name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }
}
