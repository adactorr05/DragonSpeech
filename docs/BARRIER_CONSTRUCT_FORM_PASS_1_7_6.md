# Dragon Speech v1.7.6 — Spinning Construct Forms + Multiplicity Fix

This pass starts from the user-supplied **v1.7.5** project. v1.7.4/v1.7.5 work is preserved: `varn`, `varnbinda`, `thomr`, `thombinda`, Void as a real element, reflective barriers, dangerous-domain costs, barrier analysis, reinforcement/grow-shrink, and barrier-sourced pulses.

No new Ancient Language words are added in v1.7.6.

## Barrier Saws without a fixed ability

Dragon Flux's Barrier Saws are represented compositionally using words that already literally describe the construct:

- `hringr` — ring
- `sveira` — revolve/spiral
- `hvassa` — make an edge bite deeper / sharpen
- `kasta` — cast/hurl

### Stationary cutting ring

`hringr afl sveira hvassa marklaust`

Creates a sharpened rotating Force ring where the gaze lands. Without `kasta`, the ring remains a placed/field form instead of secretly becoming a projectile. The edge uses a dedicated rotating saw-like geometry and imparts a small tangential shove when it strikes entities on the rim.

The substance remains compositional:

- `hringr is sveira hvassa marklaust` — Ice cutting ring
- `hringr eldr sveira hvassa marklaust` — Fire cutting ring
- `hringr thomr sveira hvassa marklaust` — Void cutting ring

### Hurled cutting ring / Barrier Saw

`kasta afl hringr sveira hvassa marklaust`

Recognizing all four meanings together selects a mobile cutting-ring form. It travels along the hand-to-crosshair cast path and uses the shared spell collision solver.

The moving ring:

- collides with hostile barriers before applying damage behind them;
- can pressure/shatter a barrier according to spell power, affinity and `thrett` compression;
- registers a temporary spell body so another Bolt/Ray/Lance/cutting-ring path can clash with it;
- can be reflected by a reflective (`sveigja`) barrier through the existing barrier reflection solver;
- carries any spoken element rather than being a hard-coded Force ability.

Examples:

- `kasta is hringr sveira hvassa marklaust`
- `kasta eldr hringr sveira hvassa marklaust`
- `kasta thomr hringr sveira hvassa marklaust`
- `kasta afl hringr sveira hvassa thrett marklaust` — concentrated barrier pressure

### Barrier-sourced cutting ring

`kasta afl hringr sveira hvassa med varn marklaust`

Routes the construct out of an owned barrier. The resolver prefers an owned barrier under the gaze, then the personal shield, then the nearest owned barrier in reach. If the sentence explicitly says `med varn` and no owned barrier exists, the cast fails instead of silently falling back to the hand.

## Multiplicity finally enabled

Two existing word definitions had never been given the grammar values their Java engines already expected:

- `tvefalt`: `repeat_count = 2`
- `margfalt`: `repeat_count = 3`

This now affects reusable engines that read `SpellComposition.repeatCount()` (with each engine still enforcing its own hard cap). The existing stamina/cost calculations already scale with requested repetition.

Examples:

- `kasta eldr tvefalt marklaust` — two fire bolts
- `kasta is hringr sveira hvassa tvefalt marklaust` — two ice cutting discs
- `kasta afl hringr sveira hvassa margfalt marklaust` — three force cutting discs
- rain and other count-aware forms likewise receive the requested multiplicity.

## Ring targeting correction

v1.7.5's `RingEngine` treated every non-entity anchor as though no anchor had been named, causing `hringr ... marklaust` and block-targeted rings to snap back around the caster.

v1.7.6 resolves the center according to the sentence:

- entity target -> around the entity;
- block/point target -> at that mark;
- `marklaust` direction -> where the gaze ray lands;
- no anchor -> caster fallback.

This makes ordinary rings and stationary cutting rings useful as placed constructs.

## Visuals

A new generic `CUTTING_RING` SpellBodyVfx type renders a real moving/rotating disc:

- oriented perpendicular to its travel axis;
- layered outer/inner rim;
- rotating outward teeth rather than an extra vanilla particle circle;
- small axial hub so it remains readable edge-on;
- element palette comes from the sentence, so Fire/Ice/Void/etc. retain their identity.

## Remaining Mystic vocabulary gap

No Mystic word was silently added. `afl` remains **Force**, not Mystic.

A strong future language candidate is:

- **`seid`** — *mystic/arcane power itself; magic unshaped by element or ordinary matter* (proposed new `MYSTIC` domain noun)
- **`seidbinda`** — *to bind pure mystic power precisely into the named form/place* (precise Mystic verb)

This pair is deliberately derived from the already-existing `seida` ("to conjure out of nothing; magic answering in place of matter") so it sounds like the same language family. It is only a proposal in v1.7.6 and is **not** in the dictionary yet.

## Verification

- Base project: user-supplied v1.7.5.
- Project version bumped to 1.7.6.
- 238 static word JSON files parse successfully.
- 0 missing prerequisite IDs after namespaced prerequisite normalization.
- 0 prerequisite cycles.
- `tvefalt` resolves repeat count 2; `margfalt` resolves repeat count 3.
- `SpellBodyVfxType` compiles directly under Java 21.
- Changed common/server classes (`AdvancedFormEngine`, `ElementalWorkingHandler`, `BurstEngine`) pass a compile-only signature/syntax harness built around the actual v1.7.5 method shapes used by the modifications.
- `git diff --check` reports no whitespace errors in changed Java files.
- A literal Loom/Gradle build could not be run because this environment cannot resolve/download `services.gradle.org` for the Gradle 9.5.1 distribution. IntelliJ/Loom remains the authoritative full mod compile/runtime test.
