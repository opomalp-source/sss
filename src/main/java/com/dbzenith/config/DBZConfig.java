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
        /** Bump when a balance pass changes defaults; older config files get the listed values reset once. */
        public static final int BALANCE_VERSION = 3;
        public final ForgeConfigSpec.IntValue balanceVersion;
        // --- attributes ---
        public final ForgeConfigSpec.IntValue startingAttribute;
        public final ForgeConfigSpec.IntValue attributeSoftCap;
        public final ForgeConfigSpec.IntValue attributeHardCap;

        // --- training points ---
        public final ForgeConfigSpec.DoubleValue tpCostBase;
        public final ForgeConfigSpec.DoubleValue tpCostPerPoint;
        public final ForgeConfigSpec.DoubleValue tpSoftCapCostMultiplier;
        public final ForgeConfigSpec.DoubleValue tpGainPerMind;
        public final ForgeConfigSpec.DoubleValue tpGainMultiplier;

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
        public final ForgeConfigSpec.DoubleValue guardBreakBodyFraction;
        public final ForgeConfigSpec.DoubleValue guardHitCost;
        public final ForgeConfigSpec.DoubleValue guardRegenPerSecond;
        public final ForgeConfigSpec.IntValue guardRegenDelayTicks;
        public final ForgeConfigSpec.IntValue guardBreakLockTicks;
        public final ForgeConfigSpec.IntValue guardBreakStunTicks;
        public final ForgeConfigSpec.IntValue parryWindowTicks;
        public final ForgeConfigSpec.IntValue deflectWindowTicks;
        public final ForgeConfigSpec.IntValue parryStunTicks;
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
        public final ForgeConfigSpec.DoubleValue airComboBonus;
        public final ForgeConfigSpec.DoubleValue airJuggleLift;
        public final ForgeConfigSpec.DoubleValue knockUpVelocity;
        public final ForgeConfigSpec.DoubleValue spikeVelocity;
        public final ForgeConfigSpec.DoubleValue spikeDamageBonus;
        public final ForgeConfigSpec.IntValue grabHoldTicks;
        public final ForgeConfigSpec.DoubleValue throwSpeed;
        public final ForgeConfigSpec.DoubleValue throwDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue kiTransferSeconds;

        // --- TP gains ---
        public final ForgeConfigSpec.DoubleValue tpPerHit;
        public final ForgeConfigSpec.DoubleValue tpPerKill;
        public final ForgeConfigSpec.IntValue tpChargeTrainingInterval;
        public final ForgeConfigSpec.IntValue tpPerChargeInterval;

        // --- transformations ---
        public final ForgeConfigSpec.DoubleValue unlockLevelScale;
        public final ForgeConfigSpec.DoubleValue rareVariantChance;
        public final ForgeConfigSpec.IntValue milestoneLevel;
        public final ForgeConfigSpec.DoubleValue transformKiCostPercent;
        public final ForgeConfigSpec.DoubleValue masteryGainPerSecond;
        public final ForgeConfigSpec.DoubleValue instantTransformMastery;
        public final ForgeConfigSpec.IntValue transformTimeBase;
        public final ForgeConfigSpec.IntValue transformTimePerTier;
        public final ForgeConfigSpec.DoubleValue transformInterruptDamage;
        public final ForgeConfigSpec.DoubleValue godKiXpPerSecond;
        public final ForgeConfigSpec.DoubleValue masteryMaxMultiplierBonus;
        public final ForgeConfigSpec.DoubleValue masteryMaxDrainReduction;
        public final ForgeConfigSpec.DoubleValue techniqueMasteryPerUse;
        public final ForgeConfigSpec.DoubleValue techniqueMasteryDamageBonus;
        public final ForgeConfigSpec.DoubleValue techniqueMasteryCostReduction;
        public final ForgeConfigSpec.DoubleValue techniqueMasteryCooldownReduction;
        public final ForgeConfigSpec.IntValue prestigeLevel;
        public final ForgeConfigSpec.DoubleValue prestigeTpBonus;
        public final ForgeConfigSpec.DoubleValue prestigePowerBonus;
        public final ForgeConfigSpec.DoubleValue godKiEdge;

        // --- races ---
        public final ForgeConfigSpec.DoubleValue zenkaiTriggerPercent;
        public final ForgeConfigSpec.DoubleValue zenkaiRecoverPercent;
        public final ForgeConfigSpec.IntValue zenkaiCooldownTicks;
        public final ForgeConfigSpec.DoubleValue fusionAttributeShare;
        public final ForgeConfigSpec.IntValue fusionMax;
        public final ForgeConfigSpec.DoubleValue fusionNpcShare;
        public final ForgeConfigSpec.DoubleValue absorbHealthThreshold;
        public final ForgeConfigSpec.DoubleValue majinAbsorbBonusPerStack;
        public final ForgeConfigSpec.IntValue majinAbsorbMaxStacks;
        public final ForgeConfigSpec.IntValue majinAbsorbDurationTicks;
        public final ForgeConfigSpec.DoubleValue alignmentBonus;
        public final ForgeConfigSpec.DoubleValue pathDamageBonus;
        public final ForgeConfigSpec.DoubleValue pathPoolBonus;
        public final ForgeConfigSpec.DoubleValue hybridTpBonus;
        public final ForgeConfigSpec.DoubleValue tailCutChance;
        public final ForgeConfigSpec.IntValue tailRegrowTicks;
        public final ForgeConfigSpec.DoubleValue wisdomTpPerYear;
        public final ForgeConfigSpec.DoubleValue wisdomTpMax;
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
        public final ForgeConfigSpec.IntValue chamberCooldownTicks;
        public final ForgeConfigSpec.DoubleValue trainingMultiplierCap;

        // --- dragon balls ---
        public final ForgeConfigSpec.BooleanValue dragonBallsEnabled;
        public final ForgeConfigSpec.IntValue dragonBallScatterRadius;
        public final ForgeConfigSpec.IntValue dragonBallInertTicks;
        public final ForgeConfigSpec.IntValue dragonWaitTicks;
        public final ForgeConfigSpec.IntValue radarRange;
        public final ForgeConfigSpec.LongValue wishPowerTp;
        public final ForgeConfigSpec.IntValue wishSenzuCount;
        public final ForgeConfigSpec.IntValue wishImmortalityTicks;
        public final ForgeConfigSpec.IntValue wishDiamonds;

        // --- gear ---
        public final ForgeConfigSpec.LongValue scouterLimit;
        public final ForgeConfigSpec.IntValue scouterRange;
        public final ForgeConfigSpec.IntValue spacePodRechargeTicks;
        public final ForgeConfigSpec.IntValue falseMoonTicks;

        // --- enemies ---
        public final ForgeConfigSpec.DoubleValue enemyPowerPerLevel;
        public final ForgeConfigSpec.IntValue enemyMaxLevel;
        public final ForgeConfigSpec.DoubleValue fighterArmorEffect;
        public final ForgeConfigSpec.DoubleValue enemyHealthPerLevel;
        public final ForgeConfigSpec.DoubleValue enemyDamagePerLevel;
        public final ForgeConfigSpec.DoubleValue bossEnrageHealth;

        // --- life sim ---
        public final ForgeConfigSpec.BooleanValue agingEnabled;
        public final ForgeConfigSpec.DoubleValue agingDaysPerYear;
        public final ForgeConfigSpec.BooleanValue thirstEnabled;
        public final ForgeConfigSpec.DoubleValue thirstDaysToEmpty;
        public final ForgeConfigSpec.BooleanValue temperatureEnabled;
        public final ForgeConfigSpec.DoubleValue partnerTpBonus;
        public final ForgeConfigSpec.IntValue partnerRange;

        // --- character ---
        public final ForgeConfigSpec.DoubleValue startingAge;

        // --- networking ---
        public final ForgeConfigSpec.IntValue syncIntervalTicks;

        Server(ForgeConfigSpec.Builder b) {
            balanceVersion = b.comment("Balance revision of this file. When the mod rebalances, values from older revisions are reset to the new defaults once (see BALANCE.md). Leave it alone.")
                    .defineInRange("balanceVersion", 0, 0, 1000);
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
                    .defineInRange("tpCostPerPoint", 0.45, 0.0, 1e9);
            tpSoftCapCostMultiplier = b.comment("Cost multiplier once an attribute is at or above the soft cap")
                    .defineInRange("tpSoftCapCostMultiplier", 2.0, 1.0, 1000.0);
            tpGainPerMind = b.comment("Fractional bonus to all TP gains per point of MIND (0.002 = +0.2%)")
                    .defineInRange("tpGainPerMind", 0.002, 0.0, 10.0);
            tpGainMultiplier = b.comment("Global speed of progression: every TP gain is multiplied by this (2.0 = twice as fast)")
                    .defineInRange("tpGainMultiplier", 1.0, 0.01, 1000.0);
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
            guardBreakBodyFraction = b.comment("Guard meter: blocking this fraction of your max body in one go empties it (the guard breaks)")
                    .defineInRange("guardBreakBodyFraction", 0.4, 0.01, 10.0);
            guardHitCost = b.comment("Guard meter spent by every blocked hit, on top of the damage-based cost (meter is 0-100)")
                    .defineInRange("guardHitCost", 4.0, 0.0, 100.0);
            guardRegenPerSecond = b.comment("Guard meter refilled per second while not guarding")
                    .defineInRange("guardRegenPerSecond", 15.0, 0.0, 1000.0);
            guardRegenDelayTicks = b.comment("Ticks after the last blocked hit before the guard meter refills")
                    .defineInRange("guardRegenDelayTicks", 20, 0, 1200);
            guardBreakLockTicks = b.comment("After a guard break you cannot guard for this many ticks")
                    .defineInRange("guardBreakLockTicks", 60, 0, 1200);
            guardBreakStunTicks = b.comment("A guard break stuns you for this many ticks")
                    .defineInRange("guardBreakStunTicks", 30, 0, 200);
            parryWindowTicks = b.comment("Raising your guard this many ticks before a blow lands parries it: no damage, the attacker is staggered")
                    .defineInRange("parryWindowTicks", 5, 0, 40);
            deflectWindowTicks = b.comment("Raising your guard this many ticks before a ki blast hits sends it back")
                    .defineInRange("deflectWindowTicks", 8, 0, 40);
            parryStunTicks = b.comment("How long a parried attacker is staggered (stunned), in ticks")
                    .defineInRange("parryStunTicks", 25, 0, 200);
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
            airComboBonus = b.comment("Extra melee damage against an airborne target (air combo)")
                    .defineInRange("airComboBonus", 0.15, 0.0, 10.0);
            airJuggleLift = b.comment("Upward speed given to an airborne target on each hit, keeping it in the air")
                    .defineInRange("airJuggleLift", 0.18, 0.0, 2.0);
            knockUpVelocity = b.comment("A heavy hit from the ground launches the target up at this speed (blocks/tick)")
                    .defineInRange("knockUpVelocity", 1.1, 0.0, 5.0);
            spikeVelocity = b.comment("A heavy hit from above while looking down slams the target down at this speed")
                    .defineInRange("spikeVelocity", 2.0, 0.0, 10.0);
            spikeDamageBonus = b.defineInRange("spikeDamageBonus", 0.25, 0.0, 10.0);
            grabHoldTicks = b.comment("Grab and Throw: longest hold before the target is thrown automatically")
                    .defineInRange("grabHoldTicks", 60, 1, 1200);
            throwSpeed = b.defineInRange("throwSpeed", 2.2, 0.0, 10.0);
            throwDamageMultiplier = b.comment("Impact damage of a thrown target, times the thrower's melee damage")
                    .defineInRange("throwDamageMultiplier", 1.5, 0.0, 100.0);
            kiTransferSeconds = b.comment("Ki Transfer gives this many seconds of the ki-transfer rate (SPI) in one use")
                    .defineInRange("kiTransferSeconds", 5.0, 0.1, 1000.0);
            b.pop();

            b.comment("Training point gains").push("tp_gains");
            tpPerHit = b.comment("TP per landed hit = this x square root of the DBZ damage dealt")
                    .defineInRange("tpPerHit", 0.04, 0.0, 1e6);
            tpPerKill = b.comment("TP per kill = this x square root of the victim's effective max health (leveled foes count their toughness)")
                    .defineInRange("tpPerKill", 0.5, 0.0, 1e6);
            tpChargeTrainingInterval = b.comment("Charging ki grants TP every this many ticks (spiritual training)")
                    .defineInRange("tpChargeTrainingInterval", 400, 1, 72000);
            tpPerChargeInterval = b.defineInRange("tpPerChargeInterval", 2, 0, 1_000_000);
            b.pop();

            b.comment("Forms (see transform package)").push("transformations");
            unlockLevelScale = b.comment("Multiplies every form's unlock level (0.5 = forms unlock twice as early)")
                    .defineInRange("unlockLevelScale", 1.0, 0.0, 100.0);
            rareVariantChance = b.comment("Chance a new character carries a rare destiny (Legendary Saiyan, Mutant Frost Demon, Corrupted Majin...)")
                    .defineInRange("rareVariantChance", 0.05, 0.0, 1.0);
            milestoneLevel = b.comment("Level of the first milestone: rare destinies awaken and paths (Half-Saiyan, Human) are chosen")
                    .defineInRange("milestoneLevel", 150, 1, 100000);
            transformKiCostPercent = b.comment("Ki spent to transform, % of max ki")
                    .defineInRange("transformKiCostPercent", 5.0, 0.0, 100.0);
            masteryGainPerSecond = b.comment("Form mastery (0-100) gained per second in a form, before spirit and tier scaling")
                    .defineInRange("masteryGainPerSecond", 0.05, 0.0, 100.0);
            instantTransformMastery = b.comment("Mastery of a form (0-100) at which transforming into it becomes instant; below it the transformation powers up and a hit can interrupt it")
                    .defineInRange("instantTransformMastery", 75.0, 0.0, 100.0);
            transformTimeBase = b.comment("Ticks to power up into an unmastered tier-0 form")
                    .defineInRange("transformTimeBase", 24, 0, 400);
            transformTimePerTier = b.comment("Extra power-up ticks per form tier")
                    .defineInRange("transformTimePerTier", 14, 0, 400);
            transformInterruptDamage = b.comment("A hit for at least this fraction of max health interrupts a transformation")
                    .defineInRange("transformInterruptDamage", 0.03, 0.0, 1.0);
            godKiXpPerSecond = b.comment("God ki experience per second spent in a god form (half while meditating with god ki)")
                    .defineInRange("godKiXpPerSecond", 1.0, 0.0, 1000.0);
            masteryMaxMultiplierBonus = b.comment("At 100 mastery a form's bonus (multiplier - 1) grows by this fraction")
                    .defineInRange("masteryMaxMultiplierBonus", 0.2, 0.0, 10.0);
            masteryMaxDrainReduction = b.comment("At 100 mastery a form's drain is reduced by this fraction")
                    .defineInRange("masteryMaxDrainReduction", 0.75, 0.0, 1.0);
            techniqueMasteryPerUse = b.comment("Technique mastery gained per use, times the SPI spirit modifier (100 = mastered)")
                    .defineInRange("techniqueMasteryPerUse", 0.5, 0.0, 100.0);
            techniqueMasteryDamageBonus = b.comment("At 100 technique mastery: damage bonus")
                    .defineInRange("techniqueMasteryDamageBonus", 0.25, 0.0, 10.0);
            techniqueMasteryCostReduction = b.comment("At 100 technique mastery: ki cost reduction")
                    .defineInRange("techniqueMasteryCostReduction", 0.30, 0.0, 0.95);
            techniqueMasteryCooldownReduction = b.comment("At 100 technique mastery: cooldown reduction")
                    .defineInRange("techniqueMasteryCooldownReduction", 0.25, 0.0, 0.95);
            prestigeLevel = b.comment("Character level needed to prestige (attributes and TP reset for a permanent bonus)")
                    .defineInRange("prestigeLevel", 2000, 1, 10_000_000);
            prestigeTpBonus = b.comment("TP gain bonus per prestige").defineInRange("prestigeTpBonus", 0.25, 0.0, 100.0);
            prestigePowerBonus = b.comment("STR/DEX/KI_POWER multiplier bonus per prestige").defineInRange("prestigePowerBonus", 0.05, 0.0, 10.0);
            godKiEdge = b.comment("God ki against ordinary ki: damage dealt up and taken down by this fraction")
                    .defineInRange("godKiEdge", 0.25, 0.0, 0.95);
            b.pop();

            b.comment("Racial mechanics and the technique deck").push("races");
            zenkaiTriggerPercent = b.comment("Saiyan Zenkai arms when body falls below this %")
                    .defineInRange("zenkaiTriggerPercent", 15.0, 0.0, 100.0);
            zenkaiRecoverPercent = b.comment("...and fires when body recovers to this %")
                    .defineInRange("zenkaiRecoverPercent", 60.0, 0.0, 100.0);
            zenkaiCooldownTicks = b.comment("Minimum ticks between Zenkai boosts (36000 = 30 minutes). Each boost adds about 0.25 x sqrt(value) to STR, DEX, CON and KI_POWER (Half-Saiyans 0.15)")
                    .defineInRange("zenkaiCooldownTicks", 36000, 0, 10_000_000);
            fusionAttributeShare = b.comment("Namekian fusion with a player: share of the partner's attributes added to yours")
                    .defineInRange("fusionAttributeShare", 0.25, 0.0, 1.0);
            fusionMax = b.comment("Most fusions one Namekian can make").defineInRange("fusionMax", 3, 0, 100);
            fusionNpcShare = b.comment("Fusing with a Namekian Warrior raises STR, CON, KI_POWER and SPI by this share of their value (at least 3 points)")
                    .defineInRange("fusionNpcShare", 0.05, 0.0, 10.0);
            absorbHealthThreshold = b.comment("Fusion and absorption need the target at or below this share of its health")
                    .defineInRange("absorbHealthThreshold", 0.25, 0.0, 1.0);
            majinAbsorbBonusPerStack = b.comment("Majin absorption: STR/DEX/KI_POWER multiplier bonus per absorbed fighter")
                    .defineInRange("majinAbsorbBonusPerStack", 0.10, 0.0, 10.0);
            majinAbsorbMaxStacks = b.defineInRange("majinAbsorbMaxStacks", 3, 1, 100);
            majinAbsorbDurationTicks = b.comment("How long absorbed power lasts (12000 = 10 minutes)")
                    .defineInRange("majinAbsorbDurationTicks", 12000, 20, 10_000_000);
            alignmentBonus = b.comment("Good (alignment 30+): ki regen bonus. Evil (-30 or less): damage bonus")
                    .defineInRange("alignmentBonus", 0.10, 0.0, 10.0);
            pathDamageBonus = b.comment("Fighter path: melee damage bonus. Spiritualist path: ki damage bonus")
                    .defineInRange("pathDamageBonus", 0.05, 0.0, 10.0);
            pathPoolBonus = b.comment("Fighter path: max stamina bonus. Spiritualist path: max ki bonus")
                    .defineInRange("pathPoolBonus", 0.10, 0.0, 10.0);
            hybridTpBonus = b.comment("Hybrid path: TP gain bonus").defineInRange("hybridTpBonus", 0.05, 0.0, 10.0);
            tailCutChance = b.comment("Chance that a hit from a sword or axe cuts off a Saiyan tail")
                    .defineInRange("tailCutChance", 0.10, 0.0, 1.0);
            tailRegrowTicks = b.comment("A cut tail grows back after this long (72000 = 3 days)")
                    .defineInRange("tailRegrowTicks", 72000, 20, 100_000_000);
            wisdomTpPerYear = b.comment("TP gain bonus per year of mental age above 20 (wisdom)")
                    .defineInRange("wisdomTpPerYear", 0.01, 0.0, 1.0);
            wisdomTpMax = b.defineInRange("wisdomTpMax", 0.40, 0.0, 10.0);
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
            tpPerPunch = b.comment("TP per Punching Bag hit before multipliers").defineInRange("tpPerPunch", 0.08, 0.0, 1e6);
            punchCooldownTicks = b.defineInRange("punchCooldownTicks", 8, 0, 200);
            tpPerSecondMovingUnderGravity = b.comment("TP per second spent moving under more than 1g, per g")
                    .defineInRange("tpPerSecondMovingUnderGravity", 0.01, 0.0, 1e6);
            meditationStartTicks = b.comment("Sneak and stand still this long to start meditating").defineInRange("meditationStartTicks", 60, 1, 72000);
            tpPerMeditationSecond = b.defineInRange("tpPerMeditationSecond", 0.08, 0.0, 1e6);
            meditationKiRegenMultiplier = b.defineInRange("meditationKiRegenMultiplier", 3.0, 0.0, 100.0);
            chamberTrainingMultiplier = b.comment("Extra training multiplier inside the Hyperbolic Time Chamber")
                    .defineInRange("chamberTrainingMultiplier", 4.0, 1.0, 1000.0);
            chamberGravity = b.comment("Gravity inside the Hyperbolic Time Chamber").defineInRange("chamberGravity", 10.0, 1.0, 10_000.0);
            chamberMaxStayTicks = b.comment("Longest stay in the chamber before being sent back (24000 = one day)")
                    .defineInRange("chamberMaxStayTicks", 24000, 20, 10_000_000);
            chamberCooldownTicks = b.comment("After leaving the chamber you must wait this long to enter again (24000 = one day)")
                    .defineInRange("chamberCooldownTicks", 24000, 0, 10_000_000);
            trainingMultiplierCap = b.comment("Gravity, weights and the Time Chamber multiply together, up to this")
                    .defineInRange("trainingMultiplierCap", 4.0, 1.0, 1000.0);
            b.pop();

            b.comment("Dragon Balls, radar and wishes").push("dragon_balls");
            dragonBallsEnabled = b.comment("Scatter Dragon Balls in the overworld").define("enabled", true);
            dragonBallScatterRadius = b.comment("Balls scatter within this many blocks of world spawn")
                    .defineInRange("scatterRadius", 800, 16, 30_000);
            dragonBallInertTicks = b.comment("After a wish the balls are stone for this long, then scatter again (48000 = 2 days)")
                    .defineInRange("inertTicks", 48000, 0, 10_000_000);
            dragonWaitTicks = b.comment("How long the Eternal Dragon waits for a wish (2400 = 2 minutes)")
                    .defineInRange("dragonWaitTicks", 2400, 200, 1_000_000);
            radarRange = b.comment("Dragon Radar range in blocks").defineInRange("radarRange", 1000, 16, 30_000);
            wishPowerTp = b.comment("TP granted by the power wish").defineInRange("wishPowerTp", 5000L, 0L, Long.MAX_VALUE);
            wishSenzuCount = b.defineInRange("wishSenzuCount", 10, 1, 64);
            wishImmortalityTicks = b.comment("How long the immortality wish lasts (36000 = 30 minutes)")
                    .defineInRange("wishImmortalityTicks", 36000, 20, 10_000_000);
            wishDiamonds = b.defineInRange("wishDiamonds", 16, 1, 64);
            b.pop();

            b.push("gear");
            scouterLimit = b.comment("A scouter shatters when it reads a power level above this")
                    .defineInRange("scouterLimit", 1_000_000L, 1L, Long.MAX_VALUE);
            scouterRange = b.comment("How far a scouter reads, in blocks").defineInRange("scouterRange", 64, 4, 512);
            spacePodRechargeTicks = b.comment("Space Pod recharge time between flights (1200 = 1 minute)")
                    .defineInRange("spacePodRechargeTicks", 1200, 0, 10_000_000);
            falseMoonTicks = b.comment("How long a Moon Orb's false moon shines (1200 = 1 minute)")
                    .defineInRange("falseMoonTicks", 1200, 20, 1_000_000);
            b.pop();

            b.comment("Enemy fighters and bosses scale to the strongest nearby player").push("enemies");
            enemyPowerPerLevel = b.comment("Enemy level = nearest player's full power level (at 100% release) / this, at least 1. Linear, so foes keep pace")
                    .defineInRange("powerPerLevel", 700.0, 1.0, 1e12);
            enemyMaxLevel = b.defineInRange("maxLevel", 200, 1, 10_000);
            fighterArmorEffect = b.comment("How much vanilla armour counts against fighters' punches (they hit through it; 1 = fully)")
                    .defineInRange("fighterArmorEffect", 0.25, 0.0, 1.0);
            enemyHealthPerLevel = b.comment("Health gained per enemy level (fraction of base)").defineInRange("healthPerLevel", 0.5, 0.0, 100.0);
            enemyDamagePerLevel = b.comment("Damage gained per enemy level (fraction of base)").defineInRange("damagePerLevel", 0.55, 0.0, 100.0);
            bossEnrageHealth = b.comment("Bosses enrage below this share of their health").defineInRange("bossEnrageHealth", 0.5, 0.0, 1.0);
            b.pop();

            b.comment("Optional life-sim layer").push("life_sim");
            agingEnabled = b.comment("Characters age with in-game time; past 60, STR and DEX slowly decline")
                    .define("agingEnabled", true);
            agingDaysPerYear = b.comment("In-game days per year of age (Androids and Majins never age; Namekians and Frost Demons age 4x slower)")
                    .defineInRange("agingDaysPerYear", 8.0, 0.1, 10_000.0);
            thirstEnabled = b.comment("Thirst: drink (water bottles, milk, soups, melon) or swim to refill. Thirsty fighters recover stamina slowly")
                    .define("thirstEnabled", true);
            thirstDaysToEmpty = b.comment("In-game days for a full thirst bar to run dry (twice as fast while charging, flying or hot)")
                    .defineInRange("thirstDaysToEmpty", 2.0, 0.05, 1000.0);
            temperatureEnabled = b.comment("Deserts by day and the Nether are hot, snowy places are cold (stamina recovers slowly; a fire nearby or a chestplate keeps you warm)")
                    .define("temperatureEnabled", true);
            partnerTpBonus = b.comment("Partners training near each other gain this much more TP")
                    .defineInRange("partnerTpBonus", 0.10, 0.0, 10.0);
            partnerRange = b.defineInRange("partnerRange", 32, 1, 1000);
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
        public final ForgeConfigSpec.BooleanValue customHotbar;
        public final ForgeConfigSpec.BooleanValue hideVanillaHearts;
        public final ForgeConfigSpec.BooleanValue transformCutIn;
        public final ForgeConfigSpec.DoubleValue screenShake;
        public final ForgeConfigSpec.BooleanValue hitstop;
        public final ForgeConfigSpec.BooleanValue afterimages;
        public final ForgeConfigSpec.BooleanValue firstPersonAura;
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.IntValue auraDetail;
        public final ForgeConfigSpec.BooleanValue fovEffects;
        public final ForgeConfigSpec.BooleanValue hairPhysics;
        public final ForgeConfigSpec.BooleanValue proceduralMotion;
        public final ForgeConfigSpec.BooleanValue hdArt;

        Client(ForgeConfigSpec.Builder b) {
            b.push("hud");
            showDebugOverlay = b.comment("Show the raw stat debug overlay (developer aid, drawn under the HUD position)")
                    .define("showDebugStats", false);
            customHotbar = b.comment("Draw the hotbar in the mod's style (cloud-trimmed glass slots)")
                    .define("customHotbar", true);
            hideVanillaHearts = b.comment("Hide vanilla hearts and armour: the Body bar already shows your health")
                    .define("hideVanillaHearts", true);
            transformCutIn = b.comment("Play a cut-in (portrait slash and form name) when you transform")
                    .define("transformCutIn", true);
            hudScale = b.comment("Size of the portrait HUD (0.6 - 1.4)")
                    .defineInRange("hudScale", 1.0, 0.6, 1.4);
            b.pop();
            b.push("effects");
            screenShake = b.comment("Camera shake strength from hits, explosions and landings (0 = off)")
                    .defineInRange("screenShake", 1.0, 0.0, 2.0);
            hitstop = b.comment("Freeze the fighters' animations for a few frames when a blow lands")
                    .define("hitstop", true);
            afterimages = b.comment("Draw afterimages behind dashing and fast-flying fighters")
                    .define("afterimages", true);
            firstPersonAura = b.comment("Show your own aura and transformation flashes around the screen edge in first person")
                    .define("firstPersonAura", true);
            auraDetail = b.comment("Aura detail: 0 low (fewer flame tongues, no licks), 1 normal, 2 high")
                    .defineInRange("auraDetail", 1, 0, 2);
            fovEffects = b.comment("Zoom punch on heavy blows and the widening rush on dashes")
                    .define("fovEffects", true);
            hairPhysics = b.comment("Hair sways with movement, falling, turning and auras")
                    .define("hairPhysics", true);
            hdArt = b.comment("High-detail textures for bodies and faces (off: the classic pixel look)")
                    .define("hdArt", true);
            proceduralMotion = b.comment("Bodies bank into flying turns, lean into climbs, dives and fast runs, and squash on hard landings")
                    .define("proceduralMotion", true);
            b.pop();
        }
    }
}
