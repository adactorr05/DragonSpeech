"""
Generates docs/DICTIONARY.html - a searchable, visual reference for the
Old Tongue, built from the exact same word JSON files as gen_dictionary.py.

Why a second generator instead of replacing the markdown one: DICTIONARY.md
stays useful for git diffs and quick grep/plaintext reading. This one is
for a HUMAN sitting down to actually learn or look something up - it groups
each domain's verbs/nouns into their crude -> precise chains (the single
most important structural idea in this language, and the thing a flat
precision-sorted table hides), and lets you search/filter live instead of
scrolling ~130 words of tables.

Regenerate whenever the vocabulary changes, same as gen_dictionary.py.
Never edit the OUTPUT file by hand; it will be overwritten.
"""

import json, os
from collections import defaultdict

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(_ROOT, "src", "main", "resources", "data", "dragonspeech", "dragonspeech_words")
OUT = os.path.join(_ROOT, "docs", "DICTIONARY.html")

CAT_ORDER = ["verb", "noun_target", "modifier", "scope", "binding", "control"]
CAT_META = {
    "verb":         ("Verbs", "Actions - each is bound to an effect"),
    "noun_target":  ("Nouns", "Things - they sharpen a sentence's precision"),
    "modifier":     ("Modifiers & Particles", "Manner, degree, direction, and the sentence glue"),
    "scope":        ("Scope", "How wide the working reaches"),
    "binding":      ("Binding", "Wards and oaths"),
    "control":      ("Control", "Ending and stilling a working"),
}

DOMAIN_COLOR = {
    "fire":    "#e0672a",
    "water":   "#3f7fa6",
    "earth":   "#8a6a42",
    "air":     "#9fd0cf",
    "life":    "#5e9b4e",
    "death":   "#6b5b7a",
    "mind":    "#8a63c9",
    "force":   "#5c6b8a",
    "motion":  "#2f9e8f",
    "binding": "#c9a227",
    "truth":   "#cfd3d6",
    "time":    "#70b9c7",
    "gravity": "#6e68ad",
    "fate":    "#d0a64c",
    "void":    "#5f3a73",
    "weapon":  "#8f8f9c",
}

RISK_COLOR = {
    "trivial":      "#6b7a63",
    "moderate":     "#c9922a",
    "severe":       "#c1562f",
    "catastrophic": "#9b2226",
}


def load_words():
    words = []
    for fname in sorted(os.listdir(SRC)):
        with open(os.path.join(SRC, fname)) as f:
            words.append(json.load(f))
    return words


def short(ref):
    """dragonspeech:steinn -> steinn"""
    return ref.split(":")[1] if ref else ref


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;"))


def tag_chips(w):
    chips = []
    type_tag = word_type_tag(w)
    if type_tag:
        chips.append(("type", type_tag))
    if w.get("effect_handler"):
        chips.append(("effect", short(w["effect_handler"])))
    if w.get("summon_type"):
        chips.append(("summons", w["summon_type"]))
    if w.get("wound_type"):
        chips.append(("mends", w["wound_type"] + " wounds"))
    if w.get("ward_type"):
        chips.append(("wards", w["ward_type"]))
    if w.get("scope_radius"):
        chips.append(("reach", f"{w['scope_radius']:.0f} blocks"))
    if w.get("modifier_magnitude"):
        chips.append(("magnitude", f"{w['modifier_magnitude']:+.1f}"))
    if w.get("direction"):
        chips.append(("direction", w["direction"]))
    if w.get("tool_material"):
        chips.append(("material", w["tool_material"]))
    if w.get("tool_type"):
        chips.append(("weapon", w["tool_type"]))
    return chips


def word_type_tag(w):
    """The new Type axis the person asked for - orthogonal to category/domain.
    Mob = names a summonable creature. Block = names a shapeable material.
    A word can be at most one (the current vocabulary never overlaps),
    but this returns '' rather than assuming that stays true forever."""
    if w.get("summon_type"):
        return "mob"
    if w.get("block_type"):
        return "block"
    if w.get("tool_material"):
        return "material"
    if w.get("tool_type"):
        return "weapon"
    return ""


def card_html(w, depth, extra_prereqs):
    risk = w.get("risk_tier", "trivial")
    risk_color = RISK_COLOR.get(risk, "#6b7a63")
    precision = w["precision"]
    chips = tag_chips(w)
    chip_html = "".join(
        f'<span class="chip"><span class="chip-k">{esc(k)}</span>{esc(v)}</span>' for k, v in chips
    )
    extra_html = ""
    if extra_prereqs:
        extra_html = (
            '<div class="also-draws">also draws on: '
            + ", ".join(f'<span class="also-word">{esc(p)}</span>' for p in extra_prereqs)
            + "</div>"
        )
    indent_class = f"depth-{min(depth, 3)}"
    arrow = '<span class="chain-arrow">&#8627;</span>' if depth > 0 else ""

    return f"""
    <article class="card {indent_class}" data-name="{esc(w['true_name'])}" data-meaning="{esc(w['meaning'].lower())}"
              data-domain="{esc(w['domain'])}" data-category="{esc(w['category'])}" data-risk="{esc(risk)}"
              data-type="{esc(word_type_tag(w))}">
      <div class="card-top">
        <h3 class="true-name">{arrow}{esc(w['true_name'])}</h3>
        <span class="risk-pill" style="--risk-color:{risk_color}">{esc(risk)}</span>
      </div>
      <p class="meaning">{esc(w['meaning'])}</p>
      <div class="precision-row">
        <div class="precision-bar"><div class="precision-fill" style="width:{precision*100:.0f}%"></div></div>
        <span class="precision-num">{precision:.2f}</span>
      </div>
      <div class="chip-row">{chip_html}</div>
      {extra_html}
      <div class="found-via">found via {esc(w['discovery_method'].replace('_',' '))}</div>
    </article>
    """


def build_family_html(entries, category):
    """Groups words into crude->precise chains via in-group prerequisite
    links (same category), rendering standalone words as single-node
    chains. Any prerequisite pointing OUTSIDE this category/domain group
    is preserved as an 'also draws on' note rather than silently dropped."""
    by_name = {w["true_name"]: w for w in entries}
    names_in_group = set(by_name)

    children = defaultdict(list)
    roots = []
    extra_of = {}

    for w in entries:
        prereqs = [short(p) for p in w.get("prerequisite_words", [])]
        in_group = [p for p in prereqs if p in names_in_group]
        out_group = [p for p in prereqs if p not in names_in_group]
        extra_of[w["true_name"]] = out_group
        if in_group:
            for p in in_group:
                children[p].append(w["true_name"])
        else:
            roots.append(w["true_name"])

    visited = set()
    out = []

    def render(name, depth):
        if name in visited:
            return  # already rendered under an earlier parent - words like
                     # skuggvaettr (requires BOTH vaettr and skuggi) would
                     # otherwise get drawn once per parent instead of once total
        visited.add(name)
        out.append(card_html(by_name[name], depth, extra_of.get(name, [])))
        for c in sorted(children.get(name, []), key=lambda n: by_name[n]["precision"]):
            render(c, depth + 1)

    for r in sorted(roots, key=lambda n: by_name[n]["precision"]):
        render(r, 0)
    for w in entries:  # safety net against any cycle/orphan
        if w["true_name"] not in visited:
            render(w["true_name"], 0)

    return "".join(out)


def main():
    words = load_words()
    by_cat = defaultdict(list)
    for w in words:
        by_cat[w["category"]].append(w)

    sections = []
    for cat in CAT_ORDER:
        if cat not in by_cat:
            continue
        title, subtitle = CAT_META[cat]
        by_dom = defaultdict(list)
        for w in by_cat[cat]:
            by_dom[w["domain"]].append(w)

        domain_blocks = []
        for dom in sorted(by_dom, key=lambda d: d):
            color = DOMAIN_COLOR.get(dom, "#888")
            family_html = build_family_html(by_dom[dom], cat)
            domain_blocks.append(f"""
            <div class="domain-block" style="--domain-color:{color}">
              <h4 class="domain-title">{esc(dom.capitalize())} <span class="domain-count">{len(by_dom[dom])}</span></h4>
              <div class="card-grid">{family_html}</div>
            </div>
            """)

        sections.append(f"""
        <section class="cat-section" id="cat-{cat}" data-cat="{cat}">
          <div class="cat-heading">
            <h2>{esc(title)}</h2>
            <p class="cat-subtitle">{esc(subtitle)}</p>
          </div>
          {"".join(domain_blocks)}
        </section>
        """)

    filter_chips_cat = "".join(
        f'<button class="filter-chip" data-filter-cat="{cat}">{CAT_META[cat][0]}</button>'
        for cat in CAT_ORDER if cat in by_cat
    )
    filter_chips_domain = "".join(
        f'<button class="filter-chip domain-chip" data-filter-domain="{d}" style="--domain-color:{DOMAIN_COLOR[d]}">{d.capitalize()}</button>'
        for d in sorted(DOMAIN_COLOR)
    )
    filter_chips_type = "".join(
        f'<button class="filter-chip type-chip" data-filter-type="{t}">{label}</button>'
        for t, label in (("mob", "Type: Mob"), ("block", "Type: Block"), ("material", "Type: Material"), ("weapon", "Type: Weapon"))
    )

    html = HTML_TEMPLATE.format(
        word_count=len(words),
        sections="".join(sections),
        filter_chips_cat=filter_chips_cat,
        filter_chips_domain=filter_chips_domain,
        filter_chips_type=filter_chips_type,
    )

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write(html)
    print(f"HTML dictionary written: {len(words)} words -> {OUT}")


HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Dragon Speech - Grimoire of the Old Tongue</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@500;700&family=Source+Sans+3:wght@400;600&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>
:root {{
  --bg: #16130f;
  --surface: #201a13;
  --surface-2: #241d15;
  --border: #362c1e;
  --ink: #e8e0d2;
  --ink-muted: #a89c86;
  --ember: #d9a441;
  --font-display: 'Cinzel', serif;
  --font-body: 'Source Sans 3', -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, monospace;
}}
* {{ box-sizing: border-box; }}
body {{
  margin: 0;
  background: radial-gradient(ellipse at top, #1c1811 0%, var(--bg) 55%);
  color: var(--ink);
  font-family: var(--font-body);
  line-height: 1.5;
  padding-bottom: 4rem;
}}
a {{ color: var(--ember); }}

header.hero {{
  padding: 3.5rem 1.5rem 2rem;
  text-align: center;
  border-bottom: 1px solid var(--border);
  background:
    linear-gradient(180deg, rgba(217,164,65,0.06), transparent 60%);
}}
header.hero .eyebrow {{
  font-family: var(--font-mono);
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-size: 0.72rem;
  color: var(--ink-muted);
}}
header.hero h1 {{
  font-family: var(--font-display);
  font-weight: 700;
  font-size: clamp(2rem, 5vw, 3.2rem);
  margin: 0.4rem 0 0.3rem;
  color: var(--ink);
  text-shadow: 0 0 22px rgba(217,164,65,0.25);
}}
header.hero .count {{
  color: var(--ember);
  font-family: var(--font-mono);
}}
header.hero .subtitle {{
  color: var(--ink-muted);
  max-width: 640px;
  margin: 0.6rem auto 0;
  font-size: 0.95rem;
}}

.legend {{
  max-width: 760px;
  margin: 1.75rem auto 0;
  padding: 1rem 1.25rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 0.88rem;
  color: var(--ink-muted);
}}
.legend b {{ color: var(--ink); }}
.legend .chain-demo {{ font-family: var(--font-mono); color: var(--ember); }}

.controls {{
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(22,19,15,0.92);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--border);
  padding: 0.85rem 1.25rem;
}}
.controls-inner {{ max-width: 1100px; margin: 0 auto; }}
#search {{
  width: 100%;
  max-width: 420px;
  padding: 0.6rem 0.9rem;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--ink);
  font-family: var(--font-body);
  font-size: 0.95rem;
}}
#search:focus {{ outline: 2px solid var(--ember); outline-offset: 1px; }}
.filter-row {{ display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.7rem; }}
.filter-chip {{
  font-family: var(--font-mono);
  font-size: 0.72rem;
  padding: 0.3rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--ink-muted);
  cursor: pointer;
}}
.filter-chip:hover {{ color: var(--ink); }}
.filter-chip.active {{ color: var(--bg); background: var(--ember); border-color: var(--ember); }}
.domain-chip.active {{ background: var(--domain-color); border-color: var(--domain-color); }}
.type-chip.active {{ background: #4fb8a8; border-color: #4fb8a8; color: var(--bg); }}
.filter-chip:focus-visible {{ outline: 2px solid var(--ember); outline-offset: 2px; }}

main {{ max-width: 1100px; margin: 0 auto; padding: 0 1.25rem; }}

.cat-section {{ margin-top: 3rem; }}
.cat-heading h2 {{
  font-family: var(--font-display);
  font-size: 1.6rem;
  margin-bottom: 0.1rem;
  border-left: 3px solid var(--ember);
  padding-left: 0.6rem;
}}
.cat-subtitle {{ color: var(--ink-muted); margin: 0 0 1.2rem 0.75rem; font-size: 0.88rem; }}

.domain-block {{ margin-bottom: 1.6rem; }}
.domain-title {{
  font-family: var(--font-mono);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--domain-color);
  border-bottom: 1px solid var(--border);
  padding-bottom: 0.35rem;
  margin: 0 0 0.8rem;
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}}
.domain-count {{
  font-size: 0.7rem;
  color: var(--ink-muted);
  background: var(--surface-2);
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
}}

.card-grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0.7rem; }}

.card {{
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 3px solid var(--domain-color);
  border-radius: 8px;
  padding: 0.75rem 0.85rem;
}}
.card.depth-1 {{ margin-left: 1.1rem; }}
.card.depth-2 {{ margin-left: 2.2rem; }}
.card.depth-3 {{ margin-left: 3.3rem; }}
.chain-arrow {{ color: var(--domain-color); margin-right: 0.3rem; font-weight: 400; }}

.card-top {{ display: flex; justify-content: space-between; align-items: baseline; gap: 0.5rem; }}
.true-name {{
  font-family: var(--font-display);
  font-size: 1.15rem;
  margin: 0;
  color: var(--ink);
}}
.risk-pill {{
  font-family: var(--font-mono);
  font-size: 0.62rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--risk-color);
  border: 1px solid var(--risk-color);
  border-radius: 999px;
  padding: 0.08rem 0.45rem;
  white-space: nowrap;
}}
.meaning {{ color: var(--ink-muted); font-size: 0.88rem; margin: 0.3rem 0 0.55rem; }}

.precision-row {{ display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; }}
.precision-bar {{ flex: 1; height: 5px; background: var(--surface-2); border-radius: 999px; overflow: hidden; }}
.precision-fill {{ height: 100%; background: linear-gradient(90deg, var(--domain-color), var(--ember)); }}
.precision-num {{ font-family: var(--font-mono); font-size: 0.72rem; color: var(--ink-muted); width: 2.4em; text-align: right; }}

.chip-row {{ display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 0.3rem; }}
.chip {{
  font-family: var(--font-mono);
  font-size: 0.68rem;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 5px;
  padding: 0.1rem 0.4rem;
  color: var(--ink-muted);
}}
.chip-k {{ color: var(--ember); margin-right: 0.3em; }}

.also-draws {{ font-size: 0.72rem; color: var(--ink-muted); margin-bottom: 0.3rem; }}
.also-word {{ font-family: var(--font-mono); color: var(--ink); }}

.found-via {{ font-size: 0.68rem; color: var(--ink-muted); opacity: 0.7; }}

.empty-state {{
  display: none;
  text-align: center;
  color: var(--ink-muted);
  padding: 3rem 1rem;
  font-family: var(--font-mono);
}}

footer {{
  text-align: center;
  color: var(--ink-muted);
  font-size: 0.75rem;
  margin-top: 3rem;
  padding: 0 1.5rem;
}}

@media (prefers-reduced-motion: reduce) {{
  * {{ scroll-behavior: auto !important; }}
}}
</style>
</head>
<body>

<header class="hero">
  <div class="eyebrow">Dragon Speech</div>
  <h1>Grimoire of the Old Tongue</h1>
  <div class="count">{word_count} words</div>
  <p class="subtitle">Every word the working understands, grouped by domain and laid out as crude &rarr; precise chains, since precision - not sentence length - is what actually decides a spell's cost.</p>
</header>

<div class="legend">
  <p><b>How to read a chain:</b> <span class="chain-demo">villa &rarr; blekkja &rarr; gervimynd</span> - the same idea (confuse/deceive a mind), spoken with less and less guesswork. The indented, arrow-prefixed word requires the one above it. A crude word costs more to speak because the working has to assume the worst about what you meant; a precise one leaves nothing to guess.</p>
  <p><b>Risk pill</b> = how dangerous a wrong guess at this word is (hidden in-game until discovered). <b>Bar</b> = precision, 0-1. <b>Chips</b> = what the word actually does (effect handler, wards, summons, etc.) - if a verb has no <span class="chip-k" style="color:var(--ember)">effect</span> chip, it isn't castable as a spell on its own (e.g. the five base Mind words, which instead unlock permanent duel skills).</p>
</div>

<div class="controls">
  <div class="controls-inner">
    <input id="search" type="search" placeholder="Search a word or meaning... (e.g. 'freeze', 'thrysta', 'summon')" aria-label="Search the dictionary">
    <div class="filter-row" id="cat-filters">
      <button class="filter-chip active" data-filter-cat="all">All</button>
      {filter_chips_cat}
    </div>
    <div class="filter-row" id="domain-filters">
      <button class="filter-chip active" data-filter-domain="all">All domains</button>
      {filter_chips_domain}
    </div>
    <div class="filter-row" id="type-filters">
      <button class="filter-chip active" data-filter-type="all">All types</button>
      {filter_chips_type}
    </div>
  </div>
</div>

<main>
{sections}
<p class="empty-state" id="empty-state">No words match. Try a different search or filter.</p>
</main>

<footer>Generated from the live word data - regenerate with <code>gen_dictionary_html.py</code> whenever the vocabulary changes. Never edit this file by hand; it will be overwritten. See <code>DICTIONARY.md</code> for a plaintext/diff-friendly version.</footer>

<script>
(function() {{
  const search = document.getElementById('search');
  const catButtons = document.querySelectorAll('[data-filter-cat]');
  const domButtons = document.querySelectorAll('[data-filter-domain]');
  const typeButtons = document.querySelectorAll('[data-filter-type]');
  const cards = document.querySelectorAll('.card');
  const domainBlocks = document.querySelectorAll('.domain-block');
  const catSections = document.querySelectorAll('.cat-section');
  const emptyState = document.getElementById('empty-state');

  let activeCat = 'all';
  let activeDomain = 'all';
  let activeType = 'all';

  function applyFilters() {{
    const q = search.value.trim().toLowerCase();
    let anyVisible = false;

    cards.forEach(card => {{
      const matchesCat = activeCat === 'all' || card.dataset.category === activeCat;
      const matchesDomain = activeDomain === 'all' || card.dataset.domain === activeDomain;
      const matchesType = activeType === 'all' || card.dataset.type === activeType;
      const matchesQuery = !q || card.dataset.name.toLowerCase().includes(q) || card.dataset.meaning.includes(q);
      const show = matchesCat && matchesDomain && matchesType && matchesQuery;
      card.style.display = show ? '' : 'none';
      if (show) anyVisible = true;
    }});

    domainBlocks.forEach(block => {{
      const visibleCards = block.querySelectorAll('.card:not([style*="display: none"])');
      block.style.display = visibleCards.length ? '' : 'none';
    }});

    catSections.forEach(section => {{
      const visibleBlocks = Array.from(section.querySelectorAll('.domain-block')).some(b => b.style.display !== 'none');
      section.style.display = visibleBlocks ? '' : 'none';
    }});

    emptyState.style.display = anyVisible ? 'none' : 'block';
  }}

  search.addEventListener('input', applyFilters);

  catButtons.forEach(btn => btn.addEventListener('click', () => {{
    catButtons.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    activeCat = btn.dataset.filterCat;
    applyFilters();
  }}));

  domButtons.forEach(btn => btn.addEventListener('click', () => {{
    domButtons.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    activeDomain = btn.dataset.filterDomain;
    applyFilters();
  }}));

  typeButtons.forEach(btn => btn.addEventListener('click', () => {{
    typeButtons.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    activeType = btn.dataset.filterType;
    applyFilters();
  }}));
}})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
