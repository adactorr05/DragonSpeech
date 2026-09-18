# Dragon Speech

**Dragon Speech** is a Fabric magic-and-dragons mod for **Minecraft 1.21.1** built around a language-driven spell system. Instead of selecting fixed spells from a conventional spellbook, players learn words of an Ancient Language and combine them into increasingly precise magical sentences.

The project is inspired by the style of magic, dragons, mental combat, true names, and language-based spellcasting found in *The Inheritance Cycle*, while implementing those ideas as its own Minecraft systems.

> **Development status:** Dragon Speech is actively being developed. The repository currently identifies itself as **v1.7.6**. Some systems contain debug/admin tooling or first-pass interfaces and may continue to change.

---

## Contents

- [Core Idea](#core-idea)
- [Ancient Language & Spellcasting](#ancient-language--spellcasting)
- [Learning Words](#learning-words)
- [Spell Construction](#spell-construction)
- [Stamina, Precision & Progression](#stamina-precision--progression)
- [Magic Domains](#magic-domains)
- [Advanced Spell Forms](#advanced-spell-forms)
- [Wards, Barriers & Enchanting](#wards-barriers--enchanting)
- [The Word of Words](#the-word-of-words)
- [Mind Magic & Mind Duels](#mind-magic--mind-duels)
- [True Names](#true-names)
- [Dragons](#dragons)
- [Dragon Eggs & Breeds](#dragon-eggs--breeds)
- [Dragon Hearts](#dragon-hearts)
- [Races](#races)
- [NPCs & World Content](#npcs--world-content)
- [World Generation](#world-generation)
- [Items & Discovery Content](#items--discovery-content)
- [Default Controls](#default-controls)
- [Commands & Testing Tools](#commands--testing-tools)
- [Configuration](#configuration)
- [Installation](#installation)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Data-Driven Content](#data-driven-content)
- [Documentation](#documentation)
- [License](#license)

---

## Core Idea

Dragon Speech treats magic as a **language**, not a list of isolated abilities.

A spell is assembled from words describing what the caster wants magic to do. Verbs provide actions, nouns clarify what is being affected, modifiers alter behavior, and specialized words can control direction, scale, shape, duration, targeting, repetition, barriers, binding, and other properties.

The central rule is simple:

> **Precision is safer and more efficient than vagueness.**

The casting system evaluates the entire sentence. A short but imprecise command can be more expensive than a longer sentence that carefully defines the caster's intent.

The live dictionary currently contains **238 words** across physical, elemental, mental, temporal, gravitational, fate, void, binding, life, death, and other domains.

See [`docs/DICTIONARY.md`](docs/DICTIONARY.md) for the generated full vocabulary.

---

## Ancient Language & Spellcasting

Words are registered from JSON data and interpreted by the spell system at runtime. A typical working can contain:

- **Verbs** — the action being performed, such as pushing, igniting, freezing, healing, shaping, summoning, binding, or teleporting.
- **Nouns** — the substance, object, target, or concept that makes the instruction more exact.
- **Modifiers** — direction, strength, duration, count, speed, range, and similar details.
- **Binding/control words** — words used for wards, persistent workings, channels, release, and specialized magical behavior.
- **Form and targeting language** — composition that changes a working into bolts, bursts, clouds, rain, rings, barriers, constructs, and other shapes.

The grammar validator and casting pipeline convert a sentence into a resolved working rather than requiring every possible sentence to exist as a separately coded spell.

### Example philosophy

A vague instruction such as "push upward" leaves much of the intent for the magic to infer. A sentence that precisely names the force, direction, target, and behavior can cost less despite containing more words.

This makes vocabulary knowledge and sentence construction part of progression: **knowing better words lets the player perform better magic.**

---

## Learning Words

Players are not intended to begin with the complete language. Vocabulary is tracked per player and words can be discovered through several systems.

The current discovery metadata supports:

| Discovery route | Purpose |
| --- | --- |
| **Ruin Tablet** | Early or archaeological vocabulary found through world content. |
| **Mentor NPC** | Words learned through NPC-related content and word scrolls. |
| **Ancient Text** | Vocabulary associated with rarer written discoveries. |
| **Elven Trial** | Advanced language and dangerous/high-precision workings. |
| **Guessing** | Attempting to speak a word before formally learning it. |
| **Admin Granted** | Testing and server administration. |

### Guessing unknown words

Unknown words can be attempted rather than being absolutely locked. Guessing is deliberately risky: the project contains a backlash system with word-specific risk levels and failure handling. This means discovering a word naturally is safer than blindly testing powerful vocabulary.

### Tablets and scrolls

The mod includes worn, ancient, and primordial word tablets, scholar fragments, and word scrolls. These provide in-world routes for interacting with the language instead of reducing progression to a command or menu unlock tree.

---

## Spell Construction

The client includes a dedicated spell-construction interface.

By default, press **G** to open Dragon Speech. The construction UI works with the player's known vocabulary and the same server-side casting system used by other casting paths.

The project also tracks saved/previous spell information and supports **recasting the last spell** with **R** by default.

The composition engine includes dedicated systems for:

- grammar validation;
- target resolution;
- spell preparation and execution;
- elemental workings;
- persistent/channelled workings;
- advanced forms;
- spell-body VFX;
- spell-to-spell collision;
- physical magic barriers;
- sigils and persistent fields;
- temporal and gravity effects.

This architecture is intended to let new vocabulary expand combinations rather than requiring a new hard-coded ability for every useful result.

---

## Stamina, Precision & Progression

Magic is powered primarily through **stamina**.

Casting drains the caster according to the working being attempted. The system also contains additional drain/funding logic for dangerous or unusually expensive magic, including life-force and nearby-funding mechanics used by applicable workings.

### Precision

Each word carries a precision value. Spell cost considers the **average precision of the full sentence**, not simply the number of words. More exact language can therefore reduce the cost of a working.

### Attunement

Players gain experience in individual magic domains through use. Attunement is tracked separately per domain, so repeatedly practicing one school makes the player more efficient in that school without automatically mastering every other form of magic.

### Growth

The codebase contains player growth hooks, stamina milestones, skill progression, attunement data, and race modifiers. Progression is therefore spread across both **what the player knows** and **what the player has practiced**.

---

## Magic Domains

The current fixed domain set is:

| Domain | General role |
| --- | --- |
| **Fire** | Flame, heat, ignition and fire-based workings. |
| **Water** | Water, cold/freeze-adjacent and fluid workings. |
| **Earth** | Stone, terrain, shaping, walls, pillars and thrown matter. |
| **Air** | Wind, lightning and atmospheric workings. |
| **Life** | Healing, restoration and life-force effects. |
| **Death** | Death-related, summoning and dangerous life/death workings. |
| **Mind** | Mental contact, control, possession and mind combat. |
| **Force** | Pushes, impacts, directed force and protection. |
| **Motion** | Movement and motion-oriented manipulation. |
| **Binding** | Wards, barriers, enchantments and persistent bindings. |
| **Truth** | Detection, revealing and truth-related language. |
| **Time** | Time slowing, temporal fields and stasis-style effects. |
| **Gravity** | Gravity scaling and gravity fields. |
| **Fate** | Probability/fate-oriented advanced workings. |
| **Void** | Void manipulation and high-risk advanced magic. |
| **Weapon** | Material/tool/weapon language used by weapon-oriented workings. |

Advanced domains are intentionally separate so mastery in one dangerous art does not automatically train another.

---

## Advanced Spell Forms

Dragon Speech contains composition engines for more than simple point-and-click effects. Current systems include forms and mechanics for concepts such as:

- directed bolts and projectiles;
- omnidirectional bursts;
- lingering clouds;
- rain/area patterns;
- multiplicity;
- rings and spinning constructs;
- material-driven conjured forms;
- physical barriers;
- persistent sigils;
- gravity fields;
- temporal fields and time slowing;
- stasis;
- marks;
- channelled forces;
- spell collision and barrier interaction.

The VFX system is also composition-aware. Spell visuals use multiple custom particle types and geometry/body rendering rather than treating every spell as the same generic particle ball.

---

## Wards, Barriers & Enchanting

Protection is a major subsystem rather than a single shield spell.

### Wards

The mod supports personal wards and specialized protection against different forms of harm. Ward state is tracked separately from ordinary armor and can interact with stamina/power sources.

### Physical barriers

Barrier language can create actual `MagicBarrierEntity` objects in the world. These barriers can intercept projectiles and participate in melee, explosion, fire, and spell-collision handling.

Barrier composition supports different shapes and more advanced behavior, including reinforcement, reflection-oriented language, barrier-sourced pulses, and constructed cutting/ring forms.

### Enchanting through language

Dragon Speech has its own magic-enchantment layer. Language can bind magical effects or real vanilla enchantment behavior into suitable items. The current code includes support for custom blessings/curses, wards, hidden enchantments, and mappings to vanilla enchantments such as Sharpness, Protection, Knockback, Mending, Efficiency, Silk Touch, Looting, Fire Aspect, and Unbreaking where the appropriate words are used.

---

## The Word of Words

The **Word of Words** is a separate high-level language system with its own knowledge lifecycle and GUI.

It is not merely another damage spell. It operates on magical language and workings themselves. The current action framework includes pages/actions for concepts such as:

- **Remove**
- **Add**
- **Change**
- **Halt**
- **Time**
- **Reveal**
- **Bind**
- **Restore**

Knowledge of the Word of Words can be learned, invalidated by a reshuffle, anchored/bound to memory, favorited, and synchronized through an authoritative server session. The implementation also includes admin/testing controls for rebuilding or reshuffling its knowledge state.

For the design and lifecycle details, see [`docs/WORD_OF_WORDS_REBUILD.md`](docs/WORD_OF_WORDS_REBUILD.md).

---

## Mind Magic & Mind Duels

Mind magic is one of the largest systems in the project.

Press **K** by default while looking at a valid target to **reach out with your mind**. Contact is not automatically equivalent to control: resistance and mental combat can lead into a multi-stage mind duel.

Current mind systems include:

- mental contact and resistance;
- defense-breach gameplay;
- hidden-core searching;
- decoys and mental traps;
- struggle/control phases;
- reading thoughts;
- command effects;
- temporary spellcasting suppression;
- true-name interaction;
- possession;
- player and mob mind fortitude;
- sentience tiers;
- mindscape types and training;
- multiplayer mind links;
- team mind duels;
- client GUIs and synchronization.

### Hidden Core

The Hidden Core phase gives the defender a mindscape containing the real core, decoys, empty thoughts, and potentially prepared mental traps. Attackers search and probe while defenders can shift the core or create additional deception.

### Team mind combat

Players can form consent-based mind links. Reaching a linked defender can redirect the encounter into a team duel with a shared Link Strength system and coordinated attacker/defender actions.

### Sentient mobs

Mind fortitude is not player-only. Living entities are assigned sentience tiers, and the project exposes registries/interfaces that allow other entity types to receive specialized mind behavior.

See [`docs/MIND_DUEL_PHASE6.md`](docs/MIND_DUEL_PHASE6.md) for the detailed implementation status.

---

## True Names

Players have generated **true names** integrated into the mind system.

True names are handled with the same hashing discipline used by vocabulary rather than being treated as ordinary plaintext spell data. The system includes:

- persistent generated true names;
- learning/knowing another being's name;
- grimoire tracking;
- true-name guessing;
- invalidation when a name changes;
- regeneration tied to meaningful player progression;
- true-name interactions during mind combat.

The current personality-change proxy watches the player's dominant magical attunement. A sufficiently meaningful change in that dominant domain can regenerate the player's true name, invalidating previously known versions.

---

## Dragons

Dragon Speech includes its own dragon entity and bond system.

Current dragon systems include:

- wild dragon spawning;
- multiple color/breed variants;
- egg hatching;
- habitat-dependent egg development;
- player bonding;
- persistent bond data;
- following/owner AI;
- breeding behavior;
- dragon breath combat;
- riding-related controls;
- client animation and rendering;
- wing/dive VFX;
- dragon-assisted magic systems;
- dragon mind/sentience support;
- Dragon Hearts.

The dragon implementation is designed as a gameplay system, not simply a reskinned passive mob.

---

## Dragon Eggs & Breeds

There are currently **seven data-driven dragon breeds**:

- Fire
- Ice
- Forest
- Gold
- Lightning
- End
- Void

Each breed can define properties through JSON, including colors, attribute overrides, damage immunities, and habitat requirements.

Eggs are placeable blocks and use a hatching block entity. Habitat checks can evaluate conditions such as:

- biome;
- dimension;
- fluid;
- world height;
- light;
- nearby blocks;
- nearby lightning;
- rain;
- dragon breath;
- combinations of multiple habitat requirements.

This allows hatching conditions to be expanded through data rather than hard-coding every breed's incubation rule directly into the egg block.

---

## Dragon Hearts

Dragon Hearts are the project's Eldunarí-inspired system.

The mod registers separate Dragon Heart variants corresponding to dragon colors, along with state, energy, vessel, UI, and service logic. Hearts can act as magical power sources and integrate with stamina/casting systems rather than existing only as collectibles.

The codebase includes a dedicated Dragon Heart vessel entity and a client screen for inspecting/interacting with heart state.

---

## Races

Players can choose one of four origins:

| Race | Magical affinity | Trait direction |
| --- | --- | --- |
| **Human** | No fixed domain discount | Faster general attunement growth / adaptability. |
| **Elf** | Mind, Life | Stronger mental resistance and affinity with the language. |
| **Dwarf** | Earth, Force | Greater resistance to knockback/crushing effects. |
| **Urgal** | Force, Death | Increased raw health. |

Affinity domains currently apply a **20% casting-cost discount** in those domains and stack with learned attunement rather than replacing progression.

Race data persists with the player, and the project includes race-selection/info commands plus trait hooks.

---

## NPCs & World Content

Dragon Speech includes several naturally spawning magical/sentient entity types:

- **Elves** — forest-spawning NPCs tied to advanced language content.
- **Elder Elves** — rarer forest NPCs.
- **Human Mages** — the most commonly encountered Dragon Speech caster NPCs in the Overworld.
- **Shades** — rare hostile entities.
- **Wild Dragons** — rare creatures concentrated in mountain-family biomes.

NPC casting uses shared spell infrastructure rather than a completely separate fake-magic implementation. The codebase also includes extensible entity behavior and mind-duel brain registries for specialized AI.

---

## World Generation

The mod adds archaeological/magical content to normal world exploration.

Current world-generation content includes:

- an **Ancient Shrine** placed on tagged land biomes;
- **Common Ruins**;
- **Ruin Towers**;
- a second **Ruined Tower** variant;
- **Tall Towers**;
- loot integration for word-discovery items.

The jigsaw structures use random-spread structure sets with different spacing/separation values. Land-biome tagging is shared so structures and shrines do not intentionally generate on ocean surfaces.

The repository also currently contains temporary structure diagnostic commands used during development to verify template pools and generation behavior.

---

## Items & Discovery Content

Notable custom content includes:

- **Worn Word Tablet**
- **Ancient Word Tablet**
- **Primordial Word Tablet**
- **Scholar's Fragment**
- **Word Scroll**
- **Seven placeable Dragon Egg variants**
- **Dragon Heart variants**
- **Accessory/jewelry items**

The accessory system has its own slots, materials, death-drop behavior, and inventory-screen integration rather than relying entirely on vanilla armor slots.

---

## Default Controls

All listed keys are remappable through Minecraft's Controls menu.

| Key | Action |
| --- | --- |
| **G** | Open Dragon Speech / spell construction. |
| **H** | Speak/guess a word. |
| **R** | Recast the last spell. |
| **K** | Reach out with your mind toward the targeted entity. |
| **Z** | Dragon descend control while applicable. |

Additional interaction is performed through the spell GUIs, normal mouse controls, chat casting, item use, and commands.

---

## Commands & Testing Tools

Dragon Speech contains normal gameplay commands as well as extensive development/admin utilities.

### Player-facing command groups

- `/mind ...` — mental contact, duel actions, mind links, status, disengaging, grimoire access, and related mind systems.
- `/race choose <human|elf|dwarf|urgal>` — choose a player origin.
- `/race info` — inspect race information.
- `/dragon ...` — bonded-dragon actions such as calling/status and development age controls where permitted.

### Dragon Speech admin/debug tree

The `/dragonspeech ...` command tree contains vocabulary, true-name, entity, ward, mind, structure, dragon, and debugging utilities. The exact subcommands change during development, so use in-game command suggestions as the authoritative list for the current build.

Examples of systems exposed to administrators include:

- granting or inspecting vocabulary;
- true-name testing/regeneration;
- summoning Dragon Speech entities;
- ward visibility;
- forced mind breach;
- NPC spell debugging;
- stamina inspection;
- entity auditing;
- dragon hatching/summoning diagnostics;
- structure-generation diagnostics.

> Debug commands are development tools and should not be treated as normal survival progression.

---

## Configuration

Dragon Speech has both common/server-side configuration and client configuration code. It also contains a **Mod Menu** integration when Mod Menu is installed.

Mod Menu is **optional** at runtime; the project only compiles against it so players without Mod Menu can still run Dragon Speech.

Configuration currently covers gameplay/difficulty tuning and client-facing options, while additional server-to-client configuration synchronization is present for settings that affect interfaces.

---

## Installation

### Requirements

- **Minecraft:** 1.21.1
- **Java:** 21 or newer
- **Fabric Loader:** 0.19.3 or newer for the current project configuration
- **Fabric API:** required
- **Mod Menu:** optional

### Client / singleplayer

1. Install a Minecraft 1.21.1-compatible Fabric Loader profile.
2. Install Fabric API.
3. Place the Dragon Speech `.jar` in the instance's `mods` folder.
4. Optionally install Mod Menu for easier access to configuration screens.
5. Launch Minecraft with **Java 21+**.

### Multiplayer

Dragon Speech contains server-authoritative gameplay state, custom networking, entities, world generation, and client interfaces. Install the mod and required dependencies on both the server and participating clients unless a future release explicitly states otherwise.

---

## Building from Source

Dragon Speech uses Gradle with Fabric Loom and targets Java 21.

### Windows

```powershell
.\gradlew.bat clean build
```

### Linux / macOS

```bash
./gradlew clean build
```

The compiled mod jar will be produced under:

```text
build/libs/
```

If Gradle reports that it is running on Java 8 or another old JVM, configure `JAVA_HOME`, your terminal, and/or the IDE's **Gradle JVM** to use **JDK 21** before building.

### Current build properties

```text
Minecraft:   1.21.1
Java:        21
Mod version: 1.7.6
Fabric API:  0.116.13+1.21.1
Mod Menu:    11.0.3 (optional)
```

The project uses **official Mojang mappings**.

---

## Project Structure

The codebase is split into server/common and client source sets.

```text
src/main/java/com/dragonspeech/
├── accessory/     Custom accessory slots/items
├── api/           Addon and behavior integration points
├── cast/          Cast execution/results
├── channel/       Sustained/channelled workings
├── command/       Main command trees
├── config/        Common configuration
├── death/         Death/revival systems
├── detection/     Magical detection
├── dragon/        Dragons, bonds, eggs, breeds and AI
├── effect/        Individual magical effect handlers
├── eldunari/      Dragon Heart systems
├── enchant/       Magic enchantments and wards
├── engine/        Spell forms, fields, collision and advanced workings
├── entity/        Custom entities/barriers
├── growth/        Attunement and progression
├── guess/         Unknown-word guessing/backlash
├── item/          Tablets, fragments and scrolls
├── loot/          Loot injection/discovery support
├── mind/          Mind duels, true names, possession and mind links
├── network/       Client/server payloads and casting hooks
├── race/          Human/Elf/Dwarf/Urgal origins
├── stamina/       Magic resource and drain systems
├── vocabulary/    Per-player learned vocabulary
├── ward/          Ward state and protection
├── word/          Word definitions, domains and registry
├── worldgen/      Shrines, structures and natural spawning
└── wow/           Word of Words systems

src/client/java/com/dragonspeech/client/
├── construct/     Spell construction UI
├── dragon/        Dragon model/rendering/VFX
├── fx/            Custom spell particles/rendering
├── grimoire/      Grimoire and true-name UI
├── grid/          Casting grid UI/cache
├── gui/           Dragon bond/heart screens
├── hud/           Stamina HUD
├── mind/          Mind-duel visuals/screens
└── wow/           Word of Words UI
```

---

## Data-Driven Content

A large portion of Dragon Speech is data-driven.

### Words

Ancient Language vocabulary lives under:

```text
src/main/resources/data/dragonspeech/dragonspeech_words/
```

Word JSON controls data such as meaning, domain, precision, discovery route, effect binding, prerequisites, and guessing risk.

### Dragon breeds

Breed definitions live under:

```text
src/main/resources/data/dragonspeech/dragon_breeds/
```

These files can define visual colors, attributes, immunities, and habitat rules.

### Structures and worldgen

Jigsaw structures, template pools, structure sets, biome tags, placed features, loot tables, and related data are stored in the normal Minecraft datapack resource hierarchy under:

```text
src/main/resources/data/dragonspeech/
```

This separation is intentional: code defines the core rules and safe execution boundaries, while data selects and configures content within those systems.

---

## Documentation

The repository contains additional design and implementation notes in [`docs/`](docs/).

Especially useful files include:

- [`DICTIONARY.md`](docs/DICTIONARY.md) — generated complete Ancient Language dictionary.
- [`ANCIENT_LANGUAGE_STYLE_GUIDE.md`](docs/ANCIENT_LANGUAGE_STYLE_GUIDE.md) — conventions for designing new vocabulary.
- [`WORD_OF_WORDS_REBUILD.md`](docs/WORD_OF_WORDS_REBUILD.md) — Word of Words architecture and lifecycle.
- [`MIND_DUEL_PHASE6.md`](docs/MIND_DUEL_PHASE6.md) — mind-duel implementation status.
- [`DRAGON_FLUX_PORT_PASS1.md`](docs/DRAGON_FLUX_PORT_PASS1.md) — notes on compositional VFX/mechanics brought into Dragon Speech's own language system.
- [`BARRIER_COLLISION_COMPOSITION_PASS_1_7_3.md`](docs/BARRIER_COLLISION_COMPOSITION_PASS_1_7_3.md) — spell/barrier collision and construct composition.
- [`VOID_BARRIER_LANGUAGE_PASS_1_7_4.md`](docs/VOID_BARRIER_LANGUAGE_PASS_1_7_4.md) — advanced barrier, Void and Fate language.
- [`BARRIER_ANALYSIS_REINFORCEMENT_PASS_1_7_5.md`](docs/BARRIER_ANALYSIS_REINFORCEMENT_PASS_1_7_5.md) — barrier analysis and reinforcement.
- [`BARRIER_CONSTRUCT_FORM_PASS_1_7_6.md`](docs/BARRIER_CONSTRUCT_FORM_PASS_1_7_6.md) — spinning constructs and multiplicity behavior.

`DICTIONARY.md` is generated from live word data and should not be edited manually. Use the generator scripts in `docs/` when the vocabulary changes.

---

## Design Philosophy

Dragon Speech is built around several recurring principles:

1. **Magic should be spoken/composed, not selected from a fixed hotbar of spells.**
2. **Precise language should reward the player.** Longer does not automatically mean more expensive.
3. **Knowledge is progression.** Finding the right word matters as much as raw statistics.
4. **Practice matters.** Attunement makes repeated use of a domain more efficient.
5. **Power should have consequences.** Stamina, dangerous drain behavior, backlash, wards, resistance, and counterplay prevent powerful language from being free.
6. **Advanced magic should emerge from composition.** Barriers, constructs, fields, multiplicity, and targeting should combine where the grammar permits instead of existing only as isolated abilities.
7. **Dragons and minds are systems, not decorations.** Bonding, Dragon Hearts, mental resistance, true names, and mind duels are integrated with the same magic/progression framework.
8. **Server authority matters.** Important gameplay state and casting decisions are resolved server-side, with client screens acting as interfaces to the same underlying systems.

---

## License

This repository currently includes the **CC0 1.0 Universal** license. See [`LICENSE`](LICENSE) for the full license text.

---

### Developer note

This README describes the systems present in the current repository rather than promising that every feature is final or fully balanced. Dragon Speech is under active development, and debug commands, tuning values, interfaces, vocabulary, world generation, and individual mechanics may change between builds.
