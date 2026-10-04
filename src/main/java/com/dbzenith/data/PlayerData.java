package com.dbzenith.data;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * All per-player Dragon Block state. One instance per player per logical side, attached as a capability.
 * The server copy is authoritative; the client copy is overwritten wholesale by {@code SyncPlayerDataPacket}.
 *
 * <p>Any mutator marks the data dirty; {@link PlayerDataEvents} syncs dirty data to the owner each tick
 * (rate-limited by {@code network.syncIntervalTicks}).</p>
 */
public class PlayerData {
    /** Bump when the NBT layout changes and add a migration in {@link #load}. */
    public static final int DATA_VERSION = 2;

    /** Techniques every v1 character could use before the deck system existed (migration). */
    private static final java.util.List<String> V1_TECHNIQUES =
            java.util.List.of("ki_blast", "wave_beam", "finger_beam", "rapid_volley", "cutter_disk", "homing_orb");

    private static final int FALLBACK_ATTRIBUTE = 10;

    private final int[] attributes = new int[Attribute.values().length];
    private long trainingPoints;
    private Race race = Race.HUMAN;
    private FightingPath path = FightingPath.HYBRID;
    private boolean characterCreated;
    private boolean initialized;

    private double body;
    private double ki;
    private double stamina;
    private int releasePercent = 50;
    private int alignment;
    private double physicalAge = 16;
    private double mentalAge = 16;

    // --- runtime only (not saved) ---
    private DerivedStats derived = DerivedStats.EMPTY;
    private boolean derivedStale = true;
    private boolean dirty = true;
    private int ticksSinceSync;

    public PlayerData() {
        java.util.Arrays.fill(attributes, FALLBACK_ATTRIBUTE);
    }

    /**
     * Applies config-driven defaults to a brand-new character. Server only; no-op once initialized.
     */
    public void initDefaultsIfNeeded() {
        if (initialized) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        java.util.Arrays.fill(attributes, c.startingAttribute.get());
        releasePercent = c.defaultReleasePercent.get();
        physicalAge = c.startingAge.get();
        mentalAge = c.startingAge.get();
        learned.add("ki_blast");
        if (deck.isEmpty()) deck.add("ki_blast");
        initialized = true;
        recomputeIfStale();
        body = derived.maxBody();
        ki = derived.maxKi();
        stamina = derived.maxStamina();
        markDirty();
    }

    /** Resets the character to a fresh state. Server only. */
    public void reset() {
        load(new CompoundTag());
        initDefaultsIfNeeded();
    }

    // ------------------------------------------------------------------ derived stats

    /** Server: recomputes derived stats from attributes if anything changed. */
    public void recomputeIfStale() {
        if (!derivedStale) return;
        derived = StatCalculator.compute(this);
        derivedStale = false;
        clampPools();
    }

    public DerivedStats getDerived() {
        return derived;
    }

    private void clampPools() {
        body = Mth.clamp(body, 0, derived.maxBody());
        ki = Mth.clamp(ki, 0, derived.maxKi());
        stamina = Mth.clamp(stamina, 0, derived.maxStamina());
    }

    /** Refills body, ki and stamina to max. Server only. */
    public void refill() {
        recomputeIfStale();
        body = derived.maxBody();
        ki = derived.maxKi();
        stamina = derived.maxStamina();
        markDirty();
    }

    // ------------------------------------------------------------------ attributes & TP

    public int getAttribute(Attribute attribute) {
        return attributes[attribute.ordinal()];
    }

    public void setAttribute(Attribute attribute, int value) {
        attributes[attribute.ordinal()] = Math.max(1, value);
        derivedStale = true;
        markDirty();
    }

    public long getTrainingPoints() {
        return trainingPoints;
    }

    public void setTrainingPoints(long value) {
        trainingPoints = Math.max(0, value);
        markDirty();
    }

    public void addTrainingPoints(long amount) {
        setTrainingPoints(trainingPoints + amount);
    }

    private double tpFraction;

    /** Adds a fractional TP gain (already scaled); whole points are banked, the remainder carries over. */
    public void addTrainingProgress(double amount) {
        if (amount <= 0) return;
        tpFraction += amount;
        long whole = (long) tpFraction;
        if (whole > 0) {
            tpFraction -= whole;
            addTrainingPoints(whole);
        }
    }

    // ------------------------------------------------------------------ identity

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
        derivedStale = true;
        markDirty();
    }

    public FightingPath getPath() {
        return path;
    }

    public void setPath(FightingPath path) {
        this.path = path;
        markDirty();
    }

    public boolean isCharacterCreated() {
        return characterCreated;
    }

    public void setCharacterCreated(boolean characterCreated) {
        this.characterCreated = characterCreated;
        markDirty();
    }

    public int getAlignment() {
        return alignment;
    }

    public void setAlignment(int alignment) {
        this.alignment = Mth.clamp(alignment, -100, 100);
        markDirty();
    }

    public double getPhysicalAge() {
        return physicalAge;
    }

    public void setPhysicalAge(double physicalAge) {
        double v = Math.max(0, physicalAge);
        if ((int) v != (int) this.physicalAge) derivedStale = true; // the age multiplier changes per whole year
        this.physicalAge = v;
        markDirty();
    }

    public double getMentalAge() {
        return mentalAge;
    }

    public void setMentalAge(double mentalAge) {
        this.mentalAge = Math.max(0, mentalAge);
        markDirty();
    }

    // ------------------------------------------------------------------ pools

    public double getBody() {
        return body;
    }

    public void setBody(double body) {
        recomputeIfStale();
        double v = Mth.clamp(body, 0, derived.maxBody());
        if (v != this.body) {
            this.body = v;
            markDirty();
        }
    }

    public double getKi() {
        return ki;
    }

    public void setKi(double ki) {
        recomputeIfStale();
        double v = Mth.clamp(ki, 0, derived.maxKi());
        if (v != this.ki) {
            this.ki = v;
            markDirty();
        }
    }

    public double getStamina() {
        return stamina;
    }

    public void setStamina(double stamina) {
        recomputeIfStale();
        double v = Mth.clamp(stamina, 0, derived.maxStamina());
        if (v != this.stamina) {
            this.stamina = v;
            markDirty();
        }
    }

    public int getReleasePercent() {
        return releasePercent;
    }

    public void setReleasePercent(int releasePercent) {
        int v = Mth.clamp(releasePercent, 0, 100);
        if (v != this.releasePercent) {
            this.releasePercent = v;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ appearance (saved)

    public enum BodyType { SLIM, NORMAL, BULKY }

    private BodyType bodyType = BodyType.NORMAL;
    private int hairStyle;            // Form.HairStyle ordinal for the base form; 0 = NONE (own skin)
    private int hairColor = 0x1C1A1A;
    private int eyeColor = -1;        // -1 = skin default

    public BodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(BodyType type) {
        if (type != null && type != bodyType) {
            bodyType = type;
            markDirty();
        }
    }

    public int getHairStyle() {
        return hairStyle;
    }

    public void setHairStyle(int style) {
        if (style != hairStyle) {
            hairStyle = Math.max(0, style);
            markDirty();
        }
    }

    public int getHairColor() {
        return hairColor;
    }

    public void setHairColor(int color) {
        if (color != hairColor) {
            hairColor = color & 0xFFFFFF;
            markDirty();
        }
    }

    public int getEyeColor() {
        return eyeColor;
    }

    public void setEyeColor(int color) {
        if (color != eyeColor) {
            eyeColor = color < 0 ? -1 : color & 0xFFFFFF;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ training environment (runtime; gravity + meditation synced)

    private double envGravity = 1.0;
    private long envGravityUntil;
    private double trainingMultiplier = 1.0;
    private int meditateTicks;
    private boolean meditating;

    /** Gravity applied by a chamber/dimension; must be refreshed before {@code untilGameTime} or it lapses to 1. */
    public void applyGravity(double g, long untilGameTime) {
        if (g >= envGravity || untilGameTime > envGravityUntil) {
            if (g != envGravity) markDirty();
            envGravity = Math.max(1.0, g);
            envGravityUntil = untilGameTime;
        }
    }

    public double getGravity(long gameTime) {
        return gameTime <= envGravityUntil ? envGravity : 1.0;
    }

    /** Client view (synced value). */
    public double getGravity() {
        return envGravity;
    }

    public void expireGravity(long gameTime) {
        if (gameTime > envGravityUntil && envGravity != 1.0) {
            envGravity = 1.0;
            markDirty();
        }
    }

    public double getTrainingMultiplier() {
        return trainingMultiplier;
    }

    public void setTrainingMultiplier(double m) {
        trainingMultiplier = Math.max(0, m);
    }

    public int tickMeditation(boolean still) {
        meditateTicks = still ? meditateTicks + 1 : 0;
        return meditateTicks;
    }

    public boolean isMeditating() {
        return meditating;
    }

    public void setMeditating(boolean m) {
        if (m != meditating) {
            meditating = m;
            markDirty();
        }
    }

    private String title = "";

    public String getTitle() {
        return title;
    }

    public void setTitle(String t) {
        String v = t == null ? "" : t;
        if (!v.equals(title)) {
            title = v;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ quests + Galactic Patrol (saved)

    private final java.util.Map<String, int[]> activeQuests = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> completedQuests = new java.util.HashMap<>();
    private int patrolRep;

    public java.util.Map<String, int[]> activeQuestsView() {
        return java.util.Collections.unmodifiableMap(activeQuests);
    }

    public boolean isQuestActive(String id) {
        return activeQuests.containsKey(id);
    }

    public void startQuest(String id, int objectives) {
        activeQuests.put(id, new int[objectives]);
        markDirty();
    }

    public int[] questProgress(String id) {
        return activeQuests.get(id);
    }

    /** Adds to a counted objective (kills); returns true if it changed. */
    public boolean addQuestProgress(String id, int objective, int amount, int cap) {
        int[] p = activeQuests.get(id);
        if (p == null || objective >= p.length || p[objective] >= cap) return false;
        p[objective] = Math.min(cap, p[objective] + amount);
        markDirty();
        return true;
    }

    public void finishQuest(String id) {
        activeQuests.remove(id);
        completedQuests.merge(id, 1, Integer::sum);
        markDirty();
    }

    public int timesCompleted(String id) {
        return completedQuests.getOrDefault(id, 0);
    }

    public int getPatrolRep() {
        return patrolRep;
    }

    public void addPatrolRep(int amount) {
        patrolRep = Math.max(0, patrolRep + amount);
        markDirty();
    }

    // ------------------------------------------------------------------ gear set bonus (runtime, recomputed from armor each tick)

    private double gearStr = 1.0;
    private double gearDex = 1.0;
    private double gearKi = 1.0;

    public double getGearMultiplier(com.dbzenith.stats.Attribute a) {
        return switch (a) {
            case STRENGTH -> gearStr;
            case DEXTERITY -> gearDex;
            case KI_POWER -> gearKi;
            default -> 1.0;
        };
    }

    public void setGearMultipliers(double str, double dex, double ki) {
        if (str != gearStr || dex != gearDex || ki != gearKi) {
            gearStr = str;
            gearDex = dex;
            gearKi = ki;
            derivedStale = true;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ Hyperbolic Time Chamber (saved)

    private String returnDimension = "";
    private double returnX, returnY, returnZ;
    private long chamberEnteredAt = -1;

    public void setChamberReturn(String dimension, double x, double y, double z, long enteredAt) {
        returnDimension = dimension;
        returnX = x;
        returnY = y;
        returnZ = z;
        chamberEnteredAt = enteredAt;
        markDirty();
    }

    public String getReturnDimension() { return returnDimension; }
    public double getReturnX() { return returnX; }
    public double getReturnY() { return returnY; }
    public double getReturnZ() { return returnZ; }
    public long getChamberEnteredAt() { return chamberEnteredAt; }

    public void clearChamber() {
        chamberEnteredAt = -1;
        markDirty();
    }

    private long immortalUntil = -1;

    public long getImmortalUntil() {
        return immortalUntil;
    }

    public void setImmortalUntil(long gameTime) {
        immortalUntil = gameTime;
        markDirty();
    }

    // ------------------------------------------------------------------ prestige (saved)

    private int prestige;

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int value) {
        prestige = Math.max(0, value);
        derivedStale = true;
        markDirty();
    }

    // ------------------------------------------------------------------ Namekian fusion / Majin absorption (saved)

    private int fusions;
    private int majinStacks;
    private long majinUntil = -1;

    /** How many times this Namekian has fused with another. */
    public int getFusions() {
        return fusions;
    }

    public void addFusion() {
        fusions++;
        markDirty();
    }

    /** Active Majin absorption stacks (each one multiplies STR/DEX/KI_POWER). */
    public int getMajinStacks() {
        return majinStacks;
    }

    public long getMajinUntil() {
        return majinUntil;
    }

    /** One more absorbed fighter: a stack (up to {@code max}), and the timer restarts. */
    public void addMajinStack(long now, int max, long durationTicks) {
        if (now >= majinUntil) majinStacks = 0;
        majinStacks = Math.min(max, majinStacks + 1);
        majinUntil = now + durationTicks;
        derivedStale = true;
        markDirty();
    }

    /** Drops expired stacks. */
    public void tickMajin(long now) {
        if (majinStacks > 0 && now >= majinUntil) {
            majinStacks = 0;
            derivedStale = true;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ zenkai (saved)

    private boolean zenkaiArmed;
    private long lastZenkai = Long.MIN_VALUE / 2;
    private int zenkaiCount;

    public boolean isZenkaiArmed() {
        return zenkaiArmed;
    }

    public void setZenkaiArmed(boolean armed) {
        zenkaiArmed = armed;
    }

    public long getLastZenkai() {
        return lastZenkai;
    }

    public int getZenkaiCount() {
        return zenkaiCount;
    }

    public void recordZenkai(long gameTime) {
        lastZenkai = gameTime;
        zenkaiCount++;
        zenkaiArmed = false;
        markDirty();
    }

    // ------------------------------------------------------------------ techniques (saved)

    private final java.util.Set<String> learned = new java.util.LinkedHashSet<>();
    private final java.util.List<String> deck = new java.util.ArrayList<>();
    private long absorbUntil; // runtime: Android energy-absorb window

    public boolean knows(String techniqueId) {
        return learned.contains(techniqueId);
    }

    public void learn(String techniqueId) {
        if (learned.add(techniqueId)) markDirty();
    }

    public void forget(String techniqueId) {
        if (learned.remove(techniqueId)) {
            deck.remove(techniqueId);
            markDirty();
        }
    }

    public java.util.Set<String> learnedView() {
        return java.util.Collections.unmodifiableSet(learned);
    }

    public java.util.List<String> deckView() {
        return java.util.Collections.unmodifiableList(deck);
    }

    /** Replaces the deck (callers validate learned + size). */
    public void setDeck(java.util.List<String> ids) {
        if (!deck.equals(ids)) {
            deck.clear();
            deck.addAll(ids);
            markDirty();
        }
    }

    public long getAbsorbUntil() {
        return absorbUntil;
    }

    public void setAbsorbUntil(long gameTime) {
        absorbUntil = gameTime;
        markDirty();
    }

    // ------------------------------------------------------------------ transformations (saved)

    public static final String BASE_FORM = "base";

    private String formId = BASE_FORM;
    private final java.util.Map<String, Double> mastery = new java.util.HashMap<>();
    private final java.util.Set<String> flags = new java.util.HashSet<>();
    private boolean hasTail = true;
    private String targetForm = "";
    private int overdriveLevel; // runtime only: 0 = off

    public String getFormId() {
        return formId;
    }

    public boolean isTransformed() {
        return !BASE_FORM.equals(formId);
    }

    public void setFormId(String formId) {
        String v = formId == null || formId.isEmpty() ? BASE_FORM : formId;
        if (!v.equals(this.formId)) {
            this.formId = v;
            derivedStale = true;
            markDirty();
        }
    }

    public double getMastery(String id) {
        return mastery.getOrDefault(id, 0.0);
    }

    public void setMastery(String id, double value) {
        double v = Mth.clamp(value, 0, 100);
        if (v != getMastery(id)) {
            mastery.put(id, v);
            derivedStale = true;
            markDirty();
        }
    }

    public java.util.Map<String, Double> masteryView() {
        return java.util.Collections.unmodifiableMap(mastery);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag, boolean on) {
        if (on ? flags.add(flag) : flags.remove(flag)) markDirty();
    }

    public java.util.Set<String> flagsView() {
        return java.util.Collections.unmodifiableSet(flags);
    }

    public boolean hasTail() {
        return hasTail;
    }

    public void setTail(boolean tail) {
        if (tail != hasTail) {
            hasTail = tail;
            markDirty();
        }
    }

    public String getTargetForm() {
        return targetForm;
    }

    public void setTargetForm(String id) {
        String v = id == null ? "" : id;
        if (!v.equals(targetForm)) {
            targetForm = v;
            markDirty();
        }
    }

    public int getOverdriveLevel() {
        return overdriveLevel;
    }

    public void setOverdriveLevel(int level) {
        int v = Math.max(0, level);
        if (v != overdriveLevel) {
            overdriveLevel = v;
            derivedStale = true;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ combat state (runtime only, synced but not saved)

    private boolean charging;
    private boolean guarding;
    private boolean flying;
    private int comboHits;
    private long lastHitTick = Long.MIN_VALUE / 2;
    private long lastDamagedTick = Long.MIN_VALUE / 2;
    private int chargeTicks;
    private float lastSetHealth = -1;
    private final java.util.Map<String, Long> cooldownUntil = new java.util.HashMap<>();

    public boolean isCharging() {
        return charging;
    }

    public void setCharging(boolean charging) {
        if (this.charging != charging) {
            this.charging = charging;
            chargeTicks = 0;
            markDirty();
        }
    }

    public int tickCharge() {
        return ++chargeTicks;
    }

    public boolean isGuarding() {
        return guarding;
    }

    public void setGuarding(boolean guarding) {
        if (this.guarding != guarding) {
            this.guarding = guarding;
            markDirty();
        }
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        if (this.flying != flying) {
            this.flying = flying;
            markDirty();
        }
    }

    public int getComboHits() {
        return comboHits;
    }

    /** Registers a melee hit at {@code gameTime}; returns the combo length including this hit. */
    public int registerHit(long gameTime, int windowTicks, int maxHits) {
        comboHits = gameTime - lastHitTick <= windowTicks ? Math.min(maxHits, comboHits + 1) : 1;
        lastHitTick = gameTime;
        markDirty();
        return comboHits;
    }

    public void tickCombo(long gameTime, int windowTicks) {
        if (comboHits > 0 && gameTime - lastHitTick > windowTicks) {
            comboHits = 0;
            markDirty();
        }
    }

    public long getLastDamagedTick() {
        return lastDamagedTick;
    }

    public void setLastDamagedTick(long tick) {
        this.lastDamagedTick = tick;
    }

    public float getLastSetHealth() {
        return lastSetHealth;
    }

    public void setLastSetHealth(float health) {
        this.lastSetHealth = health;
    }

    // heavy hit: charge while the key is held, armed on release, consumed by the next melee hit
    private int heavyChargeTicks = -1;
    private double heavyArmedMultiplier;
    private long heavyArmedUntil;
    private long dashEvadeUntil;
    private int lastPublicStateHash;

    public boolean isChargingHeavy() {
        return heavyChargeTicks >= 0;
    }

    public void startHeavyCharge() {
        heavyChargeTicks = 0;
        markDirty();
    }

    public int tickHeavyCharge() {
        return heavyChargeTicks >= 0 ? ++heavyChargeTicks : -1;
    }

    /** Ends the charge; returns ticks held, or -1 if none was in progress. */
    public int releaseHeavyCharge() {
        int t = heavyChargeTicks;
        heavyChargeTicks = -1;
        markDirty();
        return t;
    }

    public void armHeavy(double multiplier, long untilGameTime) {
        heavyArmedMultiplier = multiplier;
        heavyArmedUntil = untilGameTime;
        markDirty();
    }

    /** Client-side display: the armed multiplier, or 0 when nothing is armed (expiry is server-side). */
    public double getHeavyArmedMultiplier() {
        return heavyArmedMultiplier;
    }

    public boolean isHeavyArmed(long gameTime) {
        return heavyArmedMultiplier > 0 && gameTime <= heavyArmedUntil;
    }

    /** Returns the armed multiplier (1 if none) and disarms. */
    public double consumeHeavy(long gameTime) {
        double m = isHeavyArmed(gameTime) ? heavyArmedMultiplier : 1.0;
        if (heavyArmedMultiplier > 0) {
            heavyArmedMultiplier = 0;
            markDirty();
        }
        return m;
    }

    public long getDashEvadeUntil() {
        return dashEvadeUntil;
    }

    public void setDashEvadeUntil(long gameTime) {
        dashEvadeUntil = gameTime;
    }

    public int getLastPublicStateHash() {
        return lastPublicStateHash;
    }

    public void setLastPublicStateHash(int hash) {
        lastPublicStateHash = hash;
    }

    public boolean isOnCooldown(String id, long gameTime) {
        return cooldownUntil.getOrDefault(id, Long.MIN_VALUE) > gameTime;
    }

    public void setCooldown(String id, long untilGameTime) {
        cooldownUntil.put(id, untilGameTime);
    }

    // ------------------------------------------------------------------ sync bookkeeping

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called once per server tick. Returns true when a sync packet should be sent now. */
    boolean tickSyncTimer(int interval) {
        ticksSinceSync++;
        if (dirty && ticksSinceSync >= interval) {
            dirty = false;
            ticksSinceSync = 0;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);
        CompoundTag attrs = new CompoundTag();
        for (Attribute a : Attribute.values()) attrs.putInt(a.id(), getAttribute(a));
        tag.put("attributes", attrs);
        tag.putLong("tp", trainingPoints);
        tag.putString("race", race.id());
        tag.putString("path", path.id());
        tag.putBoolean("created", characterCreated);
        tag.putBoolean("initialized", initialized);
        tag.putDouble("body", body);
        tag.putDouble("ki", ki);
        tag.putDouble("stamina", stamina);
        tag.putInt("release", releasePercent);
        tag.putInt("alignment", alignment);
        tag.putDouble("physicalAge", physicalAge);
        tag.putDouble("mentalAge", mentalAge);
        tag.putBoolean("flying", flying);
        tag.putString("form", formId);
        CompoundTag m = new CompoundTag();
        mastery.forEach(m::putDouble);
        tag.put("mastery", m);
        net.minecraft.nbt.ListTag fl = new net.minecraft.nbt.ListTag();
        for (String f : flags) fl.add(net.minecraft.nbt.StringTag.valueOf(f));
        tag.put("flags", fl);
        tag.putBoolean("tail", hasTail);
        tag.putString("targetForm", targetForm);
        tag.putString("bodyType", bodyType.name());
        tag.putInt("hairStyle", hairStyle);
        tag.putInt("hairColor", hairColor);
        tag.putInt("eyeColor", eyeColor);
        tag.putBoolean("zenkaiArmed", zenkaiArmed);
        tag.putLong("lastZenkai", lastZenkai);
        tag.putInt("zenkaiCount", zenkaiCount);
        net.minecraft.nbt.ListTag lt = new net.minecraft.nbt.ListTag();
        for (String s : learned) lt.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("learned", lt);
        net.minecraft.nbt.ListTag dk = new net.minecraft.nbt.ListTag();
        for (String s : deck) dk.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("deck", dk);
        tag.putString("returnDim", returnDimension);
        tag.putDouble("returnX", returnX);
        tag.putDouble("returnY", returnY);
        tag.putDouble("returnZ", returnZ);
        tag.putLong("chamberAt", chamberEnteredAt);
        tag.putLong("immortalUntil", immortalUntil);
        tag.putInt("fusions", fusions);
        tag.putInt("prestige", prestige);
        tag.putInt("majinStacks", majinStacks);
        tag.putLong("majinUntil", majinUntil);
        CompoundTag aq = new CompoundTag();
        activeQuests.forEach((k, v) -> aq.putIntArray(k, v));
        tag.put("activeQuests", aq);
        CompoundTag cq = new CompoundTag();
        completedQuests.forEach(cq::putInt);
        tag.put("completedQuests", cq);
        tag.putInt("patrolRep", patrolRep);
        tag.putString("title", title);
        return tag;
    }

    /**
     * Loads saved state. Missing keys fall back to defaults, so an empty tag yields a fresh, uninitialized character.
     */
    public void load(CompoundTag tag) {
        CompoundTag attrs = tag.getCompound("attributes");
        for (Attribute a : Attribute.values()) {
            attributes[a.ordinal()] = attrs.contains(a.id()) ? Math.max(1, attrs.getInt(a.id())) : FALLBACK_ATTRIBUTE;
        }
        trainingPoints = tag.getLong("tp");
        race = tag.contains("race") ? Race.byId(tag.getString("race")) : Race.HUMAN;
        path = tag.contains("path") ? FightingPath.byId(tag.getString("path")) : FightingPath.HYBRID;
        characterCreated = tag.getBoolean("created");
        initialized = tag.getBoolean("initialized");
        body = tag.getDouble("body");
        ki = tag.getDouble("ki");
        stamina = tag.getDouble("stamina");
        releasePercent = tag.contains("release") ? tag.getInt("release") : 50;
        alignment = tag.getInt("alignment");
        physicalAge = tag.contains("physicalAge") ? tag.getDouble("physicalAge") : 16;
        mentalAge = tag.contains("mentalAge") ? tag.getDouble("mentalAge") : 16;
        flying = tag.getBoolean("flying");
        formId = tag.contains("form") ? tag.getString("form") : BASE_FORM;
        mastery.clear();
        CompoundTag m = tag.getCompound("mastery");
        for (String k : m.getAllKeys()) mastery.put(k, m.getDouble(k));
        flags.clear();
        net.minecraft.nbt.ListTag fl = tag.getList("flags", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < fl.size(); i++) flags.add(fl.getString(i));
        hasTail = !tag.contains("tail") || tag.getBoolean("tail");
        targetForm = tag.getString("targetForm");
        try {
            bodyType = tag.contains("bodyType") ? BodyType.valueOf(tag.getString("bodyType")) : BodyType.NORMAL;
        } catch (IllegalArgumentException e) {
            bodyType = BodyType.NORMAL;
        }
        hairStyle = tag.getInt("hairStyle");
        hairColor = tag.contains("hairColor") ? tag.getInt("hairColor") : 0x1C1A1A;
        eyeColor = tag.contains("eyeColor") ? tag.getInt("eyeColor") : -1;
        zenkaiArmed = tag.getBoolean("zenkaiArmed");
        lastZenkai = tag.contains("lastZenkai") ? tag.getLong("lastZenkai") : Long.MIN_VALUE / 2;
        zenkaiCount = tag.getInt("zenkaiCount");
        learned.clear();
        net.minecraft.nbt.ListTag lt = tag.getList("learned", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < lt.size(); i++) learned.add(lt.getString(i));
        deck.clear();
        net.minecraft.nbt.ListTag dk = tag.getList("deck", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < dk.size(); i++) deck.add(dk.getString(i));
        returnDimension = tag.getString("returnDim");
        returnX = tag.getDouble("returnX");
        returnY = tag.getDouble("returnY");
        returnZ = tag.getDouble("returnZ");
        chamberEnteredAt = tag.contains("chamberAt") ? tag.getLong("chamberAt") : -1;
        immortalUntil = tag.contains("immortalUntil") ? tag.getLong("immortalUntil") : -1;
        fusions = tag.getInt("fusions");
        prestige = tag.getInt("prestige");
        majinStacks = tag.getInt("majinStacks");
        majinUntil = tag.contains("majinUntil") ? tag.getLong("majinUntil") : -1;
        activeQuests.clear();
        CompoundTag aq = tag.getCompound("activeQuests");
        for (String k : aq.getAllKeys()) activeQuests.put(k, aq.getIntArray(k));
        completedQuests.clear();
        CompoundTag cq = tag.getCompound("completedQuests");
        for (String k : cq.getAllKeys()) completedQuests.put(k, cq.getInt(k));
        patrolRep = tag.getInt("patrolRep");
        title = tag.getString("title");
        if (initialized && tag.getInt("DataVersion") < 2) { // v1 -> v2: keep every technique v1 allowed
            learned.addAll(V1_TECHNIQUES);
            deck.addAll(V1_TECHNIQUES.subList(0, 4));
        }
        derivedStale = true;
        markDirty();
    }

    public void copyFrom(PlayerData other) {
        load(other.save());
    }

    /** Server to client: the save data plus the server-computed derived stats. */
    public CompoundTag writeSyncTag() {
        recomputeIfStale();
        CompoundTag tag = save();
        tag.put("derived", derived.save());
        tag.putBoolean("charging", charging);
        tag.putBoolean("guarding", guarding);
        tag.putInt("combo", comboHits);
        tag.putInt("overdrive", overdriveLevel);
        tag.putDouble("gravity", envGravity);
        tag.putBoolean("meditating", meditating);
        tag.putBoolean("heavyCharging", heavyChargeTicks >= 0);
        tag.putDouble("heavyArmed", heavyArmedMultiplier);
        return tag;
    }

    /** Client: apply a sync tag from the server. Derived stats are taken as-is, never recomputed client-side. */
    public void readSyncTag(CompoundTag tag) {
        load(tag);
        derived = DerivedStats.load(tag.getCompound("derived"));
        derivedStale = false;
        charging = tag.getBoolean("charging");
        guarding = tag.getBoolean("guarding");
        comboHits = tag.getInt("combo");
        overdriveLevel = tag.getInt("overdrive");
        envGravity = tag.contains("gravity") ? tag.getDouble("gravity") : 1.0;
        meditating = tag.getBoolean("meditating");
        heavyChargeTicks = tag.getBoolean("heavyCharging") ? 0 : -1;
        heavyArmedMultiplier = tag.getDouble("heavyArmed");
        dirty = false;
    }
}
