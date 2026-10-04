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
        this.body = Mth.clamp(body, 0, derived.maxBody());
        markDirty();
    }

    public double getKi() {
        return ki;
    }

    public void setKi(double ki) {
        recomputeIfStale();
        this.ki = Mth.clamp(ki, 0, derived.maxKi());
        markDirty();
    }

    public double getStamina() {
        return stamina;
    }

    public void setStamina(double stamina) {
        recomputeIfStale();
        this.stamina = Mth.clamp(stamina, 0, derived.maxStamina());
        markDirty();
    }

    public int getReleasePercent() {
        return releasePercent;
    }

    public void setReleasePercent(int releasePercent) {
        this.releasePercent = Mth.clamp(releasePercent, 0, 100);
        markDirty();
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
        return tag;
    }

    /** Client: apply a sync tag from the server. Derived stats are taken as-is, never recomputed client-side. */
    public void readSyncTag(CompoundTag tag) {
        load(tag);
        derived = DerivedStats.load(tag.getCompound("derived"));
        derivedStale = false;
        dirty = false;
    }
}
