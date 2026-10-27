package com.atomicsky.world;

import com.atomicsky.ASConfig;
import com.atomicsky.weather.ShockwaveField;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The shockwave: a ring that races outward at the configured speed. The ring is published to {@link ShockwaveField}
 * (the weather mod's wind engine reads it there), and on top of that the front shoves entities directly so the
 * push is reliable, and shatters fragile blocks near the blast.
 */
final class Shockwave {
    static final double BAND = 8.0;

    static void tick(ServerLevel level, Detonation d, long now) {
        double radius = d.shockRadius(now);
        double range = d.shockRange();
        if (radius - BAND > range) {
            d.shockDone = true;
            publish(level);
            return;
        }
        publish(level);

        double peak = ASConfig.SHOCKWAVE_PUSH.get();
        double speedPerTick = ASConfig.SHOCKWAVE_SPEED.get() / 20.0;
        AABB box = new AABB(d.center.x - radius - BAND, level.getMinBuildHeight(), d.center.z - radius - BAND,
                d.center.x + radius + BAND, level.getMaxBuildHeight(), d.center.z + radius + BAND);
        List<Entity> entities = level.getEntities((Entity) null, box, e -> !e.isSpectator());
        for (Entity e : entities) {
            double dx = e.getX() - d.center.x, dz = e.getZ() - d.center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.5 || Math.abs(dist - radius) > BAND) continue;
            if (e instanceof Player p && p.isCreative()) continue;
            double falloff = Math.min(1.0, d.craterR * 1.5 / Math.max(dist, 1.0));
            double tail = Math.max(0.0, 1.0 - radius / range);
            // The band passes an entity in BAND*2/speed ticks; spread the total impulse over that time.
            double perTick = peak * falloff * tail * speedPerTick / (BAND * 2.0);
            if (perTick < 0.002) continue;
            // A roof (a house, a cave, a bunker) muffles the front: a third of the push, a third of the damage.
            boolean sheltered = !com.atomicsky.util.Exposure.exposed(level, e.blockPosition());
            if (sheltered) perTick *= 0.3;
            double inv = 1.0 / dist;
            Vec3 push = new Vec3(dx * inv * perTick, perTick * 0.35, dz * inv * perTick);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
            if (e instanceof LivingEntity living && falloff > 0.2 && now % 5 == 0) {
                float dmg = (float) (falloff * tail * 4.0 * (sheltered ? 0.3 : 1.0));
                if (dmg >= 0.5F) living.hurt(level.damageSources().explosion(null, null), dmg);
            }
        }

        if (ASConfig.SHOCKWAVE_BREAKS_FRAGILE.get() && radius < d.heatRadius()) {
            shatter(level, d, radius);
        }
    }

    /** Publish every active front in this level so the wind mixin can add them to the weather mod's wind. */
    static void publish(ServerLevel level) {
        List<ShockwaveField.Front> fronts = new ArrayList<>();
        long now = level.getGameTime();
        for (Detonation d : DetonationManager.get(level).all()) {
            if (d.shockDone) continue;
            fronts.add(new ShockwaveField.Front(d.center, d.shockRadius(now), BAND, ASConfig.SHOCKWAVE_WIND_MPH.get(), d.yield, d.shockRange()));
        }
        ShockwaveField.setServerFronts(level.dimension(), fronts);
    }

    private static void shatter(ServerLevel level, Detonation d, double radius) {
        RandomSource rand = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int samples = 140;
        for (int i = 0; i < samples; i++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            double dist = radius - BAND + rand.nextDouble() * BAND * 2;
            if (dist < d.craterR) continue;
            int x = (int) Math.floor(d.center.x + Math.cos(ang) * dist);
            int z = (int) Math.floor(d.center.z + Math.sin(ang) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            double chance = Math.min(1.0, d.deadRadius() / dist);
            for (int dy = 0; dy < 6; dy++) {
                pos.set(x, top - dy, z);
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) continue;
                if ((isFragile(s) || (dist < d.deadRadius() && isWeak(s))) && rand.nextDouble() < chance) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Inside the dead zone the front also rips off doors, trapdoors, signs, ladders, banners and panes of any kind. */
    private static boolean isWeak(BlockState s) {
        return s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.ALL_SIGNS) || s.is(Blocks.LADDER)
                || s.is(BlockTags.BANNERS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.CANDLES) || s.is(BlockTags.FLOWER_POTS)
                || s.is(Blocks.SCAFFOLDING) || s.is(BlockTags.BEDS);
    }

    private static boolean isFragile(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.FLOWERS)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.SHORT_GRASS)
                || s.is(Blocks.TALL_GRASS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.WOOL_CARPETS)
                || s.getBlock().getExplosionResistance() < 0.35F;
    }

    private Shockwave() {}
}
