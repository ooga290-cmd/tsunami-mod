package com.tsunamimod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the wave using vanilla block_display entities (stretched blocks), so it needs
 * no custom models or shaders and shows up for every player who has the mod installed.
 *
 * The wave is a stack of 5 slabs (dark base, two mid slabs, a white crest that overhangs
 * the front, and a white foam skirt on the ground) split into several side-by-side
 * segments so each segment can follow the terrain height on its own.
 */
final class WaveVisuals {
    static final String TAG = "tsunami_visual";

    /** Blocks buried under the ground so the wall never shows a gap on uneven terrain. */
    private static final int UNDERGROUND = 8;
    private static final double SEGMENT_WIDTH = 24.0;

    /** Fractions of height (y) and thickness (z). z: 0 = front face of the wave, negative = behind it. */
    private record Layer(String block, double y0, double y1, double zBack, double zFront) {}

    private static final Layer[] LAYERS = {
            new Layer("minecraft:dark_prismarine", 0.00, 0.40, -1.00, 0.00),
            new Layer("minecraft:prismarine",      0.40, 0.65, -0.95, 0.10),
            new Layer("minecraft:prismarine",      0.65, 0.85, -0.80, 0.22),
            new Layer("minecraft:snow_block",      0.85, 1.00, -0.60, 0.35), // overhanging crest
            new Layer("minecraft:white_concrete",  0.08, 0.16,  0.00, 0.30), // foam skirt at the base
    };

    private record Piece(Entity entity, int segment) {}

    private final ServerWorld world;
    private final int segments;
    private final double[] lateral;   // sideways offset of each segment's centre
    private final double[] baseY;     // current ground level of each segment (smoothed)
    private final List<Piece> pieces = new ArrayList<>();

    WaveVisuals(ServerWorld world, TsunamiSettings s, double dirX, double dirZ,
                double startX, double startZ, double startY, double perpX, double perpZ) {
        this.world = world;
        this.segments = Math.max(1, (int) Math.ceil(s.width() / SEGMENT_WIDTH));
        this.lateral = new double[segments];
        this.baseY = new double[segments];

        double segW = (double) s.width() / segments;
        // Rotation that turns the local +Z axis (travel direction) into the real direction.
        Quaternionf rot = new Quaternionf().rotationY((float) Math.atan2(dirX, dirZ));
        double totalH = s.height() + UNDERGROUND;

        for (int i = 0; i < segments; i++) {
            lateral[i] = (i + 0.5) * segW - s.width() / 2.0;
            baseY[i] = startY;
            for (Layer l : LAYERS) {
                double sx = segW + 0.6; // tiny overlap hides seams between segments
                double sy = (l.y1() - l.y0()) * totalH;
                double sz = (l.zFront() - l.zBack()) * s.thickness();

                Vector3f corner = new Vector3f(
                        (float) (-sx / 2.0),
                        (float) (l.y0() * totalH - UNDERGROUND),
                        (float) (l.zBack() * s.thickness()));
                rot.transform(corner);

                Entity e = EntityType.BLOCK_DISPLAY.create(world);
                if (e == null) continue;
                e.readNbt(buildNbt(l.block(), rot, sx, sy, sz, corner));
                e.setPosition(startX + perpX * lateral[i], startY, startZ + perpZ * lateral[i]);
                TsunamiManager.markOwned(e);
                world.spawnEntity(e);
                pieces.add(new Piece(e, i));
            }
        }
    }

    private static NbtCompound buildNbt(String block, Quaternionf rot, double sx, double sy, double sz, Vector3f t) {
        NbtCompound nbt = new NbtCompound();

        NbtCompound state = new NbtCompound();
        state.putString("Name", block);
        nbt.put("block_state", state);

        NbtCompound tr = new NbtCompound();
        tr.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("scale", floats((float) sx, (float) sy, (float) sz));
        tr.put("translation", floats(t.x, t.y, t.z));
        nbt.put("transformation", tr);

        NbtCompound light = new NbtCompound();
        light.putInt("block", 6);
        light.putInt("sky", 15);
        nbt.put("brightness", light);

        nbt.putFloat("view_range", 5.0f);   // visible from ~320 blocks
        nbt.putFloat("shadow_radius", 0.0f);
        nbt.putInt("teleport_duration", 3);  // client smooths the movement

        NbtList tags = new NbtList();
        tags.add(NbtString.of(TAG));
        nbt.put("Tags", tags);
        return nbt;
    }

    private static NbtList floats(float... v) {
        NbtList list = new NbtList();
        for (float f : v) list.add(NbtFloat.of(f));
        return list;
    }

    /** Moves every slab to the current front position and lets each segment follow the terrain. */
    void update(double frontX, double frontZ, double perpX, double perpZ) {
        for (int i = 0; i < segments; i++) {
            int bx = (int) Math.floor(frontX + perpX * lateral[i]);
            int bz = (int) Math.floor(frontZ + perpZ * lateral[i]);
            if (TsunamiManager.isLoaded(world, bx, bz)) {
                double target = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                double step = Math.max(-1.5, Math.min(1.5, (target - baseY[i]) * 0.15));
                baseY[i] += step;
            }
        }
        for (Piece p : pieces) {
            int i = p.segment();
            p.entity().setPosition(frontX + perpX * lateral[i], baseY[i], frontZ + perpZ * lateral[i]);
        }
    }

    void discard() {
        for (Piece p : pieces) p.entity().discard();
        pieces.clear();
    }
}
