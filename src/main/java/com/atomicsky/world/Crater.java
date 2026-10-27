package com.atomicsky.world;

import com.atomicsky.ASConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
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
 * inside the crater radius vanish with the ground. The floor is left glassed and scorched. Chunks that were not
 * loaded while the rings went past are dug when they next load (see {@link #catchUpChunk}).
 */
final class Crater {
    private static final int BLOCKS_PER_TICK = 6000;

    /** Depth of the bowl below the blast centre at a horizontal distance from it (0 outside the crater). */
    static double bowlDepth(Detonation d, double horizontalDist) {
        if (horizontalDist >= d.craterR) return 0;
        double rel = horizontalDist / d.craterR;
        return d.craterDepth * (1.0 - rel * rel);
    }

    /**
     * The first tick's lethal zone: the fireball around the bomb, and everything that is about to fall into the bowl.
     * Nothing below the bowl's floor is touched, so a bunker dug deeper than the crater is a bunker.
     */
    static void killInside(ServerLevel level, Detonation d) {
        double fireball = d.yield * 1.5;
        double r = Math.max(fireball, d.craterR);
        double below = Math.max(fireball, d.craterDepth + 4);
        AABB box = new AABB(d.center.x - r, d.center.y - below, d.center.z - r, d.center.x + r, d.center.y + r, d.center.z + r);
        List<Entity> entities = level.getEntities((Entity) null, box, e -> true);
        for (Entity e : entities) {
            double dx = e.getX() - d.center.x, dz = e.getZ() - d.center.z, dy = e.getY() - d.center.y;
            double h = Math.sqrt(dx * dx + dz * dz);
            boolean inFireball = e.position().distanceTo(d.center) <= fireball;
            boolean inBowl = h <= d.craterR && dy <= r && dy >= -(bowlDepth(d, h) + 3);
            if (!inFireball && !inBowl) continue;
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
        int budget = BLOCKS_PER_TICK;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = (int) Math.floor(d.center.x), cz = (int) Math.floor(d.center.z);
        // Ring s holds every column whose floor(distance) == s; rings are dug from the centre outward.
        // A ring can hold more blocks than one tick's budget (the mid rings of a 120-radius bowl run to about 15000),
        // so a ring is resumed where it stopped rather than finished in one go; that keeps every tick about the same.
        while (d.craterShell <= R && budget > 0) {
            int s = d.craterShell;
            boolean rim = s >= R - 2;
            int flags = rim ? Block.UPDATE_ALL : (Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            int dx = Math.max(-s, d.craterDx);
            for (; dx <= s && budget > 0; dx++) {
                for (int dz = -s; dz <= s; dz++) {
                    if ((int) Math.sqrt(dx * dx + dz * dz) != s) continue;
                    int x = cx + dx, z = cz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    d.touchedChunks.add(ChunkPos.asLong(x >> 4, z >> 4));
                    budget -= digColumn(level, d, pos, x, z, rim, flags);
                }
            }
            if (dx > s) {
                d.craterShell++;
                d.craterDx = Integer.MIN_VALUE;
            } else {
                d.craterDx = dx;   // out of budget part-way round the ring: carry on here next tick
            }
        }
        if (d.craterShell > R) d.craterDone = true;
    }

    /**
     * A chunk that loads after the rings went past it: dig whatever part of the bowl falls inside it. Returns false
     * when the dig has not reached this chunk yet (the ring sweep will do it, now that the chunk is loaded).
     */
    static boolean catchUpChunk(ServerLevel level, Detonation d, ChunkPos chunk) {
        if (!ASConfig.DESTROY_BLOCKS.get()) return true;
        int cx = (int) Math.floor(d.center.x), cz = (int) Math.floor(d.center.z);
        int R = d.craterR;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean complete = true;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int dx = x - cx, dz = z - cz;
                int s = (int) Math.sqrt(dx * dx + dz * dz);
                if (s > R) continue;
                if (s >= d.craterShell) { complete = false; continue; }   // not dug yet anywhere: the sweep will come
                digColumn(level, d, pos, x, z, s >= R - 2, Block.UPDATE_ALL);
            }
        }
        return complete;
    }

    /** Digs one column of the bowl; returns how many blocks it removed. */
    private static int digColumn(ServerLevel level, Detonation d, BlockPos.MutableBlockPos pos, int x, int z, boolean rim, int flags) {
        int dx = x - (int) Math.floor(d.center.x), dz = z - (int) Math.floor(d.center.z);
        int cy = (int) Math.floor(d.center.y);
        double h = Math.sqrt(dx * dx + dz * dz);
        double rel = h / d.craterR;                                   // 0 at centre, 1 at rim
        double bowl = bowlDepth(d, h);                                // parabolic profile
        bowl += jitter(d.seed, dx, dz) * (1.5 + rel * 3.0);          // rough rim, smoother floor
        if (bowl < 0.5 && !rim) return 0;
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 1;
        int bottom = Math.max(minY, cy - (int) Math.round(bowl));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        // Inside the bowl everything down to the floor goes, including anything standing on the surface.
        top = Math.min(maxY, Math.max(top, cy + 2));
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        for (int y = top; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getBlock().getExplosionResistance() >= 1200.0F) continue; // bedrock, obsidian, reinforced blocks survive
            level.setBlock(pos, air, flags);
            removed++;
        }
        scorchFloor(level, pos.set(x, bottom - 1, z), rel);
        return removed;
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
