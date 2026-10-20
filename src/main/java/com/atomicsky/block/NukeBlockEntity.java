package com.atomicsky.block;

import com.atomicsky.AtomicSky;
import com.atomicsky.world.DetonationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Holds the fuse countdown of an armed bomb. */
public class NukeBlockEntity extends BlockEntity {
    private int fuse = -1;

    private UUID armedBy;

    public NukeBlockEntity(BlockPos pos, BlockState state) {
        super(AtomicSky.NUKE_BE.get(), pos, state);
    }

    public void startFuse(int ticks, Player player) {
        this.fuse = ticks;
        this.armedBy = player == null ? null : player.getUUID();
        setChanged();
    }

    public int getFuse() {
        return fuse;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NukeBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !state.getValue(NukeBlock.ARMED)) return;
        if (be.fuse < 0) be.fuse = com.atomicsky.ASConfig.FUSE_SECONDS.get() * 20; // armed before a reload: restart the fuse
        be.fuse--;
        int secondsLeft = be.fuse / 20;
        if (be.fuse % 20 == 0) {
            float pitch = secondsLeft <= 3 ? 1.6F : 0.9F + (0.5F * (1.0F - Math.min(1.0F, secondsLeft / 10.0F)));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, pitch);
            be.setChanged();
        } else if (secondsLeft <= 3 && be.fuse % 5 == 0) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 1.9F);
        }
        if (be.fuse <= 0) {
            level.removeBlock(pos, false);
            DetonationManager.get(server).detonate(server, pos.getCenter(), com.atomicsky.ASConfig.BLAST_RADIUS.get(), be.armedBy);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Fuse", fuse);
        if (armedBy != null) tag.putUUID("ArmedBy", armedBy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuse = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
        armedBy = tag.hasUUID("ArmedBy") ? tag.getUUID("ArmedBy") : null;
    }
}
