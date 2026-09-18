# Dragon Speech v1.7.5 — Barrier Analysis, Reinforcement, and Barrier-Sourced Pulses

This follow-up starts from the uploaded v1.7.4 source. It does not add any new Ancient Language words; the approved v1.7.4 vocabulary (`varn`, `varnbinda`, `thomr`, `thombinda`) remains unchanged.

## Barrier analysis through existing language

`skynja` already means *to sense the stored strength bound within a thing*, and `varn` means a standing barrier. The sentence now composes literally:

- `skynja varn` — analyze the barrier under the caster's gaze.
- `skynja naerum varn` (or another radius scope) — sense nearby barriers, up to eight nearest readings.

The reading reports the real live barrier data used by the engine: affinity/material, carried/placed/anchored lifecycle, ward vs cage behavior, current/max strength and percentage, radius, owner, reflection, and a counter hint derived from the same affinity pressure table used by spell/barrier collisions. The sensed barrier flashes briefly in its own color so it is clear which working was read.

This is the Dragon Speech equivalent of Dragon Flux Barrier Analyze without adding a fixed `Analyze` spell.

## Reinforcing placed boundaries

Placed `varnbinda` boundaries can now be modified in-place instead of forcing overlapping duplicates:

- `varnbinda afl frama` — raise a fixed force boundary.
- repeat at that mark with `afla` — pour strength into the existing owned boundary.
- use `mikla` — grow the existing boundary.
- use `litla` — shrink the existing boundary.

The stamina estimate uses the same feed/grow cost calculation as the actual mutation, so the preview and execution remain aligned.

## Barrier Pulse through existing grammar

`med` means *with; by means of*. A burst combined with `med varn` therefore uses an owned barrier as the source:

- `sprengja afl med varn` — force pulse from the selected/owned barrier.
- `sprengja eldr med varn` — fire pulse from the selected/owned barrier.
- other compiled elements work the same way.

The resolver first uses an owned barrier directly under the gaze, then the caster's personal shield, then the nearest owned barrier within reach. If none exists, the working fails instead of silently bursting from the player. Barrier-sourced bursts carry an extra semantic-complexity stamina multiplier because the caster is routing the working through an existing construct.

## Remaining Barrier/Mystic gaps

The following are intentionally not faked with unrelated words:

- Barrier Saws / rotating cutting discs.
- A dedicated Mystic/arcane substance distinct from raw `afl` (Force).

Those need additional approved vocabulary if they are to have literal sentence meanings instead of hidden fixed abilities.

## Verification

- 238 static word JSON files parse successfully.
- All prerequisite IDs resolve.
- No vocabulary was added or renamed in this pass.
- The normal Gradle wrapper still cannot run in this environment because Gradle 9.5.1 is not locally cached and outbound DNS/download access is unavailable. The modified files were source-audited for balanced Java structure and API usage against the existing 1.21.1 code patterns; the user's IntelliJ/Loom build remains the authoritative compile test.
