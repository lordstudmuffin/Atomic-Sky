package net.neoforged.neoforge.common;

import net.neoforged.fml.config.IConfigSpec;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Compile-time shim: the generated stub lost Builder's public no-arg constructor. Signatures match NeoForge 21.1. */
public abstract class ModConfigSpec implements IConfigSpec {
    public boolean isLoaded() { throw new UnsupportedOperationException(); }

    public static class Builder {
        public Builder() {}
        public BooleanValue define(String path, boolean defaultValue) { throw new UnsupportedOperationException(); }
        public <T> ConfigValue<T> define(String path, T defaultValue) { throw new UnsupportedOperationException(); }
        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) { throw new UnsupportedOperationException(); }
        public IntValue defineInRange(String path, int defaultValue, int min, int max) { throw new UnsupportedOperationException(); }
        public <T> ConfigValue<List<? extends T>> defineList(String path, List<? extends T> defaultValue, Supplier<T> newElementSupplier, java.util.function.Predicate<Object> elementValidator) { throw new UnsupportedOperationException(); }
        public Builder comment(String comment) { throw new UnsupportedOperationException(); }
        public Builder comment(String... comment) { throw new UnsupportedOperationException(); }
        public Builder push(String path) { throw new UnsupportedOperationException(); }
        public Builder pop() { throw new UnsupportedOperationException(); }
        public ModConfigSpec build() { throw new UnsupportedOperationException(); }
    }

    public static class ConfigValue<T> implements Supplier<T> {
        protected ConfigValue() {}
        public T get() { throw new UnsupportedOperationException(); }
        public T getDefault() { throw new UnsupportedOperationException(); }
        public void set(T value) { throw new UnsupportedOperationException(); }
    }

    public static class BooleanValue extends ConfigValue<Boolean> implements BooleanSupplier {
        protected BooleanValue() {}
        public boolean getAsBoolean() { throw new UnsupportedOperationException(); }
    }

    public static class IntValue extends ConfigValue<Integer> implements IntSupplier {
        protected IntValue() {}
        public int getAsInt() { throw new UnsupportedOperationException(); }
    }

    public static class DoubleValue extends ConfigValue<Double> implements DoubleSupplier {
        protected DoubleValue() {}
        public double getAsDouble() { throw new UnsupportedOperationException(); }
    }
}
