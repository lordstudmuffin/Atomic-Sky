package com.atomicsky.world;

import com.atomicsky.ASConfig;
import com.atomicsky.weather.WeatherBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The heat pulse. A sweep of random surface columns inside the heat radius, weighted toward the centre and fading
 * with time: grass bakes to dirt, farmland and the weather mod's soil moisture dry out, shallow water boils off,
 * anything flammable catches the weather mod's wildfire, and anything alive out in the open burns.
 */
final class HeatPulse {
    private static final int COLUMNS_PER_TICK = 110;
    private static final int SWEEP_COLUMNS_PER_TICK = 3000;

    static void tick(ServerLevel level, Detonation d, long now) {
        double intensity = d.heatIntensity(now);
        if (intensity <= 0) {
            d.heatDone = true;
            return;
        }
        double heatR = d.heatRadius();
        RandomSource rand = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long age = d.age(now);
        // The pulse races outward over the first few seconds, then keeps cooking the whole zone while it fades.
        double reach = Math.min(heatR, d.craterR + age * 1.5);
        double deadR = d.deadRadius();

        // Deterministic sweep: every column of the dead zone gets baked once, ring by ring, as the front passes it.
        int sweepTo = (int) Math.min(deadR, reach);
        int budget = SWEEP_COLUMNS_PER_TICK;
        int cx = (int) Math.floor(d.center.x), cz = (int) Math.floor(d.center.z);
        int firstRing = Math.max(0, d.craterR - 3);
        if (d.heatRing < firstRing - 1) d.heatRing = firstRing - 1;
        while (d.heatRing < sweepTo && budget > 0) {
            int s = d.heatRing + 1;
            for (int dx = -s; dx <= s; dx++) {
                for (int dz = -s; dz <= s; dz++) {
                    if ((int) Math.sqrt(dx * dx + dz * dz) != s) continue;
                    int x = cx + dx, z = cz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    if (top < level.getMinBuildHeight()) continue;
                    pos.set(x, top, z);
                    cookColumn(level, d, pos, level.getBlockState(pos), 1.0, rand);
                    budget--;
                }
            }
            d.heatRing = s;
        }

        for (int i = 0; i < COLUMNS_PER_TICK; i++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(rand.nextDouble()) * reach;      // uniform over the disc
            if (dist < d.craterR) continue;                        // the crater floor is already glass and ash
            int x = (int) Math.floor(d.center.x + Math.cos(ang) * dist);
            int z = (int) Math.floor(d.center.z + Math.sin(ang) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            double rel = d.heatRel(dist);                         // 1 across the dead zone, 0 at the edge
            double strength = intensity * rel;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (top < level.getMinBuildHeight()) continue;
            pos.set(x, top, z);
            BlockState state = level.getBlockState(pos);
            cookColumn(level, d, pos, state, strength, rand);
        }

        // Dry the soil in the weather mod's moisture system, one chunk ring per second.
        if (ASConfig.HEAT_DRIES_SOIL.get() && age % 20 == 0 && age / 20 < 40) {
            int ring = (int) (age / 20);
            int chunkR = (int) Math.ceil(heatR / 16.0);
            if (ring <= chunkR) drySoilRing(level, d, ring, chunkR);
        }

        // No heat damage to anything alive: once the blast itself is over, radiation is the only danger of the area.
    }

    private static void cookColumn(ServerLevel level, Detonation d, BlockPos.MutableBlockPos pos, BlockState top, double strength, RandomSource rand) {
        if (strength <= 0.01) return;
        BlockPos above = pos.above();
        boolean dries = ASConfig.HEAT_DRIES_SOIL.get();

        // Water: boil off shallow surface water near the centre.
        if (ASConfig.HEAT_EVAPORATES_WATER.get() && (top.is(Blocks.WATER) || !top.getFluidState().isEmpty())) {
            if (strength > 0.55 && top.getFluidState().isSource()) {
                BlockState below = level.getBlockState(pos.below());
                if (below.isSolid()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
        if (top.is(Blocks.SNOW) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.POWDER_SNOW)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (top.is(Blocks.ICE) || top.is(Blocks.FROSTED_ICE) || top.is(Blocks.PACKED_ICE)) {
            if (strength > 0.4) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        // Plants and low flammables: burn or wither.
        if (top.is(BlockTags.FLOWERS) || top.is(BlockTags.SAPLINGS) || top.is(Blocks.SHORT_GRASS) || top.is(Blocks.TALL_GRASS)
                || top.is(Blocks.FERN) || top.is(Blocks.LARGE_FERN) || top.is(BlockTags.CROPS) || top.is(Blocks.DEAD_BUSH)
                || top.is(Blocks.SUGAR_CANE) || top.is(Blocks.BAMBOO) || top.is(Blocks.VINE) || top.is(Blocks.LILY_PAD)) {
            if (rand.nextDouble() < strength * 1.5) {
                if (strength > 0.3 && rand.nextDouble() < ASConfig.HEAT_FIRE_CHANCE.get()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    WeatherBridge.ignite(level, pos.immutable(), (int) Math.ceil(strength * 10));
                } else {
                    level.setBlock(pos, strength > 0.5 ? Blocks.AIR.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
                    if (!level.getBlockState(pos).canSurvive(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                // Fall through to bake the soil underneath.
                pos.move(0, -1, 0);
                top = level.getBlockState(pos);
                above = pos.above();
            } else {
                return;
            }
        }

        // Soil: bake it.
        if (dries) {
            if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.ROOTED_DIRT) || top.is(Blocks.MOSS_BLOCK)) {
                if (rand.nextDouble() < strength * 1.2) {
                    level.setBlock(pos, strength > 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                }
            } else if (top.is(Blocks.DIRT)) {
                if (strength > 0.5 && rand.nextDouble() < strength) level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            } else if (top.is(Blocks.FARMLAND)) {
                if (top.getValue(FarmBlock.MOISTURE) > 0) level.setBlock(pos, top.setValue(FarmBlock.MOISTURE, 0), Block.UPDATE_ALL);
                if (strength > 0.7 && rand.nextDouble() < strength * 0.5) level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            } else if (top.is(Blocks.MUD) || top.is(Blocks.CLAY)) {
                if (strength > 0.4 && rand.nextDouble() < strength) level.setBlock(pos, top.is(Blocks.MUD) ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
            } else if (top.is(Blocks.SAND) && strength > 0.8) {
                if (rand.nextDouble() < strength * 0.3) level.setBlock(pos, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // In the dead zone the canopy burns off outright and whatever is under it is left to the wildfire.
        if (strength >= 0.99 && top.is(BlockTags.LEAVES)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (int i = 0; i < 6; i++) {
                pos.move(0, -1, 0);
                BlockState s = level.getBlockState(pos);
                if (s.is(BlockTags.LEAVES)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                else { top = s; above = pos.above(); break; }
            }
            if (top.is(BlockTags.LEAVES) || top.isAir()) return;
        }

        // Flammable surfaces (leaves, logs, planks, wool, hay...): the weather mod's wildfire.
        boolean flammable = top.isFlammable(level, pos, net.minecraft.core.Direction.UP) || top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)
                || top.is(BlockTags.PLANKS) || top.is(BlockTags.WOOL) || top.is(Blocks.HAY_BLOCK);
        if (flammable && rand.nextDouble() < ASConfig.HEAT_FIRE_CHANCE.get() * strength) {
            if (level.getBlockState(above).isAir()) WeatherBridge.ignite(level, above, (int) Math.ceil(strength * 10));
        } else if (!flammable && strength > 0.5 && rand.nextDouble() < 0.12 * strength) {
            // Bare baked ground still smoulders in the hot zone: short-lived fires that the weather mod spreads if it can.
            if (level.getBlockState(above).isAir()) WeatherBridge.ignite(level, above, (int) Math.ceil(strength * 6));
        }
    }

    private static void drySoilRing(ServerLevel level, Detonation d, int ring, int chunkR) {
        int ccx = ((int) Math.floor(d.center.x)) >> 4, ccz = ((int) Math.floor(d.center.z)) >> 4;
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                if (dx * dx + dz * dz > chunkR * chunkR) continue;
                if (!level.hasChunk(ccx + dx, ccz + dz)) continue;
                ChunkAccess chunk = level.getChunk(ccx + dx, ccz + dz);
                double distChunks = Math.sqrt(dx * dx + dz * dz);
                float moisture = (float) Math.max(0.0, 0.25 * (distChunks / Math.max(1, chunkR)));
                WeatherBridge.drySoil(level, chunk, moisture);
            }
        }
    }

    private HeatPulse() {}
}
