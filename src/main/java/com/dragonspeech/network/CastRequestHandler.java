package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.cast.CastExecutor;
import com.dragonspeech.cast.CastOutcome;
import com.dragonspeech.channel.ChannelManager;
import com.dragonspeech.effect.EffectHandler;
import com.dragonspeech.effect.EffectHandlerRegistry;
import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.effect.EffectTarget;
import com.dragonspeech.effect.KineticDirections;
import com.dragonspeech.effect.PreparedCast;
import com.dragonspeech.effect.SpellCastResolver;
import com.dragonspeech.effect.TargetResolver;
import com.dragonspeech.growth.AttunementService;
import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.stamina.DrainResolver;
import com.dragonspeech.word.WordRegistry;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.ward.WardService;
import com.dragonspeech.ward.WardType;
import com.dragonspeech.word.Domain;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Turns spell input into an actual cast. Two entry points feed one shared
 * pipeline: handle() for grid submissions (untrusted JSON, every word
 * re-verified against server-side vocabulary) and handleComposition() for
 * anything that already has a verified composition (chat-casting builds
 * its compositions only from words the player provably knows, so it
 * enters here too). Keeping one pipeline means control words, wards,
 * self-targeting, attunement, and channeling all behave identically no
 * matter how a spell was spoken.
 *
 * TARGETLESS CASTING ("marklaust"): a sentence containing a targetless
 * word never fails for lack of a mark. Instead of requiring something
 * under the crosshair, the cast resolves to an OfDirection target - from
 * the caster's eyes, along the combined spoken direction words (frama /
 * uppa / nidra / aftana) or the gaze if none were spoken. Handlers that
 * declare TargetKind.DIRECTION (the elemental working, notably) then
 * resolve their own strike point along that line. "eldingkast marklaust
 * frama" genuinely fires forward into open air, exactly as spoken.
 */
public final class CastRequestHandler {

    private static final double MAX_TARGET_REACH = 24.0;

    private CastRequestHandler() {}

    public static void handle(ServerPlayer player, String wordListJson) {
        handleComposition(player, parseAndVerify(player, wordListJson));
    }

    public static void handleComposition(ServerPlayer player, SpellComposition composition) {
        boolean wordOfWordsPresent = composition.words().stream()
            .anyMatch(com.dragonspeech.wow.WordOfWordsKnowledge::isDynamicWord);

        // Ordinary Word-created anti-magic can stop ordinary spells, but it cannot prevent
        // the Word of Words itself from opening or authorizing a sentence.  Otherwise a
        // player could use the Word to create a prohibition that also permanently locks
        // out the only authority capable of removing that prohibition.
        if (!wordOfWordsPresent && com.dragonspeech.wow.WordOfWordsHaltManager.isCastingSuppressed(player)) {
            player.sendSystemMessage(Component.literal("The Word of Words forbids ordinary magic here. The working cannot begin."));
            return;
        }
        if (com.dragonspeech.mind.MindSilence.isSilenced(player.getUUID(), player.level().getGameTime()) && !wordOfWordsPresent) {
            player.sendSystemMessage(Component.literal("The words will not come. Something still holds your mind quiet."));
            return;
        }

        if (wordOfWordsPresent && !com.dragonspeech.wow.WordOfWordsKnowledge.knows(player)) {
            player.sendSystemMessage(Component.literal("The Word no longer belongs to your memory."));
            return;
        }
        if (wordOfWordsPresent && composition.words().size() == 1) {
            com.dragonspeech.wow.WordOfWordsEffect.attempt(player);
            return;
        }

        boolean hasVerb = !composition.wordsOf(WordCategory.VERB).isEmpty();

        boolean hasBinding = !composition.wordsOf(WordCategory.BINDING).isEmpty();

        // CONTROL + BINDING with no verb: "cease the ward," not "stop a
        // channel" - the caster lowers their own wards of that word's type.
        if (!hasVerb && composition.hasControlWord() && hasBinding) {
            dispelOwnWards(player, composition);
            return;
        }

        // A CONTROL word with no verb means "stop," not "cast."
        if (!hasVerb && composition.hasControlWord()) {
            boolean stopped = ChannelManager.stop(player,
                    "The word falls silent; the working ceases, its cost settled where it stands.");
            if (!stopped) {
                player.sendSystemMessage(Component.literal("There is nothing held to stop."));
            }
            return;
        }

        // A BINDING word with no verb means "raise a ward," not "cast."
        if (!hasVerb && hasBinding) {
            placeWard(player, composition);
            return;
        }

        if (!hasVerb) {
            player.sendSystemMessage(Component.literal("A spell needs a verb."));
            return;
        }

        // Self-targeting spells (e.g. charging a held gem) are worked on
        // the caster themself - no look-target needed.
        Optional<EffectHandler> handlerPeek = composition.wordsOf(WordCategory.VERB).stream().findFirst()
                .flatMap(Word::effectHandlerId)
                .flatMap(EffectHandlerRegistry::get);
        boolean selfTargeting = handlerPeek.map(EffectHandler::selfTargeting).orElse(false);
        // varnbinda shares the barrier handler with personal skjoldr, but semantically names a
        // boundary placed in the world. It must therefore keep the looked-at mark/direction instead
        // of being forcibly rewritten to the caster by BarrierEffectHandler.selfTargeting().
        if (composition.occurrencesOf("varnbinda") > 0) selfTargeting = false;
        // "sprengja ... med varn" means the barrier itself is the source/anchor of the burst.
        // It therefore does not need some unrelated entity under the crosshair just to satisfy
        // ordinary target resolution; BurstEngine resolves the actual owned barrier.
        if (composition.occurrencesOf("sprengja") > 0
                && composition.occurrencesOf("med") > 0
                && composition.occurrencesOf("varn") > 0) {
            selfTargeting = true;
        }

        // An area-scope word ("umhverf") widens the net from "the thing I
        // am looking at" to "everything within its radius of me." A
        // self-scope word ("sjalfan") turns the working on the speaker.
        float areaRadius = composition.scopeWord().map(Word::scopeRadius).orElse(0f);
        boolean scopeSelf = composition.scopeWord().map(Word::scopeSelf).orElse(false);

        // "marklaust": the sentence released itself from needing a bound
        // target - resolve a direction cast instead of failing.
        boolean targetless = composition.isTargetless();

        // "kringla": the sentence asks for a standing field rather than a
        // one-off touch - it doesn't need anyone already in range at the
        // moment of casting, since the field will keep checking for
        // itself for as long as it lasts (see TemporalFieldManager). An
        // empty area is a perfectly normal thing to cast this on.
        boolean lingering = composition.words().stream().anyMatch(w -> "kringla".equals(w.trueName()));

        List<EffectTarget> targets;
        if (selfTargeting || scopeSelf) {
            targets = List.of(new EffectTarget.OfEntity(player));
        } else if (areaRadius > 0f) {
            targets = TargetResolver.resolveArea(player, areaRadius);
            if (targets.isEmpty()) {
                if (targetless) {
                    // An empty area with marklaust still casts - the working
                    // goes out around/along the caster and the handler makes
                    // what it can of the empty reach (rings, rains, bursts).
                    targets = List.of(directionTarget(player, composition));
                } else if (lingering) {
                    // The field manages its own targets from here on -
                    // proceed with nothing pre-resolved.
                    targets = List.of();
                } else {
                    player.sendSystemMessage(Component.literal("Nothing within reach answers the word."));
                    return;
                }
            }
        } else if (targetless) {
            // marklaust always direction-casts, even if something happens to
            // be under the crosshair - "without a bound mark" means exactly
            // that, and the handler's own ray decides what actually gets hit.
            targets = List.of(directionTarget(player, composition));
        } else {
            targets = TargetResolver.resolveLookTarget(player, MAX_TARGET_REACH);
            if (targets.isEmpty()) {
                player.sendSystemMessage(Component.literal("There is nothing there to work the word upon."));
                return;
            }
        }

        // Truncate to the handler's target cap (nearest-first for area
        // scopes) rather than rejecting - an area word in a crowd works
        // on those closest, it doesn't fizzle. Cost still scales with the
        // count actually affected.
        int maxTargets = handlerPeek.map(h -> h.caps().maxTargets()).orElse(Integer.MAX_VALUE);
        if (targets.size() > maxTargets) {
            targets = targets.subList(0, maxTargets);
        }

        // "The ward to block magic doesn't seem to work... I tested it
        // with marka illr" per explicit direction - MagicWardGate is the
        // general fix: filtered out HERE, before any handler ever sees
        // the target, so this covers every effect uniformly (mark,
        // gravity_scale, lift, confuse, teleport, drain_stamina/life,
        // heal-from-an-ally, ...) rather than needing a bespoke check
        // added to each individual EffectHandler. See that class's own
        // doc for the exemption list (hurl_block, explosions, blocks)
        // and the self-cast/good-mark bypass rules.
        String effectId = handlerPeek.map(h -> h.id().getPath()).orElse("");
        targets = targets.stream()
                .filter(t -> !(t instanceof EffectTarget.OfEntity(Entity e) && e instanceof LivingEntity living
                        && com.dragonspeech.ward.MagicWardGate.isBlocked(player, living, effectId)))
                .toList();

        EffectInvocation invocation = new EffectInvocation(player, composition, targets);
        // A lingering field (or anything else that legitimately has zero
        // pre-resolved targets) is centered on the caster - distance 0,
        // not a lookup into an empty list.
        float distance = targets.isEmpty() ? 0f : TargetResolver.distanceTo(player, targets.get(0));

        Optional<Domain> domain = composition.dominantDomain();
        float attunementMultiplier = domain.map(d -> AttunementService.costMultiplier(player, d)).orElse(1.0f);
        float raceMultiplier = domain.map(d -> com.dragonspeech.race.RaceService.costMultiplier(player, d)).orElse(1.0f);
        float combinedMultiplier = attunementMultiplier * raceMultiplier;

        PreparedCast prepared = SpellCastResolver.prepare(invocation, distance, combinedMultiplier);

        if (!prepared.accepted()) {
            player.sendSystemMessage(Component.literal(prepared.rejectionReason()));
            return;
        }

        var scars = com.dragonspeech.scar.ScarAccess.get(player);

        // COST_CEILING: a lasting handicap, not a one-time refusal message
        // dressed up - the caster genuinely cannot reach past this cost anymore.
        var ceilingScar = scars.find(com.dragonspeech.scar.ScarType.COST_CEILING);
        if (ceilingScar.isPresent() && prepared.cost().finalCost() > ceilingScar.get().magnitude()) {
            player.sendSystemMessage(Component.literal(
                    "The scar you carry will not let you reach that far - the working slips from your grasp."));
            return;
        }

        // CURSED_WORD: the spell still happens, but speaking the cursed
        // word wounds the speaker too - see ScarService for how it's chosen.
        var cursedScar = scars.find(com.dragonspeech.scar.ScarType.CURSED_WORD);
        if (cursedScar.isPresent() && cursedScar.get().cursedWord().isPresent()) {
            var cursedWord = WordRegistry.get(cursedScar.get().cursedWord().get());
            if (cursedWord != null && composition.words().contains(cursedWord)) {
                player.sendSystemMessage(Component.literal("The cursed word tears at you as it leaves your lips."));
                DrainResolver.applyDrain(player, 15f);
            }
        }

        // "stodugt" per explicit direction - see SpellComposition.
        // hasContinuousModifier's own doc for the fuller reasoning. A
        // handler that's naturally channeled (channel_push) already has
        // its own real costPerTick() - use that unchanged. For any OTHER
        // handler made continuous by speaking "stodugt," there's no
        // natural per-pulse price (costPerTick() defaults to 0, i.e.
        // free forever) - derive one from the SAME one-shot cost that
        // was just validated as affordable, at ONESHOT_TO_PULSE_FRACTION
        // of it per pulse, so a short channel isn't wildly more
        // expensive than casting once, but sustaining it for many
        // pulses costs meaningfully more (on top of ChannelManager's own
        // duration surcharge, which grows the longer it's held
        // regardless). "mikla makes it run more times... litla makes it
        // run less" per explicit direction - modifierMagnitudeSum()
        // scales that per-pulse price directly: mikla (+magnitude)
        // lowers it (cheaper to sustain -> more pulses before running
        // dry), litla (-magnitude) raises it (fewer pulses) - same
        // "positive magnitude helps, negative hurts" relationship every
        // other modifier-scaled effect in this mod already has.
        if (prepared.handler().isChanneled() || composition.hasContinuousModifier()) {
            float costPerTick = prepared.handler().isChanneled()
                    ? prepared.handler().costPerTick(invocation)
                    : dragonspeech$genericPulseCost(prepared.cost().finalCost(), composition);
            costPerTick *= com.dragonspeech.wow.WordOfWordsRules.magicCostMultiplier(player.getServer());
            costPerTick *= com.dragonspeech.wow.WordOfWordsKnowledge.sentenceCostMultiplier(composition);
            ChannelManager.start(player, prepared.handler(), invocation, costPerTick);
            domain.ifPresent(d -> AttunementService.grantExperience(player, d));
            return;
        }

        CastOutcome outcome = CastExecutor.execute(prepared);
        if (outcome.status() == CastOutcome.Status.EXECUTED) {
            domain.ifPresent(d -> AttunementService.grantExperience(player, d));
        }
        player.sendSystemMessage(Component.literal(outcome.message()));
    }

    private static final float ONESHOT_TO_PULSE_FRACTION = 0.4f;

    private static float dragonspeech$genericPulseCost(float oneShotCost, SpellComposition composition) {
        float scale = 1f - Math.max(-0.8f, Math.min(0.8f, composition.modifierMagnitudeSum())) * 0.3f;
        return Math.max(0.5f, oneShotCost * ONESHOT_TO_PULSE_FRACTION * scale);
    }

    /** The marklaust target: from the caster's eyes, along the spoken directions (or the gaze). */
    private static EffectTarget directionTarget(ServerPlayer player, SpellComposition composition) {
        return TargetResolver.resolveDirection(player,
                KineticDirections.combined(player, composition.directionTags()));
    }

    /**
     * Places a self-ward keyed to the BINDING word's own ward_type - the
     * wording genuinely decides what the ward blocks, per the design.
     * Energy/charges still scale with the word's precision. Warding
     * blocks/other players/items remains a future expansion.
     */
    /** However the sentence stacks a ward, it never raises more than this many layers of one type in a single cast. */
    private static final int MAX_WARD_COPIES_PER_TYPE = 6;

    /**
     * Places a self-ward for EVERY binding word in the sentence - "verja
     * ok eldverja sjalfan" raises a projectile ward AND a fire ward from
     * one cast, "ok" doing exactly what it does everywhere else in the
     * grammar (join meanings, not split the sentence). Each ward's own
     * energy/charges still scale from that binding word's own precision.
     *
     * margfalt (or repeating it - "margfalt margfalt") stacks that many
     * LAYERS of every ward named in the sentence: "verja margfalt
     * sjalfan" raises three projectile wards, not one bigger one, so
     * WardService.absorb()'s per-hit loop genuinely has three separate
     * pools to draw from.
     *
     * afla (repeatable) pours extra stored energy into every ward this
     * sentence raises; aflbinda marks every ward this sentence raises as
     * stamina-bound, so once its stored energy runs dry it keeps working
     * by draining the caster directly instead of breaking. Both apply
     * uniformly across every ward named, same as margfalt.
     */
    /** Filters a resolved target list down to the LivingEntities within it, in order - widened from ServerPlayer-only per explicit direction ("I should be able to ward any entity unless it has a ward that wards against magic" - see WardAccess/WardService's own docs for the storage-side widening this depends on). */
    private static List<LivingEntity> dragonspeech$livingAmong(List<EffectTarget> targets) {
        List<LivingEntity> living = new java.util.ArrayList<>();
        for (EffectTarget t : targets) {
            if (t instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity livingEntity) {
                living.add(livingEntity);
            }
        }
        return living;
    }

    private static void placeWard(ServerPlayer player, SpellComposition composition) {
        List<Word> bindingWords = composition.wordsOf(WordCategory.BINDING);
        if (bindingWords.isEmpty()) {
            player.sendSystemMessage(Component.literal("A ward needs a binding word."));
            return;
        }

        // FIX + explicit direction: "a ward cannot really be applied on
        // its own. It should need a scope" - naerum/sjalfan/thetta/
        // umhverf/viddum, same scope words every other spell already
        // uses. This also fixes the earlier bug where a ward always
        // landed on the caster no matter what was spoken - resolving
        // per scope type below is what actually lets "verja thetta"
        // reach an ally instead of yourself.
        Optional<Word> scopeWord = composition.scopeWord();
        if (scopeWord.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "A ward needs a direction - sjalfan for yourself, thetta for who you're looking at, "
                            + "naerum/umhverf/viddum to ward everyone nearby."));
            return;
        }

        List<LivingEntity> resolved;
        if (scopeWord.get().scopeSelf()) {
            resolved = List.of(player);
        } else if (scopeWord.get().scopeRadius() > 0f) {
            resolved = dragonspeech$livingAmong(TargetResolver.resolveArea(player, scopeWord.get().scopeRadius()));
            if (resolved.isEmpty()) {
                player.sendSystemMessage(Component.literal("Nobody else is close enough to ward."));
                return;
            }
        } else {
            // "thetta" - the single thing in front of you.
            List<LivingEntity> looked = dragonspeech$livingAmong(TargetResolver.resolveLookTarget(player, 12.0));
            if (looked.isEmpty()) {
                player.sendSystemMessage(Component.literal("You aren't looking at anyone you can ward."));
                return;
            }
            resolved = List.of(looked.get(0));
        }

        // "I should be able to ward any entity unless it has a ward
        // that wards against magic" per explicit direction - placing a
        // ward on someone else is itself "magic affecting them
        // directly," so it's gated by their OWN magic ward the same way
        // any other direct effect now is (self always exempt, since
        // `resolved` only ever contains the caster in the self-scope
        // case above).
        List<LivingEntity> recipients = resolved.stream()
                .filter(r -> r == player || !com.dragonspeech.ward.MagicWardGate.isBlocked(player, r, "verja"))
                .toList();
        if (recipients.isEmpty()) {
            player.sendSystemMessage(Component.literal("A ward of magic turns your warding aside."));
            return;
        }

        // margfalt: how many layers of EACH named ward this sentence
        // raises. Spoken once, its own repeat_count (3) sets the layer
        // count; repeating margfalt itself adds that same flat bonus
        // again per utterance, at RepetitionCost's escalating price -
        // more stacking is available, it's just not free to reach for.
        int margfaltSpoken = composition.occurrencesOf("margfalt");
        int copies = 1;
        float stackingCostMultiplier = 1f;
        if (margfaltSpoken > 0) {
            int perUtteranceBonus = composition.words().stream()
                    .filter(w -> "margfalt".equals(w.trueName())).findFirst()
                    .map(w -> Math.max(0, w.repeatCount() - 1))
                    .orElse(2);
            copies = Math.min(MAX_WARD_COPIES_PER_TYPE, 1 + margfaltSpoken * perUtteranceBonus);
            stackingCostMultiplier = com.dragonspeech.spell.RepetitionCost.multiplier(margfaltSpoken);
        }

        // afla: each utterance pours in a flat bonus of stored energy;
        // the stamina price for that bonus escalates with how many times
        // it was said, same curve as margfalt's own stacking.
        int aflaSpoken = composition.occurrencesOf("afla");
        float aflaCostMultiplier = aflaSpoken > 0 ? com.dragonspeech.spell.RepetitionCost.multiplier(aflaSpoken) : 0f;

        // aflbinda: not repeatable in the stacking sense - a ward is
        // either bound to the caster's own strength or it isn't.
        boolean staminaBound = composition.occurrencesOf("aflbinda") > 0;

        // litla/mikla (or any other MODIFIER word) scale the ward's own
        // durability up or down, same modifierMagnitudeSum() every
        // other spell already reads to scale its effect - "litla for a
        // smaller durability... mikla for a higher durability" per
        // explicit direction. Clamped so a heavily-modified ward can't
        // go negative or absurd.
        float magnitudeScale = 1f + Math.max(-0.8f, Math.min(1.5f, composition.modifierMagnitudeSum()));

        float totalCost = 0f;
        List<String> raisedTypeNames = new java.util.ArrayList<>();

        for (Word bindingWord : bindingWords) {
            WardType type = bindingWord.wardType().orElse(WardType.PROJECTILE);
            float precision = bindingWord.precision();

            float baseEnergy = (10f + (precision * 30f)) * magnitudeScale;
            int baseCharges = 1 + Math.round(precision * 2f);
            float baseCost = 8f + (baseEnergy * 0.4f);

            // afla's bonus scales off the ward's own base energy, so
            // empowering a precise ward (seidverja) is worth more than
            // empowering a crude one - same relationship precision has
            // everywhere else in this mod.
            float aflaBonusEnergy = aflaSpoken * (baseEnergy * 0.5f);
            float aflaCost = aflaSpoken > 0 ? (baseEnergy * 0.5f) * aflaCostMultiplier : 0f;

            float wardEnergy = baseEnergy + aflaBonusEnergy;
            float perCopyCost = baseCost + aflaCost;

            for (LivingEntity recipient : recipients) {
                for (int i = 0; i < copies; i++) {
                    WardService.place(recipient, type, wardEnergy, baseCharges, true, staminaBound);
                }
            }

            // Warding a crowd costs more than warding one person -
            // otherwise umhverf/viddum would be strictly better than
            // sjalfan/thetta for the same price.
            totalCost += perCopyCost * copies * stackingCostMultiplier * recipients.size();
            if (!raisedTypeNames.contains(type.getSerializedName())) {
                raisedTypeNames.add(type.getSerializedName());
            }
        }

        totalCost *= com.dragonspeech.wow.WordOfWordsRules.magicCostMultiplier(player.getServer());
        totalCost *= com.dragonspeech.wow.WordOfWordsKnowledge.sentenceCostMultiplier(composition);
        DrainResolver.applyDrain(player, totalCost);

        boolean wardedOthers = !(recipients.size() == 1 && recipients.get(0) == player);
        StringBuilder message = new StringBuilder();
        message.append(copies > 1 ? (copies + " layers of a ward") : "A ward");
        message.append(" against ").append(String.join(" and ", raisedTypeNames));
        if (!wardedOthers) {
            message.append(copies > 1 ? " settle around you" : " settles around you");
        } else if (recipients.size() == 1) {
            message.append(copies > 1 ? " settle around " : " settles around ").append(recipients.get(0).getName().getString());
        } else {
            message.append(copies > 1 ? " settle around " : " settles around ").append(recipients.size()).append(" nearby ally/allies");
        }
        if (aflaSpoken > 0) {
            message.append(", swollen with poured-in strength");
        }
        if (staminaBound) {
            message.append(", bound to your own stamina");
        }
        message.append(".");
        player.sendSystemMessage(Component.literal(message.toString()));

        if (wardedOthers) {
            Component recipientMessage = Component.literal(player.getGameProfile().getName()
                    + (copies > 1 ? " wards you with " + copies + " layers against " : " wards you against ")
                    + String.join(" and ", raisedTypeNames) + ".");
            for (LivingEntity recipient : recipients) {
                if (recipient != player && recipient instanceof ServerPlayer recipientPlayer) {
                    recipientPlayer.sendSystemMessage(recipientMessage);
                }
            }
        }
    }

    /** Lowers the caster's own wards matching EVERY binding word's type in the sentence - free (releasing a binding costs nothing; holding one never did either). "verja ok eldverja letta" drops both at once. */
    private static void dispelOwnWards(ServerPlayer player, SpellComposition composition) {
        java.util.Set<WardType> types = composition.wordsOf(WordCategory.BINDING).stream()
                .map(w -> w.wardType().orElse(WardType.PROJECTILE))
                .collect(java.util.stream.Collectors.toSet());
        if (types.isEmpty()) {
            types = java.util.Set.of(WardType.PROJECTILE);
        }

        var wards = com.dragonspeech.ward.WardAccess.get(player);
        int removed = 0;
        for (var ward : List.copyOf(wards.wards())) {
            if (types.contains(ward.type()) && ward.casterId().equals(player.getUUID())) {
                wards = wards.withRemoved(ward.id());
                removed++;
            }
        }
        com.dragonspeech.ward.WardAccess.set(player, wards);
        WardService.pushSync(player);

        player.sendSystemMessage(Component.literal(removed > 0
                ? (removed > 1 ? "You release the bindings, and the wards fade." : "You release the binding, and the ward fades.")
                : "No such ward holds around you."));
    }

    /**
     * Parses a JSON array of word ids into a composition, silently
     * dropping anything the player doesn't server-verifiably know. This
     * is the format change that removes the old fixed-slot limit: a
     * spell is now an ordered word list of ANY length - the blueprint
     * truly is unlimited, exactly as the design always intended (length
     * never appears in the cost formula, only precision and task size).
     */
    private static SpellComposition parseAndVerify(ServerPlayer player, String wordListJson) {
        List<Word> words = new java.util.ArrayList<>();
        try {
            for (JsonElement element : JsonParser.parseString(wordListJson).getAsJsonArray()) {
                try {
                    ResourceLocation wordId = ResourceLocation.parse(element.getAsString());
                    if (!VocabularyService.knowsWord(player, wordId)) {
                        DragonSpeech.LOGGER.warn("[DragonSpeech] {} submitted an unknown word '{}' - ignoring.",
                                player.getGameProfile().getName(), wordId);
                        continue;
                    }
                    if (com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID.equals(wordId)) {
                        words.add(com.dragonspeech.wow.WordOfWordsKnowledge.dynamicWord(player.getServer()));
                        continue;
                    }
                    Word word = com.dragonspeech.word.WordRegistry.get(wordId);
                    if (word != null) {
                        words.add(word);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Malformed ResourceLocation - skip that entry.
                }
            }
        } catch (Exception e) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Malformed cast submission from {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
        }
        return new SpellComposition(words);
    }
}