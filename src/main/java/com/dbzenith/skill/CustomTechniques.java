package com.dbzenith.skill;

import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Ki Creator (v2): players design their own techniques from a shape (blast, barrage, beam, bomb, disk, laser,
 * wave, nova, rain), a method (fired, charged, placed), an origin (hand, mouth, eyes, finger, body), a ki type
 * (pure, burning, freezing, shock, corrosive, draining, divine), a power (1-5), up to two modifiers of fourteen (three
 * from level 800) and a colour, and name them. The numbers are not chosen freely: ki cost and cooldown
 * come from the median of the built-in damaging techniques (ki per point of damage, cooldown per point of damage), and
 * every modifier is taxed, so a custom technique is never more efficient than the typical built-in one.
 * Custom techniques live in per-player slots and are known as {@code custom_<slot>}.
 */
public final class CustomTechniques {
    public static final String PREFIX = "custom_";
    public static final int MAX_SLOTS = 8;
    public static final int MAX_MODS = 2, MAX_MODS_LATE = 3, THIRD_MOD_LEVEL = 800;
    /** A flag beside the modifiers (not one of them, and free): the design leaves the land alone, no craters (CX-20). */
    public static final int CALM = 1 << 30;

    /** The modifier bits of a design's {@code mods}, without the flags. */
    public static int modBits(int mods) {
        return mods & ~CALM;
    }
    public static final int NAME_LENGTH = 24;
    public static final int UNLOCK_LEVEL = 10;

    public enum Kind { BLAST, BARRAGE, BEAM, BOMB, DISK, LASER, WAVE, NOVA, RAIN;
        boolean beam() { return this == BEAM || this == LASER; }
        boolean area() { return this == NOVA || this == RAIN; }
    }

    /** How it is used: thrown at once, charged first (stronger, telegraphed), or placed as a mine. */
    public enum Method {
        FIRED(1.0), CHARGED(0.9), PLACED(0.95);

        final double tax;

        Method(double tax) {
            this.tax = tax;
        }

        public boolean fits(Kind k) {
            return switch (this) {
                case FIRED -> true;
                case CHARGED -> k == Kind.BLAST || k == Kind.BEAM || k == Kind.LASER || k == Kind.BOMB || k == Kind.DISK;
                case PLACED -> k == Kind.BLAST || k == Kind.BOMB;
            };
        }
    }

    /** Where it leaves the body: small trade-offs in size, speed and power. */
    public enum Origin {
        HAND, MOUTH, EYES, FINGER, BODY;

        public boolean fits(Kind k) {
            return switch (this) {
                case HAND -> k != Kind.NOVA;
                case MOUTH -> !k.area();
                case EYES, FINGER -> k == Kind.BLAST || k == Kind.BEAM || k == Kind.LASER || k == Kind.BARRAGE || k == Kind.DISK;
                case BODY -> k == Kind.NOVA || k == Kind.WAVE;
            };
        }
    }

    public enum Mod {
        HOMING(1.15), PIERCING(1.10), EXPLOSIVE(1.20), FAST(1.05), LARGE(1.10), SPLIT(1.15), BOUNCE(1.08), GUIDED(1.12),
        CHAIN(1.20), GUARD_BREAK(1.12), STUN(1.18), KNOCKBACK(1.06), RAPID(1.0), EFFICIENT(1.0);

        final double tax;

        Mod(double tax) {
            this.tax = tax;
        }

        /** Whether the modifier means anything on this kind (beams already pass through everything, bombs already explode...). */
        public boolean fits(Kind k) {
            return switch (this) {
                case HOMING, GUIDED -> !k.beam() && k != Kind.BOMB && !k.area();
                case PIERCING -> !k.beam() && k != Kind.BOMB && k != Kind.NOVA;
                case EXPLOSIVE -> k != Kind.BOMB && k != Kind.NOVA;
                case SPLIT, BOUNCE -> !k.beam() && !k.area();
                case FAST -> k != Kind.NOVA;
                default -> true;
            };
        }
    }

    /** A design. {@code mods} is a bit set of {@link Mod} ordinals. */
    public record Spec(String name, Kind kind, int power, int mods, int color, Method method, Origin origin, Technique.KiType type) {
        public Spec(String name, Kind kind, int power, int mods, int color) {
            this(name, kind, power, mods, color, Method.FIRED, Origin.HAND, Technique.KiType.PURE);
        }

        public Set<Mod> modSet() {
            Set<Mod> s = EnumSet.noneOf(Mod.class);
            for (Mod m : Mod.values()) if ((mods & 1 << m.ordinal()) != 0) s.add(m);
            return s;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("kind", kind.name());
            t.putInt("power", power);
            t.putInt("mods", mods);
            t.putInt("color", color);
            t.putString("method", method.name());
            t.putString("origin", origin.name());
            t.putString("type", type.name());
            return t;
        }

        public static Spec load(CompoundTag t) {
            try {
                Kind kind = Kind.valueOf(t.getString("kind"));
                Method method = t.contains("method") ? Method.valueOf(t.getString("method")) : Method.FIRED;
                Origin origin = t.contains("origin") ? Origin.valueOf(t.getString("origin")) : kind == Kind.NOVA ? Origin.BODY : Origin.HAND;
                Technique.KiType type = t.contains("type") ? Technique.KiType.valueOf(t.getString("type")) : Technique.KiType.PURE;
                return new Spec(t.getString("name"), kind, t.getInt("power"), t.getInt("mods"), t.getInt("color"), method, origin, type);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(name, NAME_LENGTH * 4);
            buf.writeEnum(kind);
            buf.writeByte(power);
            buf.writeShort(mods);
            buf.writeInt(color);
            buf.writeEnum(method);
            buf.writeEnum(origin);
            buf.writeEnum(type);
        }

        public static Spec read(FriendlyByteBuf buf) {
            return new Spec(buf.readUtf(NAME_LENGTH * 4), buf.readEnum(Kind.class), buf.readByte(), buf.readUnsignedShort(), buf.readInt(),
                    buf.readEnum(Method.class), buf.readEnum(Origin.class), buf.readEnum(Technique.KiType.class));
        }
    }

    private CustomTechniques() {}

    public static boolean isCustom(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    public static String id(int slot) {
        return PREFIX + slot;
    }

    public static int slotOf(String id) {
        if (!isCustom(id)) return -1;
        try {
            int s = Integer.parseInt(id.substring(PREFIX.length()));
            return s >= 0 && s < MAX_SLOTS ? s : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Slots open at this level: three, plus one every 300 levels. */
    public static int slots(PlayerData d) {
        return Math.min(MAX_SLOTS, 3 + StatCalculator.level(d) / 300);
    }

    /** Modifiers a design may carry: two, three from level 800. */
    public static int maxMods(PlayerData d) {
        return StatCalculator.level(d) >= THIRD_MOD_LEVEL ? MAX_MODS_LATE : MAX_MODS;
    }

    /** TP to create (or rewrite) a technique: grows with the square of its power, plus each modifier. */
    public static long tpCost(Spec s) {
        return 100 + 80L * s.power() * s.power() + 150L * Integer.bitCount(modBits(s.mods()))
                + (s.type() == Technique.KiType.PURE ? 0 : 200) + (s.method() == Method.FIRED ? 0 : 150);
    }

    // ------------------------------------------------------------------ validation

    /** The name as it will be stored: no formatting codes or control characters, at most 24 characters. */
    public static String cleanName(String raw, Kind kind) {
        StringBuilder b = new StringBuilder();
        if (raw != null) {
            for (char c : raw.toCharArray()) {
                if (c == '§' || Character.isISOControl(c)) continue;
                b.append(c);
            }
        }
        String s = b.toString().trim().replaceAll("\\s+", " ");
        if (s.length() > NAME_LENGTH) s = s.substring(0, NAME_LENGTH).trim();
        if (!s.isEmpty()) return s;
        String k = kind.name().toLowerCase();                       // the server has no translations: plain words
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    /** Why this design can't be made, or null. Clamps nothing: the client must send a valid design. */
    public static Component problem(PlayerData d, int slot, Spec s) {
        if (s == null || s.kind() == null) return Component.translatable("kicreator.dbzenith.problem.invalid");
        if (slot < 0 || slot >= slots(d)) return Component.translatable("kicreator.dbzenith.problem.slot");
        if (s.power() < 1 || s.power() > 5) return Component.translatable("kicreator.dbzenith.problem.invalid");
        if (s.method() == null || s.origin() == null || s.type() == null) return Component.translatable("kicreator.dbzenith.problem.invalid");
        if (!s.method().fits(s.kind()) || !s.origin().fits(s.kind())) return Component.translatable("kicreator.dbzenith.problem.invalid");
        if (s.type() == Technique.KiType.DIVINE && !d.hasFlag("god_ki")) return Component.translatable("kicreator.dbzenith.problem.divine");
        if (s.mods() < 0 || modBits(s.mods()) >= 1 << Mod.values().length || Integer.bitCount(modBits(s.mods())) > maxMods(d))
            return Component.translatable("kicreator.dbzenith.problem.mods");
        for (Mod m : s.modSet()) if (!m.fits(s.kind())) return Component.translatable("kicreator.dbzenith.problem.mods");
        if (StatCalculator.level(d) < UNLOCK_LEVEL) return Component.translatable("technique.dbzenith.problem.level", UNLOCK_LEVEL);
        if (d.getTrainingPoints() < tpCost(s)) return Component.translatable("technique.dbzenith.problem.tp", tpCost(s));
        return null;
    }

    /** Spend TP and store the design in the slot (replacing what was there; its mastery starts over). */
    public static Component create(PlayerData d, int slot, Spec raw) {
        Spec s = raw == null ? null : new Spec(cleanName(raw.name(), raw.kind()), raw.kind(), raw.power(), raw.mods(), raw.color() & 0xFFFFFF,
                raw.method(), raw.origin(), raw.type());
        Component why = problem(d, slot, s);
        if (why != null) return why;
        d.setTrainingPoints(d.getTrainingPoints() - tpCost(s));
        d.setCustomSpec(slot, s);
        d.setMastery(TechniqueMastery.key(build(slot, s)), 0);
        TechniqueLibrary.learnFree(d, build(slot, s));
        return null;
    }

    public static void delete(PlayerData d, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS || d.getCustomSpec(slot) == null) return;
        d.forget(id(slot));
        d.setCustomSpec(slot, null);
    }

    // ------------------------------------------------------------------ building

    private static double[] anchors;

    /** Median ki cost and cooldown per point of damage over the built-in damaging techniques (the balance yardstick). */
    static double[] anchors() {
        if (anchors == null) {
            List<Double> cost = new ArrayList<>(), cd = new ArrayList<>();
            for (Technique t : Techniques.all()) {
                if (t.style() == Technique.Style.SELF && t.effect() != Technique.Effect.EXPLOSIVE_WAVE) continue;
                if (t.damageMult() <= 0.25 || t.effect() == Technique.Effect.KI_SEAL) continue;
                double units = damageUnits(t.damageMult(), t.count());
                cost.add(t.kiCost() / units);
                cd.add(t.cooldownTicks() / units);
            }
            anchors = new double[]{median(cost), median(cd)};
        }
        return anchors;
    }

    /** Damage of one use in "technique multiplier" units; volleys are counted at 60% of shots landing. */
    static double damageUnits(double mult, int count) {
        return mult * (count > 1 ? count * 0.6 : 1);
    }

    private static double median(List<Double> v) {
        v.sort(Double::compare);
        int n = v.size();
        return n == 0 ? 1 : n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
    }

    /** The technique a design makes, as slot {@code slot}. */
    public static Technique build(int slot, Spec s) {
        int p = Math.max(1, Math.min(5, s.power()));
        Set<Mod> mods = s.modSet();
        Technique.Builder b = Technique.builder(id(slot)).color(s.color() & 0xFFFFFF);
        double mult;
        int count = 1, life, pierce = 0, hold = 0;
        float speed, size, explosion = 0;
        switch (s.kind()) {
            case BLAST -> {
                mult = 0.9 + 0.45 * p;
                speed = 1.7f;
                size = 0.45f + 0.08f * p;
                life = 60;
            }
            case BARRAGE -> {
                count = 4 + p;
                mult = 0.22 + 0.06 * p;
                speed = 1.9f;
                size = 0.35f;
                life = 50;
                b.volley(count, 7f);
            }
            case BEAM -> {
                mult = 1.8 + 0.55 * p;
                speed = 4f;
                size = 0.6f + 0.12f * p;
                life = 30 + 5 * p;
                b.style(Technique.Style.BEAM);
            }
            case LASER -> {                                          // a thin, near-instant line
                mult = 1.4 + 0.4 * p;
                speed = 9f;
                size = 0.18f + 0.03f * p;
                life = 10 + 2 * p;
                b.style(Technique.Style.BEAM);
            }
            case BOMB -> {
                mult = 3.0 + 1.8 * p;
                speed = 0.85f;
                size = 1.4f + 0.35f * p;
                life = 200;
                hold = 20 + 3 * p;
                explosion = 1.8f + 0.5f * p;
            }
            case WAVE -> {                                           // a wide, short-range spray
                count = 7 + p;
                mult = 0.18 + 0.05 * p;
                speed = 1.6f;
                size = 0.5f;
                life = 14;
                b.volley(count, 40f);
            }
            case NOVA -> {                                           // everything around you at once
                mult = 1.2 + 0.5 * p;
                speed = 0;
                size = 0;
                life = 1;
                b.style(Technique.Style.SELF).effect(Technique.Effect.EXPLOSIVE_WAVE, 4 + 0.6 * p);
            }
            case RAIN -> {                                           // falls on the spot you look at
                count = 6 + 2 * p;
                mult = 0.3 + 0.08 * p;
                speed = 1.6f;
                size = 0.5f;
                life = 60;
                b.volley(count, 0).flags(Technique.RAIN);
            }
            default -> {                                             // DISK
                mult = 0.9 + 0.35 * p;
                speed = 1.5f;
                size = 0.9f;
                life = 80;
                pierce = 2 + p / 2;
                b.style(Technique.Style.DISK);
            }
        }
        double tax = s.method().tax * typeTax(s.type());
        for (Mod m : mods) tax *= m.tax;
        tax = Math.min(tax, 1.55);                                   // stacking taxes: a design is never less than ~2/3 as efficient
        int flags = 0;
        // method
        if (s.method() == Method.CHARGED) {
            mult *= 1.5;
            flags |= Technique.CHARGED;
            if (s.kind().beam()) life += 20;
            else hold = Math.max(hold, 20 + 3 * p);
        } else if (s.method() == Method.PLACED) {
            flags |= Technique.PLACED;
            hold = 0;
            life = 200;
            speed = 0;
        }
        // origin
        switch (s.origin()) {
            case MOUTH -> mult *= 1.08;
            case EYES -> {
                size *= 0.6f;
                speed *= 1.3f;
                mult *= 0.9;
            }
            case FINGER -> {
                size *= 0.6f;
                speed *= 1.35f;
                mult *= 0.95;
                pierce += 1;
            }
            default -> { }
        }
        // modifiers
        if (mods.contains(Mod.HOMING)) {
            b.homing();
            speed *= 0.85f;
        }
        if (mods.contains(Mod.PIERCING)) pierce += 2;
        if (mods.contains(Mod.EXPLOSIVE)) explosion = Math.max(explosion, s.kind().beam() ? 2f : 1.5f);
        if (mods.contains(Mod.FAST)) speed *= 1.6f;
        if (mods.contains(Mod.LARGE)) size *= 1.5f;
        if (mods.contains(Mod.SPLIT)) flags |= Technique.SPLIT;
        if (mods.contains(Mod.BOUNCE)) flags |= Technique.BOUNCE;
        if (mods.contains(Mod.GUIDED)) flags |= Technique.GUIDED;
        if (mods.contains(Mod.CHAIN)) flags |= Technique.CHAIN;
        if (mods.contains(Mod.GUARD_BREAK)) flags |= Technique.GUARD_BREAK;
        if (mods.contains(Mod.STUN)) flags |= Technique.STUN;
        if (mods.contains(Mod.KNOCKBACK)) flags |= Technique.KNOCKBACK;
        mult *= KiTraits.damageFactor(s.type());
        double costMult = 1, cooldownMult = 1;
        if (mods.contains(Mod.RAPID)) {                              // trade punch for tempo
            mult *= 0.75;
            cooldownMult = 0.6;
        }
        if (mods.contains(Mod.EFFICIENT)) {                          // trade punch for ki
            mult *= 0.85;
            costMult = 0.75;
        }
        if (s.kind() == Kind.NOVA) b.effect(Technique.Effect.EXPLOSIVE_WAVE, 4 + 0.6 * p);
        double units = damageUnits(mult, count);
        double[] a = anchors();
        b.damage(mult).speed(speed).size(size).life(life).pierce(pierce).kiType(s.type()).flags(flags)
                .cost(Math.max(10, Math.round(units * a[0] * tax * costMult)))
                .cooldown((int) Math.max(10, Math.round(units * a[1] * tax * cooldownMult)));
        if (hold > 0) b.drop(hold);
        if (explosion > 0) b.explosion(explosion);
        b.destructive((s.mods() & CALM) == 0);                         // craters where it strikes, unless made calm (CX-20)
        return b.named(s.name(), summary(s)).build();
    }

    /** What a ki type costs on top: the plain kinds nothing, the ones that do something extra a little. */
    static double typeTax(Technique.KiType t) {
        return switch (t) {
            case PURE -> 1.05;
            case BURNING, CORROSIVE, DIVINE -> 1.10;
            case FREEZING, SHOCK -> 1.12;
            case DRAINING -> 1.15;
        };
    }

    static String summary(Spec s) {
        StringBuilder b = new StringBuilder(Component.translatable("kicreator.dbzenith.kind." + s.kind().name().toLowerCase()).getString());
        b.append(" · ").append(Component.translatable("kicreator.dbzenith.power_n", s.power()).getString());
        if (s.method() != Method.FIRED) b.append(" · ").append(Component.translatable("kicreator.dbzenith.method." + s.method().name().toLowerCase()).getString());
        if (s.origin() != Origin.HAND) b.append(" · ").append(Component.translatable("kicreator.dbzenith.origin." + s.origin().name().toLowerCase()).getString());
        if (s.type() != Technique.KiType.PURE) b.append(" · ").append(Component.translatable("kicreator.dbzenith.type." + s.type().name().toLowerCase()).getString());
        for (Mod m : s.modSet()) b.append(" · ").append(Component.translatable("kicreator.dbzenith.mod." + m.name().toLowerCase()).getString());
        return b.toString();
    }
}
