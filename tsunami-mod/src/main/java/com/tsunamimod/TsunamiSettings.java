package com.tsunamimod;

/**
 * All tunable numbers for one tsunami. Sizes are in blocks, speed is blocks per tick.
 */
public record TsunamiSettings(
        int width,            // how wide the wall of water is (sideways)
        int height,           // how tall the wave is above the ground
        int thickness,        // depth of the wave body along its travel direction
        double speed,         // blocks per tick (0.9 = 18 blocks/second)
        double startDistance, // how far from the player the wave spawns
        double runPast,       // how far past the player the wave keeps travelling
        boolean destroy,      // rip up blocks?
        float damage          // damage dealt every 10 ticks to anything inside the wave
) {
    public static TsunamiSettings defaults() {
        return new TsunamiSettings(110, 60, 44, 0.9, 120.0, 200.0, true, 3.0f);
    }

    public TsunamiSettings withHeight(int h) {
        return new TsunamiSettings(width, h, thickness, speed, startDistance, runPast, destroy, damage);
    }

    public TsunamiSettings withSpeed(double s) {
        return new TsunamiSettings(width, height, thickness, s, startDistance, runPast, destroy, damage);
    }

    public TsunamiSettings withDestroy(boolean d) {
        return new TsunamiSettings(width, height, thickness, speed, startDistance, runPast, d, damage);
    }
}
