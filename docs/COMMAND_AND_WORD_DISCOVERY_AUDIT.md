# Dragon Speech Command + Word Discovery Audit

## Confirmed command root cause from the September 15 runtime log

The remaining position-13 failure was not the server command tree. The client log reported every failure through `ClientCommandInternals`, which means the command was rejected by Fabric's client-command dispatcher before it could reach the integrated server.

`DragonSpeechClient` registered a client-only command tree rooted at `/dragonspeech` solely so `/dragonspeech config` could open the config screen. Fabric checks the client dispatcher first, so that client root shadowed the real server `/dragonspeech` tree. Because its only child was `config`, every other child (`grantall`, `summon`, `revealwow`, etc.) failed immediately after the root.

### Fix

- The client-only config GUI command is now `/dragonspeechconfig`.
- There is no client-side command named `/dragonspeech` anywhere in the source.
- The server owns `/dragonspeech` exclusively through `DragonSpeechCommandRoot`.
- `/dragonspeech summon <breed> [bond|nobond]` is the dragon spawner.
- `/dragonspeech summonmob <type>` is the generic debug/NPC mob spawner.
- `grantall` accepts multi-player selectors such as `@a`.

Expected useful commands after this fix:

- `/dragonspeech`
- `/dragonspeech grantall @a`
- `/dragonspeech revealwow`
- `/dragonspeech reshufflewow`
- `/dragonspeech summon fire`
- `/dragonspeech summon ice bond`
- `/dragonspeech summon lightning nobond`
- `/dragonspeech summonmob elf`
- `/dragonspeech debug hatch`
- `/dragonspeechconfig` (client-only config GUI)

After typing `/dragonspeech summon ` and pressing Tab, the built-in breed list should include:

`end`, `fire`, `forest`, `gold`, `ice`, `lightning`, `void`

Addon breeds are shown by full `namespace:path` IDs.

## Elven Trial vault data fix

The same runtime log exposed a separate datapack error: `dragonspeech:chests/elven_trial_vault` still referenced the removed generic item `dragonspeech:eldunari`.

That item was replaced earlier by seven color-specific Dragon Heart items plus the special Mad heart. The invalid entry caused the whole vault loot table to fail parsing.

The vault now uses a dedicated rare 10% Dragon Heart bonus pool containing the seven ordinary color hearts with equal weight:

- red
- bronze
- blue
- white
- green
- black
- ender

The Mad heart is deliberately excluded from normal Elven Trial vault loot.

## Natural word-discovery audit

Current static word count: **238**. The generated Word of Words is dynamic/per-world and is intentionally not part of this static registry count.

Current discovery categories:

- `elven_trial`: 134
- `ruin_tablet`: 46
- `mentor_npc`: 33
- `ancient_text`: 25
- `admin_granted`: 0
- `guessed`: 0

A direct data audit confirms:

- **238/238** static words have a naturally reachable discovery category and non-zero natural tablet and/or Scholar's Fragment weighting under the current generators.
- **0** word prerequisite IDs are missing.
- **0** prerequisite cycles exist.

### New Dragon Flux-port vocabulary

All twelve new words use `elven_trial` discovery:

- `samdraga`
- `thrett`
- `sveira`
- `fjotbinda`
- `vefja`
- `voddr`
- `kral`
- `sveigja`
- `kringferd`
- `ferdafl`
- `fran`
- `snara`

Their ordinary tablet/fragment weighting is:

- Worn tablet: **0**
- Ancient tablet: **2**
- Primordial tablet: **6**
- Ordinary Scholar's Fragment: **1**

Elven Trial-biased fragments weight Elven Trial vocabulary more heavily. This keeps the new vocabulary naturally discoverable while making it appropriately deeper than basic language.

### Barrier/Void vocabulary added in v1.7.4

The four approved compositional words are also naturally discoverable:

- `varn` — Ancient Text vocabulary. Tablet weights: Worn **3**, Ancient **5**, Primordial **3**.
- `varnbinda` — Elven Trial vocabulary. Tablet weights: Worn **0**, Ancient **2**, Primordial **6**.
- `thomr` — Elven Trial vocabulary. Tablet weights: Worn **0**, Ancient **2**, Primordial **6**.
- `thombinda` — Elven Trial vocabulary. Tablet weights: Worn **0**, Ancient **2**, Primordial **6**.

Every prerequisite for these words resolves (`verja`; `varn` + `skjoldr`; `enda`; `thomr` + `aflbinda` respectively), and the global prerequisite graph remains acyclic.

## Future secret-word protection

Tablet and Scholar's Fragment generators give `ADMIN_GRANTED` and `GUESSED` discovery methods zero natural weight. This is important for future secret vocabulary, including the planned Word of Words rebuild: a deliberately non-natural word will not accidentally leak into ordinary tablets or fragments.
