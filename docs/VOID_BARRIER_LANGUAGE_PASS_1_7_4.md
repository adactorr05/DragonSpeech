# Dragon Speech v1.7.4 — General Barriers, Void, Fate Constructs, and Reflection

This pass continues the v1.7.3 collision/composition work. It adds only the four Ancient Language words explicitly approved after that pass: `varn`, `varnbinda`, `thomr`, and `thombinda`.

## New language

- **varn** — *a barrier; a standing boundary that something is not meant to cross*. Binding noun, precision 0.72, Ancient Text discovery, prerequisite `verja`.
- **varnbinda** — *to bind and raise a barrier as a fixed boundary in the place named*. Binding verb, precision 0.92, Elven Trial discovery, prerequisites `varn` + `skjoldr`.
- **thomr** — *true absence; the Void where matter, force, and ordinary working are not*. Void noun, precision 0.88, Elven Trial discovery, prerequisite `enda`.
- **thombinda** — *to bind true absence precisely into the form, place, or mark that is named*. Void verb, precision 0.95, Elven Trial discovery, prerequisites `thomr` + `aflbinda`.

The static registry is now **238 words**. The dynamic per-world Word of Words remains outside this static count.

## `skjoldr` vs `varnbinda`

They intentionally do not mean the same thing:

- `skjoldr` — a shield associated with a bearer. It follows/protects the bearer unless explicitly planted.
- `varnbinda` — a boundary fixed into a place in the world. It is stationary by default and finite unless `ristmark` is used for a planted/persistent working.

Examples:

- `skjoldr eldr frama` — frontal fire shield carried by the caster.
- `varnbinda eldr frama` — fixed fire boundary directly before the caster.
- `varnbinda is kringlott` — fixed spherical ice boundary at the named/looked-at mark.
- `varnbinda afl teningr` — fixed cube of pure force/arcane energy.
- `letta varnbinda` — release the nearest owned placed boundary at the named mark.

## Reflective barriers (`sveigja`)

`sveigja` already means *to bend a moving path aside*, so no fixed "Reflective Wall" ability is added.

- `varnbinda afl frama sveigja` raises a force boundary that bends incoming paths back.
- Virtual Dragon Speech spell bodies (bolt/ray/lance/etc.) reflect toward their original caster at reduced power.
- The return path goes through the same spell/barrier collision solver, so the original caster's own barrier or a third spell can stop the reflection.
- Hurled weapon entities use a swept path test against barriers, because MagicBarrierEntity intentionally disables vanilla solid collision so players may pass through shields.
- A reflected weapon reverses course and is re-attributed to the reflecting barrier owner when that player is online.
- Reflection is single-bounce within one collision resolution to prevent two facing reflective barriers from recursively bouncing a spell forever in one server tick.

## Void as a real working substance

Void is now a compiled `Element.VOID`, not a purple recolor of Shadow/Death.

- Void impact deals magical damage and inflicts Darkness + Weakness.
- Void leaves no ordinary permanent block mark.
- Void projectiles use a near-black missing-core VFX with violet edge/strand geometry.
- Void beams use a dark central absence with thin violet helical edges.
- Void rain uses compact falling absence bodies rather than a recolored ordinary particle trail.
- End/Void dragons are immune to Void elemental impact.

Examples:

- `kasta thomr marklaust` — Void bolt.
- `geisla thomr marklaust` — Void ray.
- `kasta thomr voddr marklaust` — Void lance.
- `kula thomr marklaust` — Void orb.
- `regnfalla thomr marklaust` — Void rain.
- `varnbinda thomr frama` — fixed Void barrier.
- `vopnbinda thomr sverd seida marklaust` — conjured Void sword.

`thombinda` is the precise Void-domain verb for advanced Void workings; ordinary generic form verbs can also receive `thomr` as their spoken substance.

## Dangerous-domain stamina pressure

Time, Gravity, Fate, and Void now add intrinsic reality-domain multipliers to ordinary spell cost. Precision still reduces waste, but cannot make reality-tampering as cheap as ordinary fire.

Per distinct dangerous domain present anywhere in the sentence:

- Gravity: **x1.8**
- Fate: **x2.3**
- Time: **x2.6**
- Void: **x3.5**

Different dangerous domains compound, capped at x12. This is based on the whole sentence, so `varnbinda thomr` remains expensive even though its action verb belongs to Binding.

## Fate constructs

Fate already existed as a domain (`gaefa` and related vocabulary), so it is now a conceptual `MagicAffinity` alongside Time/Gravity rather than needing a new word.

- Fate barriers have broad probabilistic resistance and apply Unluck on hostile contact.
- Fate-bound conjured weapons apply Unluck on impact.
- Fate retains the dangerous-domain x2.3 stamina pressure.

## Barrier affinity/counters

The barrier engine supports Fire, Ice, Water, Wind, Lightning, Earth, Force, Light, Shadow, Life, Death, Poison, Time, Gravity, Fate, and Void affinities. They are not merely cosmetic; incoming spell pressure is adjusted by affinity/counter relationships.

Examples already supported compositionally instead of as fixed Dragon Flux abilities:

- Dragon Flux Barrier Shield -> `varnbinda afl frama`
- Reflective Wall -> `varnbinda afl frama sveigja`
- Barrier Blade -> `vopnbinda afl sverd seida`
- Barrier Pulse -> force burst wording such as `sprengja afl`
- Barrier Break -> a concentrated working such as `geisla afl thrett stodugt`; `thrett` increases barrier pressure
- Barrier Trap / containment -> `varnbinda afl bur ristmark` with an appropriate target/shape
- Repulsion Field -> force aura/field composition rather than a named ability

Barrier Saws and analytical sight abilities are intentionally not faked as hidden fixed spells. They will need either an existing sentence whose literal meaning genuinely describes them or later approved vocabulary.

## Verification

- 238 JSON word files parsed successfully.
- All four new salted hashes match `WordHashing`.
- Every prerequisite ID resolves.
- No prerequisite cycles exist.
- All 238 static words remain naturally discoverable under the current tablet/fragment systems.
- Changed common classes were type-compiled against the project's mapped Minecraft 1.21.1 + Fabric API classpath using compile-only stubs for external libraries absent from this environment.
- The changed client spell-body renderer also type-compiled against the mapped 1.21.1 client/Fabric classes (only harmless missing Fabric Loader annotation warnings in the verification environment).
- During verification, a real `Math.round(double) -> long` compilation error in the new placed-boundary lifetime calculation was found and corrected to use float arithmetic before packaging.
