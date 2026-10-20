package com.atomicsky.mixin;

import com.atomicsky.weather.ShockwaveField;
import dev.protomanly.pmweather.weather.WindEngine;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the nuclear shockwave to the weather mod's wind field, so everything that reads wind feels the blast. */
@Mixin(value = WindEngine.class, remap = false)
public class WindEngineMixin {
    @Inject(method = "getWind(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;ZZZZZI)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void atomicsky$addBlastWind(Vec3 pos, Level level, boolean a, boolean b, boolean c, boolean d, boolean e, int f,
                                               CallbackInfoReturnable<Vec3> cir) {
        if (level == null || pos == null) return;
        if (ShockwaveField.fronts(level.isClientSide).isEmpty()) return;
        Vec3 blast = ShockwaveField.windAt(pos, level);
        if (blast == Vec3.ZERO) return;
        Vec3 base = cir.getReturnValue();
        cir.setReturnValue(base == null ? blast : base.add(blast));
    }
}
