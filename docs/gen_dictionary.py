import json, os
from collections import defaultdict

# Resolved relative to this script's own location (docs/gen_dictionary.py)
# rather than a hardcoded absolute path, so this runs correctly on any
# machine/checkout of the project instead of only the one it was written on.
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(_ROOT, "src", "main", "resources", "data", "dragonspeech", "dragonspeech_words")
OUT = os.path.join(_ROOT, "docs", "DICTIONARY.md")

words = []
for fname in sorted(os.listdir(SRC)):
    with open(os.path.join(SRC, fname)) as f:
        words.append(json.load(f))

by_cat = defaultdict(list)
for w in words:
    by_cat[w["category"]].append(w)

CAT_ORDER = ["verb", "noun_target", "modifier", "scope", "binding", "control"]
CAT_TITLES = {
    "verb": "Verbs (actions - each is bound to an effect)",
    "noun_target": "Nouns (things - they sharpen a sentence's precision)",
    "modifier": "Modifiers & Particles (manner, degree, direction, and the sentence glue)",
    "scope": "Scope (how wide the working reaches)",
    "binding": "Binding (wards and oaths)",
    "control": "Control (ending and stilling a working)",
}

lines = []
lines.append("# Dragon Speech - Dictionary of the Old Tongue")
lines.append("")
lines.append("*Generated from the live word data - regenerate with `gen_dictionary.py` whenever the vocabulary changes. "
             "Never edit this file by hand; it will be overwritten.*")
lines.append("")
lines.append(f"**{len(words)} words.**")
lines.append("")
lines.append("## How sentences work")
lines.append("")
lines.append("A spell is a sentence, and the cost formula reads the WHOLE sentence: the average precision of every "
             "verb, noun, modifier, and binding word in it sets the cost multiplier. Word COUNT never appears in the "
             "formula. This means a long, exact sentence is often CHEAPER than a short vague one:")
lines.append("")
lines.append("> `thrysta uppa` *(\"push... up-ish\")* - two words, low average precision, the working must assume the")
lines.append("> worst about your intent, and charges you for it.")
lines.append(">")
lines.append("> `thrystbinda uppa ok frama med stodugt afl thetta` *(\"exert bound force upwards and forwards with")
lines.append("> constant strength upon this\")* - eight words, nearly all at precision 0.9+, and the working costs a")
lines.append("> fraction as much. Precision is mercy. Speak fully.")
lines.append("")
lines.append("Grammar patterns the working understands:")
lines.append("- **verb** [+ nouns, modifiers, scope] -> a cast")
lines.append("- **binding word alone** -> raise the ward it carries")
lines.append("- **control word alone** -> stop a held/channeled working")
lines.append("- **control + binding** -> release your own ward of that kind")
lines.append("")

for cat in CAT_ORDER:
    if cat not in by_cat:
        continue
    lines.append(f"## {CAT_TITLES[cat]}")
    lines.append("")
    by_dom = defaultdict(list)
    for w in by_cat[cat]:
        by_dom[w["domain"]].append(w)
    for dom in sorted(by_dom):
        lines.append(f"### {dom.capitalize()}")
        lines.append("")
        lines.append("| Word | Meaning | Precision | Found via | Notes |")
        lines.append("|---|---|---|---|---|")
        for w in sorted(by_dom[dom], key=lambda x: x["precision"]):
            notes = []
            if w.get("effect_handler"):
                notes.append("effect: `" + w["effect_handler"].split(":")[1] + "`")
            if w.get("prerequisite_words"):
                notes.append("requires: " + ", ".join(p.split(":")[1] for p in w["prerequisite_words"]))
            if w.get("modifier_magnitude"):
                notes.append(f"magnitude {w['modifier_magnitude']:+.1f}")
            if w.get("scope_radius"):
                notes.append(f"reach {w['scope_radius']:.0f} blocks")
            if w.get("ward_type"):
                notes.append("wards: " + w["ward_type"])
            if w.get("wound_type"):
                notes.append("mends: " + w["wound_type"] + " wounds")
            if w.get("summon_type"):
                notes.append("summons: " + w["summon_type"])
            if w.get("block_type"):
                notes.append("shapes: " + w["block_type"])
            if w.get("tool_material"):
                notes.append("material: " + w["tool_material"])
            if w.get("tool_type"):
                notes.append("weapon: " + w["tool_type"])
            if w.get("risk_tier") in ("severe", "catastrophic"):
                notes.append("guess-risk: " + w["risk_tier"].upper())
            lines.append(f"| **{w['true_name']}** | {w['meaning']} | {w['precision']:.2f} "
                         f"| {w['discovery_method'].replace('_',' ')} | {'; '.join(notes)} |")
        lines.append("")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"dictionary written: {len(words)} words -> {OUT}")
