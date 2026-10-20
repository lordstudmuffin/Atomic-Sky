package com.atomicsky.world;

import com.atomicsky.ASConfig;
import com.atomicsky.AtomicSky;
import com.atomicsky.block.FalloutLayerBlock;
import com.atomicsky.weather.WeatherBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The fallout plume: a disc of ash that grows after the blast, drifts with the weather mod's wind, lays fallout on the
 * ground like snow (thicker near the middle) and gives Radiation to anything under it in the open.
 */
final class FalloutPlume {
    private static final int COLUMNS_PER_TICK = 40;

    static void tick(ServerLevel level, Detonation d, long now) {
        if (now < d.falloutStart) return;
        if (now >= d.falloutEnd + 1200) {           // a minute of thinning after the end
            d.falloutDone = true;
            d.plumeIntensity = 0;
            return;
        }
        double total = d.falloutEnd - d.falloutStart;
        double t = (now - d.falloutStart) / total;    // 0..1 over the fallout window, >1 while thinning
        double maxR = d.falloutMaxRadius();
        d.plumeRadius = Math.min(maxR, d.yield * 1.5 + (maxR - d.yield * 1.5) * Math.min(1.0, t / 0.25));
        if (t < 0.1) d.plumeIntensity = t / 0.1;
        else if (t < 0.75) d.plumeIntensity = 1.0;
        else d.plumeIntensity = Math.max(0.0, 1.0 - (t - 0.75) / 0.35);

        // Drift with the wind sampled above the plume centre, once a second.
        if (now % 20 == 0) {
            Vec3 wind = WeatherBridge.windMph(level, new Vec3(d.plumeX, d.center.y + 90, d.plumeZ));
            d.windX = wind.x;
            d.windZ = wind.z;
        }
        double drift = ASConfig.FALLOUT_DRIFT.get() * WeatherBridge.MPH_TO_BLOCKS_PER_TICK;
        double maxTravel = maxR * 3.0;
        double nx = d.plumeX + d.windX * drift, nz = d.plumeZ + d.windZ * drift;
        if (Math.hypot(nx - d.center.x, nz - d.center.z) <= maxTravel) {
            d.plumeX = nx;
            d.plumeZ = nz;
        }

        if (d.plumeIntensity <= 0) return;
        deposit(level, d);

        if (ASConfig.FALLOUT_RADIATION.get() && now % 20 == 0) irradiate(level, d);
    }

    private static void deposit(ServerLevel level, Detonation d) {
        RandomSource rand = level.random;
        int maxLayers = ASConfig.FALLOUT_MAX_LAYERS.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState one = AtomicSky.FALLOUT.get().defaultBlockState();
        for (int i = 0; i < COLUMNS_PER_TICK; i++) {
            double[] pt = d.randomPlumePoint(rand);
            int x = (int) Math.floor(pt[0]);
            int z = (int) Math.floor(pt[1]);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            double strength = d.falloutAt(x + 0.5, z + 0.5);
            if (rand.nextDouble() > strength) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            pos.set(x, top, z);
            BlockState at = level.getBlockState(pos);
            if (at.is(AtomicSky.FALLOUT.get())) {
                int layers = at.getValue(FalloutLayerBlock.LAYERS);
                // Deeper layers only near the middle: thickness follows the plume profile.
                int allowed = Math.max(1, (int) Math.round(maxLayers * strength));
                if (layers < allowed && rand.nextDouble() < 0.5) {
                    level.setBlock(pos, at.setValue(FalloutLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
                }
            } else {
                BlockState below = level.getBlockState(pos.below());
                if (below.is(AtomicSky.FALLOUT.get())) {
                    int layers = below.getValue(FalloutLayerBlock.LAYERS);
                    int allowed = Math.max(1, (int) Math.round(maxLayers * strength));
                    if (layers < allowed && rand.nextDouble() < 0.5) {
                        level.setBlock(pos.below(), below.setValue(FalloutLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
                    }
                } else if (FalloutLayerBlock.canPlaceLayerAt(level, pos)) {
                    level.setBlock(pos, one, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void irradiate(ServerLevel level, Detonation d) {
        double r = d.plumeRadius;
        AABB box = new AABB(Math.min(d.plumeX, d.center.x) - r, level.getMinBuildHeight(), Math.min(d.plumeZ, d.center.z) - r,
                Math.max(d.plumeX, d.center.x) + r, level.getMaxBuildHeight(), Math.max(d.plumeZ, d.center.z) + r);
        List<Entity> entities = level.getEntities((Entity) null, box, e -> e instanceof LivingEntity);
        for (Entity e : entities) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double strength = d.falloutAt(e.getX(), e.getZ());
            if (strength < 0.1) continue;
            if (!level.canSeeSky(e.blockPosition())) continue;   // a roof keeps the ash off you
            LivingEntity living = (LivingEntity) e;
            if (com.atomicsky.item.RadiationSuitItem.isProtected(living)) continue;
            int amp = strength > 0.6 ? 1 : 0;
            living.addEffect(new MobEffectInstance(AtomicSky.RADIATION, 160, amp, false, true, true));
        }
    }

    private FalloutPlume() {}
}
