package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
/** Compile-only stub of SpongePowered Mixin's annotation; the real one ships with NeoForge at runtime. */
@Retention(RetentionPolicy.CLASS) @Target(ElementType.TYPE)
public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; int priority() default 1000; boolean remap() default true; }
