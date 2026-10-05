package com.dbzenith.data;

/**
 * Combat v3 bookkeeping for one fighter (runtime only). The few fields the HUD needs (breaker charges, downed, chase
 * window) travel in the sync tag; see {@code combat.CombatMoves} for what they mean.
 */
public final class CombatState {
    private static final long NEVER = Long.MIN_VALUE / 2;

    /** Direction of the armed heavy: see {@code CombatMoves.DIR_*}. */
    public int heavyDir;
    public long lastDashTick = NEVER;

    /** Whom this fighter last launched with a heavy, and when (for chasing). */
    public int launchTargetId = -1;
    public long launchTick = NEVER;
    public int chaseCount;
    public long chaseTick = NEVER;
    /** Until when a chase is on offer (synced for the HUD prompt). */
    public long chaseReadyUntil = NEVER;

    /** When this fighter was last launched, and last chased (and by whom). */
    public long launchedAt = NEVER;
    public long chasedTick = NEVER;
    public int chaserId = -1;

    /** Last blow this fighter threw, for clashes. */
    public int lastMeleeTargetId = -1;
    public long lastMeleeTick = NEVER;

    public int breakerCharges = 2;
    public long breakerRechargeAt = NEVER;
    public long downedUntil = NEVER;
    public long hyperArmorUntil = NEVER;
    public int lastAttackerId = -1;
    /** Down right now (for the public pose flag; kept by {@code CombatMoves}). */
    public boolean downedFlag;

    public boolean downed(long now) {
        return now < downedUntil;
    }

    public void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("breakers", breakerCharges);
        tag.putLong("breakerAt", breakerRechargeAt);
        tag.putLong("downedUntil", downedUntil);
        tag.putLong("chaseReady", chaseReadyUntil);
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        breakerCharges = tag.contains("breakers") ? tag.getInt("breakers") : 2;
        breakerRechargeAt = tag.contains("breakerAt") ? tag.getLong("breakerAt") : NEVER;
        downedUntil = tag.contains("downedUntil") ? tag.getLong("downedUntil") : NEVER;
        chaseReadyUntil = tag.contains("chaseReady") ? tag.getLong("chaseReady") : NEVER;
    }
}
