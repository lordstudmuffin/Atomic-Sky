package com.atomicsky;

import com.atomicsky.block.FalloutLayerBlock;
import com.atomicsky.block.NukeBlock;
import com.atomicsky.block.NukeBlockEntity;
import com.atomicsky.command.ASCommands;
import com.atomicsky.effect.RadiationEffect;
import com.atomicsky.net.ASNetwork;
import com.atomicsky.world.DetonationManager;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Atomic Sky: a nuke whose aftermath runs through ProtoManly's Weather. Flash and heat pulse (dries soil, wildfires),
 * a shockwave carried by the wind engine, drifting fallout under darkened skies, and an optional nuclear winter.
 */
@Mod(AtomicSky.MODID)
public class AtomicSky {
    public static final String MODID = "atomicsky";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>, com.mojang.serialization.MapCodec<com.atomicsky.data.CraftableCondition>> CRAFTABLE_CONDITION =
            CONDITIONS.register("craftable", () -> com.atomicsky.data.CraftableCondition.CODEC);

    public static final DeferredHolder<Block, NukeBlock> NUKE = BLOCKS.register("nuke",
            () -> new NukeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(NukeBlock.ARMED) ? 6 : 0)));

    public static final DeferredHolder<Block, FalloutLayerBlock> FALLOUT = BLOCKS.register("fallout",
            () -> new FalloutLayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .replaceable()
                    .forceSolidOff()
                    .randomTicks()
                    .strength(0.1F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.SNOW)
                    .noLootTable()
                    .isViewBlocking((state, level, pos) -> state.getValue(FalloutLayerBlock.LAYERS) >= 8)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredHolder<Item, BlockItem> NUKE_ITEM = ITEMS.register("nuke",
            () -> new BlockItem(NUKE.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, BlockItem> FALLOUT_ITEM = ITEMS.register("fallout",
            () -> new BlockItem(FALLOUT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> FISSILE_CORE = ITEMS.register("fissile_core",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final DeferredHolder<Item, Item> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new com.atomicsky.block.GeigerCounterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NukeBlockEntity>> NUKE_BE = BLOCK_ENTITIES.register("nuke",
            () -> BlockEntityType.Builder.of(NukeBlockEntity::new, NUKE.get()).build(null));

    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION = EFFECTS.register("radiation", RadiationEffect::new);
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> RADIATION_DAMAGE =
            net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, id("radiation"));

    // Radiation suit: leather-grade protection, its job is keeping the sickness off. Materials register before items,
    // so the repair ingredient is a vanilla item looked up lazily.
    public static final DeferredHolder<net.minecraft.world.item.ArmorMaterial, net.minecraft.world.item.ArmorMaterial> RADIATION_SUIT_MATERIAL =
            ARMOR_MATERIALS.register("radiation_suit", () -> {
                java.util.EnumMap<net.minecraft.world.item.ArmorItem.Type, Integer> defense = new java.util.EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class);
                defense.put(net.minecraft.world.item.ArmorItem.Type.BOOTS, 1);
                defense.put(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, 2);
                defense.put(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, 3);
                defense.put(net.minecraft.world.item.ArmorItem.Type.HELMET, 1);
                defense.put(net.minecraft.world.item.ArmorItem.Type.BODY, 3);
                return new net.minecraft.world.item.ArmorMaterial(defense, 12, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                        () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.YELLOW_WOOL),
                        java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(id("radiation_suit"))), 0.0F, 0.0F);
            });
    public static final DeferredHolder<Item, com.atomicsky.item.RadiationSuitItem> RADIATION_SUIT_HELMET = suit(net.minecraft.world.item.ArmorItem.Type.HELMET);
    public static final DeferredHolder<Item, com.atomicsky.item.RadiationSuitItem> RADIATION_SUIT_CHESTPLATE = suit(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
    public static final DeferredHolder<Item, com.atomicsky.item.RadiationSuitItem> RADIATION_SUIT_LEGGINGS = suit(net.minecraft.world.item.ArmorItem.Type.LEGGINGS);
    public static final DeferredHolder<Item, com.atomicsky.item.RadiationSuitItem> RADIATION_SUIT_BOOTS = suit(net.minecraft.world.item.ArmorItem.Type.BOOTS);

    private static DeferredHolder<Item, com.atomicsky.item.RadiationSuitItem> suit(net.minecraft.world.item.ArmorItem.Type type) {
        return ITEMS.register("radiation_suit_" + type.getName(), () -> new com.atomicsky.item.RadiationSuitItem(RADIATION_SUIT_MATERIAL, type,
                new Item.Properties().durability(type.getDurability(18))));
    }

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH = PARTICLES.register("ash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));

    public AtomicSky(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ASConfig.SPEC);
        com.atomicsky.weather.WeatherBridge.registerAddon(container);
        container.registerConfig(ModConfig.Type.CLIENT, ASClientConfig.SPEC);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ARMOR_MATERIALS.register(modBus);
        PARTICLES.register(modBus);
        CONDITIONS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, AtomicSky::addToCreativeTabs);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ASNetwork::register);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, DetonationManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ASCommands::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, DetonationManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, DetonationManager::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ChunkEvent.Load.class, DetonationManager::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, DetonationManager::onServerStopped);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(NUKE_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(FISSILE_CORE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GEIGER_COUNTER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(RADIATION_SUIT_HELMET.get());
            event.accept(RADIATION_SUIT_CHESTPLATE.get());
            event.accept(RADIATION_SUIT_LEGGINGS.get());
            event.accept(RADIATION_SUIT_BOOTS.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(FALLOUT_ITEM.get());
        }
    }
}
