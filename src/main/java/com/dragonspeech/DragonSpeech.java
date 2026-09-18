package com.dragonspeech;

import com.dragonspeech.channel.ChannelTickHandler;
import com.dragonspeech.command.DragonSpeechCommands;
import com.dragonspeech.effect.EffectHandlerRegistry;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.growth.GrowthHooks;
import com.dragonspeech.growth.PlayerAttunementAttachments;
import com.dragonspeech.item.DragonSpeechItems;
import com.dragonspeech.loot.LootInjection;
import com.dragonspeech.network.ChatCastHooks;
import com.dragonspeech.network.DragonSpeechNetworking;
import com.dragonspeech.network.VocabularySyncHooks;
import com.dragonspeech.stamina.PlayerMagicAttachments;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.PlayerSkillsAttachments;
import com.dragonspeech.storage.SkillHooks;
import com.dragonspeech.storage.StorageDecayTicker;
import com.dragonspeech.vocabulary.PlayerVocabularyAttachments;
import com.dragonspeech.ward.WardAccess;
import com.dragonspeech.word.WordRegistry;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DragonSpeech implements ModInitializer {
	public static final String MOD_ID = "dragonspeech";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Shared Gson instance used by the datapack reload listeners (WordRegistry, etc.)
	public static final Gson GSON = new Gson();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Dragon Speech: the Ancient Language awakens...");

		// "Bonded dragons still don't persist through a full game close,
		// even with the CLIENT_STOPPING hook." That hook may simply fire
		// too late - by the time the CLIENT is stopping, the integrated
		// SERVER may have already begun (or finished) its own shutdown,
		// meaning client.getSingleplayerServer() could already be null
		// or the save could already be racing against teardown. This is
		// a second, more direct hook: SERVER_STOPPING fires as part of
		// MinecraftServer.stopServer() itself - the SAME method a normal
		// "leave world" disconnect already goes through (which is
		// confirmed working, per your own log evidence) - and hands us
		// the server instance directly rather than requiring a lookup
		// that might happen after it's already gone. Between this and
		// the CLIENT_STOPPING hook, a full game close should now be
		// caught by whichever fires first/still has a valid server.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server ->
			server.saveAllChunks(false, true, true));

		com.dragonspeech.config.DragonSpeechConfig.load();
		DragonSpeechComponents.bootstrap();
		DragonSpeechItems.bootstrap();
		// The 7 dragon egg blocks (see DragonSpeechBlocks) - registered
		// separately from items since they're real placeable blocks now,
		// not a single generic Item.
		com.dragonspeech.dragon.DragonSpeechBlocks.bootstrap();
		com.dragonspeech.loot.DragonSpeechLootFunctions.bootstrap();
		com.dragonspeech.eldunari.DragonSpeechHearts.bootstrap();
		// Particle TYPES must be in the registry before any level exists -
		// their client factories are registered separately in
		// DragonSpeechClient (client source set).
		DragonSpeechParticles.register();
		LootInjection.register();
		EffectHandlerRegistry.bootstrap();
		com.dragonspeech.mind.CommandEffectRegistry.bootstrap();
		PlayerMagicAttachments.bootstrap();
		com.dragonspeech.dragon.PlayerBondAttachments.bootstrap();
		com.dragonspeech.engine.EntityMarkAttachments.bootstrap();
		com.dragonspeech.stamina.MobStaminaAttachments.bootstrap();
		com.dragonspeech.accessory.AccessorySlotsAttachments.bootstrap();
		com.dragonspeech.accessory.AccessoryItems.bootstrap();
		com.dragonspeech.accessory.AccessoryDeathDrops.bootstrap();
		com.dragonspeech.item.DragonSpeechItemGroups.bootstrap();
		PlayerVocabularyAttachments.bootstrap();
		PlayerAttunementAttachments.bootstrap();
		PlayerSkillsAttachments.bootstrap();
		WardAccess.bootstrap();
		com.dragonspeech.wound.WoundAccess.bootstrap();
		com.dragonspeech.mind.MindDataAccess.bootstrap();
		WordRegistry.register();
		DragonSpeechNetworking.registerCommon();
		VocabularySyncHooks.register();
		ChatCastHooks.register();
		ChannelTickHandler.register();
		// Placed ristmark (sigil) workings tick here - see SigilManager.
		com.dragonspeech.engine.SigilManager.register();
		// kyrra'd mobs held outside time tick here - see StasisManager.
		com.dragonspeech.engine.StasisManager.register();
		// Virtual composed-spell bodies clash with one another and with physical magic barriers.
		com.dragonspeech.engine.SpellCollisionManager.register();
		// kringla's lingering time-bubbles tick here - see TemporalFieldManager.
		com.dragonspeech.engine.TemporalFieldManager.register();
		// thyngja/thyngdbinda's sustained gravity multiplier ticks here - see GravityFieldManager.
		com.dragonspeech.engine.GravityFieldManager.register();
		// explosions/general damage land softer on time-slowed targets - see TimeSlowDamageHooks.
		com.dragonspeech.engine.TimeSlowDamageHooks.register();
		// hambinda skin-wearing lifecycle + its crash-proof inventory stash.
		com.dragonspeech.mind.PossessionAttachments.bootstrap();
		com.dragonspeech.mind.PossessionService.register();
		// heimbinda's home anchor - see HeimbindaEffectHandler.
		com.dragonspeech.mind.HomeAnchorAttachments.bootstrap();
		// skjoldr's physical barrier entity and its melee/explosion/fire
		// damage redirect (projectiles are stopped by its own hitbox for
		// free - see MagicBarrierEntity).
		com.dragonspeech.entity.DragonSpeechEntities.bootstrap();
		// "/dragonspeech summon elf" etc. - short-name alternative to
		// vanilla's own "/summon dragonspeech:elf" (which can't be
		// shortened - see DragonSpeechSummonCommand's own doc for why).
		com.dragonspeech.entity.DragonSpeechSummonCommand.register();
		com.dragonspeech.entity.BarrierProtection.register();
		// Admin-only ward visibility ("/dragonspeech showwards <true|false>",
		// default off) plus the per-tick loops driving it and the
		// stamina/mark detection reveal (skynja afl/marka) - see
		// WardVisibility and DetectionService respectively.
		com.dragonspeech.mob.casting.WardVisibilityCommand.register();
		// Admin-only instant mind breach ("/dragonspeech forcebreach
		// <true|false>", default off) - see MindBreachAdmin/MindBreachCommand.
		com.dragonspeech.mind.MindBreachCommand.register();
		com.dragonspeech.mind.MindBreachAdmin.register();
		// Unified admin debug tree ("/dragonspeech debug <subcommand>
		// <true|false>": npcspells, staminaview, forcebreach, showwards,
		// audit) - see DebugCommand. EntityAudit needs its own
		// interaction-hook registration; the other four subcommands just
		// delegate to services already registered above/below.
		com.dragonspeech.mob.casting.DebugCommand.register();
		com.dragonspeech.dragon.DragonDebugHatchCommand.register();
		com.dragonspeech.dragon.DragonSummonCommand.register();
		com.dragonspeech.mob.casting.EntityAudit.register();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var player : server.getPlayerList().getPlayers()) {
				com.dragonspeech.mob.casting.WardVisibility.tick(player);
				com.dragonspeech.mob.casting.StaminaView.tick(player);
			}
			for (var level : server.getAllLevels()) {
				com.dragonspeech.detection.DetectionService.tick(level);
			}
		});
		com.dragonspeech.stamina.StaminaTicker.register();
		// The one-tick-delayed lethal-damage path aflbinda's stamina
		// fallback uses - see DrainResolver.applyLethalDrain.
		com.dragonspeech.stamina.LethalDrainQueue.register();
		GrowthHooks.register();
		SkillHooks.register();
		com.dragonspeech.mind.TrueNameDuelHooks.register();
		com.dragonspeech.death.DeathHooks.register();
		com.dragonspeech.death.ReviveScheduler.register();
		com.dragonspeech.scar.ScarAccess.bootstrap();
		com.dragonspeech.scar.ChronicPainTicker.register();
		com.dragonspeech.network.ScarSyncHooks.register();
		com.dragonspeech.wow.WordOfWordsEffect.register();
		StorageDecayTicker.register();
		com.dragonspeech.mind.MindDuelTickHandler.register();
		com.dragonspeech.mind.MindControlService.register();
		com.dragonspeech.mind.MobMindCombatAI.register();
		com.dragonspeech.mind.PendingContactTicker.register();
		com.dragonspeech.mind.TrueNameRevealTicker.register();
		DragonSpeechCommands.register();
		com.dragonspeech.command.MindCommands.register();
		com.dragonspeech.race.RaceAccess.bootstrap();
		com.dragonspeech.race.RaceTraitHooks.register();
		com.dragonspeech.command.RaceCommands.register();
		com.dragonspeech.worldgen.DragonSpeechFeatures.bootstrap();
		com.dragonspeech.worldgen.DragonSpeechWorldgen.register();
		// Natural spawning for the 4 races and wild Dragons - see
		// DragonSpeechSpawns' own doc for the full "this never existed
		// before at all" backstory.
		com.dragonspeech.worldgen.DragonSpeechSpawns.register();
		// TEMPORARY - answers whether the 4 structure template pools
		// actually load with real content or resolve empty. Safe to
		// remove once that's confirmed either way - see its own doc.
		com.dragonspeech.debug.StructurePoolDiagnostic.register();
		// TEMPORARY - "/dragonspeech diag structure <name>" - isolates
		// exactly which step of real structure generation fails. Safe
		// to remove once that's answered - see its own doc.
		com.dragonspeech.debug.StructureGenDiagnosticCommand.register();

		// Dragons / Dragon Heart phase. SentienceConfig must run AFTER
		// DragonSpeechEntities.bootstrap() (so DRAGON/DRAGON_HEART_VESSEL
		// are already registered - config-driven overrides use
		// TIER_OVERRIDES too, order doesn't matter between the two, but
		// both must run after entity-type registration completes, which
		// they do here).
		com.dragonspeech.compat.SentienceConfig.bootstrap();
		// FIX: DragonCompatConfig.bootstrap() removed - "cannot find
		// symbol: class DragonCompatConfig" in the real crash log. That
		// entity-designation config-screen work is on hold ("I will
		// have another chat work on that" per explicit direction) - the
		// actual DragonCompatConfig.java file itself was never applied
		// to this project, only this bootstrap call referencing it was.
		// Must run before DragonBreedRegistry.register() - a breed's
		// own JSON can reference any of the 7 habitat types, and each
		// one only actually registers itself (via a static
		// initializer) once its class is first loaded. See
		// HabitatTypes' own doc for the full reasoning.
		com.dragonspeech.dragon.egg.HabitatTypes.bootstrap();
		com.dragonspeech.dragon.breed.DragonBreedRegistry.register();
		com.dragonspeech.dragon.egg.DragonEggHatchingBlockEntities.register();
		com.dragonspeech.eldunari.DragonHeartService.register();
		com.dragonspeech.command.DragonCommands.register();
		// FIX: registerDismountHandler() removed - "cannot find symbol:
		// method registerDismountHandler()" in the real crash log. Old
		// custom-flight-system leftover (dismount-mid-flight handling
		// for the previous pitch-directed flight controller) - no
		// equivalent needed now that Dragon Mounts Legacy's own
		// standard saddle-riding replaced that system entirely.
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
