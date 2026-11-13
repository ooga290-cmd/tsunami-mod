package com.tsunamimod;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Keeps track of every running tsunami and ticks them once per world tick. */
public final class TsunamiManager {
    private static final List<Tsunami> ACTIVE = new ArrayList<>();
    private static final Set<UUID> OWNED = new HashSet<>();

    private TsunamiManager() {}

    static void start(ServerWorld world, TsunamiSettings settings, double x, double y, double z,
                      double lookX, double lookZ) {
        ACTIVE.add(new Tsunami(world, settings, x, y, z, lookX, lookZ));
    }

    static int stopAll() {
        int n = ACTIVE.size();
        for (Tsunami t : ACTIVE) t.stop();
        ACTIVE.clear();
        return n;
    }

    static void tick(ServerWorld world) {
        Iterator<Tsunami> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Tsunami t = it.next();
            if (t.world != world) continue;
            t.tick();
            if (t.isFinished()) it.remove();
        }
    }

    // ---- bookkeeping so wave entities never get left behind in the save file ----

    static void markOwned(Entity e) {
        OWNED.add(e.getUuid());
    }

    /** Called when any entity loads: wave entities that no running wave owns are leftovers and are removed. */
    static void cleanupIfOrphan(Entity e) {
        if (e.getCommandTags().contains(WaveVisuals.TAG) && !OWNED.contains(e.getUuid())) {
            e.discard();
        }
    }

    static void clearAll() {
        stopAll();
        OWNED.clear();
    }

    /** True if the chunk containing this block column is loaded (never forces chunk generation). */
    static boolean isLoaded(ServerWorld world, int blockX, int blockZ) {
        return world.getChunkManager().getWorldChunk(blockX >> 4, blockZ >> 4) != null;
    }
}
