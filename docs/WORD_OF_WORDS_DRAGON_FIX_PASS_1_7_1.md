# Dragon Speech 1.7.1 — Word of Words / Dragon Fix Pass

## Word of Words UI and actions
- Enlarged the custom Word-of-Words panel and reserved a footer region so focus/location/stamina information no longer sits beneath the bottom action buttons.
- Reveal and Remove now inspect both player-style `WardAccess` wards and NPC `MobWards` used by Shades/Elder Elves/etc.
- NPC wards are listed individually, can be removed individually, and can be restored individually.
- Halt/Bind magic now operate on both players and `SpellcastingMob` NPCs. Player chat casts and constructed/grid casts are both gated; NPC `MobCastExecutor` is gated by the same halt authority.
- Added longer target halt/bind choices (minutes rather than only seconds), with nonlinear stamina scaling.
- Restore now includes self-health and self-condition restoration as well as selected-target health and individual wards.
- The global magic-cost law now affects player casting, sustained casting, ordinary wards, Word-of-Words action costs, and NPC spell costs. It is intentionally global: enemies benefit or suffer too.
- Server-side validation whitelists the durations/radii exposed by the GUI.

## Dragon Hearts
- Usable Dragon Hearts naturally regenerate their stored stamina once per second while carried by an online player.
- Normal hearts regenerate 2 stamina/second; Mad hearts regenerate 0.75 stamina/second.
- Regeneration works in main inventory/hotbar and offhand and stops at the heart's own stored maximum.

## Dragon collision/passives
- Dragons no longer take `in_wall` suffocation damage.
- A server-side collision safety position rolls a dragon back if its large body becomes embedded in solid blocks; first-tick/teleport embedding attempts an upward recovery volume.
- Fire dragon: immune to fire/on-fire/lava/hot-floor and Dragon Speech Fire elemental damage.
- Lightning dragon: immune to lightning-bolt damage and Dragon Speech Lightning elemental damage.
- Ice dragon: immune to freeze/drown plus Dragon Speech Ice elemental damage.
- End and Void dragons: immune to out-of-world/void damage.
- Forest is the current Earth-aligned breed: 90 base health, 10 armor, plus 25% incoming damage reduction.
- Existing Gold/End/Lightning breed attribute flavor remains layered through the dragon base-attribute update.

## NPC casting architecture
NPCs still retain their historical effect executor because many old effect handlers are typed directly to `ServerPlayer`. This pass puts shared authority above both pipelines: global cost rules and Word-of-Words casting suppression now affect players and NPC casters consistently. A future deep refactor can merge the effect executors without being required for Halt/Change to work now.

## Validation
- Version bumped to 1.7.1.
- 234 static word JSON resources parse successfully with no missing prerequisite IDs.
- Changed source paths were checked against the mapped Minecraft/Fabric development classpath. Full Gradle/Loom execution is still unavailable in this environment because the required Gradle distribution is not locally installed/downloadable.
