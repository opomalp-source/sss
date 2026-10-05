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
 * The Ki Creator: players design their own techniques from a kind (blast, barrage, beam, bomb, disk), a power
 * (1-5), up to two modifiers and a colour, and name them. The numbers are not chosen freely: ki cost and cooldown
 * come from the median of the built-in damaging techniques (ki per point of damage, cooldown per point of damage), and
 * every modifier is taxed, so a custom technique is never more efficient than the typical built-in one.
 * Custom techniques live in per-player slots and are known as {@code custom_<slot>}.
 */
public final class CustomTechniques {
    public static final String PREFIX = "custom_";
    public static final int MAX_SLOTS = 8;
    public static final int MAX_MODS = 2;
    public static final int NAME_LENGTH = 24;
    public static final int UNLOCK_LEVEL = 10;

    public enum Kind { BLAST, BARRAGE, BEAM, BOMB, DISK }

    public enum Mod {
        HOMING(1.15), PIERCING(1.10), EXPLOSIVE(1.20), FAST(1.05), LARGE(1.10);

        final double tax;

        Mod(double tax) {
            this.tax = tax;
        }

        /** Whether the modifier means anything on this kind (beams already pass through everything, bombs already explode...). */
        public boolean fits(Kind k) {
            return switch (this) {
                case HOMING -> k != Kind.BEAM && k != Kind.BOMB;
                case PIERCING -> k != Kind.BEAM && k != Kind.BOMB;
                case EXPLOSIVE -> k != Kind.BOMB;
                default -> true;
            };
        }
    }

    /** A design. {@code mods} is a bit set of {@link Mod} ordinals. */
    public record Spec(String name, Kind kind, int power, int mods, int color) {
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
            return t;
        }

        public static Spec load(CompoundTag t) {
            try {
                return new Spec(t.getString("name"), Kind.valueOf(t.getString("kind")), t.getInt("power"), t.getInt("mods"), t.getInt("color"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(name, NAME_LENGTH * 4);
            buf.writeEnum(kind);
            buf.writeByte(power);
            buf.writeByte(mods);
            buf.writeInt(color);
        }

        public static Spec read(FriendlyByteBuf buf) {
            return new Spec(buf.readUtf(NAME_LENGTH * 4), buf.readEnum(Kind.class), buf.readByte(), buf.readByte(), buf.readInt());
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

    /** TP to create (or rewrite) a technique: grows with the square of its power, plus each modifier. */
    public static long tpCost(Spec s) {
        return 100 + 80L * s.power() * s.power() + 150L * Integer.bitCount(s.mods());
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
        if (s.mods() < 0 || s.mods() >= 1 << Mod.values().length || Integer.bitCount(s.mods()) > MAX_MODS)
            return Component.translatable("kicreator.dbzenith.problem.mods");
        for (Mod m : s.modSet()) if (!m.fits(s.kind())) return Component.translatable("kicreator.dbzenith.problem.mods");
        if (StatCalculator.level(d) < UNLOCK_LEVEL) return Component.translatable("technique.dbzenith.problem.level", UNLOCK_LEVEL);
        if (d.getTrainingPoints() < tpCost(s)) return Component.translatable("technique.dbzenith.problem.tp", tpCost(s));
        return null;
    }

    /** Spend TP and store the design in the slot (replacing what was there; its mastery starts over). */
    public static Component create(PlayerData d, int slot, Spec raw) {
        Spec s = raw == null ? null : new Spec(cleanName(raw.name(), raw.kind()), raw.kind(), raw.power(), raw.mods(), raw.color() & 0xFFFFFF);
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
        int count = 1;
        float speed, size;
        switch (s.kind()) {
            case BLAST -> {
                mult = 0.9 + 0.45 * p;
                speed = 1.7f;
                size = 0.45f + 0.08f * p;
                b.life(60);
            }
            case BARRAGE -> {
                count = 4 + p;
                mult = 0.22 + 0.06 * p;
                speed = 1.9f;
                size = 0.35f;
                b.volley(count, 7f).life(50);
            }
            case BEAM -> {
                mult = 1.8 + 0.55 * p;
                speed = 4f;
                size = 0.6f + 0.12f * p;
                b.style(Technique.Style.BEAM).life(30 + 5 * p);
            }
            case BOMB -> {
                mult = 3.0 + 1.8 * p;
                speed = 0.85f;
                size = 1.4f + 0.35f * p;
                b.drop(20 + 3 * p).explosion(1.8f + 0.5f * p).life(200);
            }
            default -> {                                             // DISK
                mult = 0.9 + 0.35 * p;
                speed = 1.5f;
                size = 0.9f;
                b.style(Technique.Style.DISK).pierce(2 + p / 2).life(80);
            }
        }
        double tax = 1;
        for (Mod m : mods) tax *= m.tax;
        if (mods.contains(Mod.HOMING)) {
            b.homing();
            speed *= 0.85f;
        }
        if (mods.contains(Mod.PIERCING)) b.pierce((s.kind() == Kind.DISK ? 2 + p / 2 : 0) + 2);
        if (mods.contains(Mod.EXPLOSIVE)) b.explosion(Math.max(1.5f, s.kind() == Kind.BEAM ? 2f : 1.5f));
        if (mods.contains(Mod.FAST)) speed *= 1.6f;
        if (mods.contains(Mod.LARGE)) size *= 1.5f;
        double units = damageUnits(mult, count);
        double[] a = anchors();
        b.damage(mult).speed(speed).size(size)
                .cost(Math.max(10, Math.round(units * a[0] * tax)))
                .cooldown((int) Math.max(10, Math.round(units * a[1] * tax)));
        return b.named(s.name(), summary(s)).build();
    }

    static String summary(Spec s) {
        StringBuilder b = new StringBuilder(Component.translatable("kicreator.dbzenith.kind." + s.kind().name().toLowerCase()).getString());
        b.append(" · ").append(Component.translatable("kicreator.dbzenith.power_n", s.power()).getString());
        for (Mod m : s.modSet()) b.append(" · ").append(Component.translatable("kicreator.dbzenith.mod." + m.name().toLowerCase()).getString());
        return b.toString();
    }
}
