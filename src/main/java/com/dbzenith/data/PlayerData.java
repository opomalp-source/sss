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
    private boolean poolsDirty;
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

    /** TP earned so far including the fraction not yet worth a whole point. */
    public double getTrainingProgress() {
        return trainingPoints + tpFraction;
    }

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
        derivedStale = true; // paths carry stat bonuses
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

    private double alignmentFraction;

    /** Adds a possibly fractional shift; whole points apply as they add up. */
    public void addAlignment(double shift) {
        alignmentFraction += shift;
        int whole = (int) alignmentFraction;
        alignmentFraction -= whole;
        if (whole != 0) setAlignment(alignment + whole);
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
            markPoolsDirty();
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
            markPoolsDirty();
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
            markPoolsDirty();
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

    // ------------------------------------------------------------------ race variant (race.Variant)

    private String variant = "";       // "" = the race's default
    private String destiny = "";       // a rare variant rolled at creation, hidden until the first milestone

    public com.dbzenith.race.Variant getVariant() {
        return com.dbzenith.race.Variant.byId(variant, race);
    }

    public void setVariant(com.dbzenith.race.Variant v) {
        String id = v == null || v.race() != race ? "" : v.id();
        if (!id.equals(variant)) {
            variant = id;
            derivedStale = true;
            markDirty();
        }
    }

    public String getDestiny() {
        return destiny;
    }

    public void setDestiny(String d) {
        String v = d == null ? "" : d;
        if (!v.equals(destiny)) {
            destiny = v;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ Ki Creator designs (skill.CustomTechniques)

    private final com.dbzenith.skill.CustomTechniques.Spec[] customSpecs = new com.dbzenith.skill.CustomTechniques.Spec[com.dbzenith.skill.CustomTechniques.MAX_SLOTS];
    private final com.dbzenith.skill.Technique[] customBuilt = new com.dbzenith.skill.Technique[com.dbzenith.skill.CustomTechniques.MAX_SLOTS];

    public com.dbzenith.skill.CustomTechniques.Spec getCustomSpec(int slot) {
        return slot >= 0 && slot < customSpecs.length ? customSpecs[slot] : null;
    }

    public void setCustomSpec(int slot, com.dbzenith.skill.CustomTechniques.Spec spec) {
        if (slot < 0 || slot >= customSpecs.length) return;
        customSpecs[slot] = spec;
        customBuilt[slot] = null;
        markDirty();
    }

    /** The technique a custom id ({@code custom_<slot>}) stands for in this player's slots, or null. */
    public com.dbzenith.skill.Technique customTechnique(String id) {
        int slot = com.dbzenith.skill.CustomTechniques.slotOf(id);
        if (slot < 0 || customSpecs[slot] == null) return null;
        if (customBuilt[slot] == null) customBuilt[slot] = com.dbzenith.skill.CustomTechniques.build(slot, customSpecs[slot]);
        return customBuilt[slot];
    }

    private String hairCode = "";     // appearance.HairCode; "" = bald (or the player's own skin hair)
    private int skinTone = -1;        // -1 = the player's own Minecraft skin; otherwise the generated body in this tone
    private int heightPercent = 100;  //  80..125: model and hitbox
    private int face;                 // appearance.FaceParts, packed
    private int highlightColor = -1;  // hair tips; -1 = none
    private int auraColor = -1;       // base-form aura; -1 = your race's

    public static final int MIN_HEIGHT = 80, MAX_HEIGHT = 125;

    public String getHairCode() {
        return hairCode;
    }

    /** Ignores invalid codes. */
    public void setHairCode(String code) {
        String clean = com.dbzenith.appearance.HairCode.sanitize(code);
        if (clean != null && !clean.equals(hairCode)) {
            hairCode = clean;
            markDirty();
        }
    }

    public int getSkinTone() {
        return skinTone;
    }

    public void setSkinTone(int tone) {
        int t = tone < 0 ? -1 : tone & 0xFFFFFF;
        if (t != skinTone) {
            skinTone = t;
            markDirty();
        }
    }

    public int getFace() {
        return face;
    }

    public void setFace(int f) {
        int v = com.dbzenith.appearance.FaceParts.sanitize(f);
        if (v != face) {
            face = v;
            markDirty();
        }
    }

    // ---- the race's own look (CX-16b, saved): a part style and three colours, -1 = the race's own
    private int raceStyle;
    private int raceSkinColor = -1, raceMarkColor = -1, racePartColor = -1;

    /** Style of the race's signature part (horns, antennae, tentacle, ears, wings): 0 classic, 1 long, 2 short, 3 none. */
    public int getRaceStyle() {
        return raceStyle;
    }

    /** Skin colour for a race with a skin of its own, or -1. */
    public int getRaceSkinColor() {
        return raceSkinColor;
    }

    /** Marking / shell colour (Namekian bands, Frost Demon shell, Bio-Android spots), or -1. */
    public int getRaceMarkColor() {
        return raceMarkColor;
    }

    /** Colour of horns and the tail, or -1. */
    public int getRacePartColor() {
        return racePartColor;
    }

    public void setRaceCustom(int style, int skin, int mark, int part) {
        raceStyle = Math.max(0, Math.min(3, style));
        raceSkinColor = skin < 0 ? -1 : skin & 0xFFFFFF;
        raceMarkColor = mark < 0 ? -1 : mark & 0xFFFFFF;
        racePartColor = part < 0 ? -1 : part & 0xFFFFFF;
        markDirty();
    }

    public int getHighlightColor() {
        return highlightColor;
    }

    public void setHighlightColor(int c) {
        int v = c < 0 ? -1 : c & 0xFFFFFF;
        if (v != highlightColor) {
            highlightColor = v;
            markDirty();
        }
    }

    /** Chosen base-form aura colour, or -1 for the race (or lineage) colour. */
    public int getAuraColor() {
        return auraColor;
    }

    public void setAuraColor(int c) {
        int v = c < 0 ? -1 : c & 0xFFFFFF;
        if (v != auraColor) {
            auraColor = v;
            markDirty();
        }
    }

    public int getHeightPercent() {
        return heightPercent;
    }

    public void setHeightPercent(int percent) {
        int p = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, percent));
        if (p != heightPercent) {
            heightPercent = p;
            markDirty();
        }
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

    // ---------------------------------------------------------------- PvP mode (CX-19): never saved, so every session and every life starts out of it
    private boolean pvp;
    private long pvpReadyAt, pvpCombatUntil;

    public boolean isPvp() {
        return pvp;
    }

    public void setPvp(boolean on) {
        if (on != pvp) {
            pvp = on;
            markDirty();
        }
    }

    /** Game time from which PvP mode may be toggled again. */
    public long getPvpReadyAt() {
        return pvpReadyAt;
    }

    public void setPvpReadyAt(long t) {
        pvpReadyAt = t;
    }

    /** Game time until which this player counts as in a fight with another player. */
    public long getPvpCombatUntil() {
        return pvpCombatUntil;
    }

    public void setPvpCombatUntil(long t) {
        pvpCombatUntil = t;
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

    // ------------------------------------------------------------------ life sim: needs, family, looks

    private double thirst = 100;
    /** Sync-only: -1 cold, 0 comfortable, 1 hot. */
    private int temperature;
    /** Sync-only: the partner is close by (training together). */
    private boolean nearPartner;
    private String partnerId = "";
    private String partnerName = "";
    private int scar;
    private int tattoo;
    private boolean raceLook = true;

    public double getThirst() {
        return thirst;
    }

    public void setThirst(double value) {
        double v = Mth.clamp(value, 0, 100);
        if ((int) v != (int) thirst) markDirty();
        thirst = v;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int t) {
        if (t != temperature) {
            temperature = t;
            markDirty();
        }
    }

    public boolean isNearPartner() {
        return nearPartner;
    }

    public void setNearPartner(boolean near) {
        if (near != nearPartner) {
            nearPartner = near;
            markDirty();
        }
    }

    /** The partner's UUID as a string (empty = single). */
    public String getPartnerId() {
        return partnerId;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartner(String id, String name) {
        partnerId = id == null ? "" : id;
        partnerName = name == null ? "" : name;
        if (partnerId.isEmpty()) nearPartner = false;
        markDirty();
    }

    public int getScar() {
        return scar;
    }

    public int getTattoo() {
        return tattoo;
    }

    /** Show the full race look (green Namekian, white-and-violet Frost Demon, pink Majin) over the player's own skin. */
    public boolean isRaceLook() {
        return raceLook;
    }

    public void setRaceLook(boolean on) {
        raceLook = on;
        markDirty();
    }

    public void setCosmetics(int scar, int tattoo) {
        this.scar = Math.max(0, scar);
        this.tattoo = Math.max(0, tattoo);
        markDirty();
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

    private double gearReduction;

    /** Share of incoming damage a full gi/armour set takes off (set every tick from the worn set; not saved). */
    public double getGearReduction() {
        return gearReduction;
    }

    public void setGearReduction(double r) {
        gearReduction = r;
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

    // ------------------------------------------------------------------ the other world (CX-12, saved)

    private boolean dead;
    private long deathTick;
    private boolean evilSoul;

    /** Dead and in the other world (a halo over the head) until sent back. */
    public boolean isDead() {
        return dead;
    }

    public long getDeathTick() {
        return deathTick;
    }

    private int springSoak;

    /** Seconds spent meditating in the springs of paradise. */
    public int getSpringSoak() {
        return springSoak;
    }

    public void setSpringSoak(int seconds) {
        springSoak = seconds;
        markDirty();
    }

    /** Sent to Limbo rather than the check-in station. */
    public boolean isEvilSoul() {
        return evilSoul;
    }

    public void setDead(boolean dead, long tick, boolean evil) {
        this.dead = dead;
        this.deathTick = tick;
        this.evilSoul = dead && evil;
        markDirty();
    }

    // ------------------------------------------------------------------ fusion dance / Potara (12c, saved)

    private int fusionKind;
    private boolean fusionHost;
    private String fusedWith = "";
    private String fusedName = "";
    private long fusionUntil;
    private double fusionPower = 1.0;
    private int fusionPrevMode = -1;

    /** {@link com.dbzenith.fusion.Fusion#DANCE}, {@code POTARA}, {@code FAILED_FAT}, {@code FAILED_THIN}, or 0 (not fused). */
    public int getFusionKind() {
        return fusionKind;
    }

    public boolean isFused() {
        return fusionKind != 0;
    }

    /** The one whose body the fusion uses (the other rides along, watching). */
    public boolean isFusionHost() {
        return fusionHost;
    }

    /** The other half's UUID, as a string ("" when not fused). */
    public String getFusedWith() {
        return fusedWith;
    }

    public String getFusedName() {
        return fusedName;
    }

    public long getFusionUntil() {
        return fusionUntil;
    }

    /** The fused body's attribute multiplier (1 for the partner, who has no body of their own). */
    public double getFusionPower() {
        return fusionKind != 0 && fusionHost ? fusionPower : 1.0;
    }

    /** The partner's game mode before the fusion (-1: none saved). */
    public int getFusionPrevMode() {
        return fusionPrevMode;
    }

    public void setFusion(int kind, boolean host, String with, String name, long until, double power, int prevMode) {
        fusionKind = kind;
        fusionHost = host;
        fusedWith = with;
        fusedName = name;
        fusionUntil = until;
        fusionPower = power;
        fusionPrevMode = prevMode;
        derivedStale = true;
        markDirty();
    }

    public void clearFusion() {
        setFusion(0, false, "", "", 0, 1.0, -1);
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

    public static final long TAIL_NOT_CUT = Long.MIN_VALUE;
    private long tailCutAt = TAIL_NOT_CUT;

    /** When the tail was cut off in a fight ({@link #TAIL_NOT_CUT} if not; a tail removed any other way does not grow back). */
    public long getTailCutAt() {
        return tailCutAt;
    }

    public void setTailCutAt(long gameTime) {
        tailCutAt = gameTime;
        markDirty();
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

    // ------------------------------------------------------------------ combat state (runtime only, synced but not saved)

    private boolean charging;
    private boolean guarding;
    private boolean flying;
    private int comboHits;
    private long lastHitTick = Long.MIN_VALUE / 2;
    private long lastDamagedTick = Long.MIN_VALUE / 2;
    private long lastFoeHitTick = Long.MIN_VALUE / 2;

    /** Last time a living foe (not the world) hurt this player. */
    public long getLastFoeHitTick() {
        return lastFoeHitTick;
    }

    public void setLastFoeHitTick(long tick) {
        lastFoeHitTick = tick;
    }
    private int chargeTicks;
    private float lastSetHealth = -1;
    private final java.util.Map<String, Long> cooldownUntil = new java.util.HashMap<>();

    public boolean isCharging() {
        return charging;
    }

    public void setCharging(boolean charging) {
        if (this.charging != charging) {
            if (!charging && chargeTicks > 0) {               // let go: the charge waits for a technique (Rising Charge)
                risingCharge = chargeTicks;
                risingAge = 0;
            }
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

    // racial skills: the active on the Racial key, cooldowns and buffs (saved, as game-time ticks), and the live
    // conditions their passives read (not saved; see race.RacialSkills)
    private String racialSelected = "";
    private final java.util.Map<String, Long> racialCooldowns = new java.util.HashMap<>();
    private final java.util.Map<String, Long> racialBuffs = new java.util.HashMap<>();
    private int racialMask;
    private final java.util.Set<String> racialActive = new java.util.HashSet<>();
    private final java.util.Set<String> racialAfter = new java.util.HashSet<>();
    private final java.util.Map<String, Long> racialOnce = new java.util.HashMap<>();

    public String getRacialSelected() {
        return racialSelected;
    }

    public void setRacialSelected(String id) {
        racialSelected = id == null ? "" : id;
        markDirty();
    }

    /** Game tick the skill is ready again (0 = ready). */
    public long getRacialCooldown(String id) {
        return racialCooldowns.getOrDefault(id, 0L);
    }

    public void setRacialCooldown(String id, long readyAt) {
        racialCooldowns.put(id, readyAt);
        markDirty();
    }

    /** Game tick a timed racial buff began, or absent. */
    public java.util.Map<String, Long> getRacialBuffs() {
        return racialBuffs;
    }

    public void startRacialBuff(String id, long now) {
        racialBuffs.put(id, now);
        markDirty();
    }

    /** Once-in-a-while passives (Second Wind, Death Regeneration...): the tick they last fired. */
    public long getRacialOnce(String id) {
        return racialOnce.getOrDefault(id, Long.MIN_VALUE / 2);
    }

    public void setRacialOnce(String id, long now) {
        racialOnce.put(id, now);
        markDirty();
    }

    public int getRacialMask() {
        return racialMask;
    }

    public java.util.Set<String> getRacialActive() {
        return racialActive;
    }

    public java.util.Set<String> getRacialAfter() {
        return racialAfter;
    }

    /** New live conditions: derived stats are recomputed when they change. */
    public void setRacialState(int mask, java.util.Set<String> active, java.util.Set<String> after) {
        if (mask == racialMask && active.equals(racialActive) && after.equals(racialAfter)) return;
        racialMask = mask;
        racialActive.clear();
        racialActive.addAll(active);
        racialAfter.clear();
        racialAfter.addAll(after);
        invalidateDerived();
    }

    /** Combat v3 state (runtime; partly synced). */
    private final CombatState combat = new CombatState();

    public CombatState combat() {
        return combat;
    }

    // universal skills: learned levels and the active on the Skill key (saved); Kaioken's stage and Rising Charge
    // (synced, not saved)
    private final java.util.Map<String, Integer> skillLevels = new java.util.HashMap<>();
    private String skillSelected = "";
    private int kaiokenStage;
    private int risingCharge, risingAge;

    public int getSkillLevel(String id) {
        return skillLevels.getOrDefault(id, 0);
    }

    public void setSkillLevel(String id, int level) {
        if (level <= 0) skillLevels.remove(id);
        else skillLevels.put(id, level);
        derivedStale = true;
        markDirty();
    }

    public java.util.Map<String, Integer> skillLevelsView() {
        return java.util.Collections.unmodifiableMap(skillLevels);
    }

    public String getSkillSelected() {
        return skillSelected;
    }

    public void setSkillSelected(String id) {
        skillSelected = id == null ? "" : id;
        markDirty();
    }

    public int getKaiokenStage() {
        return kaiokenStage;
    }

    public void setKaiokenStage(int stage) {
        int s = Math.max(0, stage);
        if (s != kaiokenStage) {
            kaiokenStage = s;
            invalidateDerived();
        }
    }

    /** Ticks of charging just released, for Rising Charge (0 when none is waiting). */
    public int getRisingCharge() {
        return risingCharge;
    }

    /** Spend the waiting charge: returns the ticks held. */
    public int takeRisingCharge() {
        int r = risingCharge;
        risingCharge = 0;
        return r;
    }

    /** Ages the waiting charge: it is lost two seconds after letting go. */
    public void tickRisingCharge() {
        if (risingCharge > 0 && !charging && ++risingAge > 40) risingCharge = 0;
    }

    // God Ki: experience towards levels 1-10 (saved); see transform.GodKi
    private double godKiXp;

    public double getGodKiXp() {
        return godKiXp;
    }

    public void setGodKiXp(double xp) {
        double v = Math.max(0, xp);
        if (v != godKiXp) {
            godKiXp = v;
            derivedStale = true;
            markDirty();
        }
    }

    // a transformation being powered up into (not saved; synced): the form, ticks done and ticks needed
    private String transformTarget = "";
    private int transformTicks, transformTotal;
    /** Whether the key is held (CX-23: let go and the power-up falls back), and the fall's fraction of a tick. */
    private boolean transformHeld = true;
    private double transformFall;

    public boolean isTransformHeld() {
        return transformHeld;
    }

    public void setTransformHeld(boolean held) {
        if (held != transformHeld) {
            transformHeld = held;
            markDirty();
        }
    }

    /** Lets the power-up fall back by {@code ticks} (fractions add up); returns the ticks left. */
    public int fallTransform(double ticks) {
        transformFall += ticks;
        int whole = (int) transformFall;
        if (whole > 0) {
            transformFall -= whole;
            setTransformTicks(Math.max(0, transformTicks - whole));
        }
        return transformTicks;
    }

    public String getTransformTarget() {
        return transformTarget;
    }

    public boolean isTransforming() {
        return !transformTarget.isEmpty();
    }

    public int getTransformTicks() {
        return transformTicks;
    }

    public int getTransformTotal() {
        return transformTotal;
    }

    public void startTransforming(String form, int total) {
        transformTarget = form;
        transformTicks = 0;
        transformTotal = Math.max(1, total);
        transformHeld = true;
        transformFall = 0;
        markDirty();
    }

    public void setTransformTicks(int t) {
        transformTicks = t;
        markDirty();
    }

    public void stopTransforming() {
        if (transformTarget.isEmpty()) return;
        transformTarget = "";
        transformTicks = transformTotal = 0;
        markDirty();
    }

    // ticks of continuous combat (rising forms); not saved
    private int combatTicks;
    private long lastCombatTick = Long.MIN_VALUE / 2;

    /** Hit someone or got hit: the combat clock keeps running. */
    public void markCombat(long now) {
        lastCombatTick = now;
    }

    public long getLastCombatTick() {
        return lastCombatTick;
    }

    public int getCombatTicks() {
        return combatTicks;
    }

    public void setCombatTicks(int t) {
        combatTicks = Math.max(0, t);
    }

    /** Derived stats must be recomputed (something they depend on changed outside the setters). */
    public void invalidateDerived() {
        derivedStale = true;
        markDirty();
    }

    /** The special meter (CX-19): built by fighting, spent on supers and ultimates. Bars of 100. */
    private double special;

    public double getSpecial() {
        return special;
    }

    public void setSpecial(double v) {
        double c = Math.max(0, Math.min(com.dbzenith.combat.engine.SpecialMeter.max(), v));
        if (Math.abs(c - special) > 1e-6) {
            special = c;
            markPoolsDirty();
        }
    }


    /**
     * The PvP meters (CX-20), 0..100: the form meter (the right bar; its studs are the forms you have unlocked) and the
     * technique meter (the left bar; Kaioken's stages, Ultra Instinct). See combat.meter.Meters.
     */
    private double formMeter, techMeter;

    public double getFormMeter() {
        return formMeter;
    }

    public void setFormMeter(double v) {
        double c = Math.max(0, Math.min(100, v));
        if (Math.abs(c - formMeter) > 1e-6) {
            formMeter = c;
            markPoolsDirty();
        }
    }

    public double getTechMeter() {
        return techMeter;
    }

    public void setTechMeter(double v) {
        double c = Math.max(0, Math.min(100, v));
        if (Math.abs(c - techMeter) > 1e-6) {
            techMeter = c;
            markPoolsDirty();
        }
    }

    // fighting styles (CX-20): the styles learned, the style chosen for each animation slot, and for each master the
    // affinity, the seconds trained with them, the day they were last talked to and the gifts given that day (saved);
    // the master being trained with right now (not saved)
    private final java.util.Set<String> learnedStyles = new java.util.TreeSet<>();
    private final java.util.Map<String, String> styleSlots = new java.util.TreeMap<>();
    private final java.util.Map<String, Integer> masterAffinity = new java.util.TreeMap<>(), masterTraining = new java.util.TreeMap<>(),
            masterGifts = new java.util.TreeMap<>();
    private final java.util.Map<String, Long> masterDay = new java.util.TreeMap<>();
    private String trainingWith = "";

    public boolean hasStyle(String id) {
        return learnedStyles.contains(id);
    }

    public java.util.Set<String> learnedStylesView() {
        return java.util.Collections.unmodifiableSet(learnedStyles);
    }

    public void learnStyle(String id, boolean on) {
        if (on ? learnedStyles.add(id) : learnedStyles.remove(id)) {
            if (!on) styleSlots.values().removeIf(id::equals);
            markDirty();
        }
    }

    /** The style chosen for a slot ({@link com.dbzenith.style.StyleSlot} id), or "" for the default. */
    public String getStyleSlot(String slot) {
        return styleSlots.getOrDefault(slot, "");
    }

    public void setStyleSlot(String slot, String style) {
        if (style == null || style.isEmpty()) {
            if (styleSlots.remove(slot) != null) markDirty();
        } else if (!style.equals(styleSlots.put(slot, style))) {
            markDirty();
        }
    }

    public java.util.Map<String, String> styleSlotsView() {
        return java.util.Collections.unmodifiableMap(styleSlots);
    }

    /** The slots as one string ("slot=style,..."), for the clients that draw this player. */
    public String styleSlotsCode() {
        StringBuilder b = new StringBuilder();
        styleSlots.forEach((k, v) -> b.append(b.length() == 0 ? "" : ",").append(k).append('=').append(v));
        return b.toString();
    }

    public int getAffinity(String master) {
        return masterAffinity.getOrDefault(master, 0);
    }

    public void setAffinity(String master, int value) {
        int v = Math.max(0, Math.min(com.dbzenith.style.StyleLogic.MAX_AFFINITY, value));
        if (v != getAffinity(master)) {
            masterAffinity.put(master, v);
            markDirty();
        }
    }

    public int getTrainedSeconds(String master) {
        return masterTraining.getOrDefault(master, 0);
    }

    public void addTrainedSeconds(String master, int seconds) {
        masterTraining.merge(master, seconds, Integer::sum);
        markDirty();
    }

    public long getMasterDay(String master) {
        return masterDay.getOrDefault(master, -1L);
    }

    public void setMasterDay(String master, long day) {
        masterDay.put(master, day);
        masterGifts.remove(master);
        markDirty();
    }

    public int getGiftsToday(String master) {
        return masterGifts.getOrDefault(master, 0);
    }

    public void addGiftToday(String master) {
        masterGifts.merge(master, 1, Integer::sum);
        markDirty();
    }

    public String getTrainingWith() {
        return trainingWith;
    }

    public void setTrainingWith(String master) {
        trainingWith = master == null ? "" : master;
        markDirty();
    }

    private static net.minecraft.nbt.CompoundTag intMap(java.util.Map<String, Integer> m) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        m.forEach(t::putInt);
        return t;
    }

    private void saveStyles(CompoundTag tag) {
        CompoundTag s = new CompoundTag();
        s.putString("learned", String.join(",", learnedStyles));
        CompoundTag slots = new CompoundTag();
        styleSlots.forEach(slots::putString);
        s.put("slots", slots);
        s.put("affinity", intMap(masterAffinity));
        s.put("trained", intMap(masterTraining));
        s.put("gifts", intMap(masterGifts));
        CompoundTag days = new CompoundTag();
        masterDay.forEach(days::putLong);
        s.put("days", days);
        tag.put("styles", s);
    }

    private void loadStyles(CompoundTag tag) {
        learnedStyles.clear();
        styleSlots.clear();
        masterAffinity.clear();
        masterTraining.clear();
        masterGifts.clear();
        masterDay.clear();
        CompoundTag s = tag.getCompound("styles");
        for (String id : s.getString("learned").split(",")) if (!id.isEmpty()) learnedStyles.add(id);
        CompoundTag slots = s.getCompound("slots");
        for (String k : slots.getAllKeys()) styleSlots.put(k, slots.getString(k));
        for (String k : s.getCompound("affinity").getAllKeys()) masterAffinity.put(k, s.getCompound("affinity").getInt(k));
        for (String k : s.getCompound("trained").getAllKeys()) masterTraining.put(k, s.getCompound("trained").getInt(k));
        for (String k : s.getCompound("gifts").getAllKeys()) masterGifts.put(k, s.getCompound("gifts").getInt(k));
        for (String k : s.getCompound("days").getAllKeys()) masterDay.put(k, s.getCompound("days").getLong(k));
    }
    // guard meter (combat.GuardRules): 0..100, empties under blocked hits and breaks the guard
    private double guardMeter = 100;
    private long guardStartTick = Long.MIN_VALUE / 2;   // when the guard last went up (parry window); not saved
    private long guardLockUntil;                          // no guarding until then (after a break)
    private long lastGuardHitTick = Long.MIN_VALUE / 2;

    public double getGuardMeter() {
        return guardMeter;
    }

    public void setGuardMeter(double v) {
        double c = Math.max(0, Math.min(100, v));
        if (Math.abs(c - guardMeter) > 1e-6) {
            guardMeter = c;
            markPoolsDirty();
        }
    }

    public long getGuardStartTick() {
        return guardStartTick;
    }

    public void setGuardStartTick(long t) {
        guardStartTick = t;
    }

    public long getGuardLockUntil() {
        return guardLockUntil;
    }

    public void setGuardLockUntil(long t) {
        if (t != guardLockUntil) {
            guardLockUntil = t;
            markDirty();
        }
    }

    public long getLastGuardHitTick() {
        return lastGuardHitTick;
    }

    public void setLastGuardHitTick(long t) {
        lastGuardHitTick = t;
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

    public long getCooldownUntil(String id) {
        return cooldownUntil.getOrDefault(id, Long.MIN_VALUE);
    }

    // ------------------------------------------------------------------ sync bookkeeping

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * The pools (body, ki, stamina, the special and guard meters, the PvP meters) change nearly every tick: they go in a small packet of their
     * own (CX-19 phase 7), and the whole state only when something else changed.
     */
    public void markPoolsDirty() {
        poolsDirty = true;
    }

    public static final int SYNC_NONE = 0, SYNC_POOLS = 1, SYNC_FULL = 2;

    /** Called once per server tick: what to send now ({@link #SYNC_NONE}, {@link #SYNC_POOLS} or {@link #SYNC_FULL}). */
    public int tickSyncTimer(int interval) {
        ticksSinceSync++;
        if (!(dirty || poolsDirty) || ticksSinceSync < interval) return SYNC_NONE;
        int what = dirty ? SYNC_FULL : SYNC_POOLS;
        dirty = false;
        poolsDirty = false;
        ticksSinceSync = 0;
        return what;
    }

    /** The client: the pools from a {@code PoolsSyncPacket}, as they are (the server already clamped them). */
    public void applyPools(double body, double ki, double stamina, double special, double guardMeter, double formMeter, double techMeter) {
        this.formMeter = formMeter;
        this.techMeter = techMeter;
        this.body = body;
        this.ki = ki;
        this.stamina = stamina;
        this.special = special;
        this.guardMeter = guardMeter;
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
        tag.putDouble("alignmentFrac", alignmentFraction);
        tag.putDouble("physicalAge", physicalAge);
        tag.putDouble("mentalAge", mentalAge);
        tag.putBoolean("flying", flying);
        tag.putString("form", formId);
        CompoundTag m = new CompoundTag();
        mastery.forEach(m::putDouble);
        tag.put("mastery", m);
        tag.putDouble("godKiXp", godKiXp);
        tag.putString("racialSelected", racialSelected);
        CompoundTag sl = new CompoundTag();
        skillLevels.forEach(sl::putInt);
        tag.put("skillLevels", sl);
        tag.putString("skillSelected", skillSelected);
        CompoundTag rc = new CompoundTag();
        racialCooldowns.forEach(rc::putLong);
        tag.put("racialCooldowns", rc);
        CompoundTag rb = new CompoundTag();
        racialBuffs.forEach(rb::putLong);
        tag.put("racialBuffs", rb);
        CompoundTag ro = new CompoundTag();
        racialOnce.forEach(ro::putLong);
        tag.put("racialOnce", ro);
        net.minecraft.nbt.ListTag fl = new net.minecraft.nbt.ListTag();
        for (String f : flags) fl.add(net.minecraft.nbt.StringTag.valueOf(f));
        tag.put("flags", fl);
        tag.putBoolean("tail", hasTail);
        tag.putString("targetForm", targetForm);
        tag.putString("bodyType", bodyType.name());
        tag.putInt("hairStyle", hairStyle);
        tag.putInt("hairColor", hairColor);
        tag.putInt("eyeColor", eyeColor);
        tag.putString("hairCode", hairCode);
        tag.putString("variant", variant);
        tag.putString("destiny", destiny);
        tag.putDouble("guardMeter", guardMeter);
        tag.putDouble("special", special);
        tag.putDouble("formMeter", formMeter);
        tag.putDouble("techMeter", techMeter);
        saveStyles(tag);
        net.minecraft.nbt.ListTag customs = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < customSpecs.length; i++) {
            if (customSpecs[i] == null) continue;
            CompoundTag c = customSpecs[i].save();
            c.putInt("slot", i);
            customs.add(c);
        }
        tag.put("customTechniques", customs);
        tag.putLong("guardLockUntil", guardLockUntil);
        tag.putInt("skinTone", skinTone);
        tag.putInt("heightPercent", heightPercent);
        tag.putInt("face", face);
        tag.putInt("highlight", highlightColor);
        tag.putInt("raceStyle", raceStyle);
        tag.putInt("raceSkinColor", raceSkinColor);
        tag.putInt("raceMarkColor", raceMarkColor);
        tag.putInt("racePartColor", racePartColor);
        tag.putInt("auraColor", auraColor);
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
        tag.putBoolean("dead", dead);
        tag.putLong("deathTick", deathTick);
        tag.putBoolean("evilSoul", evilSoul);
        tag.putInt("springSoak", springSoak);
        tag.putInt("fusionKind", fusionKind);
        tag.putBoolean("fusionHost", fusionHost);
        tag.putString("fusedWith", fusedWith);
        tag.putString("fusedName", fusedName);
        tag.putLong("fusionUntil", fusionUntil);
        tag.putDouble("fusionPower", fusionPower);
        tag.putInt("fusionPrevMode", fusionPrevMode);
        tag.putInt("prestige", prestige);
        CompoundTag cds = new CompoundTag();
        cooldownUntil.forEach(cds::putLong); // game time is world-wide, so these stay valid across relogs
        tag.put("cooldowns", cds);
        tag.putDouble("thirst", thirst);
        tag.putString("partnerId", partnerId);
        tag.putString("partnerName", partnerName);
        tag.putInt("scar", scar);
        tag.putInt("tattoo", tattoo);
        tag.putBoolean("raceLook", raceLook);
        tag.putLong("tailCutAt", tailCutAt);
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
        alignmentFraction = tag.getDouble("alignmentFrac");
        physicalAge = tag.contains("physicalAge") ? tag.getDouble("physicalAge") : 16;
        mentalAge = tag.contains("mentalAge") ? tag.getDouble("mentalAge") : 16;
        flying = tag.getBoolean("flying");
        formId = tag.contains("form") ? tag.getString("form") : BASE_FORM;
        mastery.clear();
        CompoundTag m = tag.getCompound("mastery");
        for (String k : m.getAllKeys()) mastery.put(k, m.getDouble(k));
        godKiXp = tag.getDouble("godKiXp");
        racialSelected = tag.getString("racialSelected");
        skillLevels.clear();
        CompoundTag sl = tag.getCompound("skillLevels");
        for (String k : sl.getAllKeys()) skillLevels.put(k, sl.getInt(k));
        skillSelected = tag.getString("skillSelected");
        racialCooldowns.clear();
        CompoundTag rc = tag.getCompound("racialCooldowns");
        for (String k : rc.getAllKeys()) racialCooldowns.put(k, rc.getLong(k));
        racialBuffs.clear();
        CompoundTag rb = tag.getCompound("racialBuffs");
        for (String k : rb.getAllKeys()) racialBuffs.put(k, rb.getLong(k));
        racialOnce.clear();
        CompoundTag ro = tag.getCompound("racialOnce");
        for (String k : ro.getAllKeys()) racialOnce.put(k, ro.getLong(k));
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
        if (tag.contains("hairCode")) {
            String code = com.dbzenith.appearance.HairCode.sanitize(tag.getString("hairCode"));
            hairCode = code == null ? "" : code;
        } else {
            hairCode = com.dbzenith.appearance.HairCode.fromLegacyStyle(hairStyle); // saved before hair codes existed
        }
        skinTone = tag.contains("skinTone") ? tag.getInt("skinTone") : -1;
        variant = tag.getString("variant");
        destiny = tag.getString("destiny");
        guardMeter = tag.contains("guardMeter") ? tag.getDouble("guardMeter") : 100;
        special = tag.getDouble("special");
        formMeter = tag.getDouble("formMeter");
        techMeter = tag.getDouble("techMeter");
        loadStyles(tag);
        java.util.Arrays.fill(customSpecs, null);
        java.util.Arrays.fill(customBuilt, null);
        net.minecraft.nbt.ListTag customs = tag.getList("customTechniques", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < customs.size(); i++) {
            CompoundTag c = customs.getCompound(i);
            int slot = c.getInt("slot");
            if (slot >= 0 && slot < customSpecs.length) customSpecs[slot] = com.dbzenith.skill.CustomTechniques.Spec.load(c);
        }
        guardLockUntil = tag.getLong("guardLockUntil");
        heightPercent = tag.contains("heightPercent") ? Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, tag.getInt("heightPercent"))) : 100;
        face = com.dbzenith.appearance.FaceParts.sanitize(tag.getInt("face"));
        highlightColor = tag.contains("highlight") ? tag.getInt("highlight") : -1;
        raceStyle = tag.getInt("raceStyle");
        raceSkinColor = tag.contains("raceSkinColor") ? tag.getInt("raceSkinColor") : -1;
        raceMarkColor = tag.contains("raceMarkColor") ? tag.getInt("raceMarkColor") : -1;
        racePartColor = tag.contains("racePartColor") ? tag.getInt("racePartColor") : -1;
        auraColor = tag.contains("auraColor") ? tag.getInt("auraColor") : -1;
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
        dead = tag.getBoolean("dead");
        deathTick = tag.getLong("deathTick");
        evilSoul = tag.getBoolean("evilSoul");
        springSoak = tag.getInt("springSoak");
        fusionKind = tag.getInt("fusionKind");
        fusionHost = tag.getBoolean("fusionHost");
        fusedWith = tag.getString("fusedWith");
        fusedName = tag.getString("fusedName");
        fusionUntil = tag.getLong("fusionUntil");
        fusionPower = tag.contains("fusionPower") ? tag.getDouble("fusionPower") : 1.0;
        fusionPrevMode = tag.contains("fusionPrevMode") ? tag.getInt("fusionPrevMode") : -1;
        prestige = tag.getInt("prestige");
        cooldownUntil.clear();
        CompoundTag cds = tag.getCompound("cooldowns");
        for (String k : cds.getAllKeys()) cooldownUntil.put(k, cds.getLong(k));
        thirst = tag.contains("thirst") ? tag.getDouble("thirst") : 100;
        partnerId = tag.getString("partnerId");
        partnerName = tag.getString("partnerName");
        scar = tag.getInt("scar");
        tattoo = tag.getInt("tattoo");
        raceLook = !tag.contains("raceLook") || tag.getBoolean("raceLook");
        tailCutAt = tag.contains("tailCutAt") ? tag.getLong("tailCutAt") : TAIL_NOT_CUT;
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
        tag.putDouble("gravity", envGravity);
        tag.putBoolean("meditating", meditating);
        tag.putInt("temperature", temperature);
        tag.putBoolean("nearPartner", nearPartner);
        tag.putBoolean("heavyCharging", heavyChargeTicks >= 0);
        tag.putDouble("heavyArmed", heavyArmedMultiplier);
        tag.putString("transformTarget", transformTarget);
        tag.putInt("transformTicks", transformTicks);
        tag.putInt("transformTotal", transformTotal);
        tag.putBoolean("transformHeld", transformHeld);
        tag.putInt("racialMask", racialMask);
        tag.putInt("kaioken", kaiokenStage);
        CompoundTag cs = new CompoundTag();
        combat.save(cs);
        tag.put("combat", cs);
        tag.putInt("risingCharge", risingCharge);
        tag.putString("trainingWith", trainingWith);
        tag.putString("racialActive", String.join(",", racialActive));
        tag.putString("racialAfter", String.join(",", racialAfter));
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
        envGravity = tag.contains("gravity") ? tag.getDouble("gravity") : 1.0;
        meditating = tag.getBoolean("meditating");
        temperature = tag.getInt("temperature");
        nearPartner = tag.getBoolean("nearPartner");
        heavyChargeTicks = tag.getBoolean("heavyCharging") ? 0 : -1;
        heavyArmedMultiplier = tag.getDouble("heavyArmed");
        transformTarget = tag.getString("transformTarget");
        transformTicks = tag.getInt("transformTicks");
        transformTotal = tag.getInt("transformTotal");
        transformHeld = !tag.contains("transformHeld") || tag.getBoolean("transformHeld");
        racialMask = tag.getInt("racialMask");
        kaiokenStage = tag.getInt("kaioken");
        combat.load(tag.getCompound("combat"));
        risingCharge = tag.getInt("risingCharge");
        trainingWith = tag.getString("trainingWith");
        racialActive.clear();
        for (String s : tag.getString("racialActive").split(",")) if (!s.isEmpty()) racialActive.add(s);
        racialAfter.clear();
        for (String s : tag.getString("racialAfter").split(",")) if (!s.isEmpty()) racialAfter.add(s);
        dirty = false;
    }
}
