package com.dragonspeech.client;

import com.dragonspeech.client.construct.SpellConstructionScreen;
import com.dragonspeech.client.config.ConfigScreen;
import com.dragonspeech.client.config.DragonSpeechClientConfig;
import com.dragonspeech.client.config.ServerConfigClientCache;
import com.dragonspeech.network.ConfigSyncPayload;
import com.dragonspeech.client.hud.ClientStaminaCache;
import com.dragonspeech.client.hud.StaminaHudOverlay;
import com.dragonspeech.client.grid.KnownWordsClientCache;
import com.dragonspeech.client.grid.LastSpellCache;
import com.dragonspeech.client.guess.GuessScreen;
import com.dragonspeech.client.storage.StorageTooltipHooks;
import com.dragonspeech.client.tablet.TabletScreen;
import com.dragonspeech.client.mind.MindDuelScreen;
import com.dragonspeech.client.mind.MindControlClientState;
import com.dragonspeech.client.mind.TeamMindDuelScreen;
import com.dragonspeech.network.CastGridSubmitPayload;
import com.dragonspeech.network.KnownWordsSyncPayload;
import com.dragonspeech.network.OpenTabletPayload;
import com.dragonspeech.network.MindDuelSyncPayload;
import com.dragonspeech.network.TeamMindDuelSyncPayload;
import com.dragonspeech.network.ReachOutPayload;
import com.dragonspeech.network.StaminaSyncPayload;
import com.dragonspeech.network.WardSyncPayload;
import com.dragonspeech.network.ScarSyncPayload;
import com.dragonspeech.client.scar.ClientScarCache;
import com.dragonspeech.client.ward.ClientWardCache;
import com.dragonspeech.client.ward.WardRingRenderer;
import com.dragonspeech.client.mind.ContactBeamRenderer;
import com.dragonspeech.client.mind.ContactBeamState;
import com.dragonspeech.network.ContactBeamPayload;
import com.dragonspeech.network.HastenContactPayload;
import com.dragonspeech.network.MindControlStatePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * VERSION-RISK NOTE: KeyMapping's constructor and the exact
 * ClientPlayNetworking.registerGlobalReceiver context accessors
 * (context.client(), etc.) are the parts of this file most likely to need
 * a small fix against your exact fabric-api version - both are otherwise
 * very standard, commonly-used Fabric patterns.
 */
public class DragonSpeechClient implements ClientModInitializer {

	private static KeyMapping openCastingGridKey;
	private static KeyMapping openGuessKey;
	/** "Make these dragon controls changeable in keybinds as well" per explicit direction - a real, Controls-menu-remappable KeyMapping, not a hardcoded key check. */
	private static KeyMapping dragonDescendKey;
	// openGrimoireKey and openDragonBondKey removed - "combine all of my
	// keybinded screens into only needing 1 keybind... tabs at the top"
	// per explicit direction. openCastingGridKey (still bound to G) now
	// opens the whole tabbed suite; Grimoire and Bonded Dragon are
	// reached via the tab bar (see DragonSpeechTabBar) instead of their
	// own separate keys.
	private static KeyMapping recastKey;
	private static KeyMapping reachOutKey;
	private static int hastenTickCounter = 0;
	// FIX: the flight-input tracking fields that used to be here are
	// removed - dead fields once their only consumer (the tick handler
	// below) was removed for the same reason (old custom flight system,
	// no longer needed now that Dragon Mounts Legacy's own riding
	// handles input through vanilla's standard mechanism).

	@Override
	public void onInitializeClient() {
		// The Client tab of the config GUI reads from this - load it before anything else so an
		// early-rendered frame (e.g. StaminaHudOverlay on the very first tick) already has real values.
		DragonSpeechClientConfig.load();

		com.dragonspeech.client.enchant.MagicEnchantmentTooltips.register();
		com.dragonspeech.client.weapon.ConjuredWeaponTooltipHooks.register();
		com.dragonspeech.client.weapon.ConjuredWeaponItemRenderer.register();
		// "If you leave the world and rejoin, the bond stays. If you
		// close the whole game, the bond is gone." The log evidence
		// from testing confirms our own save/load NBT code is correct -
		// every dragon loaded with the right BondedOwner tag, every
		// dragon saved with the right owner. Leaving a world explicitly
		// triggers a forced save as part of disconnecting, which is why
		// THAT path works. Closing the whole application doesn't
		// automatically go through that same explicit call - this hook
		// forces one, synchronously, right before the client actually
		// stops, so a full close can't race ahead of an unfinished
		// world save the way it apparently was.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			var server = client.getSingleplayerServer();
			if (server != null) {
				server.saveAllChunks(false, true, true);
			}
		});
		// FIX: "cannot find symbol: method setFlightInput" in the real
		// crash log. The entire double-tap-space/double-tap-forward/
		// control-to-descend input system that used to live in this
		// tick handler was built for the old custom pitch-directed
		// flight controller - Dragon Mounts Legacy's own riding uses
		// vanilla's standard input handling automatically (space to
		// jump/lift off, held forward to move), no custom packet
		// needed at all. Kept only the camera-state ticking, which is
		// still relevant regardless of the flight-input system
		// underneath it.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				com.dragonspeech.client.camera.DragonCameraState.reset();
				return;
			}
			if (!(client.player.getVehicle() instanceof com.dragonspeech.dragon.DragonEntity)) {
				// Not riding a dragon at all - still tick so roll
				// smoothly decays back to 0 after dismounting, rather
				// than snapping.
				com.dragonspeech.client.camera.DragonCameraState.tick();
				return;
			}
			// "Follow the mechanics that mod uses as much as possible"
			// per explicit direction, applied to the camera too - see
			// DragonCameraState's own doc for what's implemented
			// (roll only, no seat-bone position yet) versus deferred.
			com.dragonspeech.client.camera.DragonCameraState.updateTarget(client.player.getYRot());
			com.dragonspeech.client.camera.DragonCameraState.tick();
		});
		// Particle renderers + the (world, pos) factories the spell-fx
		// spawn packet uses - see com.dragonspeech.client.fx.
		com.dragonspeech.client.fx.DragonSpeechParticleFactories.registerAll();
		// Persistent geometry bodies adapted from Dragon Flux: particles are accents, not the spell itself.
		com.dragonspeech.client.fx.SpellBodyVfxState.register();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.MAGIC_BARRIER,
				com.dragonspeech.client.entity.MagicBarrierRenderer::new);
		// Dragon Mounts Legacy port per explicit direction - the
		// GeckoLib-based dragon renderer is gone entirely (see the
		// removed DragonGeoRenderer/DragonGeoModel/dragon.geo.json/
		// dragon.animation.json - flagged for deletion once this whole
		// port compiles end to end). This model layer registration
		// itself already existed (originally for DragonOnShoulderLayer,
		// a SEPARATE render context on the player's own renderer) -
		// updated here for the new DragonModel.createBodyLayer's real
		// signature (it now takes a DragonBreed.ModelVariation - see
		// that file's own doc for why, and why this is the single
		// static STANDARD variation for now, not per-breed yet).
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.dragonspeech.client.dragon.DragonRenderer.MODEL_LOCATION,
				() -> com.dragonspeech.client.dragon.DragonModel.createBodyLayer(com.dragonspeech.dragon.breed.DragonBreed.ModelVariation.STANDARD));
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.DRAGON,
				com.dragonspeech.client.dragon.DragonRenderer::new);
		// FIX: this was genuinely missing - without it, DragonEntity's
		// own animatorFactory field (see that file's own doc on the
		// split-source-set workaround it exists for) stays null
		// forever, and animator/getAnimator() silently returns null
		// client-side too - no compile error, animations just never
		// run at all.
		com.dragonspeech.dragon.DragonEntity.animatorFactory = com.dragonspeech.client.dragon.DragonAnimator::new;
		// The other half of the same fix pattern - DragonEntity.
		// ctrlKeyProvider defaults to "always false" (safe for a
		// dedicated server), and only actually reflects real ctrl-key
		// state once this client-only registration runs.
		// FIX: replaces ctrlKeyProvider - "changed from ctrl to z" per
		// explicit direction, now backed by the real, remappable
		// dragonDescendKey registered above instead of a hardcoded
		// Screen.hasControlDown() check.
		com.dragonspeech.dragon.DragonEntity.descendKeyProvider = () -> dragonDescendKey.isDown();
		// "ctrl functions as a sprint... same thing please" per explicit
		// direction. Reusing vanilla's OWN "Sprint" keybind directly
		// (its default IS left ctrl already) rather than the derived
		// isSprinting() entity flag, which is what didn't actually
		// engage while riding - this reads the raw key state instead,
		// bypassing whatever vanilla-internal gating caused that. Also
		// respects it if the player's rebound their own sprint key away
		// from ctrl, same as any other vanilla-key reuse in this file.
		com.dragonspeech.dragon.DragonEntity.sprintKeyProvider = () -> net.minecraft.client.Minecraft.getInstance().options.keySprint.isDown();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.DRAGON_HEART_VESSEL,
				com.dragonspeech.client.dragon.DragonHeartVesselRenderer::new);
		// FIX: DRAGON_HITBOX_PART registration removed - that entity
		// type itself no longer exists (see DragonSpeechEntities' own
		// doc on why the whole multi-part hitbox system was removed to
		// unblock compilation).
		// Hurled weapon projectiles render as their real 3D item model,
		// oriented to point along their flight path (like vanilla's
		// thrown Trident) - see WeaponProjectileRenderer for why this
		// replaced the earlier billboard-style ThrownItemRenderer.
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.WEAPON_PROJECTILE,
				com.dragonspeech.client.weapon.WeaponProjectileRenderer::new);
		// Elf / Elder Elf / Human Mage / Shade - see com.dragonspeech.mob.casting
		// for the shared spellcasting groundwork all four are built on, and
		// each texture's own placeholder note for why it's a flat color today.
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.dragonspeech.client.model.ElfEntityModel.LAYER_LOCATION,
				com.dragonspeech.client.model.ElfEntityModel::createBodyLayer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.ELF,
				com.dragonspeech.client.renderer.ElfRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.dragonspeech.client.model.ElderElfEntityModel.LAYER_LOCATION,
				com.dragonspeech.client.model.ElderElfEntityModel::createBodyLayer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.ELDER_ELF,
				com.dragonspeech.client.renderer.ElderElfRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.dragonspeech.client.model.HumanMageEntityModel.LAYER_LOCATION,
				com.dragonspeech.client.model.HumanMageEntityModel::createBodyLayer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.HUMAN_MAGE,
				com.dragonspeech.client.renderer.HumanMageRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.dragonspeech.client.model.ShadeEntityModel.LAYER_LOCATION,
				com.dragonspeech.client.model.ShadeEntityModel::createBodyLayer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dragonspeech.entity.DragonSpeechEntities.SHADE,
				com.dragonspeech.client.renderer.ShadeRenderer::new);
		StorageTooltipHooks.register();
		StaminaHudOverlay.register();
		WardRingRenderer.register();
		ContactBeamRenderer.register();
		MindControlClientState.register();

		openCastingGridKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dragonspeech.open_casting_grid",
				GLFW.GLFW_KEY_G,
				"category.dragonspeech.general"
		));

		openGuessKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dragonspeech.speak_word",
				GLFW.GLFW_KEY_H,
				"category.dragonspeech.general"
		));

		recastKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dragonspeech.recast",
				GLFW.GLFW_KEY_R,
				"category.dragonspeech.general"
		));

		reachOutKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dragonspeech.reach_out",
				GLFW.GLFW_KEY_K,
				"category.dragonspeech.general"
		));

		// "changed from ctrl to z... Make these dragon controls
		// changeable in keybinds as well" per explicit direction - real
		// keybind, default Z, remappable through vanilla's own Controls
		// menu like every other binding in this file.
		dragonDescendKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dragonspeech.dragon_descend",
				GLFW.GLFW_KEY_Z,
				"category.dragonspeech.general"
		));

		ClientPlayNetworking.registerGlobalReceiver(KnownWordsSyncPayload.TYPE, (payload, context) -> {
			List<KnownWordsClientCache.ClientWordEntry> parsed = parse(payload.wordsJson());
			context.client().execute(() -> KnownWordsClientCache.update(parsed));
		});

		ClientPlayNetworking.registerGlobalReceiver(StaminaSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientStaminaCache.update(payload.stamina(), payload.maxStamina())));

		ClientPlayNetworking.registerGlobalReceiver(WardSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientWardCache.update(payload.wardsJson())));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.SkillsSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.dragonspeech.client.grimoire.ClientSkillsCache.update(payload.skillsJson())));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.TrueNameProgressSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.dragonspeech.client.grimoire.ClientTrueNameCache.update(payload.progressJson())));

		ClientPlayNetworking.registerGlobalReceiver(ScarSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientScarCache.update(payload.descriptionsJson())));

		ClientPlayNetworking.registerGlobalReceiver(OpenTabletPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					// Refresh in place if the inscription is already open (a solve
					// updates the tablet's solved list); otherwise open it fresh.
					if (context.client().screen instanceof TabletScreen tabletScreen) {
						tabletScreen.refresh(payload.contentJson());
					} else {
						context.client().setScreen(new TabletScreen(payload.contentJson()));
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.OpenWordOfWordsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().screen instanceof com.dragonspeech.client.wow.WordOfWordsScreen wow) {
						wow.refresh(payload.sessionId(), payload.contextJson());
					} else {
						context.client().setScreen(new com.dragonspeech.client.wow.WordOfWordsScreen(payload.sessionId(), payload.contextJson()));
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.OpenDragonBondScreenPayload.TYPE, (payload, context) ->
				context.client().execute(() ->
						context.client().setScreen(new com.dragonspeech.client.gui.DragonBondScreen(payload.dragonId()))));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.OpenDragonHeartScreenPayload.TYPE, (payload, context) ->
				context.client().execute(() ->
						context.client().setScreen(new com.dragonspeech.client.gui.DragonHeartScreen(
								payload.heartId(), payload.colorName(), payload.energy(), payload.maxEnergy(),
								payload.useStamina(), payload.staminaBeforeOwn()))));

		ClientPlayNetworking.registerGlobalReceiver(MindDuelSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					String json = payload.duelJson();
					MindControlClientState.cacheDuelJson(json);
					if (context.client().screen instanceof MindDuelScreen duelScreen) {
						// Empty json means the duel just ended/was cleared server-side -
						// refresh() itself closes the screen when parse() returns null.
						duelScreen.refresh(json);
					} else if (!json.isBlank() && !MindControlClientState.isActive() && context.client().screen == null) {
						// A duel just started (Contact succeeded) and no screen was open yet - open one.
						// Deliberately checks "no screen at all" rather than naming specific screen
						// types to avoid (TrueNameGuessScreen, an opened inventory chest, etc.) - this
						// same routine sync payload arrives after EVERY duel action, and anything that
						// opens its own screen for a moment (the true name prompt, the Inventory card's
						// chest view) would otherwise get silently stomped the instant the next sync
						// packet arrives, since by definition the current screen isn't a MindDuelScreen
						// while one of those is open. The isActive() check covers the specific case
						// where control just closed the screen to null and this packet arrives right
						// after - screen == null would otherwise still let this branch reopen it.
						context.client().setScreen(new MindDuelScreen(json));
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(TeamMindDuelSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					String json = payload.duelJson();
					if (context.client().screen instanceof TeamMindDuelScreen teamScreen) {
						teamScreen.refresh(json);
					} else if (!json.isBlank() && !(context.client().screen instanceof MindDuelScreen)) {
						context.client().setScreen(new TeamMindDuelScreen(json));
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(ContactBeamPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (payload.canceled()) {
						ContactBeamState.cancel();
					} else {
						// adjustDuration(), not start() - this same payload is also used
						// when hastening shortens the remaining time mid-reach, and
						// calling start() again would reset progress to 0 and make the
						// beam visibly snap back to the beginning.
						ContactBeamState.adjustDuration(payload.targetEntityId(), payload.durationTicks());
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(MindControlStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> MindControlClientState.update(payload.targetEntityId(), payload.active())));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.ParticleSpawnPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.dragonspeech.client.fx.ClientParticleSpawner.spawn(payload)));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.SpellBodyVfxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.dragonspeech.client.fx.SpellBodyVfxState.accept(payload)));

		ClientPlayNetworking.registerGlobalReceiver(com.dragonspeech.network.PossessionStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> com.dragonspeech.client.mind.PossessionClientState.update(payload.mobEntityId(), payload.active())));

		// Config GUI (see ConfigScreen) - server's answer to a ConfigRequestPayload, or an unsolicited
		// re-broadcast after another admin's edit (see DragonSpeechNetworking.broadcastConfigSyncToAdmins).
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					ServerConfigClientCache.update(payload.configJson());
					if (context.client().screen instanceof ConfigScreen configScreen) {
						configScreen.onServerSyncReceived();
					} else if (context.client().screen instanceof com.dragonspeech.client.config.SentienceEditorScreen sentienceScreen) {
						sentienceScreen.onServerSyncReceived();
					} else if (context.client().screen instanceof com.dragonspeech.client.config.DifficultyTuningScreen tuningScreen) {
						tuningScreen.onServerSyncReceived();
					}
				}));

		// Clears any server's config data from the cache the moment we leave it, so opening the config
		// GUI on the Title Screen (or a different server/world) right after can't briefly show stale
		// permissions/values from wherever we were connected before.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> ServerConfigClientCache.clear());

		// Client-only config command. IMPORTANT: do NOT register a client command named
		// "dragonspeech" here. Fabric's client command dispatcher is evaluated before the
		// server dispatcher; owning that root causes every server-side command such as
		// /dragonspeech grantall, /dragonspeech revealwow and /dragonspeech summon to be
		// rejected locally at position 13 before the packet ever reaches the integrated/
		// dedicated server. Keep the client GUI on its own non-conflicting root instead.
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("dragonspeechconfig")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							client.setScreen(new ConfigScreen(client.screen));
							return 1;
						})));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openCastingGridKey.consumeClick()) {
				client.setScreen(new SpellConstructionScreen());
			}
			while (openGuessKey.consumeClick()) {
				client.setScreen(new GuessScreen());
			}
			while (recastKey.consumeClick()) {
				String last = LastSpellCache.get();
				if (last != null) {
					ClientPlayNetworking.send(new CastGridSubmitPayload(last));
				}
			}
			while (reachOutKey.consumeClick()) {
				if (MindControlClientState.isActive()) {
					// While controlling, the same key that started this all reopens the
					// menu instead of attempting a brand new reach - there's no other
					// good way back to it once the screen's been closed for possession.
					String cachedJson = MindControlClientState.lastDuelJson();
					if (cachedJson != null && !cachedJson.isBlank()) {
						client.setScreen(new com.dragonspeech.client.mind.MindDuelScreen(cachedJson));
					}
					continue;
				}
				Entity reachTarget = findReachTarget(client);
				if (reachTarget != null) {
					ClientPlayNetworking.send(new ReachOutPayload(reachTarget.getId()));
				} else if (client.player != null) {
					client.player.displayClientMessage(
							net.minecraft.network.chat.Component.literal("There is no mind nearby in the direction you're facing."), true);
				}
			}
			com.dragonspeech.client.fx.SpellBodyVfxState.tick();
			ContactBeamState.onClientTick();
			if (ContactBeamState.isActive() && reachOutKey.isDown() && hastenTickCounter++ % 5 == 0) {
				ClientPlayNetworking.send(new HastenContactPayload());
			}
		});
	}

	private static List<KnownWordsClientCache.ClientWordEntry> parse(String json) {
		List<KnownWordsClientCache.ClientWordEntry> result = new ArrayList<>();
		try {
			JsonArray array = JsonParser.parseString(json).getAsJsonArray();
			for (JsonElement element : array) {
				var obj = element.getAsJsonObject();
				result.add(new KnownWordsClientCache.ClientWordEntry(
						obj.get("id").getAsString(),
						obj.get("true_name").getAsString(),
						obj.get("meaning").getAsString(),
						obj.get("category").getAsString(),
						obj.get("domain").getAsString(),
						obj.get("favorited").getAsBoolean(),
						obj.get("discovery_method").getAsString(),
						obj.get("precision").getAsFloat(),
						obj.has("effect_handler") ? obj.get("effect_handler").getAsString() : null
				));
			}
		} catch (Exception ignored) {
			// Malformed sync payload - keep whatever the cache already had rather than clearing it out.
		}
		return result;
	}

	/**
	 * Finds the best entity to reach toward - deliberately NOT vanilla's
	 * hitResult/crosshair pick, which only reaches a few blocks and needs
	 * near-pixel-perfect aim. A mental reach is meant to work at a real
	 * distance without needing to be lined up exactly - this checks every
	 * living entity within REACH_RANGE, keeping only those roughly in
	 * front of the player (within REACH_CONE_COSINE of the look
	 * direction), and picks whichever is closest to the exact look ray
	 * rather than simply the nearest overall.
	 */
	private static final double REACH_RANGE = 48.0;
	private static final double REACH_CONE_COSINE = 0.5; // a full 120-degree cone around the look direction - generous, not "aimed"

	private static Entity findReachTarget(Minecraft client) {
		if (client.player == null || client.level == null) {
			return null;
		}
		Vec3 eyePos = client.player.getEyePosition(1.0f);
		Vec3 look = client.player.getLookAngle().normalize();

		Entity best = null;
		double bestCosine = -1.0;

		for (LivingEntity candidate : client.level.getEntitiesOfClass(LivingEntity.class,
				client.player.getBoundingBox().inflate(REACH_RANGE), e -> e != client.player && e.isAlive())) {
			Vec3 toCandidate = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eyePos);
			double along = toCandidate.dot(look);
			if (along <= 0 || along > REACH_RANGE) {
				continue;
			}
			double cosine = toCandidate.normalize().dot(look);
			if (cosine < REACH_CONE_COSINE) {
				continue;
			}
			// Highest cosine wins - "closest to the crosshair" is how
			// directly aligned an entity is with where you're looking,
			// not how many blocks away its line happens to pass from the
			// look ray (which unfairly favors distant, barely-in-view
			// entities over closer, well-aimed ones at certain angles).
			if (cosine > bestCosine) {
				bestCosine = cosine;
				best = candidate;
			}
		}
		return best;
	}
}