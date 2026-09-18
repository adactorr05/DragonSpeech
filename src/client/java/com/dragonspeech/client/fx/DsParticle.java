package com.dragonspeech.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Abstract superclass for all of Dragon Speech's magic particles. This is
 * a direct port of Electroblob's Wizardry's ParticleWizardry (from the
 * 1.20.1 community port), adapted to the 1.21.1 rendering API:
 *
 * - VertexConsumer's vertex(...)...endVertex() chain became
 *   addVertex(...).setUv(...).setColor(...).setLight(...).
 * - Everything else (fade colours, spin, entity linking, seeded
 *   randomness, custom facing) is unchanged in behaviour.
 *
 * All subclasses have a single (world, x, y, z, spriteSet) constructor
 * that sets that particle's defaults; ClientParticleSpawner then
 * overwrites whatever the server's SpellFx builder specified.
 *
 * VERSION-RISK NOTE: TextureSheetParticle field names (rCol, xd, quadSize,
 * hasPhysics, gravity...) are long-standing Mojang mappings, but this is
 * freshly-written client rendering code with no compiler available here -
 * if anything in this package fails to resolve, check the decompiled
 * TextureSheetParticle/SingleQuadParticle in your IDE first.
 */
public abstract class DsParticle extends TextureSheetParticle {

    /** particle type -> "make me one of these at this position" factory, used by ClientParticleSpawner. Filled in DragonSpeechParticleFactories. */
    public static final Map<SimpleParticleType, BiFunction<ClientLevel, Vec3, DsParticle>> PROVIDERS = new LinkedHashMap<>();

    /** The fraction of the impact velocity that should be the maximum spread speed added on impact. */
    private static final double SPREAD_FACTOR = 0.2;
    /** Lateral velocity is reduced by this factor on impact, before adding random spread velocity. */
    private static final double IMPACT_FRICTION = 0.2;

    private final boolean updateTextureOnTick;

    /**
     * Seed used for anything randomised in rendering, so randomised shapes
     * (lightning forks, etc.) stay stable across frames. Settable from the
     * server so several particles can share one shape.
     */
    protected long seed;
    /** Deliberately hides Particle's RandomSource field - this one is re-seedable. */
    protected Random random = new Random();

    /** True if the particle is shaded by world light, false for full brightness (default). */
    protected boolean shaded = false;

    protected float initialRed;
    protected float initialGreen;
    protected float initialBlue;
    protected float fadeRed = 0;
    protected float fadeGreen = 0;
    protected float fadeBlue = 0;

    protected float angle;
    protected double radius = 0;
    protected double speed = 0;

    /** The entity this particle is linked to; the particle moves with it. */
    protected Entity entity = null;
    /** Position relative to the linked entity (or spin centre when there's no entity). */
    protected double relativeX, relativeY, relativeZ;
    /** Velocity relative to the linked entity. */
    protected double relativeMotionX, relativeMotionY, relativeMotionZ;

    /** Facing yaw, or NaN to always face the viewer (default). */
    protected float yaw = Float.NaN;
    /** Facing pitch, or NaN to always face the viewer (default). */
    protected float pitch = Float.NaN;

    protected boolean adjustQuadSize;

    SpriteSet spriteSet;

    /** Previous-tick velocity, used in collision spread. */
    private double prevVelX, prevVelY, prevVelZ;

    public DsParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet, boolean updateTextureOnTick) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.relativeX = this.x;
        this.relativeY = this.y;
        this.relativeZ = this.z;
        this.updateTextureOnTick = updateTextureOnTick;
        this.setSpriteFromAge(spriteSet);
    }

    // ============================== Parameter setters ==============================

    public void setSeed(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public void setShaded(boolean shaded) {
        this.shaded = shaded;
    }

    public void setGravity(boolean gravity) {
        this.gravity = gravity ? 1 : 0;
    }

    public void setCollisions(boolean canCollide) {
        this.hasPhysics = canCollide;
    }

    @Override
    public void setParticleSpeed(double velocityX, double velocityY, double velocityZ) {
        super.setParticleSpeed(velocityX, velocityY, velocityZ);
    }

    /**
     * Sets the spin parameters of the particle.
     *
     * @param radius The spin radius
     * @param speed  The spin speed in rotations per tick
     */
    public void setSpin(double radius, double speed) {
        this.radius = radius;
        this.speed = speed * 2 * Math.PI; // rotations/tick -> radians/tick
        this.angle = this.random.nextFloat() * (float) Math.PI * 2; // random start angle
        this.x = relativeX - radius * Mth.cos(angle);
        this.z = relativeZ + radius * Mth.sin(angle);
        this.relativeMotionX = xd;
        this.relativeMotionY = yd;
        this.relativeMotionZ = zd;
    }

    /** Links this particle to the given entity; position/velocity become relative to it. */
    public void setEntity(Entity entity) {
        this.entity = entity;
        if (entity != null) {
            this.setPos(this.entity.xo + relativeX, this.entity.yo + relativeY, this.entity.zo + relativeZ);
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.relativeMotionX = xd;
            this.relativeMotionY = yd;
            this.relativeMotionZ = zd;
        }
    }

    /**
     * Sets the base colour. Note this also sets the fade colour, so a fade
     * colour must be set AFTER calling this.
     */
    @Override
    public void setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        initialRed = red;
        initialGreen = green;
        initialBlue = blue;
        setFadeColour(red, green, blue);
    }

    public void setFadeColour(float r, float g, float b) {
        this.fadeRed = r;
        this.fadeGreen = g;
        this.fadeBlue = b;
    }

    /**
     * Fixes the direction this particle faces instead of billboarding it.
     *
     * @param yaw   yaw in degrees, 0 = south
     * @param pitch pitch in degrees, 0 = horizontal
     */
    public void setFacing(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    // ============================== Overrides ==============================

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Restores the render state PARTICLE_SHEET_TRANSLUCENT.begin() set up.
     * Every particle in this package that does its own immediate-mode
     * drawing inside render() (lightning, beams, spheres, the buff swirl)
     * MUST call this before returning, so the shared particle sheet drawn
     * after our geometry isn't corrupted by our shader/blend/texture state.
     */
    protected static void restoreSheetState() {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return shaded ? super.getLightColor(partialTick) : 15728880;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        updateEntityLinking(tickDelta);

        if (Float.isNaN(this.yaw) || Float.isNaN(this.pitch)) {
            super.render(vertexConsumer, camera, tickDelta);
        } else {
            float degToRad = 0.017453292f;

            float rotationX = Mth.cos(yaw * degToRad);
            float rotationZ = Mth.sin(yaw * degToRad);
            float rotationY = Mth.cos(pitch * degToRad);
            float rotationYZ = -rotationZ * Mth.sin(pitch * degToRad);
            float rotationXY = rotationX * Mth.sin(pitch * degToRad);

            drawParticle(vertexConsumer, camera, tickDelta, rotationX, rotationY, rotationZ, rotationYZ, rotationXY);
        }
    }

    /** Fixed-facing quad rendering, 1.21.1 addVertex/setUv/setColor/setLight chain. */
    protected void drawParticle(VertexConsumer buffer, Camera camera, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        Vec3 cameraPos = camera.getPosition();

        float s = this.adjustQuadSize ? 0.1f : 1;
        float f4 = s * this.getQuadSize(partialTicks);

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();

        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());

        int light = this.getLightColor(partialTicks);

        Vec3[] corners = new Vec3[]{
            new Vec3(-rotationX * f4 - rotationXY * f4, -rotationZ * f4, -rotationYZ * f4 - rotationX * f4),
            new Vec3(-rotationX * f4 + rotationXY * f4, rotationZ * f4, -rotationYZ * f4 + rotationXZ * f4),
            new Vec3(rotationX * f4 + rotationXY * f4, rotationZ * f4, rotationYZ * f4 + rotationXZ * f4),
            new Vec3(rotationX * f4 - rotationXY * f4, -rotationZ * f4, rotationYZ * f4 - rotationXZ * f4)
        };

        if (this.roll != 0.0F) {
            float rollNow = this.roll + (this.roll - this.oRoll) * partialTicks;
            float qw = Mth.cos(rollNow * 0.5F);
            float qx = Mth.sin(rollNow * 0.5F) * camera.rotation().x();
            float qy = Mth.sin(rollNow * 0.5F) * camera.rotation().y();
            float qz = Mth.sin(rollNow * 0.5F) * camera.rotation().z();
            Vec3 axis = new Vec3(qx, qy, qz);

            for (int i = 0; i < 4; ++i) {
                corners[i] = axis.scale(2.0D * corners[i].dot(axis))
                    .add(corners[i].scale((double) (qw * qw) - axis.dot(axis)))
                    .add(axis.cross(corners[i]).scale(2.0F * qw));
            }
        }

        buffer.addVertex(px + (float) corners[0].x, py + (float) corners[0].y, pz + (float) corners[0].z).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(px + (float) corners[1].x, py + (float) corners[1].y, pz + (float) corners[1].z).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(px + (float) corners[2].x, py + (float) corners[2].y, pz + (float) corners[2].z).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        buffer.addVertex(px + (float) corners[3].x, py + (float) corners[3].y, pz + (float) corners[3].z).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    protected void updateEntityLinking(float partialTicks) {
        if (this.entity != null) {
            double entityX = Mth.lerp(partialTicks, entity.xo, entity.getX());
            double entityY = Mth.lerp(partialTicks, entity.yo, entity.getY());
            double entityZ = Mth.lerp(partialTicks, entity.zo, entity.getZ());

            x = entityX + relativeX;
            y = entityY + relativeY;
            z = entityZ + relativeZ;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.hasPhysics && this.onGround) {
            this.xd /= 0.699999988079071D;
            this.zd /= 0.699999988079071D;
        }

        if (entity != null || radius > 0) {
            double x = relativeX;
            double y = relativeY;
            double z = relativeZ;

            if (this.entity != null) {
                if (!this.entity.isAlive()) {
                    this.remove();
                    return;
                } else {
                    this.relativeX += relativeMotionX;
                    this.relativeY += relativeMotionY;
                    this.relativeZ += relativeMotionZ;

                    x = this.entity.getX() + relativeX;
                    y = this.entity.getY() + relativeY;
                    z = this.entity.getZ() + relativeZ;
                }
            } else {
                this.relativeX += relativeMotionX;
                this.relativeY += relativeMotionY;
                this.relativeZ += relativeMotionZ;
            }

            if (radius > 0) {
                angle += (float) speed;
                x += radius * -Mth.cos(angle);
                z += radius * Mth.sin(angle);
            }

            this.setPos(x, y, z);
        }

        float ageFraction = (float) this.age / (float) this.lifetime;
        this.rCol = this.initialRed + (this.fadeRed - this.initialRed) * ageFraction;
        this.gCol = this.initialGreen + (this.fadeGreen - this.initialGreen) * ageFraction;
        this.bCol = this.initialBlue + (this.fadeBlue - this.initialBlue) * ageFraction;

        if (hasPhysics) {
            if (this.xd == 0 && this.prevVelX != 0) {
                this.yd *= IMPACT_FRICTION;
                this.zd *= IMPACT_FRICTION;
                this.yd += (random.nextDouble() * 2 - 1) * this.prevVelX * SPREAD_FACTOR;
                this.zd += (random.nextDouble() * 2 - 1) * this.prevVelX * SPREAD_FACTOR;
            }

            if (this.yd == 0 && this.prevVelY != 0) {
                this.xd *= IMPACT_FRICTION;
                this.zd *= IMPACT_FRICTION;
                this.xd += (random.nextDouble() * 2 - 1) * this.prevVelY * SPREAD_FACTOR;
                this.zd += (random.nextDouble() * 2 - 1) * this.prevVelY * SPREAD_FACTOR;
            }

            if (this.zd == 0 && this.prevVelZ != 0) {
                this.xd *= IMPACT_FRICTION;
                this.yd *= IMPACT_FRICTION;
                this.xd += (random.nextDouble() * 2 - 1) * this.prevVelZ * SPREAD_FACTOR;
                this.yd += (random.nextDouble() * 2 - 1) * this.prevVelZ * SPREAD_FACTOR;
            }
        }

        this.prevVelX = xd;
        this.prevVelY = yd;
        this.prevVelZ = zd;

        if (updateTextureOnTick) {
            this.setSpriteFromAge(spriteSet);
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        double d0 = dx;
        double d1 = dy;
        double d2 = dz;
        if (this.hasPhysics && (dx != 0.0D || dy != 0.0D || dz != 0.0D) && dx * dx + dy * dy + dz * dz < Mth.square(100.0D)) {
            Vec3 collided = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), this.getBoundingBox(), this.level, List.of());
            dx = collided.x;
            dy = collided.y;
            dz = collided.z;
        }

        if (dx != 0.0D || dy != 0.0D || dz != 0.0D) {
            this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
            this.setLocationFromBoundingbox();
        }

        this.onGround = d1 != dy && d1 < 0.0D;

        if (d0 != dx) {
            this.xd = 0.0D;
        }
        if (d1 != dy) {
            this.yd = 0.0D;
        }
        if (d2 != dz) {
            this.zd = 0.0D;
        }
    }
}
