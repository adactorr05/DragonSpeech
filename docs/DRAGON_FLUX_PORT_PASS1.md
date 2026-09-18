# Dragon Flux -> Dragon Speech VFX/Ability Port — Pass 1

This pass treats Dragon Flux as a **behavior/VFX reference**, not as a fixed spell list. Dragon Speech remains sentence-driven: words resolve elements, forms, targeting and modifiers, and the renderer/gameplay engine interprets that composition.

## Implemented in this pass

### Geometry-first composed spell VFX
- Added a server -> client `SpellBodyVfxPayload` and generic `SpellBodyVfx` sender.
- Added persistent client geometry rendering for:
  - bolt
  - ray / beam
  - orb
  - burst / impact
  - ring
  - rain strike
  - cloud
  - aura
  - sigil
  - lance
  - tether
  - claw
  - shell
  - convergence
  - spiral path modifier
  - orbit path modifier
  - redirect/bend path modifier
  - wall rise
  - pillar rise
  - gale
  - updraft
  - chain/lightning arc
- Fire, Ice, Earth, Wind and Lightning use distinct geometry styles rather than only recolored vanilla particles.
- Woven spells carry an element bitmask so multiple elements can remain visibly distinct in one working.
- Existing Dragon Speech custom particles remain as secondary sparks, embers, snow, dust, impact fragments and atmosphere rather than being the entire body of the spell.

### New compositional grammar/forms
Approved vocabulary added:
- `samdraga` — draw separate parts of a working together toward one point; converge
- `thrett` — dense; compressed into little space without spreading
- `sveira` — spiral/revolve around a line or centre
- `fjotbinda` — bind a working as a flexible tether between two marks
- `vefja` — wrap/weave a working closely around a thing
- `voddr` — a long piercing point; spear/lance form
- `kral` — claw/talon; hooked cutting weapon of the hand
- `sveigja` — bend a moving path aside without stopping it
- `kringferd` — motion around a centre; orbit
- `ferdafl` — force carried by motion; momentum/inertia
- `fran` — away from; outward from the named mark
- `snara` — flexible living tendril/vine

The dictionary now contains 234 word definitions.

### New/expanded mechanics
- Added Wind as a true compiled element so Wind and Lightning can be woven into Storm workings.
- Added Lance, Tether, Claw and Shell as reusable spell forms rather than named abilities.
- `sveira` is a motion modifier, not an exclusive form, so combinations such as Rain + Spiral remain Rain while gaining spiral motion/VFX.
- Targeting modifiers now compose instead of only honoring the first targeting word.
- `thrett` narrows area while modestly concentrating impact.
- `ferdafl` transfers momentum into struck entities.
- `samdraga` produces a visible convergence stage.
- `kringferd` and `sveigja` have reusable path VFX hooks.
- Tether motion is semantic: `fjotbinda` alone connects; `til` draws toward the relevant mark; `fran` drives away. A terrain tether becomes a grapple only when movement is actually spoken.
- Earth wall/pillar spells now emit physical-looking rising construction geometry.
- Wind push/lift workings emit broad gale/updraft geometry.
- Ignite/Freeze workings now use composed fire/ice geometry instead of only basic particle feedback.
- Persistent sigils use persistent rendered geometry and refresh visibility for players entering range later.

### Advanced domains
Added separate attunement domains:
- Time
- Gravity
- Fate
- Void

Existing time, gravity and fate vocabulary was moved into the appropriate domains so mastery of ordinary Truth/Force/Binding does not automatically train these dangerous arts. The Grimoire/domain color filters were expanded accordingly.

Specialized Fate/Void vocabulary beyond the already-approved batch was deliberately **not** added yet.

### Bonded dragon summon command
`/dragonspeech summon <breed>` — normal unbonded/admin spawn

`/dragonspeech summon <breed> nobond` — explicit unbonded test spawn

`/dragonspeech summon <breed> bond` — spawn the requested breed in front of the player and establish it as that player's bonded dragon

The bond path:
- rejects a second living bond
- respects the existing rebond-after-death world setting
- ignores stale bond UUIDs that no longer point to a living bonded dragon
- records the new bond through Dragon Speech's own player bond data

## Intentionally not rebuilt yet
- Word of Words GUI/system
- world-generated 7-15 letter Word of Words
- true local time reversal / history overwrite
- deeper Fate probability engine
- deeper Void/nullification engine
- advanced plant-growth vocabulary/mechanics
- Soul, Dream and Illusion ports

## Word of Words direction agreed for later
The old phrase/op-grant behavior should be replaced by one world-specific generated word. Speaking it will open a custom, server-authoritative interface with scoped operations such as Remove, Add, Change, Halt, Time, Reveal, Bind and Restore. Operations still pay stamina based on what reality is being asked to do; the word provides privileged control/precision rather than creative-mode magic.

## Validation performed in this environment
- 234 JSON word files parsed successfully.
- All word true names are unique.
- All dictionary domains/elements/shapes/targeting values are valid current enum values.
- All prerequisite word references resolve.
- All 12 newly approved word hashes match Dragon Speech's real salted SHA-256 algorithm.
- Changed common spell/VFX classes type-compiled against the uploaded Minecraft/Fabric mapped jars.
- Changed client geometry renderer type-compiled against the uploaded client mapped jars.
- Network registration type-compiled.
- Client initializer VFX receiver/tick integration type-compiled.
- Bonded summon command type-compiled with compile-only Brigadier/Fabric dependency stubs.

A normal Gradle/Loom build could not be run in the sandbox because the exact Gradle 9.5.1 wrapper distribution is not present locally and outbound dependency download is unavailable here. The temporary compile-only stubs used for validation are **not** part of the project/ZIP.

## Command-tree and natural-discovery correction

A follow-up audit found that Dragon Speech had been registering the same `/dragonspeech` literal root independently from several classes. That setup depended on Brigadier merging duplicate roots with different requirements, and the old generic entity spawner also competed with the dragon-breed command at `/dragonspeech summon <string>`. The command system now uses one `DragonSpeechCommandRoot`; every Dragon Speech admin/debug module contributes children to that single root.

Key command changes:
- `/dragonspeech summon <breed> [bond|nobond]` is now exclusively the dragon-breed command.
- Tab completion after `/dragonspeech summon ` lists built-in breed paths (`fire`, `forest`, `gold`, `ice`, `lightning`, `end`, `void`) and full namespaced IDs for addon breeds.
- The older generic mob/entity test command moved to `/dragonspeech summonmob <elf|elder_elf|human_mage|shade|dragon> [pos]` so it no longer conflicts with dragon breeds.
- `/dragonspeech grantall` now accepts a player collection, so selectors such as `@a` work.
- Admin/debug commands use a shared permission rule: normal permission level 2 on multiplayer/dedicated servers, while the owner of an integrated single-player world is accepted automatically for mod testing.
- Running `/dragonspeech` itself now returns a short help/status message rather than leaving an incomplete root with no feedback.

Natural word discovery was also audited. The current registry contains 234 words, all 234 use a naturally reachable discovery category (`ruin_tablet`, `ancient_text`, `mentor_npc`, or `elven_trial`), every prerequisite ID resolves, and there are no prerequisite cycles in the current data set.

The twelve words added in the Dragon Flux VFX pass (`samdraga`, `thrett`, `sveira`, `fjotbinda`, `vefja`, `voddr`, `kral`, `sveigja`, `kringferd`, `ferdafl`, `fran`, `snara`) are all `elven_trial` words. In the tablet generator, that gives them weight 0 on Worn tablets, 2 on Ancient tablets, and 6 on Primordial tablets. Generic Scholar's Fragments can contain them rarely, while Elven Trial-biased fragments heavily favor them.

The world-discovery generators were additionally hardened so any future word explicitly tagged `admin_granted` or `guessed` will NOT accidentally leak into tablets or Scholar's Fragments. `WordRegistry` now logs a natural-discovery audit after datapack reload and warns about words without a normal world route or broken prerequisite references.
