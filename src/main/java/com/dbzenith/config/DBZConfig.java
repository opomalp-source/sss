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

        // --- ki / release ---
        public final ForgeConfigSpec.IntValue defaultReleasePercent;

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
            showDebugOverlay = b.comment("Show the raw stat debug overlay in the top-left corner")
                    .define("showDebugOverlay", true);
            b.pop();
        }
    }
}
