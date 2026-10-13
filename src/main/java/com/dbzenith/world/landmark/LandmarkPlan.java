package com.dbzenith.world.landmark;

import net.minecraft.core.BlockPos;

/**
 * One landmark at one site, laid out once (CX-33): its sizes, heights and random choices are fixed when the plan is
 * made (from the site's seed and the terrain's own noise, so every chunk gets the same plan), and {@link #build} then
 * draws whatever part of it falls in the chunk being generated. Plans are shared between generation threads: they
 * must not change after construction.
 */
public abstract class LandmarkPlan {
    public final Landmark type;
    /** The centre of the site and its seed. */
    public final int x, z;
    public final long seed;

    protected LandmarkPlan(LandmarkSites.Site site) {
        this.type = site.type();
        this.x = site.x();
        this.z = site.z();
        this.seed = site.seed();
    }

    /** Draws the part of this landmark inside the canvas's chunk. */
    public abstract void build(Canvas c);

    /**
     * The height above which this landmark owns the column at x, z (natural leaves and plants there are swept away),
     * or {@link Integer#MAX_VALUE} where it does not. Neighbouring chunks' trees can reach a few blocks into a chunk
     * after it was built; the sweep removes them.
     */
    public int ownedAbove(int x, int z) {
        return Integer.MAX_VALUE;
    }

    /** Where /landmark tp puts you: somewhere to stand and look at it. */
    public abstract BlockPos arrival();
}
