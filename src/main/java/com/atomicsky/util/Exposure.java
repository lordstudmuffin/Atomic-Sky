package com.atomicsky.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * "Is this spot out in the open?" for shelter from the shockwave and the fallout. Under a sky that is canSeeSky; in a
 * dimension with a ceiling (the Nether) canSeeSky is false everywhere, which would make every player sheltered and
 * nobody irradiated, so there it means no solid block in the 12 blocks overhead instead.
 */
public final class Exposure {
    public static boolean exposed(Level level, BlockPos pos) {
        if (!level.dimensionType().hasCeiling()) return level.canSeeSky(pos);
        BlockPos.MutableBlockPos p = pos.mutable();
        int top = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + 12);
        for (int y = pos.getY() + 1; y <= top; y++) {
            p.setY(y);
            if (level.getBlockState(p).isSolid()) return false;
        }
        return true;
    }

    private Exposure() {}
}
