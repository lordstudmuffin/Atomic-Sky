package com.atomicsky.data;

import com.atomicsky.ASConfig;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Recipe condition "atomicsky:craftable": the bomb recipes only load when the config says the bomb is craftable. */
public final class CraftableCondition implements ICondition {
    public static final CraftableCondition INSTANCE = new CraftableCondition();
    public static final MapCodec<CraftableCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        return ASConfig.CRAFTABLE.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
