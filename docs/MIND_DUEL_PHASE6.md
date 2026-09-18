# Phase 6 — Mind Dueling (status)

This pass builds the server-side foundation of the mind-duel system:
sentience tiers, per-duel Focus/Stamina/Willpower/Discipline pools, the
skill-gated Contact phase, Defense Breach, Struggle, Occupied Mind, and
True Name Domination, plus the physical-interruption hook and true names
themselves. It does **not** yet build the five polished per-phase client
screens from the mockups — see "What's still open" below for exactly
what that would take.

## The one rule everything else follows

**"Reaching out with your mind" is a trained SKILL, not a spoken word.**
`PlayerSkills.canReachOut` gates `ContactResolver.attempt()` directly, as
a boolean. Learning `hugleita` flips that flag permanently (see
`SkillHooks`); it does not hand the player a new verb to assemble into a
casting-grid sentence. The five mind words already in the dictionary
(`hugsnert`, `hugleita`, `hugvarna`, `hugrista`, `hugbinda`) still exist
and can still be *cast* normally for whatever other listed effect they
have — but the mind-duel *abilities* they unlock live entirely in
`PlayerSkills`, checked once, not re-derived from a sentence every time.

| Word | Unlocks |
|---|---|
| hugsnert | `canSenseMinds` — passive detection only (not wired to a UI yet) |
| hugleita | `canReachOut` — the entire ability to attempt Contact |
| hugvarna | `canWallMind` — flat fortitude/willpower bonus vs. incoming duels |
| hugrista | `canReadThoughts` — reserved for a future "read thoughts" resolver (Phase 6, design-note idea 6) |
| hugbinda | `canBindTotally` — required to ever use True Name Domination |

## Phase flow (matches the design notes' recap bar)

```
CONTACT -> DEFENSE_BREACH -> STRUGGLE -> OCCUPIED_MIND -> (TRUE_NAME_DOMINATION)
                                                 \-> ENDED (from any phase)
```

- **Contact** (`ContactResolver`): skill check, no `ActiveMindDuel` exists
  yet. Attacker power = flat floor + MIND attunement. Defender
  resistance = `MindFortitudeService.fortitude()` — trained for players
  (MIND attunement + hugvarna), tiered for mobs (`SentienceTier`). On
  success a duel is created already *in* Defense Breach.
- **Defense Breach** (`MindDuelActionService.resolveBreach`): attacker
  Probe/Pressure/Pierce/Feint vs. defender Reinforce/Redirect/Seal
  Mind/Calm, spending Stamina and moving `breachIntegrity` 100 → 0.
- **Struggle** (`resolveStruggle`): both sides share the same six actions
  (Assault/Pressure/Disrupt/Defend/Counter/Focus), draining each other's
  Focus/Stamina pools directly. Losing Focus *or* Stamina loses the
  struggle outright (matches the design notes exactly: "Reduce their
  Focus to 0 or Stamina to 0 to win").
- **Occupied Mind** (`resolveCommandPhase`): attacker issues a command,
  `ResistanceCheck.resistChance()` rolls the defender's resistance from
  Willpower vs. a difficulty number. No command *picker* UI exists yet —
  every command currently uses one flat difficulty (20) as a stand-in.
- **True Name Domination**: reachable from Struggle or Occupied Mind the
  instant `SPEAK_TRUE_NAME` succeeds, which requires `canBindTotally`
  *and* `TrueNameService.knows(attacker, defender)`. Resistance chance is
  the same formula divided by 20 (mirrors the design notes' difficulty
  table almost exactly: 20 → ~1).
- **Interruption** (`MindDuelService.onPhysicalDamage`, wired into
  `LivingEntityDamageMixin` for *any* `LivingEntity`, not just players):
  every hit taken mid-duel costs Focus (reduced by Discipline) and nudges
  `controlAdvantage`. Three interruptions in one phase, or a pool hitting
  0 from a hit, ends the duel immediately.
- **Ending** (`MindDuelService.end`): always the single choke point.
  Applies Mining Fatigue (both sides) + Confusion (loser only) as the
  "mental fatigue" cost, mirrors "winning still has a cost."

## True names

`TrueNameService` generates a plausible-sounding Ancient Language word
per player (deterministic from UUID + a generation counter), hashed with
the *exact same* `WordHashing` the vocabulary system uses — a true name
is a word, per the dictionary's own `nafn` entry, so it gets the same
"never compare or ship plaintext" discipline. `regenerate()` is wired as
the hook point for "changes when a specific aspect of personality
changes" (design notes) but nothing calls it automatically yet — that
needs a personality-tracking system this pass doesn't build.

## Mobs

`MindFortitudeService.classifyTier()` gives every `LivingEntity` a
`SentienceTier` (Warden/Enderman → CHAOTIC, Villager → SIMPLE, other
hostile mobs → INSTINCTUAL, everything else → SIMPLE floor). Addon mods
(e.g. a future dragon mod) can register `SentienceTier.DRAGON` for their
own `EntityType` via the public `MindFortitudeService.TIER_OVERRIDES` map
without touching this file.

## Interfaces that exist today

- `/mind reach <target>`, `/mind action <action>`, `/mind status`,
  `/mind disengage` — chat commands, usable by any player right now,
  with no client screen required. This is the same role `ChatCastHooks`
  plays for ordinary spellcasting — a working interface that a GUI can
  sit alongside later without becoming a second, drifting code path.
- `/dragonspeech truename|regenname|endduel <player>` — admin/debug.
- `ReachOutPayload` (C2S), `MindDuelActionPayload` (C2S),
  `MindDuelSyncPayload` (S2C, JSON) — the network payloads a real client
  screen would use; `MindDuelSyncHooks.pushSync()` already fires after
  every state change, so a screen just needs to listen for it.

## What's still open (the actual remaining Phase 6 work)

1. ~~**Client screens.**~~ **Partially done.** A single adaptive
   `MindDuelScreen` (client-side, `com.dragonspeech.client.mind`) now
   covers Defense Breach through True Name Domination with stock-widget
   bars and buttons, driven by the same `MindDuelSyncPayload` every phase
   already produces. It is NOT the fully bespoke, illustrated per-phase
   art from the mockups (mind-threads, cracking barrier, tug-of-war
   portraits, hidden-core map) - that's still a real art/UX pass on top
   of this functional version. Contact itself still has no screen; it's
   instantaneous (a keybind - `K` by default - reaching toward whatever
   entity is under the crosshair, or `/mind reach <target>`).
2. ~~**Command effects.**~~ **Done, for a first set.** `CommandEffect` +
   `CommandEffectRegistry` (mirrors `EffectHandlerRegistry`'s "fixed,
   compiled set" rule exactly) now back `ISSUE_COMMAND` with four real
   effects: `drop_weapon`, `walk_forward`, `stop_fighting`, `stun` - all
   working on any `LivingEntity`, player or mob. Picked via the new
   `param` field on `MindDuelActionPayload` / `/mind command <id>`. A
   "disable spellcasting" command is still explicitly a TODO - it needs a
   hook into `StaminaTicker`/`CastRequestHandler` that doesn't exist yet.
3. ~~**Hidden Core / Decoys / Mental Traps / Mindscape terrain**~~ **Done,
   for a first pass.** A new `HIDDEN_CORE` phase now sits between Defense
   Breach and Struggle. `MindscapeType` (7 trained "personalities" -
   Fortress, Labyrinth, Ocean Depths, Crystal Palace, Volcanic Heart,
   Void Realm, Wilderness) is persisted per player via `MindscapeService`
   exactly like `TrueNameService` persists names, and controls decoy
   count, trap frequency, and search Stamina cost. `HiddenCoreState`
   generates a true core + decoys + empty thoughts (`MindNode`); the
   attacker `SEARCH_SCAN`s for clues and `SEARCH_PROBE`s specific nodes,
   the defender `SHIFT_CORE`s or `CREATE_DECOY`s to make the search
   harder. Probing a decoy can spring one of five `MentalTrap`s
   (Thorn Wall, Memory Flood, Fear Visage, Blinding Light, Endless Maze).
   `SentienceTier.DRAGON` skips this phase entirely, straight to
   Struggle - "no walls, no maze, just scale" (idea 14). Client support:
   the `MindDuelScreen` renders a mindscape name, search-progress bar,
   a small node map, and per-node Probe buttons. Not yet built: a real
   "prepare traps in advance" UI for the defender (traps are currently
   picked at random rather than chosen ahead of time), and a deliberate
   "Custom Mind Training" system to let a player CHOOSE their mindscape
   rather than have one randomly assigned the first time they're searched.
4. ~~**Reading thoughts**~~ **Done, for a first pass.** `MindThoughtReader`
   consumes `canReadThoughts` (hugrista) as a Struggle-phase action
   (`READ_THOUGHTS`) that costs Stamina and returns one flavor snippet
   (health, current target, held item, whether an effect is active) -
   deliberately a different axis from control, matching the design
   notes' framing. It's a flat info-reveal, not the "risk vs. reward,
   detection meter" system from the mockups (image 7) - that meter is a
   real follow-up, not just wiring.
5. ~~**Multi-person duels**~~ **Done, for a first pass, as a deliberately
   separate system, now with a real client screen and consent-based
   linking.** `MindLink`/`MindLinkManager` let players form a standing
   bond - `/mind link invite <players...>` sends invites, `/mind link
   accept <inviter>` is the only way to actually join (`PendingLinkInvites`
   tracks the handshake; `MindLinkManager.join()` is the consent-checked
   path, `form()` remains as an instant/admin-testing bulk path). The
   moment anyone reaches out to a linked player, `ContactResolver`
   redirects into a `TeamMindDuel` instead of a plain `ActiveMindDuel` -
   every member is drawn in as a co-defender, and contact resistance
   scales with team size. `TeamMindDuelService` implements the design
   notes' most fully-specified multi-person scenario (image 8):
   `ISOLATE`/`OVERWHELM`/`DISRUPT_LINKS`/`FALSE_TARGETS`/
   `WEAR_THEM_DOWN` for the attacker; `REINFORCE`/`PROTECT`/`REVIVE`/
   `FOCUS_BURST` for the team, plus a shared Link Strength meter.
   `TeamMindDuelScreen` (client) now gives this a real roster GUI -
   attacker sees every defender with per-target attack buttons, a
   defender sees Link Strength, their own bars, and per-ally
   coordination buttons - synced via `TeamMindDuelSyncPayload`, the same
   pattern as the 1v1 screen. Action-bar text status still fires
   alongside it for players without the screen open. This is
   deliberately NOT a generalization of the 1v1 five-phase system - one
   shared "battle" phase, matching the one multi-person example the
   design notes actually work through in detail; no Breach/Hidden
   Core/Occupied Mind/True Name equivalent exists for teams yet.
6. ~~**Personality-driven true name changes**~~ **Done, via a concrete
   proxy.** `PersonalityShiftService` tracks each player's DOMINANT
   Attunement domain (whichever they've practiced most) as the closest
   measurable stand-in this codebase has for "personality." When that
   dominant domain changes - a fire-caster who becomes a mind-caster,
   say - `TrueNameService.regenerate()` fires automatically and anyone
   who knew the old name is told, in `TrueNameService.knows()`, that it
   no longer works. Hooked into `AttunementService.grantExperience()`,
   so it's checked on every cast but only ever fires on a genuine change.
7. **Dragon minds** (idea 14) - `SentienceTier.DRAGON` exists as numbers;
   the "no walls, just an infinite living universe" *presentation* is a
   client-screen concern that the current stock-widget screen doesn't
   attempt - it renders identically for every opponent regardless of tier.
8. ~~**Custom Mind Training**~~ **Done, for a first pass.** `MindTrainingService`
   gives Mindscape two real axes: passive MASTERY of your current
   mindscape (grows every time you successfully defend a duel with it,
   feeding a real Fortitude/Willpower bonus in `MindFortitudeService`),
   and deliberate RETRAINING toward a new one (`/mind train <mindscape>`,
   repeated Stamina-costing sessions scaled by MIND attunement). Not yet
   built: any in-game way to actually SEE this without the chat command
   (`/mind mindscape`) - a proper menu/ritual UI would fit here later.
9. **Disable spellcasting command** - also done this round.
   `MindSilence` is a new, minimal hook `CastRequestHandler` checks
   before resolving any composition; the `"silence"` `CommandEffect`
   uses it to make Occupied Mind's command list genuinely match the
   design notes' "Disable spellcasting" idea instead of skipping it.
10. ~~**Traps in advance / visual polish**~~ **Done, for a first pass.**
    `HiddenCoreState` now tracks up to `MAX_PREPARED_TRAPS` (3) defender-
    placed traps via `prepareTrap()`/`consumeTrapAt()` - a new
    `PREPARE_TRAP` action (`/mind trap <id>` or the screen's "Trap #N"
    buttons) lets the defender choose WHERE a trap sits (the specific
    `MentalTrap` type is still rolled randomly at prepare-time, not
    chosen - a "pick the trap type" UI is the natural next step). A
    prepared trap fires with certainty the instant that node is probed,
    regardless of the node's own kind, layered on top of the existing
    random DECOY trap-chance roll. This required fixing a real
    information-leak risk: the defender now sees their OWN Hidden Core
    map fully (every node's true kind, since it's not fog-of-war for the
    mind's owner) via `MindDuelSyncHooks`, while the attacker still only
    ever sees revealed nodes and never which ones are trapped.
    Visual polish: both `MindDuelScreen` and `TeamMindDuelScreen` bars
    now have a glossy top-highlight strip and pulse toward red below
    20% (a genuine "running out" cue, not just a static color swap);
    Hidden Core has per-mindscape accent colors matching the design
    notes' own terrain palette (image 4); the defender's node map shows
    true kinds with a gold outline on trapped nodes and dims anything
    the attacker has already found.
11. ~~**Fully bespoke per-phase art**~~ **Done, as far as is possible
    without actual texture/image assets.** There are no PNGs or custom
    textures anywhere in this system - `GuiGraphics.fill()` only draws
    axis-aligned rectangles - so a new shared toolkit, `MindVisuals`
    (circles, rings, and lines built from scanline rectangle fills, plus
    a stable per-index pseudo-random and a breathing pulse helper), now
    backs real procedural centerpiece visuals instead of flat bars alone:
      - **Defense Breach**: a ring "barrier" whose thickness reflects
        remaining integrity, with cracks radiating outward (golden-angle
        spaced, so earlier cracks stay put as new ones appear rather
        than jittering every frame).
      - **Struggle / Occupied Mind / True Name Domination**: two glowing
        "presence" circles connected by a tug-of-war beam that leans and
        recolors toward whoever currently holds control.
      - **Hidden Core**: nodes scattered around an ellipse instead of a
        flat grid, with a slow breathing glow on the true core once
        revealed, and a gold ring marking the defender's own prepared
        traps.
      - **Team duels**: a radial "link web" - a central hub with one
        spoke per linked defender, each spoke's brightness tied to that
        member's Focus and the hub's pulse tied to overall Link Strength.
    The numeric bars/percentages are kept alongside every one of these -
    precision plus atmosphere, not one instead of the other. This is
    genuinely NOT hand-illustrated art (no artist, no image pipeline,
    none exists for this mod) - it's real, load-bearing procedural
    visual design built entirely from primitives, which is the honest
    ceiling of what's achievable without commissioning actual textures.

## Texture assets and sentient-mob AI (this pass)

**Textures.** All user-provided art is resized to its exact in-game
render footprint (see the Python resize step - every texture is drawn
1:1, no runtime scaling, to avoid the version-fragile scaling-blit
overloads) and wired into `MindTextures` (client): the **blue** barrier
set is used for Defense Breach per explicit preference; the **gold**
barrier is repurposed as a pulsing glow specifically during True Name
Domination rather than discarded. All 16 action icons render as small
badges over their matching buttons (`MindDuelScreen`/`TeamMindDuelScreen`
both call `addIcon()` right after building each button). Hidden Core's
node scatter uses the real dormant/decoy/true-core orbs plus the thorn
trap overlay. Mindscape backgrounds render as a dimmed backdrop during
Hidden Core. The panel and Focus/Stamina bar-frame textures are resized
and sitting in the resource tree but NOT yet wired into layout - the
existing bar/panel sizes didn't have a safe, verifiable way to host them
without live-testing exact padding, and this pass had no way to
compile-test, let alone visually verify. Send a screenshot once it's
running and it's a quick follow-up.

**Sentient mob AI (`MobMindCombatAI`).** Every resolver in this system
(`MindDuelActionService`, `TeamMindDuelService`, `ContactResolver`) was
already actor-agnostic - none of them ever checked "is this a
ServerPlayer," only "does this UUID match the attacker/defender." The
only missing piece was something to actually call them on a mob's
behalf, which is what this class is. Gated on the new
`SentienceTier.canActInDuel()` (TRAINED/DISCIPLINED/CHAOTIC/DRAGON) -
villagers and animals are reachable and have real stats but don't get a
"brain," while Wardens and Endermen (both CHAOTIC out of the box, plus
whatever an addon registers via `MindFortitudeService.TIER_OVERRIDES`)
now:
- **Defend for themselves** when a player reaches them - picking
  Seal Mind/Reinforce/Calm in Defense Breach, preparing traps or
  shifting the core in Hidden Core, Assault/Defend/Restore Focus in
  Struggle based on simple Focus/Stamina heuristics.
- **Keep fighting as an attacker** once they've started a duel,
  including issuing real registered commands (not just vague ones) once
  they reach Occupied Mind, and acting as the attacker in a team duel
  against a linked group.
- **Initiate Contact on their own** - every ~5 seconds, a sentient-tier
  hostile mob within 10 blocks of a player has a small chance to reach
  out unprompted, via the new `ContactResolver.attemptAsMob()`, which
  required generalizing `ContactResolver`'s internals (attacker power,
  messaging, gating) to work for either a player or a mob through the
  same core resolution path rather than duplicating it.
This is deliberately conservative by default (most mobs still can't do
this at all) and heuristic rather than a full planner - a mob won't
out-think a skilled player, but it will no longer just sit there.

## Contact is no longer instant (this pass)

A real inconsistency was caught: Level 1 Contact was resolving the
instant the keybind was pressed, with no travel-time visual at all -
contradicting the same "line reaching toward the target" language used
for the detection-trail concept. Fixed properly, not just cosmetically:

- **`PendingContact`/`PendingContactManager`/`PendingContactTicker`** -
  Contact now takes real travel time before it resolves. Duration scales
  with the attacker's MIND attunement (skill mastery): ~3 seconds at
  zero mastery, down to ~0.5 seconds at full mastery. A dedicated
  per-tick ticker (not the once-a-second pulse everything else uses)
  checks readiness, since a hastened reach can be quite short.
- **Hastening**: holding the reach-out key while a reach is in flight
  sends `HastenContactPayload` at a throttled rate, spending Stamina
  (from the existing out-of-duel magic pool) to shorten the remaining
  time - exactly the "or you can make it go faster at the cost of
  stamina" option asked for.
- **`ContactBeamRenderer`** - a real traveling line rendered in world
  space from the player's eyes toward the target, growing as the reach
  progresses, with a soft pulse. This is genuinely new territory for
  this mod: every other visual effect here is either a GUI `Screen`
  (`GuiGraphics.blit`/`fill`) or vanilla particles (`WardRingRenderer`).
  There is no in-repo precedent for raw 3D line rendering, and no
  compiler available in this environment to verify the exact
  vertex-builder API - the single riskiest file in the project. The
  actual upload call is isolated into its own one-method file,
  `BufferUploaderCompat`, specifically so a version mismatch is a
  one-line fix in one place rather than a hunt through the renderer.
- `ContactResolver` was restructured around this: `attempt()`/
  `attemptAsMob()` now only gate and start a `PendingContact`;
  `resolveReady()` (called once travel time elapses) holds the actual
  roll + duel-creation logic that used to run inline.

Levels 2 (team) and 3 (detection) are unchanged by this pass - Level 2
still works exactly as before (Mind Link), and Level 3 remains the one
genuinely deferred piece, needing the same world-space rendering
approach as this beam, extended to multiple simultaneous lines with a
mastery-gated maximum range.

## Contact beam visual, corrected to match the reference screenshot

The first version of the beam (a thin straight `DEBUG_LINES` segment
from eye to target) was wrong on two counts, confirmed by a reference
screenshot: it needed to be a thick, glowing, gently winding ribbon that
runs near ground level before rising up the target's body - not a rigid
straight line through the air. Rebuilt properly:

- **`BeamPath`** generates the actual curve: mostly ground-level
  (interpolated between the two entities' feet, not eye height), with a
  gentle organic side-to-side "wind" (one or two soft bends, amplitude
  scaled by distance, tapered to zero at both ends so it still connects
  cleanly), then a short final rise up to about 70% of the target's
  bounding-box height where the thread visibly "connects." The wind
  pattern is seeded once per reach attempt so it stays stable frame to
  frame instead of jittering.
- **Rendering switched from a GL line primitive to a billboarded quad
  ribbon** (two passes: a wide, dim "glow" and a narrow, bright "core"),
  the same general technique vanilla Minecraft's own leash rendering
  uses for a sagging rope between two points. This also sidesteps a
  real, separate problem with the old approach: `RenderSystem.lineWidth()`
  above 1px is frequently ignored outright by modern GPU drivers in
  core-profile OpenGL, so the previous version likely wouldn't have
  rendered at any visible thickness regardless of what width was
  requested.
- Growth (the `progress` fraction from `ContactBeamState`) now truncates
  the underlying point list rather than just interpolating two
  endpoints, so the ribbon visibly grows along its own curved path
  instead of stretching a straight segment.

No terrain raycasting is done per path point (that would mean a world
query for each of ~20 points, every time the path regenerates) - the
ground-hugging look comes from interpolating between the two feet
positions rather than from querying actual terrain height, so on very
uneven ground the ribbon will occasionally clip through small bumps
rather than perfectly hugging them.

## Bug fixes and a directive reversal (this pass)

**Beam not appearing.** Found a real bug: `RenderSystem.setShaderColor`
was never explicitly reset to opaque white before drawing the ribbon.
The position_color shader multiplies each vertex's color by that global
tint, and if anything earlier in the frame left it non-white (common
for various vanilla effects), the ribbon would render with its colors
silently multiplied down to invisible regardless of what was passed to
`setColor()`. Also extended Contact's targeting: it no longer relies on
vanilla's short, exact-aim `hitResult` - a new custom raycast
(`findReachTarget` in `DragonSpeechClient`) checks a 24-block cone
around the look direction and picks whichever entity is closest to the
exact look ray, so reaching out no longer needs pixel-perfect aim from
point-blank range.

**Crack visibility was backwards.** Two compounding bugs: cracks had a
15% alpha *floor*, so all of them were faintly visible from the moment
Defense Breach started, before the attacker had touched anything - and
the mend-overlay alpha was `1 - openness`, meaning a completely fresh,
unstruck crack showed the MEND art at full opacity (reading as "already
repaired") while the crack art stayed nearly invisible. Both fixed: a
crack is now fully invisible until actually struck (openness 0 = alpha
0, no floor), and mend art now peaks in the MIDDLE of an active contest
and is zero at either extreme - it reads as "being fought over," not
"pristine." A faint always-visible ring still marks each weak point's
click location before it's struck, so the attacker isn't left guessing
where to click.

**Cards/buttons swapped back.** Per explicit clarification: bar-selection
during Breach now uses the compact BUTTON art (`smallButton`), and the
post-access command screen (Occupied Mind / True Name Domination) uses
the CARD art - reversing the previous pass's choice. The now-orphaned
`buttons/*_action.png` set (5 files) was removed; `ACTION_BUTTON_WIDTH`/
`HEIGHT`/`actionButton()` were removed from `MindTextures` along with it.

**A sixth post-access option, "confuse"**, was added to round out the
"changing emotions" category the user described (alongside drop_weapon
for items, walk_forward/stop_fighting/stun for control) - it applies
Nausea via the same `MobEffectInstance(MobEffects.CONFUSION, ...)`
pattern already proven working in three other places in this codebase
(`BacklashResolver`, `MindDuelService`, `ConfuseEffectHandler`).
