package com.dragonspeech.mind;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The creature-specific half of possession, ported from EBW 1.12.2's
 * ability/projectile registries:
 *
 * WEAPONS (grantedMainHand/grantedOffHand): you take up the creature's
 * own weapon. A bow arrives enchanted with Infinity plus a single arrow
 * (a stray's arrow is tipped with slowness) - vanilla's own bow handling
 * does the rest, exactly EBW's trick. Blaze / ghast / snow golem / witch
 * get a "focus" item (blaze rod, fire charge, snowball, potion) whose
 * right-click is intercepted below to fire that creature's projectile.
 *
 * PASSIVE ABILITIES (tickAbilities): spiders climb walls, chickens fall
 * slowly, fire-immune creatures keep their possessor unburnt - EBW's
 * three built-ins, one for one.
 *
 * INTERACTION GATING: while wearing a skin you fight AS the creature -
 * melee only lands if the creature itself can melee (no punching things
 * as a creeper), a possessed creeper's "attack" is EBW's glorious
 * right-click-ignite-and-RUN, and blocks can't be broken, placed, or
 * used (the enderman block-carrying exception is deferred; see README).
 *
 * addAbility()/addProjectile() are public for the same reason EBW's
 * were: a future addon (dragons...) can register its own possessed
 * behaviors at init without touching this file.
 */
public final class PossessionAbilities {

    private static final int PROJECTILE_COOLDOWN_TICKS = 30; // EBW's PROJECTILE_COOLDOWN
    private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();

    /** Creature-type keyed right-click projectile shots. */
    private static final Map<EntityType<?>, ProjectileShot> PROJECTILES = new HashMap<>();

    @FunctionalInterface
    public interface ProjectileShot {
        void shoot(ServerLevel level, ServerPlayer shooter, Mob skin);
    }

    private PossessionAbilities() {}

    public static void addProjectile(EntityType<?> type, ProjectileShot shot) {
        PROJECTILES.put(type, shot);
    }

    static void register() {

        // ---- EBW's projectile registry, vanilla-1.21 edition ----
        addProjectile(EntityType.BLAZE, (level, shooter, skin) -> {
            Vec3 look = shooter.getLookAngle();
            // VERSION-RISK NOTE: 1.21 fireball constructors take a Vec3
            // movement/acceleration; if yours still wants (double,double,double),
            // unpack look.scale(0.6) into its components.
            SmallFireball fireball = new SmallFireball(level, shooter, look.scale(0.6));
            fireball.setPos(shooter.getX() + look.x, shooter.getEyeY() + look.y, shooter.getZ() + look.z);
            level.addFreshEntity(fireball);
            level.playSound(null, shooter.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1f, 1f);
        });

        addProjectile(EntityType.GHAST, (level, shooter, skin) -> {
            Vec3 look = shooter.getLookAngle();
            LargeFireball fireball = new LargeFireball(level, shooter, look.scale(0.6), 1);
            fireball.setPos(shooter.getX() + look.x, shooter.getEyeY() + look.y, shooter.getZ() + look.z);
            level.addFreshEntity(fireball);
            level.playSound(null, shooter.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1f, 1f);
        });

        addProjectile(EntityType.SNOW_GOLEM, (level, shooter, skin) -> {
            Snowball snowball = new Snowball(level, shooter);
            snowball.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, 1.6f, 1.0f);
            level.addFreshEntity(snowball);
            level.playSound(null, shooter.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.NEUTRAL, 1f, 1f);
        });

        addProjectile(EntityType.WITCH, (level, shooter, skin) -> {
            ThrownPotion potion = new ThrownPotion(level, shooter);
            potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING));
            potion.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), -20f, 0.75f, 1.0f);
            level.addFreshEntity(potion);
            level.playSound(null, shooter.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1f, 1f);
        });

        // Creeper: right-click to ignite - and the skin is shed for you,
        // because EBW knew exactly what happens next. ("Aaaaaaand.... RUN!")
        addProjectile(EntityType.CREEPER, (level, shooter, skin) -> {
            if (skin instanceof Creeper creeper) {
                PossessionService.end(shooter, "You light the fuse and abandon the skin. Run.");
                creeper.ignite();
            }
        });

        // ---- Right-click interception for the focus items ----
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !PossessionService.isPossessing(serverPlayer)) {
                return InteractionResultHolder.pass(held);
            }

            Mob skin = PossessionService.possessedMob(serverPlayer);
            if (skin == null) {
                return InteractionResultHolder.pass(held);
            }

            // Bows and other genuinely usable items work through vanilla.
            if (held.getItem() instanceof BowItem || held.is(Items.ARROW) || held.is(Items.TIPPED_ARROW)) {
                return InteractionResultHolder.pass(held);
            }

            ProjectileShot shot = PROJECTILES.get(skin.getType());
            if (shot != null && level instanceof ServerLevel serverLevel) {
                long now = serverLevel.getServer().getTickCount();
                if (now - LAST_SHOT.getOrDefault(serverPlayer.getUUID(), -100L) >= PROJECTILE_COOLDOWN_TICKS) {
                    LAST_SHOT.put(serverPlayer.getUUID(), now);
                    shot.shoot(serverLevel, serverPlayer, skin);
                }
                return InteractionResultHolder.success(held);
            }

            // No usable ability: the skin has no hands for that.
            return InteractionResultHolder.fail(held);
        });

        // ---- Melee gating: fight only as the creature can fight ----
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !PossessionService.isPossessing(serverPlayer)) {
                return InteractionResult.PASS;
            }
            Mob skin = PossessionService.possessedMob(serverPlayer);
            if (skin == null) {
                return InteractionResult.PASS;
            }
            // Creepers famously have no business punching (EBW's exact
            // exasperation); more generally, no attack-damage attribute
            // and no held weapon means the creature simply can't melee.
            if (skin instanceof Creeper
                || (skin.getAttribute(Attributes.ATTACK_DAMAGE) == null && skin.getMainHandItem().isEmpty())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // ---- No block breaking, placing, or using while in a skin ----
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
            !level.isClientSide() && player instanceof ServerPlayer sp && PossessionService.isPossessing(sp)
                ? InteractionResult.FAIL : InteractionResult.PASS);

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
            !level.isClientSide() && player instanceof ServerPlayer sp && PossessionService.isPossessing(sp)
                ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    // ============================== Weapons ==============================

    /** The creature's own weapon, prepared for a possessor's hand - bows gain Infinity, an enderman's held block becomes its item form. */
    static ItemStack grantedMainHand(Mob target, ServerLevel level) {
        if (target instanceof EnderMan enderMan && enderMan.getCarriedBlock() != null) {
            return new ItemStack(enderMan.getCarriedBlock().getBlock());
        }

        ItemStack stack = target.getMainHandItem().copy();

        if (stack.getItem() instanceof BowItem) {
            // VERSION-RISK NOTE: 1.21 enchanting-by-code goes through the
            // enchantment registry's Holder. If registryOrThrow/getHolderOrThrow
            // moved in your mappings, any path that yields
            // Holder<Enchantment> for Enchantments.INFINITY works here.
            var infinity = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.INFINITY);
            stack.enchant(infinity, 1);
        }

        // Focus items for creatures whose attack isn't a held weapon.
        if (stack.isEmpty()) {
            if (target instanceof Blaze) return new ItemStack(Items.BLAZE_ROD);
            if (target instanceof Ghast) return new ItemStack(Items.FIRE_CHARGE);
            if (target instanceof SnowGolem) return new ItemStack(Items.SNOWBALL);
            if (target instanceof Witch) return PotionContents.createItemStack(Items.POTION, Potions.HARMING);
            if (target instanceof Creeper) return new ItemStack(Items.GUNPOWDER);
        }

        return stack;
    }

    /** A bow needs an arrow: one Infinity-fed arrow, tipped with slowness for a stray - EBW to the letter. */
    static ItemStack grantedOffHand(Mob target) {
        if (target.getMainHandItem().getItem() instanceof BowItem) {
            if (target instanceof Stray) {
                return PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.SLOWNESS);
            }
            return new ItemStack(Items.ARROW);
        }
        return ItemStack.EMPTY;
    }

    // ============================== Passive abilities ==============================

    /** Called every tick for the possessing player - EBW's ability registry distilled. */
    static void tickAbilities(ServerPlayer player, Mob skin) {

        // Spiders climb: pressing into a wall carries you up it.
        if ((skin instanceof Spider || skin instanceof CaveSpider) && player.horizontalCollision) {
            Vec3 delta = player.getDeltaMovement();
            player.setDeltaMovement(delta.x, 0.2, delta.z);
            player.hurtMarked = true;
        }

        // Chickens flutter: falling is gentle.
        if (skin instanceof Chicken && !player.onGround()) {
            if (!player.hasEffect(MobEffects.SLOW_FALLING)) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
            }
        }

        // A fire-immune skin keeps its wearer unburnt.
        if (skin.fireImmune() && player.isOnFire()) {
            player.clearFire();
        }
    }

    /** Cleans per-player transient state; called on possession end via the maps simply going stale is fine, but this keeps LAST_SHOT tidy. */
    static void forget(LivingEntity player) {
        LAST_SHOT.remove(player.getUUID());
    }
}
