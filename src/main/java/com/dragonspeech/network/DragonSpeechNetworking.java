package com.dragonspeech.network;

import com.dragonspeech.vocabulary.VocabularyService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers both payload types and the server-side receiver. Call
 * registerCommon() from onInitialize(). The client registers its own
 * receiver for KnownWordsSyncPayload separately in DragonSpeechClient,
 * since client-only code can't live in the main source set.
 *
 * VERSION-RISK NOTE: PayloadTypeRegistry.playS2C()/playC2S() is the
 * current (post-1.20.5) Fabric API entry point for registering custom
 * payloads. If exact method names differ in your fabric-api version,
 * search your dependencies for "PayloadTypeRegistry" - the general
 * register(Type, StreamCodec) shape has stayed fairly stable even as
 * the surrounding APIs shifted around it.
 */
public final class DragonSpeechNetworking {

    private DragonSpeechNetworking() {}

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(KnownWordsSyncPayload.TYPE, KnownWordsSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(WardSyncPayload.TYPE, WardSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SkillsSyncPayload.TYPE, SkillsSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TrueNameProgressSyncPayload.TYPE, TrueNameProgressSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ScarSyncPayload.TYPE, ScarSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTabletPayload.TYPE, OpenTabletPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MindDuelSyncPayload.TYPE, MindDuelSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TeamMindDuelSyncPayload.TYPE, TeamMindDuelSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ContactBeamPayload.TYPE, ContactBeamPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MindControlStatePayload.TYPE, MindControlStatePayload.STREAM_CODEC);
        // The particle engine's spawn packet - see SpellFx (server builder)
        // and ClientParticleSpawner (client receiver logic).
        PayloadTypeRegistry.playS2C().register(ParticleSpawnPayload.TYPE, ParticleSpawnPayload.STREAM_CODEC);
        // Geometry-first composed spell bodies (form + element mask, never named abilities).
        PayloadTypeRegistry.playS2C().register(SpellBodyVfxPayload.TYPE, SpellBodyVfxPayload.STREAM_CODEC);
        // Possession presentation state - see PossessionService / PossessedMobRenderMixin.
        PayloadTypeRegistry.playS2C().register(PossessionStatePayload.TYPE, PossessionStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenWordOfWordsPayload.TYPE, OpenWordOfWordsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(WordOfWordsActionPayload.TYPE, WordOfWordsActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CastGridSubmitPayload.TYPE, CastGridSubmitPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TranslateAttemptPayload.TYPE, TranslateAttemptPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(GuessSubmitPayload.TYPE, GuessSubmitPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleFavoritePayload.TYPE, ToggleFavoritePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ReachOutPayload.TYPE, ReachOutPayload.STREAM_CODEC);
        // FIX: DragonCompatUpdatePayload registration removed -
        // "cannot find symbol: class DragonCompatUpdatePayload" in the
        // real crash log, same root cause as DragonSpeech.java's own
        // DragonCompatConfig fix - that whole entity-designation
        // config-screen feature is on hold, and neither the payload
        // class nor the config class it depends on were ever actually
        // applied to this project. Missed this file when I fixed
        // DragonSpeech.java earlier - should have searched for every
        // reference, not just the one I happened to already know about.
        PayloadTypeRegistry.playC2S().register(MindDuelActionPayload.TYPE, MindDuelActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TrueNameLetterAttemptPayload.TYPE, TrueNameLetterAttemptPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TrueNameGuessSubmitPayload.TYPE, TrueNameGuessSubmitPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TeamActionPayload.TYPE, TeamActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(HastenContactPayload.TYPE, HastenContactPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(MindControlInputPayload.TYPE, MindControlInputPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CastGridSubmitPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // Network callbacks fire off the server thread - hop back on
            // before touching any game state.
            context.server().execute(() -> CastRequestHandler.handle(player, payload.assignmentsJson()));
        });

        ServerPlayNetworking.registerGlobalReceiver(GuessSubmitPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> GuessRequestHandler.handle(player, payload.candidate()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ToggleFavoritePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    ResourceLocation id = ResourceLocation.parse(payload.wordId());
                    VocabularyService.toggleFavorite(player, id);
                    VocabularySyncHooks.pushSync(player);
                } catch (Exception ignored) {
                    // Malformed word id from a stale/tampered client - just ignore it.
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(TranslateAttemptPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> TranslateAttemptHandler.handle(player, payload.wordId(), payload.chosenMeaning()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ReachOutPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.mind.MindDuelRequestHandler.handleReachOut(player, payload.targetEntityId()));
        });

        // FIX: the DragonCompatUpdatePayload handler that used to be
        // here is removed for the same reason as the type registration
        // above - that whole feature is on hold.

        ServerPlayNetworking.registerGlobalReceiver(MindDuelActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.mind.MindDuelRequestHandler.handleAction(player, payload.action(), payload.param()));
        });

        ServerPlayNetworking.registerGlobalReceiver(TrueNameLetterAttemptPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var outcome = com.dragonspeech.mind.TrueNameProgressService.attemptLetter(context.server(), player, payload.targetId());
                String message = switch (outcome.result()) {
                    case NOT_UNLOCKED -> "You have no thread to this mind at all.";
                    case ALREADY_SOLVED -> "Their true name is already known to you.";
                    case TARGET_UNAVAILABLE -> "They are not here for you to reach toward.";
                    case INSUFFICIENT_STAMINA -> "You have not the strength left to reach.";
                    case FAILED -> "You reach, and grasp nothing.";
                    case GAINED_LETTER -> "A letter surfaces: '" + outcome.letter() + "'.";
                    case ALREADY_HAD_LETTER -> "A letter surfaces: '" + outcome.letter() + "' - one you already held.";
                };
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TrueNameGuessSubmitPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var result = com.dragonspeech.mind.TrueNameProgressService.submitGuess(context.server(), player, payload.targetId(), payload.guess());
                String message = switch (result) {
                    case NOT_UNLOCKED -> "You have no thread to this mind at all.";
                    case ALREADY_SOLVED -> "Their true name is already known to you.";
                    case TARGET_UNAVAILABLE -> "They are not here for the name to settle onto.";
                    case ON_COOLDOWN -> "The last wrong shape you spoke still echoes - wait before trying again.";
                    case CORRECT -> "Correct - their true name is yours to keep, now and always.";
                    case WRONG -> "Wrong - the shape does not fit, and slips away from you.";
                };
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TeamActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.mind.TeamMindDuelRequestHandler.handleAction(player, payload.action(), payload.targetId().orElse(null)));
        });

        ServerPlayNetworking.registerGlobalReceiver(HastenContactPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.mind.PendingContactRequestHandler.handleHasten(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(MindControlInputPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.mind.MindControlService.handleInput(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(WordOfWordsActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.wow.WordOfWordsService.executeAction(
                player, payload.sessionId(), payload.action(), payload.parameter()));
        });

        PayloadTypeRegistry.playS2C().register(OpenDragonBondScreenPayload.TYPE, OpenDragonBondScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DragonBondSettingsPayload.TYPE, DragonBondSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(com.dragonspeech.network.DragonFlightInputPayload.TYPE, com.dragonspeech.network.DragonFlightInputPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetDragonNamePayload.TYPE, SetDragonNamePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(com.dragonspeech.network.OpenDragonHeartScreenPayload.TYPE, com.dragonspeech.network.OpenDragonHeartScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(com.dragonspeech.network.SetHeartStaminaSettingsPayload.TYPE, com.dragonspeech.network.SetHeartStaminaSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(com.dragonspeech.network.GiveDragonHeartPayload.TYPE, com.dragonspeech.network.GiveDragonHeartPayload.STREAM_CODEC);

        // Config GUI (see ConfigScreen / ConfigRequestHandler).
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigRequestPayload.TYPE, ConfigRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ReloadSentiencePayload.TYPE, ReloadSentiencePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetSentienceOverridePayload.TYPE, SetSentienceOverridePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetDifficultyTuningPayload.TYPE, SetDifficultyTuningPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ResetDifficultyTuningPayload.TYPE, ResetDifficultyTuningPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ResetServerTabPayload.TYPE, ResetServerTabPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ResetAllConfigPayload.TYPE, ResetAllConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ClearSentienceOverridesPayload.TYPE, ClearSentienceOverridesPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleRequest(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleUpdate(player, payload.configJson()));
        });
        ServerPlayNetworking.registerGlobalReceiver(ReloadSentiencePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleReloadSentience(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SetSentienceOverridePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleSentienceUpdate(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(SetDifficultyTuningPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleDifficultyTuningUpdate(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(ResetDifficultyTuningPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleDifficultyTuningReset(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ResetServerTabPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleResetServerTab(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ResetAllConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleResetAllConfig(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ClearSentienceOverridesPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ConfigRequestHandler.handleClearSentienceOverrides(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SetDragonNamePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var dragon = com.dragonspeech.dragon.DragonEntity.findNearestBonded(player, 64.0)
                    .filter(d -> d.getUUID().equals(payload.dragonId()));
                dragon.ifPresent(d -> {
                    String trimmed = payload.name().strip();
                    if (trimmed.isEmpty()) {
                        d.setCustomName(null);
                        d.setCustomNameVisible(false);
                    } else {
                        d.setCustomName(net.minecraft.network.chat.Component.literal(trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed));
                        d.setCustomNameVisible(true);
                    }
                });
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DragonBondSettingsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                // Re-validated here, not trusted from the packet: only a
                // dragon actually bonded to THIS player can have its
                // settings changed by them, regardless of what UUID the
                // client sent.
                var dragon = com.dragonspeech.dragon.DragonEntity.findNearestBonded(player, 64.0)
                    .filter(d -> d.getUUID().equals(payload.dragonId()));
                dragon.ifPresent(d -> {
                    d.setUseDragonStamina(payload.useDragonStamina());
                    d.setStaminaBeforeOwn(payload.staminaBeforeOwn());
                    d.setStaminaLimiterPercent(payload.staminaLimiterPercent());
                    d.setOptionFollowing(payload.optionFollowing());
                    d.setOptionAggressiveAssist(payload.optionAggressiveAssist());
                    d.setAttackNearbyHostile(payload.attackNearbyHostile());
                    d.setAttackNearbyNeutral(payload.attackNearbyNeutral());
                    d.setAttackNearbyPassive(payload.attackNearbyPassive());
                    d.setOptionStay(payload.optionStay());
                });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.SetHeartStaminaSettingsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var stack = com.dragonspeech.eldunari.DragonHeartService.findById(player, payload.heartId());
                // Re-validated here, not trusted from the packet: only a
                // heart actually present in THIS player's own inventory
                // (found via the stable ELDUNARI_ID, not a slot index
                // the client could lie about) can have its settings
                // changed by them.
                if (stack != null) {
                    stack.set(com.dragonspeech.storage.DragonSpeechComponents.HEART_USE_STAMINA, payload.useStamina());
                    stack.set(com.dragonspeech.storage.DragonSpeechComponents.HEART_STAMINA_BEFORE_OWN, payload.staminaBeforeOwn());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.GiveDragonHeartPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> com.dragonspeech.eldunari.DragonHeartService.giveOwnHeart(player, payload.dragonId()));
        });

        // FIX: the DragonFlightInputPayload handler that used to be
        // here is removed - "cannot find symbol: method
        // setFlightInput/riddenJump" in the real crash log. Both were
        // part of the old custom pitch-directed flight controller;
        // Dragon Mounts Legacy's own riding uses vanilla's standard
        // rider-input sync (getRiddenInput()) automatically, with no
        // custom packet needed at all. The payload type itself may
        // still be registered elsewhere (harmless if so, just unused)
        // but nothing should still be sending it client-side either.
    }

    public static void sendKnownWordsSync(ServerPlayer player, String wordsJson) {
        ServerPlayNetworking.send(player, new KnownWordsSyncPayload(wordsJson));
    }

    public static void sendStaminaSync(ServerPlayer player, float stamina, float maxStamina) {
        ServerPlayNetworking.send(player, new StaminaSyncPayload(stamina, maxStamina));
    }

    public static void sendWardSync(ServerPlayer player, String wardsJson) {
        ServerPlayNetworking.send(player, new WardSyncPayload(wardsJson));
    }

    public static void sendSkillsSync(ServerPlayer player, String skillsJson) {
        ServerPlayNetworking.send(player, new SkillsSyncPayload(skillsJson));
    }

    public static void sendTrueNameProgressSync(ServerPlayer player, String progressJson) {
        ServerPlayNetworking.send(player, new TrueNameProgressSyncPayload(progressJson));
    }

    public static void sendScarSync(ServerPlayer player, String descriptionsJson) {
        ServerPlayNetworking.send(player, new ScarSyncPayload(descriptionsJson));
    }

    public static void sendOpenTablet(ServerPlayer player, String contentJson) {
        ServerPlayNetworking.send(player, new OpenTabletPayload(contentJson));
    }

    public static void sendMindDuelSync(ServerPlayer player, String duelJson) {
        ServerPlayNetworking.send(player, new MindDuelSyncPayload(duelJson));
    }

    public static void sendTeamMindDuelSync(ServerPlayer player, String duelJson) {
        ServerPlayNetworking.send(player, new TeamMindDuelSyncPayload(duelJson));
    }

    public static void sendContactBeam(ServerPlayer player, int targetEntityId, int durationTicks, boolean canceled) {
        ServerPlayNetworking.send(player, new ContactBeamPayload(targetEntityId, durationTicks, canceled));
    }

    public static void sendMindControlState(ServerPlayer player, int targetEntityId, boolean active) {
        ServerPlayNetworking.send(player, new MindControlStatePayload(targetEntityId, active));
    }

    public static void sendPossessionState(ServerPlayer player, int mobEntityId, boolean active) {
        ServerPlayNetworking.send(player, new PossessionStatePayload(mobEntityId, active));
    }

    public static void sendConfigSync(ServerPlayer player, String configJson) {
        ServerPlayNetworking.send(player, new ConfigSyncPayload(configJson));
    }

    /**
     * After any successful Server-tab edit, every OTHER admin who might currently have the config
     * screen open gets refreshed too - otherwise two admins editing around the same time could each
     * see a stale snapshot and stomp each other's change without realizing it. Cheap: only ever
     * reaches players who are actually online AND op level 4, so on a normal server this is a no-op
     * or reaches a small handful of people at most.
     */
    public static void broadcastConfigSyncToAdmins(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (admin.hasPermissions(4)) {
                sendConfigSync(admin, ConfigRequestHandler.buildSyncJson(admin));
            }
        }
    }
}
