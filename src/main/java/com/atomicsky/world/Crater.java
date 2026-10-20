package com.atomicsky.world;

import com.atomicsky.ASConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Carves the crater: a wide parabolic bowl (deepest at the centre, shallow at the rim) dug out ring by ring over a
 * few seconds so one tick never stalls. Everything above the bowl in each column goes too, so trees and buildings
 * inside the crater radius vanish with the ground. The floor is left glassed and scorched.
 */
final class Crater {
    private static final int BLOCKS_PER_TICK = 6000;

    static void killInside(ServerLevel level, Detonation d) {
        double r = Math.max(d.yield * 1.5, d.craterR);
        AABB box = new AABB(d.center.x - r, d.center.y - r, d.center.z - r, d.center.x + r, d.center.y + r, d.center.z + r);
        List<Entity> entities = level.getEntities((Entity) null, box, e -> true);
        for (Entity e : entities) {
            double dist = e.position().distanceTo(d.center);
            if (dist > r) continue;
            if (e instanceof LivingEntity living) {
                living.hurt(level.damageSources().explosion(null, null), 10000.0F);
            } else if (!(e instanceof net.minecraft.world.entity.player.Player)) {
                e.discard();
            }
        }
    }

    static void tick(ServerLevel level, Detonation d) {
        if (!ASConfig.DESTROY_BLOCKS.get()) {
            d.craterDone = true;
            return;
        }
        int R = d.craterR;
        int depth = d.craterDepth;
        int budget = BLOCKS_PER_TICK;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = (int) Math.floor(d.center.x), cy = (int) Math.floor(d.center.y), cz = (int) Math.floor(d.center.z);
        BlockState air = Blocks.AIR.defaultBlockState();
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 1;
        // Ring s holds every column whose floor(distance) == s; rings are dug from the centre outward.
        while (d.craterShell <= R && budget > 0) {
            int s = d.craterShell;
            boolean rim = s >= R - 2;
            int flags = rim ? Block.UPDATE_ALL : (Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            for (int dx = -s; dx <= s; dx++) {
                for (int dz = -s; dz <= s; dz++) {
                    int h2 = dx * dx + dz * dz;
                    if ((int) Math.sqrt(h2) != s) continue;
                    double rel = Math.sqrt(h2) / R;                       // 0 at centre, 1 at rim
                    double bowl = depth * (1.0 - rel * rel);              // parabolic profile
                    bowl += jitter(d.seed, dx, dz) * (1.5 + rel * 3.0);   // rough rim, smoother floor
                    if (bowl < 0.5 && !rim) continue;
                    int x = cx + dx, z = cz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int bottom = Math.max(minY, cy - (int) Math.round(bowl));
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    // Inside the bowl everything down to the floor goes, including anything standing on the surface.
                    top = Math.min(maxY, Math.max(top, cy + 2));
                    for (int y = top; y >= bottom; y--) {
                        pos.set(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) continue;
                        if (state.getBlock().getExplosionResistance() >= 1200.0F) continue; // bedrock, obsidian, reinforced blocks survive
                        level.setBlock(pos, air, flags);
                        budget--;
                    }
                    scorchFloor(level, pos.set(x, bottom - 1, z), rel);
                }
            }
            d.craterShell++;
        }
        if (d.craterShell > R) d.craterDone = true;
    }

    /** Deterministic -1..1 noise per column so the bowl is rough but the same every tick. */
    private static double jitter(long seed, int dx, int dz) {
        long h = seed ^ (dx * 0x9E3779B97F4A7C15L) ^ (dz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return ((h & 0xFFFF) / 65535.0) * 2.0 - 1.0;
    }

    /** Glassed, scorched floor: sand turns to glass near the middle, soil to coarse dirt, stone to blackstone. */
    private static void scorchFloor(ServerLevel level, BlockPos pos, double rel) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return;
        if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
            level.setBlock(pos, rel < 0.6 ? Blocks.GLASS.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
            level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (s.is(Blocks.STONE) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE) || s.is(Blocks.ANDESITE) || s.is(Blocks.TUFF)) {
            level.setBlock(pos, rel < 0.35 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (s.is(Blocks.DEEPSLATE)) {
            level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Crater() {}
}
