# Dragon Speech v1.7.3 — Barrier / Collision / Construct Composition Pass

This pass continues from v1.7.2 and does **not** add any unapproved Ancient Language words.

## Word of Words
- `WordOfWordsScreen.renderBackground(...)` is a no-op, matching the other Dragon Speech custom screens. The screen draws its own dim layer instead of Minecraft's blur post-process.
- A current Word of Words utterance bypasses Word-created anti-magic. Ordinary Dragon Speech remains blocked by the halt.
- Remove now lists/removes individual wards, individual sigils/traps, individual target magic halts, and individual Word-created anti-magic zones near the session focus.

## Existing shield/barrier grammar
- `skjoldr` remains the magical shield verb.
- `veggja` remains a **physical material wall** verb; it is intentionally not overloaded to mean magical fire/ice/etc. barriers.
- `skjoldr frama` now resolves to a frontal WALL rather than the default personal sphere.
- Existing element/concept words determine barrier affinity:
  - `skjoldr eldr frama` — fire barrier
  - `skjoldr is frama` — ice barrier
  - `skjoldr vatn frama` — water barrier
  - `skjoldr vindr frama` — wind barrier
  - `skjoldr elding frama` — lightning barrier
  - `skjoldr afl frama` — force / pure-energy barrier
  - `skjoldr tid frama` — time barrier
  - `skjoldr thyngdarafl frama` — gravity barrier
- Affinities have different spell-pressure resistances/weaknesses instead of being color-only.

## Spell / barrier collision foundation
- Composed BOLT, RAY, ORB, LANCE, RAIN, and CHAIN arcs now resolve through `SpellCollisionManager`.
- Enemy barriers mathematically intercept the path before target damage is applied.
- A surviving barrier stops the working. A shattered barrier lets a weakened remainder pass.
- Recent opposing spell paths can clash. Similar power cancels both; a clearly stronger working can continue weakened.
- `thrett` compression increases collision/barrier pressure, so a highly concentrated working is naturally better at breaking a barrier without inventing a fixed "Barrier Break" ability.
- The caster's own barrier is ignored by their outgoing spell path so a shield does not trap its owner's magic inside.

## Material-driven conjured weapons
- `vopnbinda` / `vopnkasta` no longer force conjured weapons to be a vanilla wood/gold/etc. item when an elemental/conceptual substance was spoken.
- For conjured weapons, MagicAffinity becomes the substance and the tool noun supplies the shape.
- Example: `vopnbinda is sverd seida` creates an ICE sword projectile rather than a gold/wood sword with ice particles pasted over it.
- Fire/Lightning/Water/Wind/Earth/Force/Time/Gravity constructs work through the same mechanism.
- A real item drawn with `taka` remains physically the real item, but can carry a spoken magic affinity around it.
- Magical constructs have custom color-geometry weapon bodies on the client; they do not render as the vanilla physical material item.
- Weapon throws now originate from the same approximate right-hand casting point as the composed-spell engine rather than the eye/camera.
- Magical weapon projectiles interact with `MagicBarrierEntity`; barriers can stop them or break under sufficient impact.

## Ice spikes without a new word
- `sulbinda is` can use packed ice as the pillar material.
- `sulbinda is voddr` composes the existing pillar meaning with `voddr` (long piercing point) to raise an ice spike at the place under the caster's gaze.
- `mikla` makes the spike taller and uses blue ice for the elemental material path.
- The spike physically rises, has lance geometry, and applies the Ice element to living things caught by it.

## New words NOT added yet
The proposed generic barrier pair remains only a proposal:
- `varn` — barrier; a standing boundary that something is not meant to cross.
- `varnbinda` — to bind/raise such a barrier into place.

Void also still lacks an approved core noun, so Void barriers/weapons are not exposed through normal vocabulary yet.

## Verification
A broad Java source compile was run against the extracted Minecraft 1.21.1 mapped common/client jars and Fabric API jars from the Dragon Flux build cache. The environment lacks external Mojang serialization/Gson/Brigadier/JOML dependency jars, so a full local Gradle/Loom build is still impossible here. The compiler produced no direct Java errors for the modified common classes; client errors for the changed screen/renderer were limited to those unavailable external dependency classes (e.g. JOML/Gson), not source-method mismatches.
