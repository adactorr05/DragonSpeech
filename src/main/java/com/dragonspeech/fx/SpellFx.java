package com.dragonspeech.fx;

import com.dragonspeech.network.ParticleSpawnPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The server-side face of the particle engine: a builder mirroring EBW's
 * ParticleBuilder, except every spawn is serialized into a
 * ParticleSpawnPayload and sent to nearby clients (Dragon Speech's spell
 * logic runs entirely server-side, so unlike EBW there is no client code
 * path that spawns fx directly during a cast).
 *
 * Usage from any effect handler or engine:
 *
 *   SpellFx.of(DragonSpeechParticles.MAGIC_FIRE)
 *       .pos(hit).vel(0, 0.05, 0).color(0xff6d2a).fade(0xffd211)
 *       .count(12).jitter(0.3)
 *       .spawn(level);
 *
 * plus the static helpers (trail/burst/arc/beam/sphere/flash/spiral/
 * buffSwirl) that cover the common spell-visual patterns.
 */
public final class SpellFx {

    /** How far away a player can be and still be shown the fx. */
    private static final double VIEW_RANGE = 96.0;

    private SpellFx() {}

    public static Builder of(SimpleParticleType type) {
        return new Builder(type);
    }

    // ============================== Convenience helpers ==============================

    /** A line of motes from `from` to `to`, one every `spacing` blocks, drifting gently. */
    public static void trail(ServerLevel level, SimpleParticleType type, int color, int fadeColor, Vec3 from, Vec3 to, double spacing) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 0.001) {
            return;
        }
        Vec3 step = delta.normalize().scale(spacing);
        int steps = Math.min(64, (int) (distance / spacing));

        Vec3 pos = from;
        for (int i = 0; i <= steps; i++) {
            of(type).pos(pos)
                .vel((level.getRandom().nextDouble() - 0.5) * 0.02, 0.01, (level.getRandom().nextDouble() - 0.5) * 0.02)
                .color(color).fade(fadeColor)
                .scale(0.9f)
                .spawn(level);
            pos = pos.add(step);
        }
    }

    /** A radial puff of `count` motes at `center`, flying outward at `speed`. */
    public static void burst(ServerLevel level, SimpleParticleType type, int color, int fadeColor, Vec3 center, int count, double speed) {
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
            if (dir.lengthSqr() < 0.0001) {
                dir = new Vec3(0, 1, 0);
            }
            dir = dir.normalize().scale(speed * (0.5 + random.nextDouble()));
            of(type).pos(center).vel(dir).color(color).fade(fadeColor).spawn(level);
        }
    }

    /** A forked lightning arc stretched from `from` to `to` (the ParticleLightning renderer). */
    public static void arc(ServerLevel level, int color, Vec3 from, Vec3 to) {
        of(DragonSpeechParticles.LIGHTNING).pos(from).target(to).color(color).spawn(level);
    }

    /**
     * Forked lightning whose origin is linked to an entity (normally the caster or the previous
     * chained target).  `from` is still supplied in world coordinates so callers can use the same
     * hand/centre point they used for gameplay; the payload stores it relative to the owner so the
     * arc does not jump back to the camera or lag behind a moving player.
     */
    public static void arc(ServerLevel level, int color, Entity owner, Vec3 from, Vec3 to, int lifetime) {
        if (owner == null) {
            of(DragonSpeechParticles.LIGHTNING).pos(from).target(to).color(color).time(lifetime).spawn(level);
            return;
        }
        Vec3 relative = from.subtract(owner.position());
        of(DragonSpeechParticles.LIGHTNING)
            .pos(relative)
            .entity(owner)
            .target(to)
            .color(color)
            .time(lifetime)
            .spawn(level);
    }

    /** A straight glowing beam stretched from `from` to `to`. */
    public static void beam(ServerLevel level, int color, Vec3 from, Vec3 to) {
        of(DragonSpeechParticles.BEAM).pos(from).target(to).color(color).time(6).spawn(level);
    }

    /** An expanding translucent sphere shell at `center`; `scale` sets its final radius (roughly scale * 10 / 2 blocks). */
    public static void sphere(ServerLevel level, int color, Vec3 center, float scale) {
        of(DragonSpeechParticles.SPHERE).pos(center).color(color).scale(scale).spawn(level);
    }

    /** A soft pulsing flash at `center`. */
    public static void flash(ServerLevel level, int color, Vec3 center) {
        of(DragonSpeechParticles.FLASH).pos(center).color(color).spawn(level);
    }

    /** A slow ring of motes spiralling around an entity (auras). */
    public static void spiral(ServerLevel level, SimpleParticleType type, int color, int fadeColor, Entity around, double radius) {
        var random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            of(type)
                .pos(0, 0.4 + random.nextDouble() * (around.getBbHeight() * 0.8), 0)
                .entity(around)
                .spin(radius, 0.02 + random.nextDouble() * 0.01)
                .color(color).fade(fadeColor)
                .time(50)
                .spawn(level);
        }
    }

    /** The rising buff swirl on an entity (EBW's buff.png sleeve effect). */
    public static void buffSwirl(ServerLevel level, int color, Entity on) {
        of(DragonSpeechParticles.BUFF).pos(0, 0, 0).entity(on).color(color).spawn(level);
    }

    // ============================== Builder ==============================

    public static final class Builder {
        private final SimpleParticleType type;
        private double x, y, z;
        private double vx = Double.NaN, vy = Double.NaN, vz = Double.NaN;
        private float r = -1, g = -1, b = -1;
        private float fr = -1, fg = -1, fb = -1;
        private int lifetime = -1;
        private float scale = 1;
        private boolean gravity, shaded, collide;
        private double spinRadius, spinSpeed;
        private float yaw = Float.NaN, pitch = Float.NaN;
        private long seed;
        private double length = -1;
        private double tx = Double.NaN, ty = Double.NaN, tz = Double.NaN;
        private Entity entity, target;
        private int count = 1;
        private double jitter;

        private Builder(SimpleParticleType type) {
            this.type = type;
        }

        public Builder pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder pos(Vec3 pos) {
            return pos(pos.x, pos.y, pos.z);
        }

        public Builder vel(double vx, double vy, double vz) {
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            return this;
        }

        public Builder vel(Vec3 vel) {
            return vel(vel.x, vel.y, vel.z);
        }

        /** 0xRRGGBB */
        public Builder color(int hex) {
            this.r = ((hex >> 16) & 0xff) / 255f;
            this.g = ((hex >> 8) & 0xff) / 255f;
            this.b = (hex & 0xff) / 255f;
            return this;
        }

        /** 0xRRGGBB - the colour the particle fades to over its lifetime. */
        public Builder fade(int hex) {
            this.fr = ((hex >> 16) & 0xff) / 255f;
            this.fg = ((hex >> 8) & 0xff) / 255f;
            this.fb = (hex & 0xff) / 255f;
            return this;
        }

        public Builder time(int lifetime) {
            this.lifetime = lifetime;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder gravity(boolean gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder shaded(boolean shaded) {
            this.shaded = shaded;
            return this;
        }

        public Builder collide(boolean collide) {
            this.collide = collide;
            return this;
        }

        /** Orbit around the spawn point (or linked entity): radius in blocks, speed in rotations/tick. */
        public Builder spin(double radius, double speed) {
            this.spinRadius = radius;
            this.spinSpeed = speed;
            return this;
        }

        /** Fixed facing instead of billboarding. yaw 0 = south; pitch 90 = flat on the ground. */
        public Builder face(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        /** Shared random seed, so several particles (or several ticks) keep the same randomized shape. */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** For stretched particles linked to an entity: reach this far along its line of sight. */
        public Builder length(double length) {
            this.length = length;
            return this;
        }

        /** For stretched particles (BEAM/LIGHTNING/GUARDIAN_BEAM): the far end point. */
        public Builder target(Vec3 target) {
            this.tx = target.x;
            this.ty = target.y;
            this.tz = target.z;
            return this;
        }

        /** For stretched particles: follow this entity as the far end. */
        public Builder target(Entity target) {
            this.target = target;
            return this;
        }

        /** Link to an entity: pos() becomes relative to it and the particle moves with it. */
        public Builder entity(Entity entity) {
            this.entity = entity;
            return this;
        }

        /** Spawn `count` copies in one packet... */
        public Builder count(int count) {
            this.count = Math.max(1, count);
            return this;
        }

        /** ...each offset randomly by up to `jitter` blocks on every axis. */
        public Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }

        public void spawn(ServerLevel level) {
            ParticleSpawnPayload payload = new ParticleSpawnPayload(
                BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString(),
                x, y, z, vx, vy, vz,
                r, g, b, fr, fg, fb,
                lifetime, scale, gravity, shaded, collide,
                spinRadius, spinSpeed, yaw, pitch, seed, length,
                tx, ty, tz,
                entity != null ? entity.getId() : -1,
                target != null ? target.getId() : -1,
                count, jitter
            );

            // Fx anchored to an entity should be judged by the entity's
            // position, not a (0,0,0)-relative one.
            Vec3 focus = entity != null ? entity.position() : new Vec3(x, y, z);

            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(focus) <= VIEW_RANGE * VIEW_RANGE) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }
}
