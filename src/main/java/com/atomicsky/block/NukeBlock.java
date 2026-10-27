package com.atomicsky.block;

import com.atomicsky.ASConfig;
import com.atomicsky.AtomicSky;
import com.atomicsky.world.DetonationManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The bomb. Arm it with flint and steel, a fire charge or a redstone signal; it counts down (config fuseSeconds)
 * with a rising beep, then detonates. Sneak-right-click with both hands empty to disarm (vanilla skips block
 * interactions for a sneaking player who holds anything). Explosions do not set it off,
 * so a stray creeper cannot ruin a base; it must be armed on purpose.
 */
public class NukeBlock extends BaseEntityBlock {
    public static final MapCodec<NukeBlock> CODEC = simpleCodec(NukeBlock::new);
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NukeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ARMED, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<NukeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NukeBlockEntity(pos, state);
    }


    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(ARMED)) return null;
        return createTickerHelper(type, AtomicSky.NUKE_BE.get(), NukeBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean igniter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        if (!igniter) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!state.getValue(ARMED)) {
            if (!level.isClientSide) {
                arm(level, pos, state, player);
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                } else {
                    stack.consume(1, player);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(ARMED) && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ARMED, false), 3);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 0.6F);
                player.displayClientMessage(Component.translatable("message.atomicsky.disarmed"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!state.getValue(ARMED) && level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.atomicsky.how_to_arm"), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && !state.getValue(ARMED) && level.hasNeighborSignal(pos)) {
            arm(level, pos, state, null);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide && !state.getValue(ARMED) && level.hasNeighborSignal(pos)) {
            arm(level, pos, state, null);
        }
    }

    public static void arm(Level level, BlockPos pos, BlockState state, Player player) {
        level.setBlock(pos, state.setValue(ARMED, true), 3);
        if (level.getBlockEntity(pos) instanceof NukeBlockEntity nuke) {
            nuke.startFuse(ASConfig.FUSE_SECONDS.get() * 20, player);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0F, 0.5F);
        if (level instanceof ServerLevel server) {
            Component msg = Component.translatable("message.atomicsky.armed", ASConfig.FUSE_SECONDS.get());
            for (Player p : server.players()) {
                if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 200 * 200) p.displayClientMessage(msg, true);
            }
        }
    }

    /** Explosions never trigger the bomb; they just fail to break it (blast resistance is high anyway). */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // Intentionally does nothing: a creeper or TNT must not set off a nuke. The block survives.
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atomicsky.nuke.1"));
        tooltip.add(Component.translatable("tooltip.atomicsky.nuke.2"));
    }
}
