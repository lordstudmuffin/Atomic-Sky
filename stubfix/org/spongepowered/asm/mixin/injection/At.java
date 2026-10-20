package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target({})
public @interface At { String value(); String target() default ""; int ordinal() default -1; boolean remap() default true; }
