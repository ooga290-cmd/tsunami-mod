package com.tsunamimod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rips the land apart where the wave front passes: everything above the natural ground is
 * removed (houses, trees, plants), a few layers of ground are scraped off and the exposed
 * surface turns to mud. Some of the removed blocks are thrown into the air as debris.
 */
final class WaveDestroyer {
    private static final int MAX_DEBRIS_PER_TICK = 40;
    private static final int MAX_SCAN_DOWN = 48;

    private final ServerWorld world;
    private final Tsunami t;
    private int debris;

    WaveDestroyer(ServerWorld world, Tsunami t) {
        this.world = world;
        this.t = t;
    }

    /** Handles every column whose centre lies between the previous and the current front position. */
    void rip(double prevFront, double front) {
        debris = 0;
        double half = t.settings.width() / 2.0;

        double[] fs = {prevFront, front};
        double[] us = {-half, half};
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double f : fs) {
            for (double u : us) {
                double x = t.originX + t.dirX * f + t.perpX * u;
                double z = t.originZ + t.dirZ * f + t.perpZ * u;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }

        for (int x = (int) Math.floor(minX) - 1; x <= (int) Math.ceil(maxX) + 1; x++) {
            for (int z = (int) Math.floor(minZ) - 1; z <= (int) Math.ceil(maxZ) + 1; z++) {
                double rx = x + 0.5 - t.originX;
                double rz = z + 0.5 - t.originZ;
                double f = rx * t.dirX + rz * t.dirZ;
                if (f <= prevFront || f > front) continue;
                double u = rx * t.perpX + rz * t.perpZ;
                if (Math.abs(u) > half) continue;
                ripColumn(x, z);
            }
        }
    }

    private void ripColumn(int x, int z) {
        if (!TsunamiManager.isLoaded(world, x, z)) return;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int minY = world.getBottomY();
        int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        int limit = Math.max(minY, surface - MAX_SCAN_DOWN);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 1) everything above the natural ground
        int y = surface - 1;
        boolean foundGround = false;
        while (y >= limit) {
            pos.set(x, y, z);
            BlockState st = world.getBlockState(pos);
            if (st.isAir() || st.getBlock() instanceof FluidBlock) {
                y--;
                continue;
            }
            if (isGround(st)) {
                foundGround = true;
                break;
            }
            remove(pos, st, true);
            y--;
        }
        if (!foundGround) return;

        // 2) scrape a few layers off the ground
        int depth = 2 + rand.nextInt(3);
        for (int i = 0; i < depth && y >= minY; i++, y--) {
            pos.set(x, y, z);
            BlockState st = world.getBlockState(pos);
            if (st.isAir() || st.getBlock() instanceof FluidBlock) break;
            if (!remove(pos, st, false)) return;
        }

        // 3) leave wet mud behind the wave
        if (y >= minY) {
            pos.set(x, y, z);
            BlockState st = world.getBlockState(pos);
            if ((st.isIn(BlockTags.DIRT) || st.isIn(BlockTags.SAND)) && !st.isOf(Blocks.MUD)
                    && st.getFluidState().isEmpty() && rand.nextInt(100) < 85) {
                world.setBlockState(pos, Blocks.MUD.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static boolean isGround(BlockState st) {
        return st.isIn(BlockTags.DIRT) || st.isIn(BlockTags.SAND) || st.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || st.isIn(BlockTags.TERRACOTTA)
                || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.CLAY) || st.isOf(Blocks.DIRT_PATH)
                || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.ICE) || st.isOf(Blocks.PACKED_ICE);
    }

    /** Returns false if the block is protected and was left alone. */
    private boolean remove(BlockPos pos, BlockState st, boolean allowDebris) {
        if (st.hasBlockEntity()) return false;                      // chests, furnaces ... never destroyed
        if (st.getHardness(world, pos) < 0) return false;           // bedrock, barriers
        if (st.getBlock().getBlastResistance() >= 1200.0f) return false; // obsidian & co.

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (allowDebris && debris < MAX_DEBRIS_PER_TICK && rand.nextInt(5) == 0) {
            BlockPos fixed = pos.toImmutable();
            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(world, fixed, st);
            fb.setDestroyedOnLanding();
            fb.dropItem = false;
            double sp = t.settings.speed() * (1.0 + rand.nextDouble() * 0.6);
            fb.setVelocity(
                    t.dirX * sp + (rand.nextDouble() - 0.5) * 0.5,
                    0.35 + rand.nextDouble() * 0.9,
                    t.dirZ * sp + (rand.nextDouble() - 0.5) * 0.5);
            fb.velocityModified = true;
            debris++;
            return true;
        }
        // keep water where the block was waterlogged, otherwise this becomes air
        world.setBlockState(pos, st.getFluidState().getBlockState(), Block.NOTIFY_LISTENERS);
        return true;
    }
}
