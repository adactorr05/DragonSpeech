package com.dragonspeech.engine;

import com.dragonspeech.word.Domain;
import com.dragonspeech.word.Word;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A semantic material/affinity for magic constructs.  Element is intentionally not widened just
 * to make barriers/weapons possible: Time, Gravity and future Void are conceptual materials that
 * can shape a construct even when they are not ordinary elemental projectile payloads.
 *
 * This is shared by shields and conjured weapons so sentences decide WHAT the construct is made
 * from rather than selecting a fixed pre-authored "ice sword" / "time shield" ability.
 */
public enum MagicAffinity {
    ARCANE(0x62d8a6, 0xd6fff0, null),
    FIRE(0xff6d2a, 0xffd211, Element.FIRE),
    LIGHTNING(0x4db8ff, 0xffffff, Element.LIGHTNING),
    WIND(0xbcecff, 0xffffff, Element.WIND),
    ICE(0xa4e5ff, 0xffffff, Element.ICE),
    WATER(0x2f7bd6, 0x9adcff, Element.WATER),
    POISON(0x54c936, 0x216b0e, Element.POISON),
    FORCE(0xf2e28d, 0xffffff, Element.FORCE),
    EARTH(0x8a6a3b, 0xcbb27a, Element.EARTH),
    LIGHT(0xfff4b8, 0xffffff, Element.LIGHT),
    SHADOW(0x352a4d, 0x0d0a17, Element.SHADOW),
    DEATH(0x3d1a4f, 0x0e0413, Element.DEATH),
    LIFE(0x8bff8b, 0xd8ffd8, Element.LIFE),
    TIME(0xcda8ff, 0xf3e8ff, null),
    GRAVITY(0x7747c9, 0xc9b0ff, null),
    FATE(0xe0b95a, 0xfff0b5, null),
    VOID(0x241331, 0x8d4bbb, Element.VOID);

    private final int color;
    private final int fadeColor;
    private final Element element;

    MagicAffinity(int color, int fadeColor, Element element) {
        this.color = color;
        this.fadeColor = fadeColor;
        this.element = element;
    }

    public int color() { return color; }
    public int fadeColor() { return fadeColor; }
    public Optional<Element> element() { return Optional.ofNullable(element); }

    /** Resolve the first explicitly spoken material/element. Conceptual domains only win if no normal element was named. */
    public static MagicAffinity resolve(List<Word> words) {
        for (Word word : words) {
            if (word.element().isPresent()) return fromElement(word.element().get());
        }
        for (Word word : words) {
            if (word.domain() == Domain.TIME) return TIME;
            if (word.domain() == Domain.GRAVITY) return GRAVITY;
            if (word.domain() == Domain.FATE) return FATE;
            if (word.domain() == Domain.VOID) return VOID;
        }
        return ARCANE;
    }

    public static MagicAffinity fromElement(Element element) {
        return switch (element) {
            case FIRE -> FIRE;
            case LIGHTNING -> LIGHTNING;
            case WIND -> WIND;
            case ICE -> ICE;
            case WATER -> WATER;
            case POISON -> POISON;
            case FORCE -> FORCE;
            case EARTH -> EARTH;
            case LIGHT -> LIGHT;
            case SHADOW -> SHADOW;
            case DEATH -> DEATH;
            case LIFE -> LIFE;
            case VOID -> VOID;
        };
    }

    /**
     * How strongly an incoming ordinary elemental working pressures this construct.
     * 1 = neutral, >1 = weakness, <1 = resistance.  These are deliberately readable rather than
     * rock-paper-scissors perfection; the language can later add more precise counter-words.
     */
    public float pressureMultiplier(List<Element> incoming) {
        if (incoming == null || incoming.isEmpty()) return 1f;
        float total = 0f;
        for (Element e : incoming) total += pressureAgainst(e);
        return total / incoming.size();
    }

    /**
     * Human-readable strongest known pressure relationship, used by skynja varn.
     * This intentionally reports the same counter table absorbSpellImpact actually uses, so
     * analysis never teaches a weakness that the collision solver does not honor.
     */
    public String analysisCounterHint() {
        return switch (this) {
            case ARCANE -> "no single elemental counter stands out";
            case FIRE -> "water bites deepest; ice also pressures it";
            case ICE -> "fire is its clearest weakness";
            case WATER -> "lightning bites deepest; ice can lock it";
            case WIND -> "earth disrupts it most strongly";
            case LIGHTNING -> "earth grounds it most strongly";
            case EARTH -> "concentrated force pressures it most; water also wears it down";
            case FORCE -> "no ordinary element has a dominant advantage";
            case LIGHT -> "shadow and death pressure it";
            case SHADOW -> "light is its clearest weakness";
            case DEATH -> "life and light pressure it";
            case LIFE -> "death and fire pressure it";
            case POISON -> "fire disrupts it most strongly";
            case TIME -> "force and lightning disturb its timing, but only slightly";
            case GRAVITY -> "concentrated force breaks its balance most strongly";
            case FATE -> "force and lightning are its best ordinary pressures";
            case VOID -> "light and life oppose the absence most strongly";
        };
    }

    private float pressureAgainst(Element e) {
        return switch (this) {
            case ARCANE -> 1.0f;
            case FIRE -> switch (e) { case WATER -> 1.65f; case ICE -> 1.25f; case FIRE -> .45f; default -> 1f; };
            case ICE -> switch (e) { case FIRE -> 1.65f; case WATER -> .75f; case ICE -> .45f; default -> 1f; };
            case WATER -> switch (e) { case LIGHTNING -> 1.55f; case ICE -> 1.25f; case FIRE -> .70f; case WATER -> .45f; default -> 1f; };
            case WIND -> switch (e) { case EARTH -> 1.40f; case WIND -> .45f; default -> 1f; };
            case LIGHTNING -> switch (e) { case EARTH -> 1.55f; case WATER -> 1.20f; case LIGHTNING -> .40f; default -> 1f; };
            case EARTH -> switch (e) { case FORCE -> 1.35f; case WATER -> 1.20f; case EARTH -> .45f; case LIGHTNING -> .70f; default -> 1f; };
            case FORCE -> switch (e) { case FORCE -> .60f; default -> 1f; };
            case LIGHT -> switch (e) { case SHADOW, DEATH -> 1.35f; case LIGHT -> .50f; default -> 1f; };
            case SHADOW -> switch (e) { case LIGHT -> 1.55f; case SHADOW -> .45f; default -> 1f; };
            case DEATH -> switch (e) { case LIFE, LIGHT -> 1.45f; case DEATH -> .45f; default -> 1f; };
            case LIFE -> switch (e) { case DEATH, FIRE -> 1.30f; case LIFE -> .45f; default -> 1f; };
            case POISON -> switch (e) { case FIRE -> 1.25f; case POISON -> .45f; default -> 1f; };
            case TIME -> switch (e) { case FORCE, LIGHTNING -> 1.15f; default -> .82f; };
            case GRAVITY -> switch (e) { case FORCE -> 1.35f; case EARTH -> .80f; default -> .90f; };
            case FATE -> switch (e) { case FORCE, LIGHTNING -> 1.10f; default -> .76f; };
            case VOID -> switch (e) { case LIGHT, LIFE -> 1.35f; case VOID -> .35f; case SHADOW, DEATH -> .65f; default -> .78f; };
        };
    }

    /** Contact/retaliation behavior for an affinity barrier. */
    public void retaliate(ServerPlayer caster, LivingEntity attacker, float power) {
        if (element != null) {
            element.hitEntity(caster, attacker, power);
            return;
        }
        switch (this) {
            case TIME -> {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 1));
            }
            case GRAVITY -> {
                Vec3 v = attacker.getDeltaMovement();
                attacker.setDeltaMovement(v.x * .65, Math.min(v.y, -.35), v.z * .65);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 1));
                attacker.hurtMarked = true;
            }
            case FATE -> attacker.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 100, 1));
            case VOID -> {
                attacker.hurt(caster.level().damageSources().indirectMagic(caster, caster), Math.max(1f, power));
                attacker.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0));
            }
            default -> { }
        }
    }

    /** Applies the secondary identity of a conjured magical weapon after its physical impact. */
    public void applyConstructHit(ServerPlayer caster, LivingEntity target, float power) {
        float p = Math.max(.5f, power);
        if (element != null) {
            // The projectile already dealt its physical hit.  Use a reduced elemental pulse so the
            // spoken material changes behavior without simply doubling the entire weapon hit.
            element.hitEntity(caster, target, p * .45f);
            return;
        }
        switch (this) {
            case TIME -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 70, 1));
            }
            case GRAVITY -> {
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(v.x * .45, Math.min(v.y, -.45), v.z * .45);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                target.hurtMarked = true;
            }
            case FATE -> target.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 120, 1));
            case VOID -> {
                target.hurt(caster.level().damageSources().indirectMagic(caster, caster), p * .5f);
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0));
            }
            default -> { }
        }
    }

    /** Extra damage identity used by conjured weapons. */
    public float weaponDamageMultiplier() {
        return switch (this) {
            case FIRE, ICE, EARTH -> 1.00f;
            case WATER, WIND -> .90f;
            case LIGHTNING -> 1.12f;
            case FORCE -> 1.05f;
            case LIGHT, SHADOW, LIFE, POISON -> .95f;
            case DEATH -> 1.08f;
            case TIME -> 1.18f;
            case GRAVITY -> 1.22f;
            case FATE -> 1.10f;
            case VOID -> 1.30f;
            case ARCANE -> 1.00f;
        };
    }

    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
