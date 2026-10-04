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

        // --- TP gains ---
        public final ForgeConfigSpec.DoubleValue tpPerDamageDealt;
        public final ForgeConfigSpec.DoubleValue tpPerKillHealth;
        public final ForgeConfigSpec.IntValue tpChargeTrainingInterval;
        public final ForgeConfigSpec.IntValue tpPerChargeInterval;

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
