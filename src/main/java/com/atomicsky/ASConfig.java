package com.atomicsky;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config (config/atomicsky-common.toml): everything about how big, how long and how harsh a detonation is. */
public final class ASConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BLAST_RADIUS;
    public static final ModConfigSpec.IntValue CRATER_RADIUS;
    public static final ModConfigSpec.IntValue CRATER_DEPTH;
    public static final ModConfigSpec.IntValue FUSE_SECONDS;
    public static final ModConfigSpec.BooleanValue CRAFTABLE;
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ModConfigSpec.IntValue FLASH_RANGE;

    public static final ModConfigSpec.DoubleValue HEAT_RADIUS_FACTOR;
    public static final ModConfigSpec.IntValue HEAT_FADE_BEYOND;
    public static final ModConfigSpec.IntValue HEAT_PULSE_SECONDS;
    public static final ModConfigSpec.DoubleValue HEAT_FIRE_CHANCE;
    public static final ModConfigSpec.BooleanValue HEAT_DRIES_SOIL;
    public static final ModConfigSpec.BooleanValue HEAT_EVAPORATES_WATER;

    public static final ModConfigSpec.DoubleValue SHOCKWAVE_RANGE_FACTOR;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_SPEED;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_PUSH;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_WIND_MPH;
    public static final ModConfigSpec.BooleanValue SHOCKWAVE_BREAKS_FRAGILE;

    public static final ModConfigSpec.IntValue FALLOUT_DELAY_SECONDS;
    public static final ModConfigSpec.IntValue FALLOUT_MINUTES;
    public static final ModConfigSpec.DoubleValue FALLOUT_RADIUS_FACTOR;
    public static final ModConfigSpec.IntValue FALLOUT_MAX_LAYERS;
    public static final ModConfigSpec.DoubleValue FALLOUT_DRIFT;
    public static final ModConfigSpec.BooleanValue FALLOUT_RADIATION;
    public static final ModConfigSpec.IntValue FALLOUT_DECAY_DAYS;

    public static final ModConfigSpec.BooleanValue WINTER_ENABLED;
    public static final ModConfigSpec.IntValue WINTER_DAYS;
    public static final ModConfigSpec.DoubleValue WINTER_TEMPERATURE_DROP;
    public static final ModConfigSpec.IntValue WINTER_DELAY_HOURS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("The bomb itself.").push("blast");
        BLAST_RADIUS = b.comment("Yield in blocks: the fireball, and the shockwave (x16) and fallout plume (x8) scale from it; 32 = shockwave to 512 blocks, plume radius 256. The crater and dead zone have their own sizes below.")
                .defineInRange("blastRadius", 32, 4, 128);
        CRATER_RADIUS = b.comment("Radius in blocks of the crater bowl (at the default yield; a bigger or smaller yield scales it along). 120 swallows a whole village and its fields.")
                .defineInRange("craterRadius", 120, 4, 400);
        CRATER_DEPTH = b.comment("Depth in blocks of the crater at its centre. The bowl is parabolic: wide and shallow at the rim, deepest in the middle.")
                .defineInRange("craterDepth", 45, 2, 200);
        FUSE_SECONDS = b.comment("Seconds between arming the bomb (flint and steel, fire charge or redstone) and detonation.")
                .defineInRange("fuseSeconds", 10, 1, 600);
        CRAFTABLE = b.comment("Whether the bomb and the fissile core have crafting recipes. Set false on servers that hand them out by other means.")
                .define("craftable", true);
        DESTROY_BLOCKS = b.comment("Carve the crater. False leaves terrain intact (entities, heat, shockwave and fallout still happen).")
                .define("destroyBlocks", true);
        FLASH_RANGE = b.comment("Distance in blocks at which the flash is still visible (and blinding, if facing it).")
                .defineInRange("flashRange", 1500, 50, 10000);
        b.pop();

        b.comment("Heat pulse: a wave of heat right after the flash that dries soil, evaporates shallow water and starts wildfires, then fades.").push("heat");
        HEAT_RADIUS_FACTOR = b.comment("Dead-zone radius as a multiple of the crater radius: everything out to here is baked dead (2.0 = a dead ring around the crater as wide as the crater itself). Heat and fire keep going, fading, for fadeBeyond blocks past it.")
                .defineInRange("radiusFactor", 2.0, 1.0, 6.0);
        HEAT_FADE_BEYOND = b.comment("How many blocks past the dead zone the heat and fires reach before fading to nothing.")
                .defineInRange("fadeBeyond", 100, 0, 1000);
        HEAT_PULSE_SECONDS = b.comment("How long the heat pulse keeps working before it has fully faded (real seconds).")
                .defineInRange("pulseSeconds", 150, 10, 1200);
        HEAT_FIRE_CHANCE = b.comment("Chance (0-1) that a flammable surface block in the heat zone catches fire, at the centre; falls off with distance.")
                .defineInRange("fireChance", 0.85, 0.0, 1.0);
        HEAT_DRIES_SOIL = b.comment("Turn grass to dirt, dirt to coarse dirt, drain farmland and tell the weather mod the soil is dry.")
                .define("driesSoil", true);
        HEAT_EVAPORATES_WATER = b.comment("Boil off shallow surface water near the centre.")
                .define("evaporatesWater", true);
        b.pop();

        b.comment("Shockwave: an expanding ring of wind fed into the weather mod's wind engine; pushes players, mobs and items and weakens with distance.").push("shockwave");
        SHOCKWAVE_RANGE_FACTOR = b.comment("How far the shockwave travels, as a multiple of blastRadius (32 x 16 = 512 blocks).")
                .defineInRange("rangeFactor", 16.0, 2.0, 60.0);
        SHOCKWAVE_SPEED = b.comment("Speed of the shock front in blocks per second.")
                .defineInRange("speedBlocksPerSecond", 60.0, 5.0, 400.0);
        SHOCKWAVE_PUSH = b.comment("Push strength at the crater edge (blocks per tick of velocity). Falls off with distance.")
                .defineInRange("pushStrength", 6.0, 0.0, 30.0);
        SHOCKWAVE_WIND_MPH = b.comment("Peak wind speed (mph) the shock front adds to the weather mod's wind field at the crater edge.")
                .defineInRange("windMph", 500.0, 0.0, 2000.0);
        SHOCKWAVE_BREAKS_FRAGILE = b.comment("The front shatters glass, leaves, torches and other fragile blocks near the blast.")
                .define("breaksFragile", true);
        b.pop();

        b.comment("Fallout: ash that falls like snow from a darkened plume and drifts downwind, building up on the ground.").push("fallout");
        FALLOUT_DELAY_SECONDS = b.comment("Seconds after detonation before fallout starts to come down.")
                .defineInRange("delaySeconds", 25, 0, 600);
        FALLOUT_MINUTES = b.comment("How long fallout keeps falling (real minutes).")
                .defineInRange("durationMinutes", 20, 1, 600);
        FALLOUT_RADIUS_FACTOR = b.comment("Full-grown plume radius as a multiple of blastRadius (32 x 8 = 256 blocks).")
                .defineInRange("radiusFactor", 8.0, 1.0, 40.0);
        FALLOUT_MAX_LAYERS = b.comment("Deepest fallout layer that can build up (1-8, like snow).")
                .defineInRange("maxLayers", 6, 1, 8);
        FALLOUT_DRIFT = b.comment("How strongly the plume centre follows the weather mod's wind (1.0 = the full wind speed; 0.06 = a 25 mph wind moves it about 40 blocks a minute). Ash keeps falling on the whole trail between the blast and the plume.")
                .defineInRange("windDrift", 0.06, 0.0, 3.0);
        FALLOUT_RADIATION = b.comment("Fallout gives Radiation to anything out in the open under the plume or standing in it.")
                .define("radiation", true);
        FALLOUT_DECAY_DAYS = b.comment("In-game days before fallout on the ground has mostly crumbled away (random ticks).")
                .defineInRange("decayDays", 6, 1, 1000);
        b.pop();

        b.comment("Nuclear winter: the weather mod's temperatures drop for a set number of its calendar days after a detonation.").push("winter");
        WINTER_ENABLED = b.comment("Turn nuclear winter on or off.").define("enabled", true);
        WINTER_DAYS = b.comment("How many in-game days the winter lasts, counted on the weather mod's calendar.")
                .defineInRange("days", 7, 1, 3650);
        WINTER_TEMPERATURE_DROP = b.comment("Temperature drop in degrees Celsius at the height of the winter.")
                .defineInRange("temperatureDropC", 18.0, 1.0, 80.0);
        WINTER_DELAY_HOURS = b.comment("In-game hours after detonation before temperatures begin to fall.")
                .defineInRange("delayHours", 6, 0, 240);
        b.pop();
        SPEC = b.build();
    }

    private ASConfig() {}
}
