package com.atomicsky.world;

import com.atomicsky.weather.WeatherBridge;
import net.minecraft.server.level.ServerLevel;

/** Nuclear winter bookkeeping: the temperature drop itself is applied by the weather-mod temperature hook. */
final class NuclearWinter {
    static void tick(ServerLevel level, Detonation d) {
        if (level.getGameTime() % 100 != 0) return;
        double day = WeatherBridge.calendarDay(level);
        if (d.winterEndDay <= d.winterStartDay || day > d.winterEndDay) d.winterDone = true;
    }

    private NuclearWinter() {}
}
