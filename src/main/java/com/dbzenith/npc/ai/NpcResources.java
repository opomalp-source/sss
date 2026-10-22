package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * An NPC fighter's pools, which it never had before the combat AI: ki (techniques, flight, vanishes, forms and their
 * drain), stamina (every move it throws, dashes) and a guard meter (as players have). Ki is counted in units, 1,000 at
 * level 1 and a little more with every level, so a technique's own ki cost means the same share for every fighter.
 * Nothing is free: an NPC that spends carelessly runs dry and has to back off and charge.
 */
public final class NpcResources {
    public static final double MAX_STAMINA = 100;

    double ki = 1000, maxKi = 1000, stamina = MAX_STAMINA, guard = 100;
    long guardLockUntil, guardStartTick = Long.MIN_VALUE / 2, lastGuardHit = Long.MIN_VALUE / 2, lastStaminaUse = Long.MIN_VALUE / 2,
            lastKiUse = Long.MIN_VALUE / 2;
    boolean guarding;

    /** Pools for a fighter of this level (keeps the shares it had). */
    public void setLevel(int level) {
        double share = maxKi > 0 ? ki / maxKi : 1;
        maxKi = Math.min(3000, 1000 + 25.0 * Math.max(0, level - 1));
        ki = share * maxKi;
    }

    public double ki() {
        return ki;
    }

    public double maxKi() {
        return maxKi;
    }

    public double kiShare() {
        return maxKi <= 0 ? 0 : ki / maxKi;
    }

    public double staminaShare() {
        return stamina / MAX_STAMINA;
    }

    public double guardShare() {
        return guard / 100.0;
    }

    public boolean guarding() {
        return guarding;
    }

    /** Spends ki if there is enough. */
    public boolean spendKi(double amount, long now) {
        if (amount <= 0) return true;
        if (ki < amount) return false;
        ki -= amount;
        lastKiUse = now;
        return true;
    }

    /** Drains ki (form upkeep, flight): may run it to zero. */
    void drainKi(double amount, long now) {
        ki = Math.max(0, ki - amount);
        lastKiUse = now;
    }

    public boolean spendStamina(double amount, long now) {
        if (amount <= 0) return true;
        if (stamina < amount) return false;
        stamina -= amount;
        lastStaminaUse = now;
        return true;
    }

    void drainStamina(double amount, long now) {
        stamina = Math.max(0, stamina - amount);
        lastStaminaUse = now;
    }

    /** Tests and commands. */
    public void set(double kiShare, double staminaShare) {
        ki = Mth.clamp(kiShare, 0, 1) * maxKi;
        stamina = Mth.clamp(staminaShare, 0, 1) * MAX_STAMINA;
    }

    /**
     * One tick of recovery. {@code powering}: charging ki on purpose (fast, as a player holding Charge);
     * {@code fighting}: in a fight (ki comes back slowly). Stamina returns a second after the last move.
     */
    void tick(long now, boolean powering, boolean fighting) {
        double kiPerSecond = powering ? 0.08 : fighting ? 0.006 : 0.02;
        if (powering || now - lastKiUse > 20) ki = Math.min(maxKi, ki + maxKi * kiPerSecond / 20.0);
        if (now - lastStaminaUse > 20) stamina = Math.min(MAX_STAMINA, stamina + (powering ? 6 : 12) / 20.0);
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!guarding && guard < 100 && now - lastGuardHit >= c.guardRegenDelayTicks.get()) guard = Math.min(100, guard + c.guardRegenPerSecond.get() / 20.0);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("ki", ki);
        t.putDouble("stamina", stamina);
        t.putDouble("guard", guard);
        return t;
    }

    void load(CompoundTag t) {
        ki = t.contains("ki") ? Mth.clamp(t.getDouble("ki"), 0, maxKi) : maxKi;
        stamina = t.contains("stamina") ? Mth.clamp(t.getDouble("stamina"), 0, MAX_STAMINA) : MAX_STAMINA;
        guard = t.contains("guard") ? Mth.clamp(t.getDouble("guard"), 0, 100) : 100;
    }
}
