package com.atomicsky.weather;

import com.atomicsky.ASConfig;
import com.atomicsky.AtomicSky;
import com.atomicsky.world.Detonation;
import com.atomicsky.world.DetonationManager;
import dev.protomanly.pmweather.addons.AddonHelper;
import dev.protomanly.pmweather.addons.AddonInfo;
import dev.protomanly.pmweather.block.ModBlocks;
import dev.protomanly.pmweather.block.PMWFireBlock;
import dev.protomanly.pmweather.config.ServerConfig;
import dev.protomanly.pmweather.seasons.MoistureHandler;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModContainer;

import java.util.List;

/**
 * Every call into ProtoManly's Weather goes through here, so the rest of the mod reads as plain game logic.
 * Wind is in the weather mod's unit (mph); the calendar is its day counter; fire and soil moisture are its own systems.
 */
public final class WeatherBridge {
    public static final double MPH_TO_BLOCKS_PER_TICK = 0.44704 / 20.0;

    /** Tell the weather mod an addon is present (it logs this and checks the version list). Never crashes the game. */
    public static void registerAddon(ModContainer container) {
        try {
            AddonHelper.registerAddon(new AddonInfo(container, List.of("0.17", "0.17.16", "0.18")));
        } catch (Throwable t) {
            AtomicSky.LOGGER.warn("ProtoManly's Weather did not accept Atomic Sky as a registered addon ({}); carrying on anyway.", t.toString());
        }
    }

    /** Wind at a point in mph, including storms and tornadoes but ignoring shelter (we sample open air). */
    public static Vec3 windMph(Level level, Vec3 pos) {
        try {
            return WindEngine.getWind(pos, level, false, false, false, true, false);
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    /** Fractional day on the weather mod's calendar. */
    public static double calendarDay(Level level) {
        try {
            int day = SeasonHandler.getDay(level);
            double frac = (level.getDayTime() % 24000L) / 24000.0;
            return day + frac;
        } catch (Throwable t) {
            return level.getDayTime() / 24000.0;
        }
    }

    /** Dry out a chunk's soil in the weather mod's moisture system (drought: grass yellows, crops struggle). */
    public static void drySoil(ServerLevel level, ChunkAccess chunk, float moisture) {
        try {
            MoistureHandler.setMoistureOnChunk(level, chunk, moisture);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Start a wildfire at a position using the weather mod's own fire block (which spreads, chars and scars the way
     * its lightning fires do). Falls back to vanilla fire when its wildfires are disabled in its config.
     */
    public static boolean ignite(ServerLevel level, BlockPos pos, int intensity) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !at.canBeReplaced()) return false;
        BlockState below = level.getBlockState(pos.below());
        try {
            if (ServerConfig.doWildfires) {
                BlockState fire = ModBlocks.FIRE.get().defaultBlockState();
                if (fire.hasProperty(PMWFireBlock.INTENSITY)) {
                    fire = fire.setValue(PMWFireBlock.INTENSITY, Math.max(1, Math.min(10, intensity)));
                }
                if (PMWFireBlock.canBurnOn(level, below, pos.below(), intensity) || PMWFireBlock.isGroundSuitable(level, below, pos.below())) {
                    return level.setBlock(pos, fire, 3);
                }
                return false;
            }
        } catch (Throwable ignored) {
        }
        if (below.isSolid() || below.ignitedByLava() || below.isFlammable(level, pos.below(), net.minecraft.core.Direction.UP)) {
            return level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
        }
        return false;
    }

    /** Winter strength per server dimension, refreshed by the detonation tick so the temperature hook never touches world data. */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<Level>, Double> SERVER_WINTER = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setServerWinter(ServerLevel level, double factor) {
        if (factor <= 0) SERVER_WINTER.remove(level.dimension());
        else SERVER_WINTER.put(level.dimension(), factor);
    }

    /** Nuclear-winter temperature drop (degrees C) at a level right now; read by the temperature mixin, possibly off-thread. */
    public static float temperatureDropC(Level level) {
        if (!ASConfig.WINTER_ENABLED.get()) return 0;
        double factor;
        if (level.isClientSide) {
            factor = com.atomicsky.client.ClientState.winterFactor();
        } else {
            Double f = SERVER_WINTER.get(level.dimension());
            factor = f == null ? 0 : f;
        }
        if (factor <= 0) return 0;
        return (float) (factor * ASConfig.WINTER_TEMPERATURE_DROP.get());
    }

    public static void onDetonation(ServerLevel level, Detonation d) {
        // Nothing to pre-register with the weather mod: wind, heat and fallout feed in tick by tick.
    }

    private WeatherBridge() {}
}
