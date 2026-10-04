package com.dbzenith.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Every tunable number lives here.
 * <ul>
 *   <li>{@link Server} - gameplay/balance values. Per-world (saved in serverconfig/), synced to clients by the loader.</li>
 *   <li>{@link Client} - display-only preferences.</li>
 * </ul>
 */
public final class DBZConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private DBZConfig() {}

    public static final class Server {
        // --- attributes ---
        public final ForgeConfigSpec.IntValue startingAttribute;
        public final ForgeConfigSpec.IntValue attributeSoftCap;
        public final ForgeConfigSpec.IntValue attributeHardCap;

        // --- training points ---
        public final ForgeConfigSpec.DoubleValue tpCostBase;
        public final ForgeConfigSpec.DoubleValue tpCostPerPoint;
        public final ForgeConfigSpec.DoubleValue tpSoftCapCostMultiplier;
        public final ForgeConfigSpec.DoubleValue tpGainPerMind;

        // --- derived stats ---
        public final ForgeConfigSpec.DoubleValue baseBody;
        public final ForgeConfigSpec.DoubleValue bodyPerConstitution;
        public final ForgeConfigSpec.DoubleValue baseStamina;
        public final ForgeConfigSpec.DoubleValue staminaPerConstitution;
        public final ForgeConfigSpec.DoubleValue baseKi;
        public final ForgeConfigSpec.DoubleValue kiPerWillpower;
        public final ForgeConfigSpec.DoubleValue meleeDamagePerStrength;
        public final ForgeConfigSpec.DoubleValue kiDamagePerKiPower;
        public final ForgeConfigSpec.DoubleValue defensePerDexterity;
        public final ForgeConfigSpec.DoubleValue defensePerConstitution;
        public final ForgeConfigSpec.DoubleValue evasionPerDexterity;
        public final ForgeConfigSpec.DoubleValue evasionCap;
        public final ForgeConfigSpec.DoubleValue kiControlPerMind;
        public final ForgeConfigSpec.DoubleValue kiControlCap;
        public final ForgeConfigSpec.DoubleValue spiritModifierPerSpirit;
        public final ForgeConfigSpec.DoubleValue kiTransferPerSpirit;
        public final ForgeConfigSpec.DoubleValue attackSpeedPerDexterity;
        public final ForgeConfigSpec.DoubleValue attackSpeedCap;
        public final ForgeConfigSpec.DoubleValue moveSpeedPerDexterity;
        public final ForgeConfigSpec.DoubleValue moveSpeedCap;

        // --- ki / release / regen ---
        public final ForgeConfigSpec.IntValue defaultReleasePercent;
        public final ForgeConfigSpec.DoubleValue kiRegenPercentPerSecond;
        public final ForgeConfigSpec.DoubleValue staminaRegenPercentPerSecond;
        public final ForgeConfigSpec.DoubleValue bodyRegenPercentPerSecond;
        public final ForgeConfigSpec.IntValue bodyRegenDelayTicks;
        public final ForgeConfigSpec.DoubleValue chargeKiPercentPerSecond;
        public final ForgeConfigSpec.DoubleValue chargeStaminaPercentPerSecond;
        public final ForgeConfigSpec.IntValue chargeReleaseStepTicks;
        public final ForgeConfigSpec.IntValue releaseLowerStep;
        public final ForgeConfigSpec.DoubleValue flightKiPercentPerSecond;
        public final ForgeConfigSpec.DoubleValue flightBaseSpeed;
        public final ForgeConfigSpec.DoubleValue flightSpeedPerMoveBonus;

        // --- combat ---
        public final ForgeConfigSpec.DoubleValue vanillaDamageToBody;
        public final ForgeConfigSpec.DoubleValue minDamageFraction;
        public final ForgeConfigSpec.DoubleValue dbzDamageVsMobsScale;
        public final ForgeConfigSpec.DoubleValue meleeStaminaCost;
        public final ForgeConfigSpec.DoubleValue exhaustedDamageMultiplier;
        public final ForgeConfigSpec.IntValue comboWindowTicks;
        public final ForgeConfigSpec.DoubleValue comboBonusPerHit;
        public final ForgeConfigSpec.IntValue comboMaxHits;
        public final ForgeConfigSpec.DoubleValue meleeKnockbackPerStrength;
        public final ForgeConfigSpec.DoubleValue guardDamageReduction;
        public final ForgeConfigSpec.DoubleValue guardStaminaPerDamage;
        public final ForgeConfigSpec.DoubleValue kiCostReleaseScaling;
        public final ForgeConfigSpec.DoubleValue kiBlastBaseDamage;
        public final ForgeConfigSpec.BooleanValue kiBlastsBreakBlocks;
        public final ForgeConfigSpec.IntValue heavyMaxChargeTicks;
        public final ForgeConfigSpec.DoubleValue heavyMinMultiplier;
        public final ForgeConfigSpec.DoubleValue heavyMaxMultiplier;
        public final ForgeConfigSpec.IntValue heavyArmedTicks;
        public final ForgeConfigSpec.DoubleValue heavyStaminaCost;
        public final ForgeConfigSpec.DoubleValue heavyKnockback;
        public final ForgeConfigSpec.DoubleValue dashStrength;
        public final ForgeConfigSpec.DoubleValue dashStaminaCost;
        public final ForgeConfigSpec.DoubleValue dashKiCost;
        public final ForgeConfigSpec.IntValue dashCooldownTicks;
        public final ForgeConfigSpec.IntValue dashEvadeTicks;
        public final ForgeConfigSpec.IntValue beamDamageIntervalTicks;

        // --- TP gains ---
        public final ForgeConfigSpec.DoubleValue tpPerDamageDealt;
        public final ForgeConfigSpec.DoubleValue tpPerKillHealth;
        public final ForgeConfigSpec.IntValue tpChargeTrainingInterval;
        public final ForgeConfigSpec.IntValue tpPerChargeInterval;

        // --- transformations ---
        public final ForgeConfigSpec.DoubleValue unlockLevelScale;
        public final ForgeConfigSpec.DoubleValue transformKiCostPercent;
        public final ForgeConfigSpec.DoubleValue masteryGainPerSecond;
        public final ForgeConfigSpec.DoubleValue masteryMaxMultiplierBonus;
        public final ForgeConfigSpec.DoubleValue masteryMaxDrainReduction;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Double>> overdriveLevels;
        public final ForgeConfigSpec.IntValue overdriveUnlockLevel;
        public final ForgeConfigSpec.DoubleValue overdriveMasteryPerLevel;
        public final ForgeConfigSpec.DoubleValue overdriveBodyDrainPercent;
        public final ForgeConfigSpec.DoubleValue overdriveStaminaDrainPercent;
        public final ForgeConfigSpec.DoubleValue overdriveBacklashPercent;
        public final ForgeConfigSpec.DoubleValue overdriveMinBodyPercent;

        // --- races ---
        public final ForgeConfigSpec.DoubleValue zenkaiTriggerPercent;
        public final ForgeConfigSpec.DoubleValue zenkaiRecoverPercent;
        public final ForgeConfigSpec.IntValue zenkaiCooldownTicks;
        public final ForgeConfigSpec.IntValue deckBaseSlots;
        public final ForgeConfigSpec.IntValue deckLevelsPerExtraSlot;
        public final ForgeConfigSpec.IntValue deckMaxSlots;

        // --- training ---
        public final ForgeConfigSpec.IntValue gravityMax;
        public final ForgeConfigSpec.IntValue gravityRadius;
        public final ForgeConfigSpec.DoubleValue gravityTolerancePerPoint;
        public final ForgeConfigSpec.DoubleValue gravityTrainingBonus;
        public final ForgeConfigSpec.DoubleValue gravityMaxTrainingMultiplier;
        public final ForgeConfigSpec.DoubleValue gravitySlowPerExcess;
        public final ForgeConfigSpec.DoubleValue gravityBodyDamagePercentPerExcess;
        public final ForgeConfigSpec.DoubleValue tpPerPunch;
        public final ForgeConfigSpec.IntValue punchCooldownTicks;
        public final ForgeConfigSpec.DoubleValue tpPerSecondMovingUnderGravity;
        public final ForgeConfigSpec.IntValue meditationStartTicks;
        public final ForgeConfigSpec.DoubleValue tpPerMeditationSecond;
        public final ForgeConfigSpec.DoubleValue meditationKiRegenMultiplier;
        public final ForgeConfigSpec.DoubleValue chamberTrainingMultiplier;
        public final ForgeConfigSpec.DoubleValue chamberGravity;
        public final ForgeConfigSpec.IntValue chamberMaxStayTicks;

        // --- dragon balls ---
        public final ForgeConfigSpec.BooleanValue dragonBallsEnabled;
        public final ForgeConfigSpec.IntValue dragonBallScatterRadius;
        public final ForgeConfigSpec.IntValue dragonBallInertTicks;
        public final ForgeConfigSpec.IntValue radarRange;
        public final ForgeConfigSpec.LongValue wishPowerTp;
        public final ForgeConfigSpec.IntValue wishSenzuCount;
        public final ForgeConfigSpec.IntValue wishImmortalityTicks;
        public final ForgeConfigSpec.IntValue wishDiamonds;

        // --- character ---
        public final ForgeConfigSpec.DoubleValue startingAge;

        // --- networking ---
        public final ForgeConfigSpec.IntValue syncIntervalTicks;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Base attributes (STR, DEX, CON, KI_POWER, WIL, MND, SPI)").push("attributes");
            startingAttribute = b.comment("Value every attribute starts at for a new character")
                    .defineInRange("startingAttribute", 10, 1, 1_000_000);
            attributeSoftCap = b.comment("Above this value, raising an attribute costs tpSoftCapCostMultiplier times more TP")
                    .defineInRange("attributeSoftCap", 5_000, 1, 10_000_000);
            attributeHardCap = b.comment("Attributes can never exceed this value")
                    .defineInRange("attributeHardCap", 10_000, 1, 10_000_000);
            b.pop();

            b.comment("Training Points (TP) economy").push("training_points");
            tpCostBase = b.comment("Flat TP cost to raise any attribute by one point")
                    .defineInRange("tpCostBase", 5.0, 0.0, 1e9);
            tpCostPerPoint = b.comment("Extra TP cost per point the attribute already has")
                    .defineInRange("tpCostPerPoint", 0.25, 0.0, 1e9);
            tpSoftCapCostMultiplier = b.comment("Cost multiplier once an attribute is at or above the soft cap")
                    .defineInRange("tpSoftCapCostMultiplier", 2.0, 1.0, 1000.0);
            tpGainPerMind = b.comment("Fractional bonus to all TP gains per point of MIND (0.002 = +0.2%)")
                    .defineInRange("tpGainPerMind", 0.002, 0.0, 10.0);
            b.pop();

            b.comment("How base attributes turn into derived stats. All math is in StatCalculator.").push("derived");
            baseBody = b.defineInRange("baseBody", 100.0, 1.0, 1e12);
            bodyPerConstitution = b.defineInRange("bodyPerConstitution", 20.0, 0.0, 1e9);
            baseStamina = b.defineInRange("baseStamina", 100.0, 1.0, 1e12);
            staminaPerConstitution = b.defineInRange("staminaPerConstitution", 5.0, 0.0, 1e9);
            baseKi = b.defineInRange("baseKi", 100.0, 1.0, 1e12);
            kiPerWillpower = b.defineInRange("kiPerWillpower", 15.0, 0.0, 1e9);
            meleeDamagePerStrength = b.defineInRange("meleeDamagePerStrength", 1.0, 0.0, 1e9);
            kiDamagePerKiPower = b.defineInRange("kiDamagePerKiPower", 1.0, 0.0, 1e9);
            defensePerDexterity = b.defineInRange("defensePerDexterity", 0.5, 0.0, 1e9);
            defensePerConstitution = b.defineInRange("defensePerConstitution", 0.25, 0.0, 1e9);
            evasionPerDexterity = b.comment("Evasion chance (0-1) gained per DEX point")
                    .defineInRange("evasionPerDexterity", 0.0002, 0.0, 1.0);
            evasionCap = b.defineInRange("evasionCap", 0.40, 0.0, 1.0);
            kiControlPerMind = b.comment("Ki cost reduction (0-1) gained per MIND point")
                    .defineInRange("kiControlPerMind", 0.0005, 0.0, 1.0);
            kiControlCap = b.defineInRange("kiControlCap", 0.75, 0.0, 1.0);
            spiritModifierPerSpirit = b.comment("Spirit modifier = 1 + SPI * this. Scales mastery gain.")
                    .defineInRange("spiritModifierPerSpirit", 0.01, 0.0, 100.0);
            kiTransferPerSpirit = b.comment("Ki per second that can be transferred to an ally, per SPI point")
                    .defineInRange("kiTransferPerSpirit", 0.5, 0.0, 1e9);
            attackSpeedPerDexterity = b.comment("Fractional melee attack-speed bonus per DEX point")
                    .defineInRange("attackSpeedPerDexterity", 0.001, 0.0, 10.0);
            attackSpeedCap = b.defineInRange("attackSpeedCap", 1.0, 0.0, 100.0);
            moveSpeedPerDexterity = b.comment("Fractional movement-speed bonus per DEX point")
                    .defineInRange("moveSpeedPerDexterity", 0.0005, 0.0, 10.0);
            moveSpeedCap = b.defineInRange("moveSpeedCap", 0.5, 0.0, 100.0);
            b.pop();

            b.push("ki");
            defaultReleasePercent = b.comment("Release % a new character starts at")
                    .defineInRange("defaultReleasePercent", 50, 0, 100);
            kiRegenPercentPerSecond = b.comment("Passive ki regen, % of max per second (not while charging/flying)")
                    .defineInRange("kiRegenPercentPerSecond", 2.0, 0.0, 100.0);
            staminaRegenPercentPerSecond = b.defineInRange("staminaRegenPercentPerSecond", 5.0, 0.0, 100.0);
            bodyRegenPercentPerSecond = b.comment("Out-of-combat body regen, % of max per second")
                    .defineInRange("bodyRegenPercentPerSecond", 1.0, 0.0, 100.0);
            bodyRegenDelayTicks = b.comment("Ticks after taking damage before body regen starts")
                    .defineInRange("bodyRegenDelayTicks", 100, 0, 72000);
            chargeKiPercentPerSecond = b.comment("Ki gained per second while charging, % of max")
                    .defineInRange("chargeKiPercentPerSecond", 10.0, 0.0, 100.0);
            chargeStaminaPercentPerSecond = b.comment("Stamina drained per second while charging, % of max")
                    .defineInRange("chargeStaminaPercentPerSecond", 4.0, 0.0, 100.0);
            chargeReleaseStepTicks = b.comment("While charging, release % rises by 1 every this many ticks")
                    .defineInRange("chargeReleaseStepTicks", 2, 1, 200);
            releaseLowerStep = b.comment("Release % removed per press of the lower-release key")
                    .defineInRange("releaseLowerStep", 10, 1, 100);
            flightKiPercentPerSecond = b.comment("Ki drained per second while flying, % of max")
                    .defineInRange("flightKiPercentPerSecond", 1.5, 0.0, 100.0);
            flightBaseSpeed = b.comment("Vanilla creative flight speed is 0.05")
                    .defineInRange("flightBaseSpeed", 0.05, 0.0, 1.0);
            flightSpeedPerMoveBonus = b.comment("Flight speed multiplier per unit of DEX move-speed bonus")
                    .defineInRange("flightSpeedPerMoveBonus", 2.0, 0.0, 100.0);
            b.pop();

            b.comment("Damage math (see combat.DamageCalculator)").push("combat");
            vanillaDamageToBody = b.comment("Body damage per point of vanilla damage from mobs/environment")
                    .defineInRange("vanillaDamageToBody", 10.0, 0.0, 1e6);
            minDamageFraction = b.comment("Defense can never reduce a hit below this fraction of its raw value")
                    .defineInRange("minDamageFraction", 0.1, 0.0, 1.0);
            dbzDamageVsMobsScale = b.comment("DBZ damage is multiplied by this when it hits a non-player (vanilla health scale)")
                    .defineInRange("dbzDamageVsMobsScale", 0.1, 0.0, 1e6);
            meleeStaminaCost = b.comment("Stamina spent per melee hit")
                    .defineInRange("meleeStaminaCost", 3.0, 0.0, 1e6);
            exhaustedDamageMultiplier = b.comment("Melee damage multiplier when out of stamina")
                    .defineInRange("exhaustedDamageMultiplier", 0.5, 0.0, 1.0);
            comboWindowTicks = b.comment("Max ticks between hits to keep a combo going")
                    .defineInRange("comboWindowTicks", 30, 1, 200);
            comboBonusPerHit = b.defineInRange("comboBonusPerHit", 0.05, 0.0, 10.0);
            comboMaxHits = b.defineInRange("comboMaxHits", 10, 1, 1000);
            meleeKnockbackPerStrength = b.comment("Extra melee knockback per STR point")
                    .defineInRange("meleeKnockbackPerStrength", 0.002, 0.0, 10.0);
            guardDamageReduction = b.comment("Fraction of damage blocked while guarding")
                    .defineInRange("guardDamageReduction", 0.6, 0.0, 1.0);
            guardStaminaPerDamage = b.comment("Stamina spent per point of body damage blocked")
                    .defineInRange("guardStaminaPerDamage", 0.05, 0.0, 100.0);
            kiCostReleaseScaling = b.comment("Technique ki cost multiplier at 100% release (linear from 1 at 0%)")
                    .defineInRange("kiCostReleaseScaling", 1.5, 0.0, 100.0);
            kiBlastBaseDamage = b.comment("Flat DBZ damage added to every ki technique before multipliers")
                    .defineInRange("kiBlastBaseDamage", 40.0, 0.0, 1e9);
            kiBlastsBreakBlocks = b.comment("Whether explosive techniques break blocks")
                    .define("kiBlastsBreakBlocks", false);
            heavyMaxChargeTicks = b.comment("Ticks of holding the heavy-hit key for a full charge")
                    .defineInRange("heavyMaxChargeTicks", 30, 1, 1200);
            heavyMinMultiplier = b.comment("Heavy hit multiplier for a tap")
                    .defineInRange("heavyMinMultiplier", 1.5, 1.0, 100.0);
            heavyMaxMultiplier = b.comment("Heavy hit multiplier at full charge")
                    .defineInRange("heavyMaxMultiplier", 3.0, 1.0, 100.0);
            heavyArmedTicks = b.comment("How long an armed heavy hit waits for a melee hit")
                    .defineInRange("heavyArmedTicks", 60, 1, 1200);
            heavyStaminaCost = b.comment("Stamina to arm a fully charged heavy hit (scaled by charge)")
                    .defineInRange("heavyStaminaCost", 25.0, 0.0, 1e6);
            heavyKnockback = b.comment("Extra knockback of a heavy hit")
                    .defineInRange("heavyKnockback", 1.5, 0.0, 10.0);
            dashStrength = b.comment("Dash velocity (blocks/tick) before the DEX move bonus")
                    .defineInRange("dashStrength", 1.6, 0.0, 20.0);
            dashStaminaCost = b.defineInRange("dashStaminaCost", 15.0, 0.0, 1e6);
            dashKiCost = b.defineInRange("dashKiCost", 5.0, 0.0, 1e6);
            dashCooldownTicks = b.defineInRange("dashCooldownTicks", 15, 0, 1200);
            dashEvadeTicks = b.comment("Afterimage: ticks after a dash during which entity attacks miss")
                    .defineInRange("dashEvadeTicks", 6, 0, 200);
            beamDamageIntervalTicks = b.comment("A beam damages what it touches every this many ticks")
                    .defineInRange("beamDamageIntervalTicks", 4, 1, 100);
            b.pop();

            b.comment("Training point gains").push("tp_gains");
            tpPerDamageDealt = b.comment("TP per point of DBZ damage dealt")
                    .defineInRange("tpPerDamageDealt", 0.05, 0.0, 1e6);
            tpPerKillHealth = b.comment("TP per point of max health of a killed entity")
                    .defineInRange("tpPerKillHealth", 1.0, 0.0, 1e6);
            tpChargeTrainingInterval = b.comment("Charging ki grants TP every this many ticks (spiritual training)")
                    .defineInRange("tpChargeTrainingInterval", 100, 1, 72000);
            tpPerChargeInterval = b.defineInRange("tpPerChargeInterval", 2, 0, 1_000_000);
            b.pop();

            b.comment("Forms and the Overdrive buff (see transform package)").push("transformations");
            unlockLevelScale = b.comment("Multiplies every form's unlock level (0.5 = forms unlock twice as early)")
                    .defineInRange("unlockLevelScale", 1.0, 0.0, 100.0);
            transformKiCostPercent = b.comment("Ki spent to transform, % of max ki")
                    .defineInRange("transformKiCostPercent", 5.0, 0.0, 100.0);
            masteryGainPerSecond = b.comment("Form mastery (0-100) gained per second in a form, before spirit and tier scaling")
                    .defineInRange("masteryGainPerSecond", 0.05, 0.0, 100.0);
            masteryMaxMultiplierBonus = b.comment("At 100 mastery a form's bonus (multiplier - 1) grows by this fraction")
                    .defineInRange("masteryMaxMultiplierBonus", 0.2, 0.0, 10.0);
            masteryMaxDrainReduction = b.comment("At 100 mastery a form's drain is reduced by this fraction")
                    .defineInRange("masteryMaxDrainReduction", 0.75, 0.0, 1.0);
            overdriveLevels = b.comment("Overdrive multipliers per level, lowest first")
                    .defineList("overdriveLevels", java.util.List.of(2.0, 3.0, 4.0, 10.0, 20.0), o -> o instanceof Double d && d >= 1.0);
            overdriveUnlockLevel = b.comment("Character level needed to use Overdrive (or the 'overdrive' flag)")
                    .defineInRange("overdriveUnlockLevel", 100, 0, 1_000_000);
            overdriveMasteryPerLevel = b.comment("Overdrive mastery needed per extra level (level 1 always allowed)")
                    .defineInRange("overdriveMasteryPerLevel", 20.0, 0.0, 100.0);
            overdriveBodyDrainPercent = b.comment("Body drained per second, % of max, per point of multiplier above 1")
                    .defineInRange("overdriveBodyDrainPercent", 0.4, 0.0, 100.0);
            overdriveStaminaDrainPercent = b.comment("Stamina drained per second, % of max, per point of multiplier above 1")
                    .defineInRange("overdriveStaminaDrainPercent", 1.0, 0.0, 100.0);
            overdriveBacklashPercent = b.comment("On ending Overdrive: body lost, % of max, per point of multiplier above 1")
                    .defineInRange("overdriveBacklashPercent", 0.5, 0.0, 100.0);
            overdriveMinBodyPercent = b.comment("Overdrive switches off below this body %")
                    .defineInRange("overdriveMinBodyPercent", 10.0, 0.0, 100.0);
            b.pop();

            b.comment("Racial mechanics and the technique deck").push("races");
            zenkaiTriggerPercent = b.comment("Saiyan Zenkai arms when body falls below this %")
                    .defineInRange("zenkaiTriggerPercent", 15.0, 0.0, 100.0);
            zenkaiRecoverPercent = b.comment("...and fires when body recovers to this %")
                    .defineInRange("zenkaiRecoverPercent", 60.0, 0.0, 100.0);
            zenkaiCooldownTicks = b.comment("Minimum ticks between Zenkai boosts")
                    .defineInRange("zenkaiCooldownTicks", 12000, 0, 10_000_000);
            deckBaseSlots = b.comment("Technique deck slots every character has")
                    .defineInRange("deckBaseSlots", 4, 1, 20);
            deckLevelsPerExtraSlot = b.comment("One extra deck slot per this many character levels")
                    .defineInRange("deckLevelsPerExtraSlot", 250, 1, 1_000_000);
            deckMaxSlots = b.defineInRange("deckMaxSlots", 8, 1, 20);
            b.pop();

            b.comment("Training: gravity chamber, punching bag, meditation, Hyperbolic Time Chamber").push("training");
            gravityMax = b.comment("Highest setting of the Gravity Chamber").defineInRange("gravityMax", 100, 1, 10_000);
            gravityRadius = b.comment("Gravity Chamber reach in blocks").defineInRange("gravityRadius", 6, 1, 32);
            gravityTolerancePerPoint = b.comment("Gravity tolerated per point of STR + CON (1 + points * this)")
                    .defineInRange("gravityTolerancePerPoint", 0.02, 0.0, 10.0);
            gravityTrainingBonus = b.comment("Training multiplier gained per g above 1")
                    .defineInRange("gravityTrainingBonus", 0.1, 0.0, 10.0);
            gravityMaxTrainingMultiplier = b.defineInRange("gravityMaxTrainingMultiplier", 10.0, 1.0, 1000.0);
            gravitySlowPerExcess = b.comment("Movement slowdown per g above your tolerance (capped at 80%)")
                    .defineInRange("gravitySlowPerExcess", 0.05, 0.0, 1.0);
            gravityBodyDamagePercentPerExcess = b.comment("Body lost per second, % of max, per g above your tolerance (capped at 5%/s)")
                    .defineInRange("gravityBodyDamagePercentPerExcess", 0.4, 0.0, 100.0);
            tpPerPunch = b.comment("TP per Punching Bag hit before multipliers").defineInRange("tpPerPunch", 0.5, 0.0, 1e6);
            punchCooldownTicks = b.defineInRange("punchCooldownTicks", 8, 0, 200);
            tpPerSecondMovingUnderGravity = b.comment("TP per second spent moving under more than 1g, per g")
                    .defineInRange("tpPerSecondMovingUnderGravity", 0.05, 0.0, 1e6);
            meditationStartTicks = b.comment("Sneak and stand still this long to start meditating").defineInRange("meditationStartTicks", 60, 1, 72000);
            tpPerMeditationSecond = b.defineInRange("tpPerMeditationSecond", 0.2, 0.0, 1e6);
            meditationKiRegenMultiplier = b.defineInRange("meditationKiRegenMultiplier", 3.0, 0.0, 100.0);
            chamberTrainingMultiplier = b.comment("Extra training multiplier inside the Hyperbolic Time Chamber")
                    .defineInRange("chamberTrainingMultiplier", 4.0, 1.0, 1000.0);
            chamberGravity = b.comment("Gravity inside the Hyperbolic Time Chamber").defineInRange("chamberGravity", 10.0, 1.0, 10_000.0);
            chamberMaxStayTicks = b.comment("Longest stay in the chamber before being sent back (24000 = one day)")
                    .defineInRange("chamberMaxStayTicks", 24000, 20, 10_000_000);
            b.pop();

            b.comment("Dragon Balls, radar and wishes").push("dragon_balls");
            dragonBallsEnabled = b.comment("Scatter Dragon Balls in the overworld").define("enabled", true);
            dragonBallScatterRadius = b.comment("Balls scatter within this many blocks of world spawn")
                    .defineInRange("scatterRadius", 800, 16, 30_000);
            dragonBallInertTicks = b.comment("After a wish the balls are stone for this long, then scatter again (48000 = 2 days)")
                    .defineInRange("inertTicks", 48000, 0, 10_000_000);
            radarRange = b.comment("Dragon Radar range in blocks").defineInRange("radarRange", 1000, 16, 30_000);
            wishPowerTp = b.comment("TP granted by the power wish").defineInRange("wishPowerTp", 5000L, 0L, Long.MAX_VALUE);
            wishSenzuCount = b.defineInRange("wishSenzuCount", 10, 1, 64);
            wishImmortalityTicks = b.comment("How long the immortality wish lasts (36000 = 30 minutes)")
                    .defineInRange("wishImmortalityTicks", 36000, 20, 10_000_000);
            wishDiamonds = b.defineInRange("wishDiamonds", 16, 1, 64);
            b.pop();

            b.push("character");
            startingAge = b.comment("Physical and mental age (years) of a new character")
                    .defineInRange("startingAge", 16.0, 0.0, 100_000.0);
            b.pop();

            b.push("network");
            syncIntervalTicks = b.comment("Minimum ticks between player-data syncs to the client while values are changing")
                    .defineInRange("syncIntervalTicks", 2, 1, 100);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showDebugOverlay;

        Client(ForgeConfigSpec.Builder b) {
            b.push("hud");
            showDebugOverlay = b.comment("Show the raw stat debug overlay (developer aid, drawn under the HUD position)")
                    .define("showDebugStats", false);
            b.pop();
        }
    }
}
