# Dragon Speech 1.6.4 — VFX / Targeting Test Fix Pass

This pass responds to the first live test of the Dragon Flux-derived composed-spell VFX layer.

## Targeting / origin

- `frama` now uses the caster's full 3D gaze vector. Looking up/down is preserved; it is no longer flattened to the horizontal plane.
- Hand-cast spell gameplay starts at an approximate right-hand position on the server, while target selection remains crosshair/gaze based.
- Client spell bodies refine the origin to the rendered right hand.
- A hand-origin ray/bolt aims toward the point under the crosshair so moving the source off the camera does not make spells miss the crosshair target.
- Gaze-placed weather/cloud/sigil effects use the camera only to decide the target point, not as the visible spell source.

## Legacy trail cleanup

- Removed the old full travel particle trails from Bolt, Ray and Orb bodies.
- Removed full vertical travel trails from Rain.
- Impact bursts remain and are emitted at the impact point; they are accents, not the main spell body.

## Lightning

- The custom forked `LIGHTNING` renderer used by chain jumps is now the canonical Lightning Bolt / homing Lightning / Lightning Ray travel body.
- Pure lightning no longer stacks a second generic geometry lightning body on top of it.
- Woven spells render the non-lightning element's body plus exactly one forked lightning layer.
- Lightning arcs can now be entity-linked at their authored hand/chain origin so they do not snap back to the camera.
- Sustained lightning rays request a longer lightning lifetime so `stodugt` pulses overlap visually.

## Ray / duration

- One-shot ray body lifetime increased from 5 to 10 ticks.
- `stodugt` remains the existing continuous/sustained word. Sustained ray body lifetime is 28 ticks so its 20-tick channel pulses overlap.
- Fixed a channel accounting bug: the generic `stodugt` pulse cost calculated at cast time was previously discarded and many ordinary sustained workings could become free. The stored pulse cost is now actually drained, with the existing increasing duration surcharge.
- No new finite-duration vocabulary was added in this pass. A separate word for "for a measured span" should be proposed/approved before addition.

## Tether / grapple

- Entity pull/repel impulse increased substantially.
- Terrain grapple impulse increased substantially.
- Tether form still does not imply motion by itself: `til` pulls/draws and `fran` repels.

## Shell

- Shell/wrap visual changed from horizontal ring stacks to a faceted enclosing 3D shell with structural ribs.
- It remains a wrapping elemental form, not an implicit defensive ward.

## Rain / spiral

- Rain strikes are now moving falling bodies rather than vertical beams.
- Ice Rain uses falling crystalline shard/hail bodies.
- Fire Rain uses compact falling fire/comet bodies.
- Earth Rain uses falling rock forms.
- Wind Rain carries coherent wind wake geometry.
- `sveira` modifies each falling body's trajectory into a corkscrew and also distributes strikes in a spiral pattern; it does not add a separate unrelated ring effect.
- A restrained cloud body appears overhead so the working reads as weather.

## Wind visibility

- Wind carried by a bolt/lance has a dedicated multi-strand air wake so woven Ice + Wind should visibly retain the Wind contribution.

## Word filters

Spell Construction's domain filter now includes:

`time`, `gravity`, `fate`, `void`, `weapon`

These are backed by the same `DomainColors` mapping used by the Grimoire.

## Validation

- Changed common spell/VFX classes type-compiled successfully against the uploaded project's mapped Minecraft/Fabric development classes.
- Changed client geometry renderer, Lightning particle subclass, and Spell Construction screen type-compiled successfully.
- Word data remains 234 definitions; this pass adds no unapproved vocabulary.
