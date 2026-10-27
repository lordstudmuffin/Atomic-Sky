package com.atomicsky.block;

import com.atomicsky.ASConfig;
import com.atomicsky.AtomicSky;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.BlockTags;

/**
 * Fallout on the ground: a snow-like layered block of radioactive ash. Builds up 1..8 layers under the plume, gives
 * Radiation to whatever stands in it, and crumbles away over the configured number of days on random ticks.
 */
public class FalloutLayerBlock extends Block {
    public static final MapCodec<FalloutLayerBlock> CODEC = simpleCodec(FalloutLayerBlock::new);
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[]{
            Shapes.empty(),
            Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 0, 0, 16, 4, 16), Block.box(0, 0, 0, 16, 6, 16),
            Block.box(0, 0, 0, 16, 8, 16), Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 0, 0, 16, 12, 16),
            Block.box(0, 0, 0, 16, 14, 16), Block.box(0, 0, 0, 16, 16, 16)};

    public FalloutLayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected MapCodec<FalloutLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[Math.max(0, state.getValue(LAYERS) - 1)];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LAYERS) == 8 ? 0.2F : 1.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) return false;
        if (below.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) return true;
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP)
                || (below.is(this) && below.getValue(LAYERS) == 8);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (context.getItemInHand().is(this.asItem()) && layers < 8) {
            return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
        }
        return layers == 1;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(LAYERS, Math.min(8, existing.getValue(LAYERS) + 1));
        }
        return super.getStateForPlacement(context);
    }

    /** Fallout crumbles away: a stack of maxLayers lasts about decayDays in-game days, thinner patches go sooner. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A block gets a random tick about every 68 s at the default speed; 24000 ticks per day -> ~17.6 random ticks per day.
        double ticksPerDay = 17.6;
        double chance = 1.0 / Math.max(1.0, ticksPerDay * ASConfig.FALLOUT_DECAY_DAYS.get() / ASConfig.FALLOUT_MAX_LAYERS.get());
        if (random.nextDouble() < chance) {
            int layers = state.getValue(LAYERS);
            if (layers <= 1) level.removeBlock(pos, false);
            else level.setBlock(pos, state.setValue(LAYERS, layers - 1), 2);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !ASConfig.FALLOUT_RADIATION.get()) return;
        if (entity instanceof LivingEntity living && level.getGameTime() % 20 == 0) {
            if (living instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) return;
            if (com.atomicsky.item.RadiationSuitItem.isProtected(living)) return;
            int amp = state.getValue(LAYERS) >= 4 ? 1 : 0;
            living.addEffect(new MobEffectInstance(AtomicSky.RADIATION, 200, amp, false, true, true));
        }
    }

    public static boolean canPlaceLayerAt(LevelReader level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!(at.isAir() || (at.canBeReplaced() && at.getFluidState().isEmpty() && !at.is(Blocks.SNOW)))) return false;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) return false;
        if (below.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) return true;
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP);
    }
}
