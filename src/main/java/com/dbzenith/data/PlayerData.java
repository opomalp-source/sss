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
    public static final int DATA_VERSION = 1;

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
        this.physicalAge = Math.max(0, physicalAge);
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
        dirty = false;
    }
}
