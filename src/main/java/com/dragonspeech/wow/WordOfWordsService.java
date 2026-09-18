package com.dragonspeech.wow;

import com.dragonspeech.engine.Element;
import com.dragonspeech.engine.SigilManager;
import com.dragonspeech.engine.StasisManager;
import com.dragonspeech.engine.TemporalFieldManager;
import com.dragonspeech.effect.EffectTarget;
import com.dragonspeech.effect.TargetResolver;
import com.dragonspeech.network.DragonSpeechNetworking;
import com.dragonspeech.network.OpenWordOfWordsPayload;
import com.dragonspeech.mob.casting.MobWards;
import com.dragonspeech.mob.casting.SpellcastingMob;
import com.dragonspeech.mob.casting.Warded;
import com.dragonspeech.stamina.DrainResolver;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.ward.ActiveWard;
import com.dragonspeech.ward.PlayerWards;
import com.dragonspeech.ward.WardAccess;
import com.dragonspeech.ward.WardService;
import com.dragonspeech.ward.WardType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative runtime for the Word-of-Words GUI. */
public final class WordOfWordsService {
    private static final int SESSION_TICKS = 20 * 60;
    private static final double TARGET_REACH = 32.0;
    private static final int SNAPSHOT_RADIUS = 6;
    private static final double SIGIL_SCAN_RADIUS = 18.0;
    private static final double HALT_SCAN_RADIUS = 32.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record BlockSnapshot(BlockState state, CompoundTag blockEntityTag) {}
    private record LivingSnapshot(UUID id, Vec3 pos, Vec3 velocity, float health, int fireTicks, int frozenTicks,
                                  float yRot, float xRot, CompoundTag entityTag) {}
    private record LocalSnapshot(ResourceKey<Level> dimension, Map<BlockPos, BlockSnapshot> blocks,
                                 List<LivingSnapshot> living, long dayTime) {}
    private record Session(UUID id, UUID playerId, ResourceKey<Level> dimension, Vec3 anchor,
                           UUID targetId, long expiresAt, LocalSnapshot snapshot) {}

    private WordOfWordsService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getTickCount();
            SESSIONS.values().removeIf(s -> s.expiresAt() <= now || server.getPlayerList().getPlayer(s.playerId()) == null);
        });
    }

    public static void invalidateAll(MinecraftServer server) {
        SESSIONS.clear();
    }

    /** Speaking the Word is deliberately free: payment happens only in executeAction(). */
    public static void open(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (!WordOfWordsKnowledge.knows(player)) {
            player.sendSystemMessage(Component.literal("The Word no longer belongs to your memory."));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        List<EffectTarget> looked = TargetResolver.resolveLookTarget(player, TARGET_REACH);
        EffectTarget focus = looked.isEmpty() ? null : looked.get(0);
        UUID targetId = null;
        Vec3 anchor = player.getEyePosition().add(player.getLookAngle().scale(8));
        if (focus instanceof EffectTarget.OfEntity(Entity entity)) {
            targetId = entity.getUUID();
            anchor = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        } else if (focus instanceof EffectTarget.OfBlock(BlockPos pos)) {
            anchor = Vec3.atCenterOf(pos);
        }

        long now = level.getServer().getTickCount();
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.expiresAt() > now && existing.dimension().equals(level.dimension())) {
            boolean sameEntity = existing.targetId() != null && existing.targetId().equals(targetId);
            boolean samePlace = existing.targetId() == null && targetId == null && existing.anchor().distanceToSqr(anchor) <= 16.0;
            if (sameEntity || samePlace) {
                sendContext(player, existing);
                return;
            }
        }

        UUID sessionId = UUID.randomUUID();
        Session session = new Session(sessionId, player.getUUID(), level.dimension(), anchor, targetId,
            now + SESSION_TICKS, capture(level, anchor));
        SESSIONS.put(player.getUUID(), session);
        sendContext(player, session);
    }

    public static void executeAction(ServerPlayer player, UUID sessionId, String action, String parameter) {
        Session session = SESSIONS.get(player.getUUID());
        MinecraftServer server = player.getServer();
        if (server == null || !WordOfWordsKnowledge.knows(player) || session == null || !session.id().equals(sessionId)
            || session.expiresAt() <= server.getTickCount() || !session.dimension().equals(player.level().dimension())) {
            player.sendSystemMessage(Component.literal("The Word's authority has already slipped away. Speak it again."));
            return;
        }

        String a = action == null ? "" : action.trim().toLowerCase();
        String p = parameter == null ? "" : parameter.trim().toLowerCase();
        String invalid = validate(player, session, a, p);
        if (invalid != null) {
            player.sendSystemMessage(Component.literal(invalid));
            sendContext(player, session);
            return;
        }
        float cost = cost(player, session, a, p);
        if (cost < 0f) {
            player.sendSystemMessage(Component.literal("That change is not one the Word recognizes."));
            return;
        }

        var drain = DrainResolver.applyDrain(player, cost);
        if (!drain.succeeded()) {
            player.sendSystemMessage(Component.literal("You shape the command, but do not have enough life-force to make reality obey."));
            return;
        }
        DragonSpeechNetworking.sendStaminaSync(player, StaminaAccess.get(player).stamina(), StaminaAccess.get(player).maxStamina());

        String result;
        try {
            result = perform(player, session, a, p);
        } catch (Exception ex) {
            result = "The command reaches reality, but its target has changed too much to obey cleanly.";
        }
        player.sendSystemMessage(Component.literal(result + "  [" + Math.round(cost) + " stamina]"));
        if (!"change_word".equals(a)) {
            sendContext(player, session); // refresh wards/traps/rules after every paid action
        }
    }

    private static String perform(ServerPlayer player, Session s, String action, String param) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = targetLiving(level, s, player);

        return switch (action) {
            case "remove_ward" -> {
                boolean existed = removeSpecificWard(target, param);
                yield existed ? "The chosen ward is removed, and only that ward." : "That ward is no longer present.";
            }
            case "remove_trap" -> {
                long id = Long.parseLong(param);
                yield SigilManager.remove(level, id) ? "The selected trap unravels." : "That trap has already vanished.";
            }
            case "remove_halt_area" -> {
                long id = Long.parseLong(param);
                yield WordOfWordsHaltManager.removeZone(id)
                    ? "The selected prohibition on magic is lifted from that area."
                    : "That area prohibition has already ended.";
            }
            case "remove_halt_target" -> {
                long id = Long.parseLong(param);
                yield WordOfWordsHaltManager.removeEntityHalt(id)
                    ? "The selected prohibition on spoken magic is lifted from " + target.getName().getString() + "."
                    : "That target is no longer held under that prohibition.";
            }
            case "add_ward" -> {
                WardType type = WardType.valueOf(param.toUpperCase());
                if (type == WardType.REVIVAL && !(target instanceof ServerPlayer)) {
                    yield "A revival binding needs a living player-thread to anchor to.";
                }
                float energy = type == WardType.REVIVAL ? 1f : 120f;
                WardService.place(target, type, energy, 1, true, false);
                yield "A " + type.getSerializedName() + " ward is written onto " + target.getName().getString() + ".";
            }
            case "add_sigil" -> {
                Element element = Element.valueOf(param.toUpperCase());
                SigilManager.place(level, s.anchor(), List.of(element), 7f, player);
                yield "A prepared " + element.getSerializedName() + " working is bound to the chosen place.";
            }
            case "change_cost" -> {
                float multiplier = Float.parseFloat(param);
                WordOfWordsRules.setMagicCostMultiplier(player.getServer(), multiplier);
                yield "All Dragon Speech magic now costs x" + String.format(java.util.Locale.ROOT, "%.2f", multiplier) + ".";
            }
            case "change_word" -> {
                boolean anchored = WordOfWordsKnowledge.isAnchored(player);
                WowPhraseState.reshuffleByWord(player.getServer());
                yield anchored
                    ? "The Word of Words changes, but your bound memory follows it. The new Word is already known to you."
                    : "The Word of Words changes. Your memory held only the old Word, and that knowledge is now gone.";
            }
            case "bind_word_memory" -> {
                WordOfWordsKnowledge.bindToMemory(player);
                yield "The Word of Words is bound to your memory. Future changes made through the Word itself will not sever your knowledge.";
            }
            case "halt_magic_area" -> {
                int[] spec = parsePair(param, 8, 20 * 30);
                int radius = spec[0], ticks = spec[1];
                WordOfWordsHaltManager.suppressArea(level, s.anchor(), radius, ticks);
                yield "Spellcasting is halted within " + radius + " blocks of the chosen place for " + durationText(ticks) + ".";
            }
            case "halt_magic_target" -> {
                int ticks = parseTicks(param, 20 * 45);
                if (target instanceof ServerPlayer || target instanceof SpellcastingMob) {
                    WordOfWordsHaltManager.suppressEntity(target, ticks);
                    yield "Magic is halted around " + target.getName().getString() + " for " + durationText(ticks) + ".";
                }
                yield "That target does not cast through Dragon Speech's magic system.";
            }
            case "halt_target" -> {
                int ticks = parseTicks(param, 20 * 15);
                if (target instanceof Mob mob) {
                    StasisManager.hold(level, mob, ticks);
                    yield target.getName().getString() + " is held outside motion for " + durationText(ticks) + ".";
                }
                if (target instanceof ServerPlayer targetPlayer) {
                    targetPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ticks, 20));
                    targetPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, ticks, 10));
                    yield target.getName().getString() + " is forced nearly still for " + durationText(ticks) + ".";
                }
                yield "There is nothing living there to halt.";
            }
            case "halt_area" -> {
                int count = 0;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(s.anchor(), s.anchor()).inflate(8), Mob::isAlive)) {
                    StasisManager.hold(level, mob, 20 * 10);
                    count++;
                }
                yield "Time arrests " + count + " creature(s) around the chosen place for ten seconds.";
            }
            case "time_fast" -> {
                long ticks = Long.parseLong(param);
                level.setDayTime(level.getDayTime() + ticks);
                yield "The world's clock advances by " + ticks + " ticks.";
            }
            case "time_slow_area" -> {
                TemporalFieldManager.place(level, player, 8, -1f, 3, false, 20 * 30);
                yield "Local time drags around you for thirty seconds.";
            }
            case "time_stasis_area" -> {
                TemporalFieldManager.place(level, player, 8, -1f, 6, true, 20 * 12);
                yield "Local time is nearly halted around you for twelve seconds.";
            }
            case "time_reverse_local" -> restore(level, s.snapshot());
            case "reveal_wards" -> revealWards(target);
            case "reveal_traps" -> revealTraps(level, s.anchor());
            case "reveal_target" -> "Target: " + target.getName().getString() + ", health " + String.format(java.util.Locale.ROOT, "%.1f/%.1f", target.getHealth(), target.getMaxHealth()) + ", wards " + wardCount(target) + ".";
            case "bind_magic_target" -> {
                int ticks = parseTicks(param, 20 * 60);
                if (target instanceof ServerPlayer || target instanceof SpellcastingMob) {
                    WordOfWordsHaltManager.suppressEntity(target, ticks);
                    yield "A prohibition against spoken magic is bound to " + target.getName().getString() + " for " + durationText(ticks) + ".";
                }
                yield "The chosen target does not cast through Dragon Speech's magic system.";
            }
            case "bind_target" -> {
                int ticks = parseTicks(param, 20 * 30);
                if (target instanceof Mob mob) {
                    StasisManager.hold(level, mob, ticks);
                    yield "The target is bound to this place for " + durationText(ticks) + ".";
                }
                yield "This binding currently requires a mob target.";
            }
            case "restore_health" -> {
                float missing = target.getMaxHealth() - target.getHealth();
                target.heal(missing);
                target.clearFire();
                yield target.getName().getString() + " is restored to full health and extinguished.";
            }
            case "restore_ward" -> {
                boolean restored = restoreSpecificWard(target, param);
                yield restored ? "The selected ward is restored to its original strength." : "That ward is no longer present.";
            }
            case "restore_self_health" -> {
                float missing = player.getMaxHealth() - player.getHealth();
                player.heal(missing);
                player.clearFire();
                yield "Your body is restored to full health and the flames are extinguished.";
            }
            case "restore_self_condition" -> {
                player.clearFire();
                player.setTicksFrozen(0);
                player.setAirSupply(player.getMaxAirSupply());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20f);
                yield "Your breath, warmth, hunger, and bodily condition are restored.";
            }
            default -> "Nothing changes.";
        };
    }

    private static String validate(ServerPlayer player, Session s, String action, String param) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = targetLiving(level, s, player);
        try {
            return switch (action) {
                case "remove_ward", "restore_ward" -> hasSpecificWard(target, param) ? null : "That specific ward is no longer present.";
                case "remove_trap" -> SigilManager.nearby(level, s.anchor(), SIGIL_SCAN_RADIUS).stream()
                    .anyMatch(v -> Long.toString(v.id()).equals(param)) ? null : "That specific trap is no longer present.";
                case "remove_halt_area" -> WordOfWordsHaltManager.nearbyZones(level, s.anchor(), HALT_SCAN_RADIUS).stream()
                    .anyMatch(v -> Long.toString(v.id()).equals(param)) ? null : "That specific area prohibition is no longer present.";
                case "remove_halt_target" -> {
                    WordOfWordsHaltManager.EntityHaltView halt = WordOfWordsHaltManager.haltFor(target);
                    yield halt != null && Long.toString(halt.id()).equals(param) ? null : "That target is no longer held under that prohibition.";
                }
                case "add_ward" -> {
                    WardType type = WardType.valueOf(param.toUpperCase());
                    yield type == WardType.REVIVAL && !(target instanceof ServerPlayer)
                        ? "A revival binding needs a living player-thread to anchor to." : null;
                }
                case "add_sigil" -> { Element.valueOf(param.toUpperCase()); yield null; }
                case "change_cost" -> { float m = Float.parseFloat(param); yield (m < 0.25f || m > 4f) ? "That cost law is outside the Word's allowed bounds." : null; }
                case "change_word" -> null;
                case "bind_word_memory" -> WordOfWordsKnowledge.isAnchored(player) ? "The Word is already bound to your memory." : null;
                case "halt_magic_area" -> { int[] q = parsePair(param, 8, 600); yield ((q[0] == 8 && q[1] == 600) || (q[0] == 16 && q[1] == 2400)) ? null : "That halt radius or duration is not available."; }
                case "halt_magic_target" -> {
                    int t = parseTicks(param, 900);
                    boolean durationOk = t == 900 || t == 2400 || t == 6000;
                    yield (target instanceof ServerPlayer || target instanceof SpellcastingMob) && durationOk ? null : "That command needs a Dragon Speech caster and an available duration.";
                }
                case "bind_magic_target" -> {
                    int t = parseTicks(param, 1200);
                    boolean durationOk = t == 1200 || t == 6000 || t == 18000;
                    yield (target instanceof ServerPlayer || target instanceof SpellcastingMob) && durationOk ? null : "That binding needs a Dragon Speech caster and an available duration.";
                }
                case "bind_target" -> {
                    int t = parseTicks(param, 600);
                    boolean durationOk = t == 600 || t == 2400 || t == 6000;
                    yield target instanceof Mob && durationOk ? null : "That binding needs a mob target and an available duration.";
                }
                case "time_fast" -> { long t = Long.parseLong(param); yield (t == 1000 || t == 6000) ? null : "That time step is not available."; }
                case "halt_target" -> {
                    int t = parseTicks(param, 300);
                    yield (t == 300 || t == 1200 || t == 6000) ? null : "That halt duration is not available.";
                }
                case "halt_area", "time_slow_area", "time_stasis_area", "time_reverse_local",
                     "reveal_wards", "reveal_traps", "reveal_target", "restore_health", "restore_self_health", "restore_self_condition" -> null;
                default -> "That change is not one the Word recognizes.";
            };
        } catch (Exception ex) {
            return "That command is malformed and reality refuses it.";
        }
    }

    private static float cost(ServerPlayer player, Session s, String action, String param) {
        float base = baseCost(player, s, action, param);
        if (base < 0f) return base;
        return base * WordOfWordsRules.magicCostMultiplier(player.getServer());
    }

    private static float baseCost(ServerPlayer player, Session s, String action, String param) {
        LivingEntity target = targetLiving((ServerLevel) player.level(), s, player);
        try {
            return switch (action) {
                case "remove_ward" -> wardRemoveBaseCost(target, param);
                case "remove_trap" -> 70f;
                case "remove_halt_area" -> {
                    long id = Long.parseLong(param);
                    var zone = WordOfWordsHaltManager.nearbyZones((ServerLevel) player.level(), s.anchor(), HALT_SCAN_RADIUS).stream()
                        .filter(v -> v.id() == id).findFirst().orElse(null);
                    yield zone == null ? 120f : 110f + (float)zone.radius() * 5f + durationScale((int)Math.min(Integer.MAX_VALUE, zone.remainingTicks()), 600) * 35f;
                }
                case "remove_halt_target" -> {
                    var halt = WordOfWordsHaltManager.haltFor(target);
                    yield halt == null ? 90f : 80f + durationScale((int)Math.min(Integer.MAX_VALUE, halt.remainingTicks()), 900) * 40f;
                }
                case "add_ward" -> WardType.valueOf(param.toUpperCase()) == WardType.REVIVAL ? 950f : 130f;
                case "add_sigil" -> 115f;
                case "change_cost" -> 260f + Math.abs(Float.parseFloat(param) - 1f) * 700f;
                case "change_word" -> 700f;
                case "bind_word_memory" -> 500f;
                case "halt_magic_area" -> { int[] q = parsePair(param, 8, 600); yield (280f + q[0] * 15f) * durationScale(q[1], 600); }
                case "halt_magic_target" -> 220f * durationScale(parseTicks(param, 900), 900);
                case "halt_target" -> 180f * durationScale(parseTicks(param, 300), 300);
                case "halt_area" -> 520f;
                case "time_fast" -> Long.parseLong(param) >= 6000 ? 420f : 180f;
                case "time_slow_area" -> 360f;
                case "time_stasis_area" -> 650f;
                case "time_reverse_local" -> 900f;
                case "reveal_wards" -> 25f;
                case "reveal_traps" -> 30f;
                case "reveal_target" -> 20f;
                case "bind_magic_target" -> 300f * durationScale(parseTicks(param, 1200), 1200);
                case "bind_target" -> 260f * durationScale(parseTicks(param, 600), 600);
                case "restore_health" -> 45f + Math.max(0f, target.getMaxHealth() - target.getHealth()) * 10f;
                case "restore_self_health" -> 45f + Math.max(0f, player.getMaxHealth() - player.getHealth()) * 10f;
                case "restore_self_condition" -> 120f;
                case "restore_ward" -> wardRestoreBaseCost(target, param);
                default -> -1f;
            };
        } catch (Exception ex) {
            return -1f;
        }
    }

    private static LivingEntity targetLiving(ServerLevel level, Session s, ServerPlayer fallback) {
        if (s.targetId() != null && level.getEntity(s.targetId()) instanceof LivingEntity living && living.isAlive()) return living;
        return fallback;
    }

    private static String revealWards(LivingEntity target) {
        List<String> parts = new ArrayList<>();
        for (ActiveWard w : WardAccess.get(target).wards()) {
            parts.add(w.type().getSerializedName() + " " + Math.round(w.remainingEnergy()) + "/" + Math.round(w.maxEnergy()));
        }
        if (target instanceof Warded warded) {
            for (var e : warded.activeWards().entrySet()) {
                MobWards.WardInstance w = e.getValue();
                parts.add(e.getKey().name().toLowerCase(java.util.Locale.ROOT) + " " + Math.round(w.durability()) + "/" + Math.round(w.maxDurability()));
            }
        }
        if (parts.isEmpty()) return target.getName().getString() + " carries no ward.";
        return target.getName().getString() + " carries " + parts.size() + " ward(s): " + String.join(", ", parts) + ".";
    }

    private static int wardCount(LivingEntity target) {
        int count = WardAccess.get(target).wards().size();
        if (target instanceof Warded warded) count += warded.activeWards().size();
        return count;
    }

    private static boolean hasSpecificWard(LivingEntity target, String key) {
        if (key.startsWith("mob:")) {
            if (!(target instanceof Warded warded)) return false;
            try { return warded.activeWards().containsKey(MobWards.WardType.valueOf(key.substring(4).toUpperCase(java.util.Locale.ROOT))); }
            catch (Exception ignored) { return false; }
        }
        try {
            UUID id = UUID.fromString(key);
            return WardAccess.get(target).wards().stream().anyMatch(w -> w.id().equals(id));
        } catch (Exception ignored) { return false; }
    }

    private static boolean removeSpecificWard(LivingEntity target, String key) {
        if (key.startsWith("mob:")) {
            if (!(target instanceof Warded warded)) return false;
            try { return warded.activeWards().remove(MobWards.WardType.valueOf(key.substring(4).toUpperCase(java.util.Locale.ROOT))) != null; }
            catch (Exception ignored) { return false; }
        }
        try {
            UUID id = UUID.fromString(key);
            boolean existed = WardAccess.get(target).wards().stream().anyMatch(w -> w.id().equals(id));
            if (existed) WardService.disable(target, id);
            return existed;
        } catch (Exception ignored) { return false; }
    }

    private static boolean restoreSpecificWard(LivingEntity target, String key) {
        if (key.startsWith("mob:")) {
            if (!(target instanceof Warded warded)) return false;
            try {
                MobWards.WardType type = MobWards.WardType.valueOf(key.substring(4).toUpperCase(java.util.Locale.ROOT));
                MobWards.WardInstance found = warded.activeWards().get(type);
                if (found == null) return false;
                warded.activeWards().put(type, new MobWards.WardInstance(type, found.maxDurability()));
                return true;
            } catch (Exception ignored) { return false; }
        }
        try {
            UUID id = UUID.fromString(key);
            PlayerWards wards = WardAccess.get(target);
            ActiveWard found = wards.wards().stream().filter(w -> w.id().equals(id)).findFirst().orElse(null);
            if (found == null) return false;
            ActiveWard repaired = new ActiveWard(found.id(), found.casterId(), found.type(), found.maxEnergy(), found.maxEnergy(),
                found.maxCharges(), found.chargesUsed(), found.visibleToOthers(), found.staminaBound());
            WardAccess.set(target, wards.withReplaced(repaired));
            WardService.pushSync(target);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static float wardRemoveBaseCost(LivingEntity target, String key) {
        if (key.startsWith("mob:") && target instanceof Warded warded) {
            try {
                MobWards.WardInstance w = warded.activeWards().get(MobWards.WardType.valueOf(key.substring(4).toUpperCase(java.util.Locale.ROOT)));
                return w == null ? 35f : 35f + Math.max(10f, w.durability() * 0.45f);
            } catch (Exception ignored) { return 35f; }
        }
        try { UUID id = UUID.fromString(key); return WardAccess.get(target).wards().stream().filter(w -> w.id().equals(id)).findFirst().map(w -> 35f + Math.max(10f, w.remainingEnergy() * 0.45f)).orElse(35f); }
        catch (Exception ignored) { return 35f; }
    }

    private static float wardRestoreBaseCost(LivingEntity target, String key) {
        if (key.startsWith("mob:") && target instanceof Warded warded) {
            try {
                MobWards.WardInstance w = warded.activeWards().get(MobWards.WardType.valueOf(key.substring(4).toUpperCase(java.util.Locale.ROOT)));
                return w == null ? 30f : 30f + Math.max(0f, w.maxDurability() - w.durability()) * 0.55f;
            } catch (Exception ignored) { return 30f; }
        }
        try { UUID id = UUID.fromString(key); return WardAccess.get(target).wards().stream().filter(w -> w.id().equals(id)).findFirst().map(w -> 30f + Math.max(0f, w.maxEnergy() - w.remainingEnergy()) * 0.55f).orElse(30f); }
        catch (Exception ignored) { return 30f; }
    }

    private static int parseTicks(String param, int fallback) {
        try { int t = Integer.parseInt(param); return Math.max(20, Math.min(20 * 60 * 30, t)); }
        catch (Exception ignored) { return fallback; }
    }

    private static int[] parsePair(String param, int fallbackA, int fallbackB) {
        try {
            String[] bits = param.split(":", 2);
            return new int[]{Integer.parseInt(bits[0]), bits.length > 1 ? Integer.parseInt(bits[1]) : fallbackB};
        } catch (Exception ignored) { return new int[]{fallbackA, fallbackB}; }
    }

    private static float durationScale(int ticks, int baseTicks) {
        return (float)Math.pow(Math.max(1f, ticks / (float)baseTicks), 0.72);
    }

    private static String durationText(int ticks) {
        int seconds = Math.max(1, ticks / 20);
        if (seconds % 60 == 0) { int minutes = seconds / 60; return minutes + (minutes == 1 ? " minute" : " minutes"); }
        return seconds + " seconds";
    }

    private static String revealTraps(ServerLevel level, Vec3 anchor) {
        List<SigilManager.SigilView> sigils = SigilManager.nearby(level, anchor, SIGIL_SCAN_RADIUS);
        if (sigils.isEmpty()) return "No prepared traps answer near the chosen place.";
        return sigils.size() + " prepared trap(s) answer within " + Math.round(SIGIL_SCAN_RADIUS) + " blocks.";
    }

    private static LocalSnapshot capture(ServerLevel level, Vec3 anchor) {
        BlockPos center = BlockPos.containing(anchor);
        Map<BlockPos, BlockSnapshot> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SNAPSHOT_RADIUS, -SNAPSHOT_RADIUS, -SNAPSHOT_RADIUS),
                                                    center.offset(SNAPSHOT_RADIUS, SNAPSHOT_RADIUS, SNAPSHOT_RADIUS))) {
            if (pos.distSqr(center) <= SNAPSHOT_RADIUS * SNAPSHOT_RADIUS) {
                CompoundTag beTag = null;
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                blocks.put(pos.immutable(), new BlockSnapshot(level.getBlockState(pos), beTag));
            }
        }
        List<LivingSnapshot> living = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
            new net.minecraft.world.phys.AABB(anchor, anchor).inflate(SNAPSHOT_RADIUS + 2), LivingEntity::isAlive)) {
            CompoundTag entityTag = null;
            if (!(entity instanceof ServerPlayer)) {
                entityTag = new CompoundTag();
                entity.save(entityTag);
            }
            living.add(new LivingSnapshot(entity.getUUID(), entity.position(), entity.getDeltaMovement(), entity.getHealth(),
                entity.getRemainingFireTicks(), entity.getTicksFrozen(), entity.getYRot(), entity.getXRot(), entityTag));
        }
        return new LocalSnapshot(level.dimension(), Map.copyOf(blocks), List.copyOf(living), level.getDayTime());
    }

    private static String restore(ServerLevel level, LocalSnapshot snapshot) {
        if (!snapshot.dimension().equals(level.dimension())) return "The remembered place is no longer in this world.";
        int changed = 0;
        for (var entry : snapshot.blocks().entrySet()) {
            BlockSnapshot old = entry.getValue();
            if (!level.getBlockState(entry.getKey()).equals(old.state())) {
                level.setBlock(entry.getKey(), old.state(), 3);
                changed++;
            }
            if (old.blockEntityTag() != null) {
                var blockEntity = level.getBlockEntity(entry.getKey());
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(old.blockEntityTag().copy(), level.registryAccess());
                    blockEntity.setChanged();
                }
            }
        }
        int restoredEntities = 0;
        for (LivingSnapshot old : snapshot.living()) {
            Entity existing = level.getEntity(old.id());
            if (existing instanceof LivingEntity living && living.isAlive()) {
                living.moveTo(old.pos().x, old.pos().y, old.pos().z, old.yRot(), old.xRot());
                living.setDeltaMovement(old.velocity());
                living.setHealth(Math.min(living.getMaxHealth(), old.health()));
                living.setRemainingFireTicks(old.fireTicks());
                living.setTicksFrozen(old.frozenTicks());
                restoredEntities++;
            } else if (existing == null && old.entityTag() != null) {
                Entity restored = EntityType.loadEntityRecursive(old.entityTag().copy(), level, entity -> entity);
                if (restored != null && level.addFreshEntity(restored)) restoredEntities++;
            }
        }
        level.setDayTime(snapshot.dayTime());
        return "Local history is forced back to the moment the Word was spoken: " + changed + " block(s), " + restoredEntities + " living thread(s), and the local world-clock restored.";
    }

    private static void sendContext(ServerPlayer player, Session s) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = targetLiving(level, s, player);
        JsonObject root = new JsonObject();
        root.addProperty("target_name", target == player && s.targetId() == null ? "Self (no entity selected)" : target.getName().getString());
        root.addProperty("target_health", target.getHealth());
        root.addProperty("target_max_health", target.getMaxHealth());
        root.addProperty("restore_health_cost", cost(player, s, "restore_health", ""));
        root.addProperty("restore_self_health_cost", cost(player, s, "restore_self_health", ""));
        root.addProperty("anchor", String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", s.anchor().x, s.anchor().y, s.anchor().z));
        root.addProperty("cost_multiplier", WordOfWordsRules.magicCostMultiplier(player.getServer()));
        root.addProperty("word_bound", WordOfWordsKnowledge.isAnchored(player));
        root.addProperty("stamina", StaminaAccess.get(player).stamina());
        root.addProperty("max_stamina", StaminaAccess.get(player).maxStamina());

        JsonArray wards = new JsonArray();
        for (ActiveWard ward : WardAccess.get(target).wards()) {
            JsonObject w = new JsonObject();
            w.addProperty("id", ward.id().toString());
            w.addProperty("type", ward.type().getSerializedName());
            w.addProperty("remaining", ward.remainingEnergy());
            w.addProperty("max", ward.maxEnergy());
            w.addProperty("remove_cost", cost(player, s, "remove_ward", ward.id().toString()));
            w.addProperty("restore_cost", cost(player, s, "restore_ward", ward.id().toString()));
            wards.add(w);
        }
        if (target instanceof Warded warded) {
            for (var entry : warded.activeWards().entrySet()) {
                MobWards.WardInstance ward = entry.getValue();
                String key = "mob:" + entry.getKey().name().toLowerCase(java.util.Locale.ROOT);
                JsonObject w = new JsonObject();
                w.addProperty("id", key);
                w.addProperty("type", entry.getKey().name().toLowerCase(java.util.Locale.ROOT));
                w.addProperty("remaining", ward.durability());
                w.addProperty("max", ward.maxDurability());
                w.addProperty("remove_cost", cost(player, s, "remove_ward", key));
                w.addProperty("restore_cost", cost(player, s, "restore_ward", key));
                wards.add(w);
            }
        }
        root.add("wards", wards);

        JsonArray halts = new JsonArray();
        WordOfWordsHaltManager.EntityHaltView targetHalt = WordOfWordsHaltManager.haltFor(target);
        if (targetHalt != null) {
            JsonObject h = new JsonObject();
            h.addProperty("kind", "target");
            h.addProperty("id", targetHalt.id());
            h.addProperty("label", "Magic halt on " + target.getName().getString());
            h.addProperty("remaining_ticks", targetHalt.remainingTicks());
            h.addProperty("remove_cost", cost(player, s, "remove_halt_target", Long.toString(targetHalt.id())));
            halts.add(h);
        }
        for (WordOfWordsHaltManager.ZoneView zone : WordOfWordsHaltManager.nearbyZones(level, s.anchor(), HALT_SCAN_RADIUS)) {
            JsonObject h = new JsonObject();
            h.addProperty("kind", "area");
            h.addProperty("id", zone.id());
            h.addProperty("label", "Area magic halt, radius " + Math.round(zone.radius()) + "m");
            h.addProperty("remaining_ticks", zone.remainingTicks());
            h.addProperty("distance", Math.sqrt(zone.center().distanceToSqr(s.anchor())));
            h.addProperty("remove_cost", cost(player, s, "remove_halt_area", Long.toString(zone.id())));
            halts.add(h);
        }
        root.add("halts", halts);

        JsonArray sigils = new JsonArray();
        for (SigilManager.SigilView view : SigilManager.nearby(level, s.anchor(), SIGIL_SCAN_RADIUS)) {
            JsonObject v = new JsonObject();
            v.addProperty("id", view.id());
            v.addProperty("element", view.elements().isEmpty() ? "magic" : view.elements().get(0).getSerializedName());
            v.addProperty("power", view.power());
            v.addProperty("distance", Math.sqrt(view.pos().distanceToSqr(s.anchor())));
            v.addProperty("remove_cost", cost(player, s, "remove_trap", Long.toString(view.id())));
            sigils.add(v);
        }
        root.add("sigils", sigils);
        ServerPlayNetworking.send(player, new OpenWordOfWordsPayload(s.id(), root.toString()));
    }
}
