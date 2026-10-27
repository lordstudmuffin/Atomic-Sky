package com.atomicsky.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Radiation sickness: slow damage (never below half a heart from the effect alone at level I) and hunger drain.
 * Fallout re-applies the effect every second, which keeps its remaining duration bouncing between two values that
 * need not include a multiple of the damage interval, so the schedule runs on the entity's own tick counter instead
 * of the duration: every 3 seconds at level I, every 1.5 seconds at level II, however often the effect is refreshed.
 */
public class RadiationEffect extends MobEffect {
    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FB33A);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        int interval = amplifier >= 1 ? 30 : 60;
        if (entity.tickCount % interval != 0) return true;
        if (entity instanceof Player player) {
            player.causeFoodExhaustion(0.08F * (amplifier + 1));
        }
        float floor = amplifier >= 1 ? 0.0F : 1.0F;
        if (entity.getHealth() > floor) {
            entity.hurt(entity.damageSources().source(com.atomicsky.AtomicSky.RADIATION_DAMAGE), 1.0F);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;   // the interval is applied in applyEffectTick, from the entity's tick counter
    }
}
