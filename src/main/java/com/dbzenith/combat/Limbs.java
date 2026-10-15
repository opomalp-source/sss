package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.LimbsPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Limbs (CX-53, the user: "a system with limbs and how damaged they can be; damaged a lot they can't be used; they can
 * even be cut off in battle"). Every fighter (players and NPC fighters) has two arms and two legs, each with its own
 * health (0..100):
 * <ul>
 *   <li><b>Hit:</b> about half the blows that land also hurt a limb, chosen by where the blow came from: the side the
 *       attacker is on, and high (arms) or low (legs). The bigger the blow against the fighter's health, the more.</li>
 *   <li><b>Broken</b> at 0: an arm hangs limp and a leg drags. Broken limbs mend slowly on their own (from 15 seconds
 *       after the last blow), back in use from a quarter.</li>
 *   <li><b>Cut off:</b> a broken (or nearly broken) limb hit by a cutting attack (a blade, a disc of ki like the
 *       Destructo Disc) or by an overwhelming blow (a third of the fighter's health at once) is lost. It stays lost
 *       (see CX-54 for prosthetics and regrowing).</li>
 *   <li><b>What it costs:</b> one arm out, punches 30% weaker and ki 20% weaker; both arms out, kicks only (half
 *       strength) and no ki techniques. One leg out, 35% slower, no sprinting, a weaker jump; both legs out, crawling
 *       (85% slower, no jumping); flying still works.</li>
 * </ul>
 * State lives in the entity's persistent data (so it comes back with relogging, and a death leaves it behind), and
 * goes to clients in {@link LimbsPacket}.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Limbs {
    public enum Limb {
        RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG;

        public boolean arm() {
            return this == RIGHT_ARM || this == LEFT_ARM;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Back in use from this much health. */
    public static final int USABLE = 25;
    /** A lost limb's wound: open (it bleeds until closed), bandaged, seared shut with ki, healed over (a closed stump). */
    public static final int OPEN = 0, BANDAGED = 1, SEARED = 2, HEALED = 3;
    /** Bandaged or seared, a stump has healed over after five minutes. */
    private static final int HEAL_OVER = 6000;
    private static final String TAG = "dbzenith_limbs";
    private static final UUID SLOW = UUID.fromString("c53d7a10-3e1b-4c5a-9e1d-0c53ab7e0001");

    /** One fighter's limbs. */
    public static final class State {
        public final byte[] health = {100, 100, 100, 100};
        public int severed, prosthetic;
        /** Bleeding limbs (a bit mask; the knocked-out rework): fresh cuts, until {@link #bleedUntil}. */
        public int bleeding;
        final long[] bleedUntil = new long[4];
        /** A lost limb's wound (the limbs rework): open, bandaged, seared shut, or healed over; and since when. */
        public final byte[] stump = new byte[4];
        final long[] stumpSince = new long[4];

        public int stump(Limb l) {
            return stump[l.ordinal()];
        }
        long lastHit;
        /** When each lost limb grows back (Namekians, Majins; 0 not set yet). */
        final long[] regrowAt = new long[4];

        public int health(Limb l) {
            return health[l.ordinal()];
        }

        public boolean severed(Limb l) {
            return (severed & 1 << l.ordinal()) != 0;
        }

        public boolean prosthetic(Limb l) {
            return (prosthetic & 1 << l.ordinal()) != 0;
        }

        public boolean bleeding(Limb l) {
            return (bleeding & 1 << l.ordinal()) != 0;
        }

        /** The right arm gone (no prosthetic) and the left still good: fights, fires and poses with the left (the limbs rework). */
        public boolean leftHanded() {
            return severed(Limb.RIGHT_ARM) && !prosthetic(Limb.RIGHT_ARM) && works(Limb.LEFT_ARM);
        }

        /** Can this limb be used (not cut off, or replaced, and not broken)? */
        public boolean works(Limb l) {
            if (severed(l)) return prosthetic(l) && health(l) >= USABLE;
            return health(l) >= USABLE;
        }

        /** Broken but still there (it hangs or drags). */
        public boolean broken(Limb l) {
            return !severed(l) && health(l) < USABLE;
        }

        public int armsOut() {
            return (works(Limb.RIGHT_ARM) ? 0 : 1) + (works(Limb.LEFT_ARM) ? 0 : 1);
        }

        public int legsOut() {
            return (works(Limb.RIGHT_LEG) ? 0 : 1) + (works(Limb.LEFT_LEG) ? 0 : 1);
        }

        /** On the ground with a leg gone (and no prosthetic in its place), or both legs useless: crawling. */
        public boolean mustCrawl() {
            for (Limb l : new Limb[]{Limb.RIGHT_LEG, Limb.LEFT_LEG}) if (severed(l) && !prosthetic(l)) return true;
            return legsOut() == 2;
        }

        public boolean whole() {
            return severed == 0 && bleeding == 0 && health[0] == 100 && health[1] == 100 && health[2] == 100 && health[3] == 100;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putByteArray("health", health.clone());
            t.putInt("severed", severed);
            t.putInt("prosthetic", prosthetic);
            t.putByteArray("stump", stump.clone());
            return t;
        }

        void load(CompoundTag t) {
            byte[] h = t.getByteArray("health");
            for (int i = 0; i < 4 && i < h.length; i++) health[i] = h[i];
            severed = t.getInt("severed");
            prosthetic = t.getInt("prosthetic");
            byte[] st = t.getByteArray("stump");
            for (int i = 0; i < 4 && i < st.length; i++) stump[i] = st[i];
        }
    }

    private static final State WHOLE = new State();
    private static final Map<LivingEntity, State> SERVER = new WeakHashMap<>();
    private static final Map<Integer, State> CLIENT = new HashMap<>();

    private Limbs() {}

    public static boolean eligible(Entity e) {
        return e instanceof Player || e instanceof KiFighter;
    }

    /** This fighter's limbs (on either side; whole for anything else). */
    public static State of(LivingEntity e) {
        if (e.level().isClientSide) return CLIENT.getOrDefault(e.getId(), WHOLE);
        if (!eligible(e)) return WHOLE;
        return SERVER.computeIfAbsent(e, k -> {
            State s = new State();
            if (k.getPersistentData().contains(TAG)) s.load(k.getPersistentData().getCompound(TAG));
            return s;
        });
    }

    /** Stores and sends a change. */
    public static void changed(LivingEntity e) {
        State s = of(e);
        if (s.whole()) e.getPersistentData().remove(TAG);
        else e.getPersistentData().put(TAG, s.save());
        if (!e.level().isClientSide) handedness(e, s);                     // the left takes over at once (the limbs rework)
        broadcast(e, s);
    }

    private static LimbsPacket packet(LivingEntity e, State s) {
        int stumps = 0;
        for (int i = 0; i < 4; i++) stumps |= (s.stump[i] & 3) << (i * 2);
        return new LimbsPacket(e.getId(), s.health.clone(), s.severed, s.prosthetic, s.bleeding, stumps);
    }

    private static void broadcast(LivingEntity e, State s) {
        LimbsPacket m = packet(e, s);
        if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(p, m);
        else ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> e), m);
    }

    public static void onPacket(LimbsPacket m) {
        State s = new State();
        for (int i = 0; i < 4; i++) s.health[i] = m.health()[i];
        s.severed = m.severed();
        s.prosthetic = m.prosthetic();
        s.bleeding = m.bleeding();
        for (int i = 0; i < 4; i++) s.stump[i] = (byte) (m.stumps() >> (i * 2) & 3);
        if (s.whole()) CLIENT.remove(m.entityId());
        else CLIENT.put(m.entityId(), s);
    }

    // ------------------------------------------------------------------ blows

    /** After all the other rules: a share of the blow lands on a limb. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !eligible(v) || event.getAmount() <= 0 || KnockOut.isDown(v)) return;
        if (v instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        float share = event.getAmount() / Math.max(1f, v.getMaxHealth());
        if (share < 0.01f) return;
        DamageSource source = event.getSource();
        if (share >= 0.12f) Blood.spill(v, v.position().add(0, v.getBbHeight() * 0.6, 0), Math.min(24, (int) (share * 40)));   // a hard blow draws blood (the knocked-out rework)
        boolean cutting = cutting(source), overwhelming = share >= 0.33f;
        if (!cutting && !overwhelming && v.getRandom().nextFloat() > 0.55f) return;   // not every blow finds a limb
        Limb limb = pick(v, source);
        hit(v, limb, share * 180f, cutting, overwhelming, source.getDirectEntity() instanceof com.dbzenith.skill.KiBlastEntity);
    }

    /** {@code amount}% off a limb; a cutting or overwhelming blow can take a broken one off. */
    public static void hit(LivingEntity v, Limb limb, float amount, boolean cutting, boolean overwhelming) {
        hit(v, limb, amount, cutting, overwhelming, false);
    }

    /** The same; {@code seared}: cut by ki, which sears the wound (it bleeds far less). */
    public static void hit(LivingEntity v, Limb limb, float amount, boolean cutting, boolean overwhelming, boolean seared) {
        State s = of(v);
        if (s.severed(limb) && !s.prosthetic(limb)) return;                // nothing left to hit (a prosthetic can be)
        int before = s.health(limb);
        int after = Math.max(0, Math.round(before - amount));
        s.lastHit = v.level().getGameTime();
        if ((cutting && before <= USABLE + 10 || overwhelming && before == 0) && after == 0) {
            sever(v, limb, seared);
            return;
        }
        s.health[limb.ordinal()] = (byte) after;
        if (before >= USABLE && after < USABLE) broke(v, limb);
        changed(v);
    }

    /** Which limb a blow lands on: the side the attacker is on, high or low by where they struck from. */
    static Limb pick(LivingEntity v, DamageSource source) {
        Entity by = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        boolean right = v.getRandom().nextBoolean(), high = v.getRandom().nextFloat() < 0.55f;
        if (by != null) {
            Vec3 to = by.position().subtract(v.position());
            double yr = Math.toRadians(v.getYRot());
            Vec3 rightDir = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
            double side = to.x * rightDir.x + to.z * rightDir.z;
            if (Math.abs(side) > 0.3) right = side > 0;
            double rel = by.getY() + by.getBbHeight() * 0.5 - (v.getY() + v.getBbHeight() * 0.5);
            if (rel > 0.5) high = true;
            else if (rel < -0.5) high = false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) high = false;
        return high ? (right ? Limb.RIGHT_ARM : Limb.LEFT_ARM) : (right ? Limb.RIGHT_LEG : Limb.LEFT_LEG);
    }

    /** A blade, or a disc of ki (the Destructo Disc and its kind). */
    static boolean cutting(DamageSource source) {
        if (source.getDirectEntity() instanceof com.dbzenith.skill.KiBlastEntity b && b.getStyle() == com.dbzenith.skill.Technique.Style.DISK) return true;
        if (source.getDirectEntity() instanceof LivingEntity a && source.getDirectEntity() == source.getEntity()) {
            ItemStack held = a.getMainHandItem();
            return held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem;
        }
        return false;
    }

    private static void broke(LivingEntity v, Limb limb) {
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.1f, 0.55f);
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), com.dbzenith.registry.ModSounds.GUARD_BREAK.get(), SoundSource.PLAYERS, 0.7f, 0.6f);
        if (v instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.limb_broken", Component.translatable("limb.dbzenith." + limb.key())), true);
    }

    /** Lost: gone from the body, whatever that hand held dropped. */
    public static void sever(LivingEntity v, Limb limb) {
        sever(v, limb, false);
    }

    /**
     * The same; {@code seared}: cut by ki, the wound burnt shut, so it bleeds for six seconds instead of twenty. Either
     * way the limb flies off ({@link SeveredLimb}) and the stump spurts (the knocked-out rework).
     */
    public static void sever(LivingEntity v, Limb limb, boolean seared) {
        State s = of(v);
        s.bleeding |= 1 << limb.ordinal();
        long now0 = v.level().getGameTime();
        boolean regrows = regrowTicks(v) >= 0;
        s.bleedUntil[limb.ordinal()] = now0 + (seared ? 120 : regrows ? (regrowTicks(v) <= 200 ? 60 : 120) : 400);   // open wounds keep seeping after that, until closed
        s.stump[limb.ordinal()] = (byte) (seared ? SEARED : OPEN);
        s.stumpSince[limb.ordinal()] = now0;
        s.severed |= 1 << limb.ordinal();
        int regrow = regrowTicks(v);
        if (regrow >= 0) s.regrowAt[limb.ordinal()] = v.level().getGameTime() + regrow;
        s.prosthetic &= ~(1 << limb.ordinal());
        s.health[limb.ordinal()] = 0;
        if (v.level() instanceof ServerLevel level) {
            Vec3 at = v.position().add(0, limb.arm() ? v.getBbHeight() * 0.65 : v.getBbHeight() * 0.25, 0);
            double yr = Math.toRadians(v.yBodyRot);
            Vec3 out = new Vec3(-Math.cos(yr), 0, -Math.sin(yr)).scale(limb == Limb.RIGHT_ARM || limb == Limb.RIGHT_LEG ? 1 : -1);
            Vec3 socket = at.add(out.scale(v.getBbWidth() * (limb.arm() ? 0.6 : 0.25)));
            Blood.spurt(v, socket, out.add(0, 0.6, 0).normalize(), seared ? 12 : 34);   // the stump spurts
            if (seared) level.sendParticles(ParticleTypes.SMOKE, socket.x, socket.y, socket.z, 10, 0.1, 0.1, 0.1, 0.02);   // ki sears it
            SeveredLimb.throwOff(v, limb, socket, out);                              // and the limb flies
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.2f, 0.4f);
        }
        if (limb.arm()) {                                                     // what that hand held falls
            InteractionHand hand = limb == Limb.RIGHT_ARM ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack held = v.getItemInHand(hand);
            if (!held.isEmpty()) {
                if (v instanceof Player p) p.drop(held.copy(), true);
                else v.spawnAtLocation(held.copy());
                v.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        if (v instanceof Player p) p.displayClientMessage(Component.translatable(seared || regrows ? "message.dbzenith.limb_lost" : "message.dbzenith.limb_lost_open", Component.translatable("limb.dbzenith." + limb.key())), true);
        changed(v);
    }


    // ------------------------------------------------------------------ getting better (CX-54)

    /** Broken limbs set whole again (a Senzu Bean); lost ones stay lost. */
    public static void mend(LivingEntity e) {
        if (e.level().isClientSide || !eligible(e)) return;
        State s = of(e);
        boolean any = false;
        if (s.bleeding != 0) {
            s.bleeding = 0;
            any = true;
        }
        for (Limb l : Limb.values()) {                                      // a Senzu Bean closes a stump at once (the limbs rework)
            if (s.severed(l) && !s.prosthetic(l) && s.stump(l) != HEALED) {
                s.stump[l.ordinal()] = HEALED;
                s.stumpSince[l.ordinal()] = e.level().getGameTime();
                any = true;
            }
        }
        for (Limb l : Limb.values()) {
            if (s.severed(l) && !s.prosthetic(l) || s.health(l) >= 100) continue;
            s.health[l.ordinal()] = 100;
            any = true;
        }
        if (any) changed(e);
    }

    /** The body made whole (a wish, Regenerate): lost limbs back as they were, prosthetics handed back. */
    public static void restore(LivingEntity e) {
        if (e.level().isClientSide || !eligible(e)) return;
        State s = of(e);
        if (s.whole()) return;
        for (Limb l : Limb.values()) {
            if (s.prosthetic(l) && e instanceof Player p)
                p.getInventory().placeItemBackInInventory(new ItemStack(l.arm() ? com.dbzenith.registry.ModItems.PROSTHETIC_ARM.get() : com.dbzenith.registry.ModItems.PROSTHETIC_LEG.get()));
            s.health[l.ordinal()] = 100;
        }
        s.severed = 0;
        s.prosthetic = 0;
        s.bleeding = 0;
        changed(e);
    }

    /** Fits a prosthetic where a limb of that kind was lost (the right side first); false if there is none to replace. */
    public static boolean fit(LivingEntity e, boolean arm) {
        State s = of(e);
        Limb[] order = arm ? new Limb[]{Limb.RIGHT_ARM, Limb.LEFT_ARM} : new Limb[]{Limb.RIGHT_LEG, Limb.LEFT_LEG};
        for (Limb l : order) {
            if (!s.severed(l) || s.prosthetic(l)) continue;
            s.prosthetic |= 1 << l.ordinal();
            s.health[l.ordinal()] = 100;
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1f, 1f);
            if (e instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.prosthetic_fitted", Component.translatable("limb.dbzenith." + l.key())), true);
            changed(e);
            return true;
        }
        return false;
    }

    /** Namekians and Majins grow lost limbs back (players by race, and the Namekian warriors). */
    public static int regrowTicks(LivingEntity e) {
        if (e instanceof Player p) {
            var race = com.dbzenith.data.ModCapabilities.get(p).map(com.dbzenith.data.PlayerData::getRace).orElse(null);
            if (race == com.dbzenith.stats.Race.MAJIN) return 200;
            if (race == com.dbzenith.stats.Race.NAMEKIAN) return 800;
            return -1;
        }
        return e.getType() == com.dbzenith.npc.ModNpcs.NAMEKIAN_WARRIOR.get() ? 800 : -1;
    }

    /** Every lost limb grown back at once (the Regenerate technique), each with its regrowth. */
    public static void regrowAll(LivingEntity e) {
        if (e.level().isClientSide || !eligible(e)) return;
        State s = of(e);
        boolean majin = e instanceof Player p && com.dbzenith.data.ModCapabilities.get(p).map(d -> d.getRace() == com.dbzenith.stats.Race.MAJIN).orElse(false);
        for (Limb l : Limb.values()) {
            if (!s.severed(l)) continue;
            if (s.prosthetic(l) && e instanceof Player p)
                p.getInventory().placeItemBackInInventory(new ItemStack(l.arm() ? com.dbzenith.registry.ModItems.PROSTHETIC_ARM.get() : com.dbzenith.registry.ModItems.PROSTHETIC_LEG.get()));
            s.prosthetic &= ~(1 << l.ordinal());
            regrow(e, l, majin);
        }
    }

    /** One lost limb grown back: weak at first, mending from there. */
    public static void regrow(LivingEntity e, Limb l, boolean majin) {
        State s = of(e);
        s.severed &= ~(1 << l.ordinal());
        s.stump[l.ordinal()] = OPEN;
        s.bleeding &= ~(1 << l.ordinal());
        s.health[l.ordinal()] = 40;
        if (e.level() instanceof ServerLevel level) {
            Vec3 at = e.position().add(0, l.arm() ? e.getBbHeight() * 0.65 : e.getBbHeight() * 0.25, 0);
            // the limb grows out of the stump in slime (Namekians) or goo (Majins), drawn by the clients (the limbs rework)
            com.dbzenith.network.ImpactPacket.at(at, new Vec3(0, 1, 0), com.dbzenith.network.ImpactPacket.REGROW, l.ordinal(), majin ? 0xF06FB0 : 0xB890D8, e.getId()).send(level);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, majin ? 0.8f : 0.55f);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1f, 0.6f);
        }
        if (e instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.limb_regrown", Component.translatable("limb.dbzenith." + l.key())), true);
        changed(e);
    }

    // ------------------------------------------------------------------ bleeding (the knocked-out rework)

    /**
     * Once a second while a wound is fresh: a share of health drains away (1% of the most for each bleeding limb), and
     * a wound that has run its course stops. Bleeding out can knock a fighter down. Down, they don't bleed more: their
     * own clock runs.
     */
    private static void bleed(LivingEntity e, State s) {
        long now = e.level().getGameTime();
        boolean regrows = regrowTicks(e) >= 0;
        float rate = 0;
        boolean changed = false;
        for (Limb l : Limb.values()) {
            if (!s.bleeding(l)) continue;
            if (now < s.bleedUntil[l.ordinal()]) {
                rate += 0.01f;                                                // a fresh wound pours
                continue;
            }
            if (s.severed(l) && !s.prosthetic(l) && s.stump(l) == OPEN && !regrows) {
                rate += 0.004f;                                               // an open stump seeps on until it is closed (the limbs rework)
                continue;
            }
            s.bleeding &= ~(1 << l.ordinal());                                 // run its course (seared, or a body that closes its own wounds)
            if (regrows && s.severed(l) && s.stump(l) == OPEN) {
                s.stump[l.ordinal()] = HEALED;
                s.stumpSince[l.ordinal()] = now;
            }
            changed = true;
        }
        if (changed) changed(e);
        if (rate <= 0) return;
        if (KnockOut.isDown(e) || e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        float loss = e.getMaxHealth() * rate;
        if (e instanceof ServerPlayer p) {
            com.dbzenith.data.PlayerData data = com.dbzenith.data.ModCapabilities.get(p).orElse(null);
            if (data != null) {
                double body = data.getBody() - data.getDerived().maxBody() * rate;
                if (body <= 0 && KnockOut.eligible(p)) {
                    KnockOut.knockOut(p, p.damageSources().generic());
                    return;
                }
                data.setBody(Math.max(1, body));
                BodyHealth.mirror(p, data);
                data.markDirty();
                return;
            }
        }
        if (e.getHealth() - loss <= 0 && KnockOut.eligible(e)) KnockOut.knockOut(e, e.damageSources().generic());
        else e.setHealth(Math.max(1, e.getHealth() - loss));
    }

    /** Bandaged and seared stumps heal over in time (checked with the rest, every half second). */
    private static void healOver(LivingEntity e, State s) {
        long now = e.level().getGameTime();
        boolean any = false;
        for (Limb l : Limb.values()) {
            if (!s.severed(l) || s.prosthetic(l)) continue;
            int st = s.stump(l);
            if ((st == BANDAGED || st == SEARED) && now - s.stumpSince[l.ordinal()] >= HEAL_OVER) {
                s.stump[l.ordinal()] = HEALED;
                s.stumpSince[l.ordinal()] = now;
                // (healed over: the stump shows a pale, puckered seam, LimbRender)
                any = true;
            }
        }
        if (any) changed(e);
    }

    /** Healing ki (Ki Heal, a Namekian's healing): wounds close, healed over at once. */
    public static void healWounds(LivingEntity e) {
        if (e.level().isClientSide || !eligible(e)) return;
        State s = of(e);
        boolean any = s.bleeding != 0;
        s.bleeding = 0;
        for (Limb l : Limb.values()) {
            if (s.severed(l) && !s.prosthetic(l) && s.stump(l) != HEALED) {
                s.stump[l.ordinal()] = HEALED;
                s.stumpSince[l.ordinal()] = e.level().getGameTime();
                any = true;
            }
        }
        if (any) changed(e);
    }

    /**
     * How fast the body heals with its wounds (the feel pass): an open stump, never closed, slows healing to a third,
     * so leaving it costs something even out of a fight.
     */
    public static double healingFactor(LivingEntity e) {
        return openWound(e) ? 0.35 : 1.0;
    }

    /** Has an open stump (it needs closing: a bandage, searing it with ki, a Senzu Bean, healing). */
    public static boolean openWound(LivingEntity e) {
        State s = of(e);
        for (Limb l : Limb.values()) if (s.severed(l) && !s.prosthetic(l) && s.stump(l) == OPEN && regrowTicks(e) < 0) return true;
        return false;
    }

    /** Sears open stumps shut with one's own ki (Sneak + the Ki Blast key). Returns whether there was one. */
    public static boolean sear(LivingEntity e) {
        State s = of(e);
        boolean any = false;
        long now = e.level().getGameTime();
        for (Limb l : Limb.values()) {
            if (!s.severed(l) || s.prosthetic(l) || s.stump(l) != OPEN) continue;
            s.stump[l.ordinal()] = SEARED;
            s.stumpSince[l.ordinal()] = now;
            s.bleeding &= ~(1 << l.ordinal());
            any = true;
            if (e.level() instanceof ServerLevel level) {
                Vec3 at = e.position().add(0, l.arm() ? e.getBbHeight() * 0.7 : e.getBbHeight() * 0.3, 0);
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, 0.1, 0.1, 0.1, 0.03);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.06, 0.06, 0.06, 0.01);
            }
        }
        if (any) {
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.9f, 1.2f);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), com.dbzenith.registry.ModSounds.KI_CRACKLE.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
            changed(e);
        }
        return any;
    }

    /** Stops every wound bleeding (a bandage, healing). Returns whether any was. */
    public static boolean stopBleeding(LivingEntity e) {
        if (e.level().isClientSide || !eligible(e)) return false;
        State s = of(e);
        boolean any = s.bleeding != 0;
        s.bleeding = 0;
        long now = e.level().getGameTime();
        for (Limb l : Limb.values()) {                                      // an open stump is bound up (the limbs rework)
            if (s.severed(l) && !s.prosthetic(l) && s.stump(l) == OPEN) {
                s.stump[l.ordinal()] = BANDAGED;
                s.stumpSince[l.ordinal()] = now;
                any = true;
            }
        }
        if (any) changed(e);
        return any;
    }

    /** The most hurt limb still on the body (a bandage tends it), or null if all are whole. */
    public static Limb worst(LivingEntity e) {
        State s = of(e);
        Limb worst = null;
        for (Limb l : Limb.values()) {
            if (s.severed(l) && !s.prosthetic(l) || s.health(l) >= 100) continue;
            if (worst == null || s.health(l) < s.health(worst)) worst = l;
        }
        return worst;
    }

    /** Adds {@code amount} health to a limb (a bandage). */
    public static void tend(LivingEntity e, Limb l, int amount) {
        State s = of(e);
        s.health[l.ordinal()] = (byte) Math.min(100, s.health(l) + amount);
        changed(e);
    }

    // ------------------------------------------------------------------ what it costs

    /** Blows thrown without arms (or with one) are weaker; no arms, kicks only. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity a) || !eligible(a)) return;
        State s = of(a);
        int arms = s.armsOut();
        if (arms == 0) return;
        boolean melee = event.getSource().getDirectEntity() == a;
        float f = melee ? (arms == 1 ? 0.7f : 0.5f) : (arms == 1 ? 0.8f : 1f);
        event.setAmount(event.getAmount() * f);
    }

    /**
     * With the right arm gone the left takes over (the limbs rework): it becomes the main arm, so what you hold is in it
     * and it swings, and the other hand (on the side that is gone) holds nothing. Put back as it was when the arm is.
     */
    private static void handedness(LivingEntity e, State s) {
        CompoundTag d = e.getPersistentData();
        boolean left = s.leftHanded(), swapped = d.contains("dbzenith_main_arm");
        if (left) {
            if (!swapped) d.putByte("dbzenith_main_arm", (byte) (e instanceof Player p ? p.getMainArm().ordinal()
                    : e instanceof net.minecraft.world.entity.Mob m && m.isLeftHanded() ? 0 : 1));
            if (e instanceof Player p) {
                if (p.getMainArm() != net.minecraft.world.entity.HumanoidArm.LEFT) p.setMainArm(net.minecraft.world.entity.HumanoidArm.LEFT);
                ItemStack off = p.getOffhandItem();
                if (!off.isEmpty()) {
                    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);   // (emptied first: putting it back would stack it onto itself)
                    p.getInventory().placeItemBackInInventory(off);
                }
            } else if (e instanceof net.minecraft.world.entity.Mob m && !m.isLeftHanded()) m.setLeftHanded(true);
        } else if (swapped) {
            boolean wasLeft = d.getByte("dbzenith_main_arm") == 0;
            d.remove("dbzenith_main_arm");
            if (e instanceof Player p) p.setMainArm(wasLeft ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT);
            else if (e instanceof net.minecraft.world.entity.Mob m) m.setLeftHanded(wasLeft);
        }
    }

    /** Ki needs a hand: with both arms out no technique can be fired. */
    public static boolean canUseKi(LivingEntity e) {
        return of(e).armsOut() < 2;
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!eligible(e)) return;
        State s = of(e);
        if (s.legsOut() > 0 && e instanceof Player p && p.isSprinting()) p.setSprinting(false);
        if (e.level().isClientSide || (e.tickCount % 10) != 0) return;
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double want = s.mustCrawl() ? -0.82 : s.legsOut() == 1 ? -0.35 : 0;   // a leg gone: crawling (the user)
            AttributeModifier now = speed.getModifier(SLOW);
            if (now == null ? want != 0 : now.getAmount() != want) {
                if (now != null) speed.removeModifier(SLOW);
                if (want != 0) speed.addTransientModifier(new AttributeModifier(SLOW, "dbzenith limbs", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        handedness(e, s);
        int regrow = regrowTicks(e);                                          // Namekians and Majins grow lost limbs back (CX-54)
        if (regrow >= 0 && s.severed != 0) {
            long t = e.level().getGameTime();
            for (Limb l : Limb.values()) {
                if (!s.severed(l) || s.prosthetic(l)) continue;
                if (s.regrowAt[l.ordinal()] == 0) s.regrowAt[l.ordinal()] = t + regrow;
                else if (t >= s.regrowAt[l.ordinal()]) {
                    s.regrowAt[l.ordinal()] = 0;
                    regrow(e, l, regrow <= 200);
                }
            }
        }
        if (s.bleeding != 0 && e.tickCount % 20 == 0) bleed(e, s);            // fresh wounds bleed (the knocked-out rework)
        if (s.severed != 0 && e.tickCount % 20 == 0) healOver(e, s);
        long now = e.level().getGameTime();                                   // broken limbs mend slowly on their own
        if (now - s.lastHit > 300 && !KnockOut.isDown(e)) {
            boolean any = false;
            for (Limb l : Limb.values()) {
                if (s.severed(l) || s.health(l) >= 100) continue;
                if (e.tickCount % 40 == 0) {
                    s.health[l.ordinal()] = (byte) Math.min(100, s.health(l) + 1);
                    any = true;
                }
            }
            if (any) changed(e);
        }
    }

    /** A weaker jump on one leg, none on none (both sides: players jump on their own client). */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (!eligible(e)) return;
        State s = of(e);
        int legs = s.legsOut();
        if (legs == 0) return;
        boolean crawl = s.mustCrawl();
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * (crawl ? 0.2 : 0.7), crawl ? 0 : v.y * 0.65, v.z * (crawl ? 0.2 : 0.7));
    }

    /** Someone starts seeing a fighter with hurt limbs, or a player joins: tell them. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity v && eligible(v) && event.getEntity() instanceof ServerPlayer p) {
            State s = of(v);
            if (!s.whole()) ModNetwork.sendTo(p, packet(v, s));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            State s = of(p);
            if (!s.whole()) ModNetwork.sendTo(p, packet(p, s));
        }
    }

    /** A new life: whole again (the persistent data didn't come along). */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            p.getPersistentData().remove(TAG);
            SERVER.remove(p);
            broadcast(p, of(p));
        }
    }

    /** Every fighter with hurt limbs this client knows of (entity id to state). */
    public static Map<Integer, State> clientStates() {
        return CLIENT;
    }

    /** Clears the client copy (leaving a world). */
    public static void clearClient() {
        CLIENT.clear();
    }
}
