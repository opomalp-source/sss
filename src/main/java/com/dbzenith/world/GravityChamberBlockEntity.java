package com.dbzenith.world;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Holds the gravity setting and applies it to players within range every half second. */
public class GravityChamberBlockEntity extends BlockEntity {
    public static final int[] STEPS = {1, 2, 5, 10, 25, 50, 100, 200, 500, 1000};

    private int gravity = 1;

    public GravityChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_CHAMBER.get(), pos, state);
    }

    public int getGravity() {
        return gravity;
    }

    /** Next/previous step within the configured maximum. */
    public int cycle(boolean down) {
        int max = DBZConfig.SERVER.gravityMax.get();
        int i = 0;
        while (i < STEPS.length && STEPS[i] < gravity) i++;
        int next = down ? Math.max(0, i - 1) : i + 1;
        if (next >= STEPS.length || STEPS[next] > max) next = 0; // wrap to 1g
        gravity = STEPS[next];
        setChanged();
        return gravity;
    }

    public void setGravity(int g) {
        gravity = Math.max(1, Math.min(g, DBZConfig.SERVER.gravityMax.get()));
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityChamberBlockEntity be) {
        long now = level.getGameTime();
        if (be.gravity <= 1 || now % 10 != 0) return;
        int r = DBZConfig.SERVER.gravityRadius.get();
        AABB area = new AABB(pos).inflate(r);
        for (Player p : level.getEntitiesOfClass(Player.class, area)) {
            ModCapabilities.get(p).ifPresent(d -> d.applyGravity(be.gravity, now + 15));
        }
        if (now % 40 == 0 && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("gravity", gravity);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        gravity = Math.max(1, tag.getInt("gravity"));
    }
}
