package com.atomicsky.mixin;

import com.atomicsky.weather.WeatherBridge;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nuclear winter: lowers every temperature the weather mod samples while a winter is running. */
@Mixin(value = ThermodynamicEngine.class, remap = false)
public class ThermodynamicEngineMixin {
    @Inject(method = "samplePoint(Ldev/protomanly/pmweather/weather/WeatherHandler;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;Ldev/protomanly/pmweather/block/entity/RadarBlockEntity;ILjava/lang/Integer;ZZ)Ldev/protomanly/pmweather/weather/ThermodynamicEngine$AtmosphericDataPoint;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void atomicsky$nuclearWinter(WeatherHandler handler, Vec3 pos, Level level, RadarBlockEntity radar, int a, Integer b,
                                                boolean c, boolean d, CallbackInfoReturnable<ThermodynamicEngine.AtmosphericDataPoint> cir) {
        if (level == null) return;
        float drop = WeatherBridge.temperatureDropC(level);
        if (drop <= 0.01F) return;
        ThermodynamicEngine.AtmosphericDataPoint p = cir.getReturnValue();
        if (p == null) return;
        cir.setReturnValue(new ThermodynamicEngine.AtmosphericDataPoint(
                p.temperature() - drop, p.dewpoint() - drop * 0.9F, p.pressure(), p.virtualTemperature() - drop));
    }
}
