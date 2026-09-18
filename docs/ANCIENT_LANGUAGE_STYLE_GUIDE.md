# Dragon Speech — Ancient Language Style Guide

This is an **original constructed language** for the mod. It draws its phonetic
character from real, public-domain historical languages (Old Norse, Old
English) the way Paolini's own Ancient Language did — but the specific words
here are ours, not his. Do not add his book vocabulary (`brisingr`, `waíse
heill`, `atra esterní ono thelduin`, etc.) to this list; build new words using
the rules below instead.

## Sound inventory

**Vowels:** a, e, i, o, u, y, æ (short); á, é, í, ó, ú, ý (long/stressed)

**Consonants:** t, d, k, g, þ (rendered `th` in filenames/IDs), f, v, s, r, l,
n, m, h, w

**Common onset clusters:** br, dr, fr, gr, kr, thr, skr, st, sk, sp, sl, sn, sv

**Common codas:** r, n, m, l, s, t, k, d, g, th, ll, nn, rn, st

**Syllable shape:** (C)(C)V(C), 1–3 syllables per word. Longer, more layered
words should generally trend toward higher precision (see below) — this
mirrors the idea that a more "complete" word carries more exact meaning.

## Domain sound-symbolism (soft guidelines, not hard rules)

| Domain  | Lean toward                          | Feel                  |
|---------|---------------------------------------|------------------------|
| Fire    | hard plosives (k, t, br, kr), a/i     | sharp, sudden          |
| Water   | liquids/nasals (l, m, n, r), o/u      | flowing, rounded       |
| Earth   | heavy stops (d, g, th), a/o           | grounded, slow         |
| Air     | fricatives/sibilants (s, f, h, w), i/í| light, quick           |
| Life    | soft consonants (l, n, v), open vowels| warm                   |
| Death   | voiceless/breathy (th, h, s), closed  | cold                   |
| Mind    | nasal + liquid combos (m, n, l, hu-)  | resonant               |
| Force   | hard clusters (kr, dr, thr, str)      | powerful               |
| Motion  | quick consonants (t, d, r), short     | swift                  |
| Binding | doubled/knotted consonants (rn, nn)   | tight, wound           |
| Truth   | clear vowels, minimal clusters        | precise, unadorned     |

## Precision ladders (synonym groups)

Every domain's core verbs should exist in a 2–4 word **precision ladder**,
tied together with a shared `synonym_group` id:

1. **Vague** (precision ~0.3–0.4) — a rough, imprecise version, usually the
   first word a player finds. Costs more because the game must assume
   worst-case scope when resolving it.
2. **Working** (precision ~0.6–0.7) — a real, usable version. Solid mid-game
   vocabulary.
3. **Precise** (precision ~0.85–0.95) — tightly bounded, expensive to
   discover (elven_trial tier), cheap to cast once known.

Gate each rung behind the previous one via `prerequisite_words`, so players
can't stumble onto the precise form before the vague one narratively makes
sense.

## Category conventions

- `verb` — the action itself (ignite, mend, reach)
- `noun_target` — what's acted on (fire, water, a mind)
- `modifier` — degree/manner words (greatly, slightly, swiftly)
- `scope` — bounds the effect ("this", "all within reach")
- `control` — grammar particles like the universal stop-word; keep this
  category small and mostly fixed
- `binding` — oath/ward words; these are what make a spell persistent

## Worked example: the Fire ignite ladder

```
vidbrenn  (0.30, verb, fire)  -> "heat stirs, unfocused"
kyndla    (0.60, verb, fire)  -> "to kindle" [requires vidbrenn]
brenlokk  (0.92, verb, fire)  -> "controlled combustion of a bound target" [requires kyndla]
```

This is the reference pattern — copy this shape for every new domain ladder.

## Generating a new word's file

1. Pick the domain, category, and where it sits in a precision ladder (or
   mark it standalone if it doesn't need one).
2. Coin the word using the sound rules above — lean on the domain's
   sound-symbolism table for the phonetic feel.
3. Hash it: `java com.dragonspeech.word.WordHashing yourwordhere`
   (see `WordHashing.java`'s standalone `main()`).
4. Copy `brenlokk.json` as a template and fill in the fields.
5. Drop it in `data/dragonspeech/dragonspeech_words/<id>.json`.

## Channeled words

Some verbs back a *held* effect instead of an instant one - the effect
handler applies repeatedly (roughly once per second) for as long as the
caster keeps the word open, draining a little stamina each pulse instead
of one lump sum. These are stopped early with the CONTROL word ("letta").

There's no separate WordCategory for this - it's a property of which
`effect_handler` the word points to (a channeled handler has
`isChanneled() = true` in code), not of the word data itself. When coining
a channeled verb:

- Gate it behind the equivalent instant verb as a prerequisite (e.g.
  `haldthrysta`, "hold-force," requires already knowing `thrysta`) - a
  sustained technique should feel like a refinement of the basic one, not
  a shortcut around it.
- Keep the meaning distinct in flavor from its instant counterpart - not
  just "the same thing but longer." `thrysta` is a shove; `haldthrysta` is
  steady, ongoing pressure.

## Current starter vocabulary (23 words)

Fire: `vidbrenn`, `kyndla`, `brenlokk`, `eldr`
Water: `vatnhrer`, `streyma`, `flodbinda`, `vatn`
Mind: `hugsnert`, `hugleita`, `hugbinda`, `hugr`
Force: `thrysta`, `thrystbinda`, `haldthrysta` (channeled), `mikla`, `litla`
Life: `graeda`, `graedbinda`
Binding: `verja`
Scope: `thetta`
Control: `letta`

Still needed: full ladders for Earth, Air, Death, Motion, Truth, plus
`noun_target` and `modifier` words to round out every domain. Worth a
dedicated vocabulary-expansion pass once the core mechanics are further
along, rather than trying to fill this out all at once disconnected from
what the mechanics actually need.
