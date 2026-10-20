package com.atomicsky;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/atomicsky-client.toml): visuals only. */
public final class ASClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FLASH;
    public static final ModConfigSpec.DoubleValue FLASH_STRENGTH;
    public static final ModConfigSpec.BooleanValue MUSHROOM_CLOUD;
    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue DARK_SKY;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("visuals");
        FLASH = b.comment("Show the white-out flash when a bomb goes off in view.").define("flash", true);
        FLASH_STRENGTH = b.comment("Peak brightness of the flash (0-1).").defineInRange("flashStrength", 1.0, 0.0, 1.0);
        MUSHROOM_CLOUD = b.comment("Build a particle mushroom cloud over the blast. Turn off on weak PCs.").define("mushroomCloud", true);
        PARTICLE_DENSITY = b.comment("Multiplier on fallout ash and cloud particle counts.").defineInRange("particleDensity", 1.0, 0.0, 4.0);
        DARK_SKY = b.comment("Darken sky and fog under the fallout plume.").define("darkSky", true);
        CAMERA_SHAKE = b.comment("Shake the camera when the shock front passes you.").define("cameraShake", true);
        b.pop();
        SPEC = b.build();
    }

    private ASClientConfig() {}
}
