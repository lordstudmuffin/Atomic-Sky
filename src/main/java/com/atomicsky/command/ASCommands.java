package com.atomicsky.command;

import com.atomicsky.ASConfig;
import com.atomicsky.world.DetonationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /atomicsky detonate [pos] [radius], /atomicsky status, /atomicsky clear, /atomicsky endwinter (operators only). */
public final class ASCommands {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("atomicsky").requires(s -> s.hasPermission(2))
                .then(Commands.literal("detonate")
                        .executes(ctx -> detonate(ctx.getSource(), ctx.getSource().getPosition(), ASConfig.BLAST_RADIUS.get()))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> detonate(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), ASConfig.BLAST_RADIUS.get()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 128))
                                        .executes(ctx -> detonate(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("endwinter").executes(ctx -> endWinter(ctx.getSource()))));
    }

    private static int detonate(CommandSourceStack source, Vec3 pos, int radius) {
        ServerLevel level = source.getLevel();
        DetonationManager.get(level).detonate(level, pos, radius, source.getPlayer() == null ? null : source.getPlayer().getUUID());
        source.sendSuccess(() -> Component.translatable("command.atomicsky.detonated", (int) pos.x, (int) pos.y, (int) pos.z, radius), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        DetonationManager m = DetonationManager.get(level);
        if (m.all().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.atomicsky.status.none"), false);
            return 0;
        }
        long now = level.getGameTime();
        for (var d : m.all()) {
            double day = com.atomicsky.weather.WeatherBridge.calendarDay(level);
            String winter = d.winterDone ? "done" : String.format("%.0f%% (days %.1f-%.1f, now %.1f)", d.winterFactor(day) * 100, d.winterStartDay, d.winterEndDay, day);
            String line = String.format("#%d at %d,%d,%d  yield %d crater r=%d %s  age %ds  heat %.0f%%  shock %s  fallout r=%.0f %.0f%% drifted %.0fm  winter %s",
                    d.id, (int) d.center.x, (int) d.center.y, (int) d.center.z, d.yield, d.craterR, d.craterDone ? "done" : "digging", d.age(now) / 20, d.heatIntensity(now) * 100,
                    d.shockDone ? "done" : String.format("%.0fm", d.shockRadius(now)), d.plumeRadius, d.plumeIntensity * 100,
                    Math.hypot(d.plumeX - d.center.x, d.plumeZ - d.center.z), winter);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return m.all().size();
    }

    private static int clear(CommandSourceStack source) {
        DetonationManager m = DetonationManager.get(source.getLevel());
        int n = m.all().size();
        m.clearAll();
        m.sync(source.getLevel());
        com.atomicsky.weather.ShockwaveField.setServerFronts(java.util.List.of());
        source.sendSuccess(() -> Component.translatable("command.atomicsky.cleared", n), true);
        return n;
    }

    private static int endWinter(CommandSourceStack source) {
        DetonationManager m = DetonationManager.get(source.getLevel());
        m.endWinter();
        m.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("command.atomicsky.winter_ended"), true);
        return 1;
    }

    private ASCommands() {}
}
