package com.dragonspeech.weapon;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.spell.SustainMode;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Shared item/state implementation for magical weapons spoken into being. */
public final class ConjuredWeaponItems {

    public static final int BASE_LIFETIME_TICKS = 20 * 60;
    private static final int MIN_LIFETIME_TICKS = 20 * 8;
    private static final int MAX_LIFETIME_TICKS = 20 * 60 * 12;

    public static final Item SWORD = registerItem("conjured_sword", ToolType.SWORD);
    public static final Item AXE = registerItem("conjured_axe", ToolType.AXE);
    public static final Item PICKAXE = registerItem("conjured_pickaxe", ToolType.PICKAXE);
    public static final Item SHOVEL = registerItem("conjured_shovel", ToolType.SHOVEL);
    public static final Item HOE = registerItem("conjured_hoe", ToolType.HOE);
    public static final Item SPEAR = registerItem("conjured_spear", ToolType.SPEAR);
    public static final Item TRIDENT = registerItem("conjured_trident", ToolType.TRIDENT);

    private ConjuredWeaponItems() {}

    private static Item registerItem(String id, ToolType type) {
        return Registry.register(BuiltInRegistries.ITEM, DragonSpeech.id(id), new ConjuredWeaponItem(type));
    }

    public static Item itemFor(ToolType type) {
        return switch (type) {
            case SWORD -> SWORD;
            case AXE -> AXE;
            case PICKAXE -> PICKAXE;
            case SHOVEL -> SHOVEL;
            case HOE -> HOE;
            case SPEAR -> SPEAR;
            case TRIDENT -> TRIDENT;
        };
    }

    /**
     * The spoken substance of a conjured weapon is deliberately separate from its tool material.
     * A material word such as jarn/steinn/demantr means a real vanilla-looking tool made from that
     * material, while an elemental/conceptual substance word means the translucent magical form.
     * `seidr` is the explicit pure-magic substance; plain `seida sverd` therefore remains wood.
     */
    public record Substance(MagicAffinity affinity, boolean holographic) {}

    public static Substance resolveSubstance(java.util.List<Word> words) {
        if (words == null) return new Substance(MagicAffinity.ARCANE, false);
        for (Word word : words) {
            // A tool-material word is physical matter for weapon creation.  This is especially
            // important for steinn, which also carries EARTH elemental metadata elsewhere.
            if (word.toolMaterial().isPresent()) continue;

            if ("seidr".equals(word.trueName())) {
                return new Substance(MagicAffinity.ARCANE, true);
            }
            if (word.element().isPresent()) {
                return new Substance(MagicAffinity.fromElement(word.element().get()), true);
            }

            // Conceptual domains can be weapon substance only when a noun names that substance.
            // This prevents control/modifier words such as thyngdleysa from accidentally turning
            // an otherwise wooden sword into a gravity hologram.
            if (word.category() == WordCategory.NOUN_TARGET) {
                MagicAffinity conceptual = switch (word.domain()) {
                    case TIME -> MagicAffinity.TIME;
                    case GRAVITY -> MagicAffinity.GRAVITY;
                    case FATE -> MagicAffinity.FATE;
                    case VOID -> MagicAffinity.VOID;
                    default -> null;
                };
                if (conceptual != null) return new Substance(conceptual, true);
            }
        }
        return new Substance(MagicAffinity.ARCANE, false);
    }

    public static boolean isHolographic(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ConjuredWeaponItem;
    }

    public static int lifetimeTicks(EffectInvocation invocation) {
        double scale = Math.pow(2.0, invocation.modifierMagnitudeSum());
        return clamp((int) Math.round(BASE_LIFETIME_TICKS * scale), MIN_LIFETIME_TICKS, MAX_LIFETIME_TICKS);
    }

    /** `afla` is a spell reserve, not a duration. Greater/lesser magnitude changes how much reserve is created. */
    public static float reserveAmount(EffectInvocation invocation, ToolType toolType) {
        float base = 28f + toolType.baseDamage() * 4f;
        float scale = (float)Math.pow(2.0, invocation.modifierMagnitudeSum());
        int repeats = Math.max(1, invocation.composition().occurrencesOf("afla"));
        return Math.max(6f, Math.min(500f, base * scale * repeats));
    }

    public static ItemStack create(ToolMaterial material, ToolType toolType, MagicAffinity affinity, boolean holographic,
                                   long nowGameTime, int lifetimeTicks, SustainMode sustainMode,
                                   UUID ownerId, float reserve) {
        // Physical-material conjurations are genuine vanilla item stacks with Dragon Speech sustain
        // metadata attached. Magical substances use the dedicated holographic item renderer.
        ItemStack stack = holographic
            ? new ItemStack(itemFor(toolType))
            : WeaponItems.stackFor(material, toolType);
        MagicAffinity resolvedAffinity = affinity == null ? MagicAffinity.ARCANE : affinity;
        SustainMode mode = sustainMode == null ? SustainMode.DURATION : sustainMode;

        stack.set(DragonSpeechComponents.CONJURED_SUSTAIN_MODE, mode.name().toLowerCase(Locale.ROOT));
        if (mode == SustainMode.DURATION) {
            stack.set(DragonSpeechComponents.CONJURED_EXPIRES_AT, nowGameTime + Math.max(1, lifetimeTicks));
        } else if (mode == SustainMode.RESERVE) {
            float r = Math.max(1f, reserve);
            stack.set(DragonSpeechComponents.CONJURED_RESERVE, r);
            stack.set(DragonSpeechComponents.CONJURED_MAX_RESERVE, r);
        } else if (ownerId != null) {
            stack.set(DragonSpeechComponents.CONJURED_OWNER, ownerId.toString());
        }

        stack.set(DragonSpeechComponents.CONJURED_AFFINITY, resolvedAffinity.getSerializedName());
        stack.set(DragonSpeechComponents.CONJURED_TOOL_TYPE, toolType.getSerializedName());
        stack.set(DragonSpeechComponents.CONJURED_MATERIAL, material.getSerializedName());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName(material, toolType, resolvedAffinity, holographic)));
        return stack;
    }

    /** Compatibility overload: affinity-bearing constructs remain holographic, ARCANE defaults physical. */
    public static ItemStack create(ToolMaterial material, ToolType toolType, MagicAffinity affinity,
                                   long nowGameTime, int lifetimeTicks, SustainMode sustainMode,
                                   UUID ownerId, float reserve) {
        MagicAffinity resolved = affinity == null ? MagicAffinity.ARCANE : affinity;
        return create(material, toolType, resolved, resolved != MagicAffinity.ARCANE, nowGameTime, lifetimeTicks, sustainMode, ownerId, reserve);
    }

    /** Compatibility overload for older callers: default duration-based conjuration. */
    public static ItemStack create(ToolMaterial material, ToolType toolType, MagicAffinity affinity,
                                   long nowGameTime, int lifetimeTicks) {
        return create(material, toolType, affinity, nowGameTime, lifetimeTicks, SustainMode.DURATION, null, 0f);
    }

    public static boolean isTemporary(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DragonSpeechComponents.CONJURED_TOOL_TYPE) != null;
    }

    public static SustainMode sustainMode(ItemStack stack) {
        String value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_SUSTAIN_MODE);
        if (value == null) return stack != null && stack.get(DragonSpeechComponents.CONJURED_EXPIRES_AT) != null ? SustainMode.DURATION : SustainMode.DURATION;
        try { return SustainMode.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return SustainMode.DURATION; }
    }

    public static Optional<UUID> owner(ItemStack stack) {
        String value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_OWNER);
        if (value == null || value.isBlank()) return Optional.empty();
        try { return Optional.of(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public static float reserve(ItemStack stack) {
        Float value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_RESERVE);
        return value == null ? 0f : Math.max(0f, value);
    }

    public static float maxReserve(ItemStack stack) {
        Float value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_MAX_RESERVE);
        return value == null ? 0f : Math.max(0f, value);
    }

    public static boolean consumeReserve(ItemStack stack, float amount) {
        if (sustainMode(stack) != SustainMode.RESERVE) return true;
        float left = Math.max(0f, reserve(stack) - Math.max(0f, amount));
        stack.set(DragonSpeechComponents.CONJURED_RESERVE, left);
        return left > 0f;
    }

    /** aflbinda draws ONLY the caster's stamina. It never cascades into hunger/health or a hidden durability pool. */
    public static boolean consumeCasterStamina(MinecraftServer server, ItemStack stack, float amount) {
        if (sustainMode(stack) != SustainMode.CASTER) return true;
        Optional<UUID> owner = owner(stack);
        if (owner.isEmpty()) return false;
        ServerPlayer caster = server.getPlayerList().getPlayer(owner.get());
        if (caster == null) return true; // do not destroy a bound object merely because its owner logged out
        PlayerMagicData data = StaminaAccess.get(caster);
        float cost = Math.max(0f, amount);
        if (data.stamina() <= 0f || data.stamina() < cost) {
            StaminaAccess.set(caster, data.withStamina(0f));
            return false;
        }
        StaminaAccess.set(caster, data.withStamina(data.stamina() - cost));
        return true;
    }

    public static long expiresAt(ItemStack stack) {
        Long value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_EXPIRES_AT);
        return value == null ? Long.MAX_VALUE : value;
    }

    public static boolean durationFrozen(ItemStack stack) {
        Boolean value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_DURATION_FROZEN);
        return Boolean.TRUE.equals(value);
    }

    public static long frozenRemaining(ItemStack stack) {
        Long value = stack == null ? null : stack.get(DragonSpeechComponents.CONJURED_FROZEN_REMAINING);
        return value == null ? 0L : Math.max(0L, value);
    }

    public static void addDuration(ItemStack stack, long now, long extraTicks) {
        if (!isTemporary(stack)) return;
        if (sustainMode(stack) != SustainMode.DURATION) {
            stack.set(DragonSpeechComponents.CONJURED_SUSTAIN_MODE, SustainMode.DURATION.name().toLowerCase(Locale.ROOT));
            stack.remove(DragonSpeechComponents.CONJURED_RESERVE);
            stack.remove(DragonSpeechComponents.CONJURED_MAX_RESERVE);
            stack.remove(DragonSpeechComponents.CONJURED_OWNER);
        }
        if (durationFrozen(stack)) {
            stack.set(DragonSpeechComponents.CONJURED_FROZEN_REMAINING, frozenRemaining(stack) + Math.max(0L, extraTicks));
        } else {
            long base = Math.max(now, expiresAt(stack));
            stack.set(DragonSpeechComponents.CONJURED_EXPIRES_AT, base + Math.max(0L, extraTicks));
        }
    }

    public static void bindToCaster(ItemStack stack, UUID ownerId) {
        if (!isTemporary(stack)) return;
        stack.set(DragonSpeechComponents.CONJURED_SUSTAIN_MODE, SustainMode.CASTER.name().toLowerCase(Locale.ROOT));
        stack.set(DragonSpeechComponents.CONJURED_OWNER, ownerId.toString());
        stack.remove(DragonSpeechComponents.CONJURED_EXPIRES_AT);
        stack.remove(DragonSpeechComponents.CONJURED_RESERVE);
        stack.remove(DragonSpeechComponents.CONJURED_MAX_RESERVE);
        stack.remove(DragonSpeechComponents.CONJURED_DURATION_FROZEN);
        stack.remove(DragonSpeechComponents.CONJURED_FROZEN_REMAINING);
    }

    public static void setDurationFrozen(ItemStack stack, long now, boolean frozen) {
        if (!isTemporary(stack) || sustainMode(stack) != SustainMode.DURATION) return;
        if (frozen) {
            long remaining = Math.max(1L, expiresAt(stack) - now);
            stack.set(DragonSpeechComponents.CONJURED_FROZEN_REMAINING, remaining);
            stack.set(DragonSpeechComponents.CONJURED_DURATION_FROZEN, true);
        } else {
            long remaining = Math.max(1L, frozenRemaining(stack));
            stack.set(DragonSpeechComponents.CONJURED_EXPIRES_AT, now + remaining);
            stack.set(DragonSpeechComponents.CONJURED_DURATION_FROZEN, false);
            stack.remove(DragonSpeechComponents.CONJURED_FROZEN_REMAINING);
        }
    }

    public static boolean isExpired(ItemStack stack, long nowGameTime) {
        if (!isTemporary(stack)) return false;
        return switch (sustainMode(stack)) {
            case DURATION -> !durationFrozen(stack) && nowGameTime >= expiresAt(stack);
            case RESERVE -> reserve(stack) <= 0f;
            case CASTER -> false; // server-aware owner check happens in shouldDissolve
        };
    }

    public static boolean shouldDissolve(ItemStack stack, MinecraftServer server, long nowGameTime) {
        if (!isTemporary(stack)) return false;
        if (isExpired(stack, nowGameTime)) return true;
        if (sustainMode(stack) == SustainMode.CASTER) {
            Optional<UUID> id = owner(stack);
            if (id.isEmpty()) return true;
            ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(id.get());
            return ownerPlayer != null && StaminaAccess.get(ownerPlayer).stamina() <= 0f;
        }
        return false;
    }

    public static Optional<MagicAffinity> affinity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        String value = stack.get(DragonSpeechComponents.CONJURED_AFFINITY);
        if (value == null || value.isBlank()) return Optional.empty();
        try { return Optional.of(MagicAffinity.valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public static Optional<ToolType> toolType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        String value = stack.get(DragonSpeechComponents.CONJURED_TOOL_TYPE);
        if (value == null || value.isBlank()) {
            if (stack.getItem() instanceof ConjuredWeaponItem item) return Optional.of(item.toolType());
            return Optional.empty();
        }
        try { return Optional.of(ToolType.valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public static Optional<ToolMaterial> material(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        String value = stack.get(DragonSpeechComponents.CONJURED_MATERIAL);
        if (value == null || value.isBlank()) return Optional.empty();
        try { return Optional.of(ToolMaterial.valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    public static boolean matches(ItemStack stack, ToolMaterial material, ToolType toolType) {
        if (stack == null || stack.isEmpty()) return false;
        if (!isTemporary(stack)) return stack.is(WeaponItems.canonicalItem(material, toolType));
        Optional<ToolType> storedTool = toolType(stack);
        Optional<ToolMaterial> storedMaterial = material(stack);
        return storedTool.orElse(toolType) == toolType && storedMaterial.orElse(material) == material;
    }

    public static boolean dissolveIfExpired(ItemStack stack, long nowGameTime) {
        if (!isExpired(stack, nowGameTime)) return false;
        stack.shrink(stack.getCount());
        return true;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ConjuredWeaponItems::tickServer);
    }

    private static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long now = player.level().getGameTime();
            player.getInventory().items.forEach(stack -> dissolveIfNeeded(stack, server, now));
            player.getInventory().offhand.forEach(stack -> dissolveIfNeeded(stack, server, now));
            player.getInventory().armor.forEach(stack -> dissolveIfNeeded(stack, server, now));
        }
        for (var level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && shouldDissolve(item.getItem(), server, now)) item.discard();
            }
        }
    }

    private static void dissolveIfNeeded(ItemStack stack, MinecraftServer server, long now) {
        if (shouldDissolve(stack, server, now)) stack.shrink(stack.getCount());
    }

    private static String displayName(ToolMaterial material, ToolType toolType, MagicAffinity affinity, boolean holographic) {
        String identity;
        if (!holographic) identity = material.getSerializedName();
        else if (affinity == MagicAffinity.ARCANE) identity = "pure magic";
        else identity = affinity.getSerializedName();
        return "Conjured " + title(identity) + " " + title(toolType.getSerializedName());
    }

    private static String title(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (lower.isBlank()) return "Arcane";
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static String durationText(int ticks) {
        int seconds = Math.max(1, (int)Math.ceil(ticks / 20.0));
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60, remain = seconds % 60;
        return remain == 0 ? minutes + "m" : minutes + "m " + remain + "s";
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
