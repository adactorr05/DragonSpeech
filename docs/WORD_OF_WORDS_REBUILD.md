# Dragon Speech 1.7.0 — Word of Words Rebuild

## Core rule
The Word of Words is now one generated Ancient-Language-style word per world, not a fixed phrase and not an operator-grant command. Its plaintext is 7–15 letters long, persisted inside that world save, and regenerated independently for different worlds.

**Speaking/constructing the Word by itself costs 0 stamina.** It only opens the Word of Words interface. Stamina is charged only when a player actually executes one of the interface actions.

The stable internal construction id is `dragonspeech:word_of_words`; the plaintext attached to that slot changes when the world Word changes.

## Learning and knowledge lifecycle
The current Word can be learned in either intended player-facing route:

1. Enter the exact current Word in the normal Dragon Speech Guess screen.
2. Speak the exact Word in chat.

A successful discovery records the current Word generation for that player. Once known:

- it appears as a normal usable word block in Spell Construction;
- it appears in the Grimoire under **✧ True Names ✧** as `Word of Words: <current word>`;
- it can be favorited like another known construction word;
- it can be submitted in saved/constructed sentences through its stable internal id.

The generated secret is deliberately **not** a normal WordRegistry/datapack entry, so it cannot leak from tablets, Scholar fragments, NPC vocabulary pools, datapack dumps, or ordinary registry enumeration.

## Used alone vs. in a sentence
### Word used by itself
A one-word construction containing only `dragonspeech:word_of_words`, or speaking the current secret alone in chat, opens the custom reality-editing GUI.

Opening the GUI costs **0 stamina**.

### Word used with other words
The Word remains in the normal composition instead of opening the GUI. It represents direct authority over the Ancient Language itself. In the current implementation, a valid working containing the Word of Words has its normal life-force/stamina cost multiplied by **0.35** (65% cheaper).

This applies to one-shot casts, sustained/channel costs, and ordinary ward creation. It does **not** discount Word-of-Words GUI actions; those reality-editing commands have their own costs.

The server re-verifies that the player knows the current generation before accepting the stable Word-of-Words construction id. A stale saved spell or modified client cannot use a reshuffled Word.

## Reshuffling
Every world Word has a generation number.

### Admin hard reset
`/dragonspeech reshufflewow`

- creates a new generated Word;
- increments the generation;
- removes knowledge of the old Word from everyone;
- severs Word-memory bindings too;
- immediately resynchronizes online players so the Word disappears from Spell Construction and the Grimoire.

Offline players retain only historical generation data; when they next join, it does not count as knowing the current generation.

### Word-powered change
In the GUI: **Change → CHANGE THE WORD OF WORDS** (700 stamina)

- creates a new Word and generation;
- ordinary players who knew only the old Word lose it;
- players who previously paid to bind the Word to memory follow the change and immediately know the replacement Word.

### Binding the Word to memory
In the GUI: **Bind → Bind the Word of Words to my memory** (500 stamina)

This protects knowledge only against a future change made **through the Word itself**. It deliberately does not survive the admin `reshufflewow` hard reset.

## Construction-screen stale knowledge handling
The construction archive is synchronized from server truth. If the Word is reshuffled while Spell Construction is already open:

- the old Word disappears from the archive;
- an already-placed Word block is purged from the current blueprint if the player no longer knows the current generation;
- Remembered Phrases never continue displaying the old plaintext secret. A saved phrase containing the stable Word slot shows `[forgotten Word]` while it is unknown, and resolves to the new current plaintext after the player knows the new generation.

Saved phrase IDs can retain the stable structural slot, but they cannot restore knowledge and the server rejects it until the current Word is legitimately known.

## Grimoire
The Word is intentionally not mixed into the ordinary alphabetical word list. When known, it is displayed in the Grimoire's **True Names** area. When the current generation is unknown, that entry disappears.

## GUI authority/session model
The interface is server-authoritative. Speaking/constructing the Word creates or resumes a short-lived session tied to the player and the entity/block/location they were looking at. The client sends only an action key/parameter. The server rechecks:

- session ownership and expiry;
- current Word knowledge;
- selected target/focus;
- action validity;
- action-specific stamina cost;
- available life-force before execution.

Invalid requests are rejected before stamina is spent whenever the target/state can be validated in advance.

A session can be reopened for roughly 60 seconds by speaking the Word again at the same focus. This is particularly useful for local Time reversal: capture the area at invocation, close the GUI, change the area, then reopen the same session and reverse it.

## GUI pages/actions
### Remove
- Remove one selected ward at a time.
- Remove one selected nearby trap/sigil at a time.

There is intentionally no blanket "remove every ward" button.

### Add
- Add individual ward types.
- Add a revival ward (very expensive).
- Prepare elemental sigils at the selected focus.

### Change
- Change ordinary world spell-cost law within bounded multipliers.
- Change the Word of Words itself.

Changing ordinary spell cost does not change the cost of Word-of-Words GUI commands.

### Halt
- Halt a selected player's spoken magic temporarily.
- Halt magic in an 8- or 16-block area temporarily.
- Freeze a selected mob in place.
- Freeze nearby creatures in an area.

### Time
- Fast-forward world time by 1,000 or 6,000 ticks.
- Create a local slowed-time field.
- Create a local stasis field.
- Reverse local state to the session's invocation snapshot.

Local reversal snapshots/restores blocks, block-entity NBT (containers/signs/etc.), living-entity state, removed mob NBT where possible, and world time. It is intentionally among the most expensive options.

### Reveal
- Reveal wards on the selected target.
- Reveal nearby traps.
- Reveal selected target state.

### Bind
- Bind the Word of Words to the player's memory across Word-powered changes.
- Bind a selected player's spoken magic temporarily.
- Bind a selected mob to its place temporarily.

### Restore
- Restore selected target health.
- Restore one selected depleted/damaged ward.

Dangerous actions use a confirmation page and show their stamina price before execution.

## Stamina/progression safeguards
- Merely opening the Word interface costs 0.
- Every executed GUI action has an explicit cost.
- The Word used in ordinary sentences reduces that ordinary working's cost but does not make it free.
- The generated Word is excluded from normal "every five vocabulary words" maximum-stamina milestones, preventing reshuffle/relearn loops from farming permanent stamina.

## Admin/testing commands
- `/dragonspeech revealwow` — reveals the current world Word to the command source; does not automatically teach it.
- `/dragonspeech reshufflewow` — hard-reset replacement; severs all old knowledge and memory bindings.
- `/dragonspeech resetvocab <player>` — also clears that player's Word-of-Words knowledge and memory binding.

## Recommended test sequence
1. Run `/dragonspeech revealwow` and copy the current secret for controlled testing.
2. Confirm the Word is absent from Spell Construction and True Names before learning it.
3. Enter the exact Word in the normal Guess screen. Confirm the learned message, then reopen Spell Construction and the Grimoire.
4. Confirm the generated plaintext appears as a construction block and under **True Names**.
5. Put only the Word block in the blueprint and press Construct. Confirm the custom GUI opens and stamina does not drop.
6. Build a valid ordinary sentence containing the Word, such as a normal elemental cast plus the Word modifier. Confirm the spell casts rather than opening the GUI and costs substantially less than the same sentence without it.
7. Open the Word GUI → Bind → bind the Word to memory. Confirm the 500 stamina cost.
8. Open Change → Change the Word of Words. Confirm the 700 stamina cost, GUI closes, and an anchored player immediately receives the newly generated plaintext in construction/True Names.
9. Test without memory binding on another player: after a Word-powered change, the old Word disappears from that player's archive/True Names.
10. Save a Remembered Phrase containing the Word, then lose Word knowledge. Confirm its old plaintext no longer appears and the slot is shown as `[forgotten Word]`.
11. Use `/dragonspeech reshufflewow`. Confirm even a memory-bound player loses the Word until the newly generated one is rediscovered.
12. For Time reversal: look at a location, open the Word GUI, close it, alter blocks/containers or kill a mob inside the snapshot area, re-speak the Word while focused on the same place within the session window, then use **Time → Reverse Local State**.

## Verification note
The changed Word-of-Words server classes, networking/casting integration, command integration, custom client screen, Grimoire integration, and the final construction-screen lifecycle delta were type-compiled against the project's mapped Minecraft 1.21.1/Fabric development classes. A normal Gradle wrapper build could not be executed in this environment because the required Gradle 9.5.1 distribution could not be downloaded here; IntelliJ/Gradle on the user's development machine remains the final full Loom build/runtime test.
