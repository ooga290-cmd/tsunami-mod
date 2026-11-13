package com.tsunamimod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** One wave that is currently rolling across a world. */
final class Tsunami {
    final ServerWorld world;
    final TsunamiSettings settings;
    final double originX, originZ;   // where the front starts
    final double dirX, dirZ;         // unit vector the wave travels along
    final double perpX, perpZ;       // unit vector pointing sideways along the wall

    private final WaveVisuals visuals;
    private final WaveDestroyer destroyer;
    private double front;            // distance travelled from the origin
    private int age;
    private boolean finished;

    Tsunami(ServerWorld world, TsunamiSettings settings, double playerX, double playerY, double playerZ,
            double lookX, double lookZ) {
        this.world = world;
        this.settings = settings;

        // The wave spawns in the direction the player is looking and travels towards the player.
        this.originX = playerX + lookX * settings.startDistance();
        this.originZ = playerZ + lookZ * settings.startDistance();
        this.dirX = -lookX;
        this.dirZ = -lookZ;
        this.perpX = -dirZ;
        this.perpZ = dirX;

        this.visuals = new WaveVisuals(world, settings, dirX, dirZ, originX, originZ, playerY, perpX, perpZ);
        this.destroyer = new WaveDestroyer(world, this);
    }

    boolean isFinished() {
        return finished;
    }

    void tick() {
        if (finished) return;
        age++;

        double prev = front;
        front += settings.speed();

        double fx = originX + dirX * front;
        double fz = originZ + dirZ * front;
        visuals.update(fx, fz, perpX, perpZ);

        if (settings.destroy()) destroyer.rip(prev, front);
        affectEntities(fx, fz);
        spawnEffects(fx, fz);

        if (front >= settings.startDistance() + settings.runPast()) stop();
    }

    void stop() {
        finished = true;
        visuals.discard();
    }

    // ------------------------------------------------------------------ entities

    private void affectEntities(double fx, double fz) {
        double half = settings.width() / 2.0 + 2;
        double reach = Math.max(half, settings.thickness()) + 4;
        Box box = new Box(fx - reach, world.getBottomY(), fz - reach, fx + reach, world.getTopY(), fz + reach);

        List<LivingEntity> living = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && inside(e, fx, fz));
        for (LivingEntity e : living) {
            // tell the client to tint the screen while the player is inside the wave
            if (e instanceof ServerPlayerEntity sp && age % 4 == 0) {
                ServerPlayNetworking.send(sp, new WaveOverlayPayload(1.0f));
            }
            if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) continue;
            push(e);
            if (age % 10 == 0 && settings.damage() > 0) {
                e.damage(world.getDamageSources().drown(), settings.damage());
            }
        }

        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, e -> inside(e, fx, fz));
        for (ItemEntity item : items) push(item);
    }

    /** True when the entity is within the body of the wave (between front face and back, within the width). */
    private boolean inside(Entity e, double fx, double fz) {
        double rx = e.getX() - originX;
        double rz = e.getZ() - originZ;
        double f = rx * dirX + rz * dirZ;
        double u = rx * perpX + rz * perpZ;
        return Math.abs(u) <= settings.width() / 2.0
                && f <= front && f >= front - settings.thickness();
    }

    private void push(Entity e) {
        double sp = settings.speed() * 1.05;
        e.setVelocity(dirX * sp, Math.max(e.getVelocity().y, 0.35), dirZ * sp);
        e.velocityModified = true;
        e.fallDistance = 0;
    }

    // ------------------------------------------------------------------ particles & sound

    private void spawnEffects(double fx, double fz) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(fx, player.getY(), fz) > 300.0 * 300.0) continue;

            // the player's own height is the reference for where the foam line is drawn
            double groundY = player.getY();
            double top = groundY + settings.height();

            for (double u = -settings.width() / 2.0; u <= settings.width() / 2.0; u += 7) {
                double x = fx + perpX * u;
                double z = fz + perpZ * u;
                // spray on the ground at the front, foam falling off the crest
                world.spawnParticles(player, ParticleTypes.SPLASH, true, x + dirX * 6, groundY + 1, z + dirZ * 6,
                        10, 3.0, 2.0, 3.0, 0.4);
                world.spawnParticles(player, ParticleTypes.CLOUD, true, x + dirX * 10, top - 2, z + dirZ * 10,
                        4, 3.0, 3.0, 3.0, 0.02);
            }

            // low rumble that gets louder the closer the wave is
            if (age % 20 == 0) {
                double dist = Math.sqrt(player.squaredDistanceTo(fx, player.getY(), fz));
                float vol = (float) Math.max(0.3, Math.min(8.0, 400.0 / (dist + 20.0)));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT,
                        vol, 0.5f + rand.nextFloat() * 0.2f);
            }
        }
    }
}
