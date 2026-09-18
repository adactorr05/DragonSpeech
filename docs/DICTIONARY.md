# Dragon Speech - Dictionary of the Old Tongue

*Generated from the live word data - regenerate with `gen_dictionary.py` whenever the vocabulary changes. Never edit this file by hand; it will be overwritten.*

**238 words.**

## How sentences work

A spell is a sentence, and the cost formula reads the WHOLE sentence: the average precision of every verb, noun, modifier, and binding word in it sets the cost multiplier. Word COUNT never appears in the formula. This means a long, exact sentence is often CHEAPER than a short vague one:

> `thrysta uppa` *("push... up-ish")* - two words, low average precision, the working must assume the
> worst about your intent, and charges you for it.
>
> `thrystbinda uppa ok frama med stodugt afl thetta` *("exert bound force upwards and forwards with
> constant strength upon this")* - eight words, nearly all at precision 0.9+, and the working costs a
> fraction as much. Precision is mercy. Speak fully.

Grammar patterns the working understands:
- **verb** [+ nouns, modifiers, scope] -> a cast
- **binding word alone** -> raise the ward it carries
- **control word alone** -> stop a held/channeled working
- **control + binding** -> release your own ward of that kind

## Verbs (actions - each is bound to an effect)

### Air

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **glitra** | static stirs, unfocused sparking | 0.35 | ruin tablet | effect: `elemental_working` |
| **blasa** | air stirs and shoves, undirected | 0.40 | ruin tablet | effect: `push` |
| **eldingkast** | to hurl a bolt of lightning | 0.75 | mentor npc | effect: `elemental_working`; requires: glitra; guess-risk: SEVERE |
| **vindkast** | to hurl with a directed gust | 0.75 | mentor npc | effect: `push`; requires: blasa |
| **skyja** | to cloud; a lingering hanging mist | 0.80 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **thrumubinda** | to bind lightning along one precise bolt | 0.90 | elven trial | effect: `elemental_working`; requires: eldingkast; guess-risk: SEVERE |

### Binding

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **gala** | to chant a working into lasting shape - the general idea of enchanting, not any one working in particular | 0.85 | elven trial | effect: `apply_enchant`; guess-risk: CATASTROPHIC |
| **skjoldr** | to raise a shield of light, real and standing, before oneself | 0.85 | elven trial | effect: `barrier`; guess-risk: SEVERE |
| **arinheill** | the hearth's favor - hunger fades slower while this is worn | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **duljabol** | a curse of concealment - hides another working already bound into the same object from any eye that looks for it, though it never stops working | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **eldbit** | a burning bite - real vanilla Fire Aspect, bound into a blade | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **ennisbol** | a mark upon the brow - the wearer is marked ill, lastingly, and no unbinding lifts it | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **grafleysa** | no rest in the grave - the wearer can never be called back once the crossing is made, while this is worn | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **handheill** | a steady hand - a wrong guess at an unknown word costs you less than it should | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **haugbol** | bound to the grave - what carries this can never be taken off, nor lost to death, and whatever else it carries is bound the same way | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **herfang** | a taker's own luck, bound into a blade - real vanilla Looting | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **hlifd** | a shielding working - the real vanilla Protection, bound into armor and nothing else | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **hrinda** | a working of the shove - real vanilla Knockback, bound into a blade | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **hugopna** | an open mind - the wearer's own mind cannot be walled, and lies the easier open to another's reaching | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **hungrord** | a hungry word - every working spoken while this is worn costs more than it should, always | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **hvassa** | to make an edge bite deeper - the working behind a weapon's real vanilla Sharpness | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **kyrrafl** | quiet strength - stamina gathers back into you faster while this is worn | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **laekning** | a mending working - real vanilla Mending, drawing experience back into what carries it | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **leikni** | a worker's own quickness, lent to the tool in hand - real vanilla Efficiency | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **lifheill** | life's favor - calling life back to what has left costs you far less than it otherwise would | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **merkiheill** | a lasting sign - marks you place hold longer, and marks upon others are plainer to your eye | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **mjukhond** | a soft hand at the taking - real vanilla Silk Touch, bound into a tool | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **nafnheill** | a name half-heard - the true names of those nearby surface to you sooner than they otherwise would | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **seigla** | toughness bound into a thing, so it wears slower than it should - real vanilla Unbreaking | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **thunnhula** | a thin veil - the wearer's true name is the easier found by those who go looking | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **vaengheill** | the wing's favor - strengthens the bond to a dragon, and how readily it lends its own strength to you | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **visnatak** | a withering grip - strength stored in anything else the wearer carries fades the faster for it | 0.88 | elven trial | guess-risk: CATASTROPHIC |
| **eldgaldr** | to bind a ward against flame and burning into an object | 0.90 | elven trial | requires: eldverja; guess-risk: CATASTROPHIC |
| **fallgaldr** | to bind a ward against the fall and the earth's pull into an object | 0.90 | elven trial | requires: fallverja; guess-risk: CATASTROPHIC |
| **galdrverja** | to bind a ward into an object, so it carries the working rather than merely raising it | 0.90 | elven trial | requires: verja; guess-risk: CATASTROPHIC |
| **hoggaldr** | to bind a ward against the striking blow into an object | 0.90 | elven trial | requires: hoggverja; guess-risk: CATASTROPHIC |
| **sprengaldr** | to bind a ward against the bursting force into an object | 0.90 | elven trial | requires: sprengverja; guess-risk: CATASTROPHIC |
| **varnbinda** | to bind and raise a barrier as a fixed boundary in the place named | 0.92 | elven trial | effect: `barrier`; requires: varn, skjoldr; guess-risk: SEVERE |

### Death

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **kalla** | to call out, crude and undirected | 0.40 | ruin tablet | effect: `summon` |
| **kallbinda** | to call and bind a creature to service | 0.78 | elven trial | effect: `summon`; requires: kalla, vaettr; guess-risk: CATASTROPHIC |

### Earth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **gera** | to shape crudely, unstable and rough | 0.40 | ruin tablet | effect: `shape_block` |
| **herda** | to stiffen and slow, crude and undirected | 0.40 | ruin tablet | effect: `petrify` |
| **molva** | to crudely shatter and crumble | 0.40 | ruin tablet | effect: `sunder` |
| **sula** | to stack matter beneath oneself, crude and unsteady | 0.40 | ruin tablet | effect: `pillar` |
| **veggja** | to stack a rough barrier before oneself | 0.40 | ruin tablet | effect: `wall` |
| **grjotkasta** | to hurl broken matter, crude and undirected | 0.45 | mentor npc | effect: `hurl_block`; guess-risk: SEVERE |
| **brjota** | to sunder; to break stone and matter apart | 0.65 | ruin tablet | effect: `sunder`; guess-risk: SEVERE |
| **steinherda** | to harden flesh toward stone, unevenly | 0.75 | mentor npc | effect: `petrify`; requires: herda, steinn; guess-risk: SEVERE |
| **skapbinda** | to bind matter precisely into a named shape | 0.85 | elven trial | effect: `shape_block`; requires: gera; guess-risk: SEVERE |
| **sulbinda** | to raise a true pillar of bound matter, and ride it skyward | 0.85 | elven trial | effect: `pillar`; requires: sula; guess-risk: SEVERE |
| **veggbinda** | to raise a true wall of bound matter | 0.85 | elven trial | effect: `wall`; requires: veggja; guess-risk: SEVERE |
| **grjotbinda** | to hurl bound matter along a single true path | 0.88 | elven trial | effect: `hurl_block`; requires: grjotkasta; guess-risk: SEVERE |
| **steinbrjota** | to sunder stone along its truest seam | 0.90 | elven trial | effect: `sunder`; requires: brjota; guess-risk: SEVERE |
| **steinbinda** | to bind flesh utterly into stone | 0.92 | elven trial | effect: `petrify`; requires: steinherda; guess-risk: CATASTROPHIC |

### Fate

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **gaefa** | a working of fortune - real vanilla Fortune, more from the earth than the earth would otherwise give | 0.88 | elven trial | guess-risk: CATASTROPHIC |

### Fire

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **vidbrenn** | heat stirs; something grows warm, unfocused | 0.30 | ruin tablet | effect: `ignite` |
| **kyndla** | to kindle; spark caught to flame | 0.60 | mentor npc | effect: `ignite`; requires: vidbrenn; guess-risk: SEVERE |
| **narro** | to kindle, to set alight | 0.85 | mentor npc | effect: `ignite`; guess-risk: SEVERE |
| **brenlokk** | controlled combustion of a single bound target | 0.92 | elven trial | effect: `ignite`; requires: kyndla; guess-risk: SEVERE |

### Force

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **thrysta** | to press or push with unshaped, raw force | 0.50 | mentor npc | effect: `push` |
| **aflsuga** | to drain strength; to tear the force from a living thing | 0.65 | mentor npc | effect: `drain_stamina`; requires: thrysta; guess-risk: SEVERE |
| **draga** | to draw stored strength into oneself | 0.65 | elven trial | effect: `charge_item`; requires: skynja; guess-risk: SEVERE |
| **haldthrysta** | to hold force steady; a sustained, unyielding pressure | 0.75 | elven trial | effect: `channel_push`; requires: thrysta; guess-risk: SEVERE |
| **kasta** | to cast forth; to hurl a working as a bolt | 0.75 | mentor npc | effect: `elemental_working` |
| **kula** | to shape as an orb; a slow gathered sphere | 0.80 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **sprengja** | to burst outward; a working that explodes | 0.80 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **thrystbinda** | to exert force along a single, precise, bound path | 0.88 | elven trial | effect: `push`; requires: thrysta; guess-risk: SEVERE |

### Gravity

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **thyngja** | to weigh down, crude and undirected | 0.45 | mentor npc | effect: `gravity_scale`; guess-risk: SEVERE |
| **thyngdbinda** | to bind the pull of gravity itself, exact and sustained | 0.90 | elven trial | effect: `gravity_scale`; requires: thyngja; guess-risk: CATASTROPHIC |

### Life

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **lifga** | to rouse the flesh, crude and desperate | 0.35 | mentor npc | effect: `heal`; guess-risk: SEVERE |
| **eitra** | to taint, crude and sickening | 0.40 | ruin tablet | effect: `poison` |
| **graeda** | a rough, imprecise mending of flesh | 0.40 | mentor npc | effect: `heal`; guess-risk: SEVERE |
| **lifssuga** | to drain life; to tear the force of living from a thing | 0.65 | elven trial | effect: `drain_life`; requires: eitra; guess-risk: SEVERE |
| **aftrlifga** | to call life back to what has left; the crossing reversed | 0.75 | elven trial | effect: `resurrect`; requires: graedbinda, nar; guess-risk: CATASTROPHIC |
| **eitrbinda** | to bind poison precisely into a target | 0.85 | elven trial | effect: `poison`; requires: eitra; guess-risk: CATASTROPHIC |
| **umljomi** | a surrounding field; a steady aura | 0.85 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **graedbinda** | precise, tightly bounded mending of flesh | 0.88 | elven trial | effect: `heal`; requires: graeda; guess-risk: CATASTROPHIC |

### Mind

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **hugsnert** | a faint mental brush; unfocused sensing of a mind | 0.40 | mentor npc |  |
| **villa** | to confuse and mislead, crude and undirected | 0.40 | ruin tablet | effect: `confuse` |
| **skynja** | to sense the stored strength bound within a thing | 0.60 | elven trial | effect: `sense_stored_strength`; requires: hugsnert |
| **blekkja** | to deceive with a false seeming | 0.65 | mentor npc | effect: `confuse`; requires: villa; guess-risk: SEVERE |
| **hugleita** | to deliberately seek out and touch a mind | 0.70 | elven trial | requires: hugsnert; guess-risk: SEVERE |
| **hugrista** | to read the surface of a thought | 0.70 | elven trial | requires: hugleita; guess-risk: SEVERE |
| **hugvarna** | to wall one's own mind against another | 0.80 | elven trial | requires: hugsnert |
| **gervimynd** | to weave a false image indistinguishable from truth | 0.90 | elven trial | effect: `confuse`; requires: blekkja; guess-risk: SEVERE |
| **hambinda** | to bind oneself into another's skin; to wear its shape | 0.92 | elven trial | effect: `possess`; requires: hugbinda, hamr; guess-risk: CATASTROPHIC |
| **hugbinda** | to bind mind to mind with total precision | 0.92 | elven trial | requires: hugleita; guess-risk: CATASTROPHIC |

### Motion

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **hopa** | to lurch elsewhere, crude and disorienting | 0.40 | ruin tablet | effect: `teleport` |
| **lyfta** | to lift; to raise a thing against its weight | 0.65 | mentor npc | effect: `lift` |
| **vikja** | to step aside from here to there | 0.70 | mentor npc | effect: `teleport`; requires: hopa; guess-risk: SEVERE |
| **kyrra** | to still utterly; to hold a thing outside time's flow | 0.85 | elven trial | effect: `temporal_working`; requires: tidbinda; guess-risk: CATASTROPHIC |
| **svifbinda** | to bear aloft, bound and steady | 0.90 | elven trial | effect: `lift`; requires: lyfta; guess-risk: SEVERE |
| **heimbinda** | to bind oneself to a known place and return | 0.92 | elven trial | effect: `home_anchor`; requires: vikja; guess-risk: CATASTROPHIC |

### Time

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **tidbinda** | to bind time; to bend its flow around a thing | 0.90 | elven trial | effect: `temporal_working`; requires: tid; guess-risk: CATASTROPHIC |

### Truth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **geisla** | to cast as a ray; a straight unbroken beam | 0.85 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **hringr** | a ring; the working drawn in a circle | 0.85 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |
| **marka** | to mark; to leave a lasting sign upon | 0.85 | elven trial | effect: `mark`; guess-risk: SEVERE |
| **ristmark** | a placed mark; a rune that waits | 0.85 | elven trial | effect: `elemental_working`; requires: kasta; guess-risk: SEVERE |

### Void

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **thombinda** | to bind true absence precisely into the form, place, or mark that is named | 0.95 | elven trial | effect: `elemental_working`; requires: thomr, aflbinda; guess-risk: CATASTROPHIC |

### Water

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **kaldna** | cold gathers, unfocused and biting | 0.35 | ruin tablet | effect: `freeze` |
| **vatnhrer** | water stirs, unsettled and undirected | 0.35 | ruin tablet | effect: `push` |
| **streyma** | to stream; to flow with real intent | 0.65 | mentor npc | effect: `push`; requires: vatnhrer |
| **frysta** | to freeze; to still a thing with cold | 0.70 | elven trial | effect: `freeze`; requires: kaldna; guess-risk: SEVERE |
| **regnfalla** | to fall like rain; many scattered descending strikes | 0.85 | elven trial | effect: `elemental_working`; requires: regn; guess-risk: SEVERE |
| **flodbinda** | precisely bound and directed flow | 0.90 | elven trial | effect: `push`; requires: streyma; guess-risk: SEVERE |
| **isbinda** | to freeze a single bound target utterly | 0.92 | elven trial | effect: `freeze`; requires: frysta; guess-risk: SEVERE |

### Weapon

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **vopnkasta** | to hurl a weapon, crude and undirected | 0.45 | mentor npc | effect: `hurl_weapon`; guess-risk: SEVERE |
| **vopnbinda** | to hurl a bound weapon along a single true path | 0.88 | elven trial | effect: `hurl_weapon`; requires: vopnkasta; guess-risk: SEVERE |

## Nouns (things - they sharpen a sentence's precision)

### Air

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **stormr** | storm; the sky's anger | 0.75 | ruin tablet |  |
| **elding** | lightning itself | 0.80 | ruin tablet |  |
| **andi** | breath; the air within | 0.85 | ancient text |  |
| **thruma** | thunder | 0.85 | ancient text |  |
| **vindr** | wind; air in motion | 0.85 | ruin tablet |  |

### Binding

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **varn** | a barrier; a standing boundary that something is not meant to cross | 0.72 | ancient text | requires: verja; guess-risk: SEVERE |
| **fjotur** | a fetter; a binding imposed | 0.85 | elven trial | guess-risk: SEVERE |
| **eidr** | an oath; a binding freely taken | 0.90 | elven trial | guess-risk: SEVERE |

### Death

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **myrkr** | darkness | 0.80 | ancient text |  |
| **skuggi** | shadow; the shape darkness takes | 0.80 | ancient text |  |
| **vaettr** | a spirit; the shape a summoned creature takes | 0.80 | ancient text | guess-risk: SEVERE |
| **nar** | the dead; what life has left | 0.85 | elven trial | guess-risk: SEVERE |
| **beinvaettr** | a skeleton; a spirit bound in bone | 0.90 | elven trial | requires: vaettr, bein; summons: skeleton; guess-risk: SEVERE |
| **eldvaettr** | a blaze; a spirit bound in living fire | 0.90 | elven trial | requires: vaettr, eldr; summons: blaze; guess-risk: SEVERE |
| **holdvaettr** | a zombie; a spirit bound in rotten flesh | 0.90 | elven trial | requires: vaettr, hold; summons: zombie; guess-risk: SEVERE |
| **kongurvaettr** | a spider; a spirit bound in many legs and web | 0.90 | elven trial | requires: vaettr; summons: spider; guess-risk: SEVERE |
| **skuggvaettr** | an enderman; a spirit bound in shadow, standing outside the world it steps through | 0.90 | elven trial | requires: vaettr, skuggi; summons: enderman; guess-risk: SEVERE |
| **ulfvaettr** | a wolf; a spirit bound in fang and fur | 0.90 | elven trial | requires: vaettr; summons: wolf; guess-risk: SEVERE |

### Earth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **gull** | gold; the soft shining ore | 0.80 | mentor npc | requires: malmr; material: gold |
| **jord** | earth; the living soil | 0.80 | ruin tablet | shapes: dirt |
| **sandr** | sand; stone worn to grains | 0.80 | ruin tablet | shapes: sand |
| **jarn** | iron; ore struck true and forged | 0.82 | mentor npc | requires: malmr; material: iron |
| **malmr** | metal; ore in the vein | 0.85 | ancient text |  |
| **steinn** | stone | 0.85 | ruin tablet | shapes: stone; material: stone |
| **eldsteinn** | fire-stone; the rock of the burning deep, which never stops smouldering | 0.88 | elven trial | requires: eldr, steinn; shapes: netherrack |
| **demantr** | diamond; the unbreakable light caught in stone | 0.90 | elven trial | requires: steinn; material: diamond; guess-risk: SEVERE |
| **endasteinn** | end-stone; the pale stone of the final silence, from beyond the world's edge | 0.90 | elven trial | requires: enda, steinn; shapes: end_stone |
| **svartmalmr** | netherite; black ore that survives the burning deep | 0.93 | elven trial | requires: malmr, eldsteinn; material: netherite; guess-risk: SEVERE |

### Fire

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **glod** | ember; fire sleeping in coal | 0.75 | ruin tablet |  |
| **aska** | ash; what fire leaves behind | 0.80 | ruin tablet |  |
| **eldr** | fire itself, flame as a substance | 0.80 | ruin tablet | mends: burn wounds |
| **ljos** | light itself | 0.85 | ancient text |  |
| **logi** | living blaze; fire at its fullest | 0.90 | ancient text |  |

### Force

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **afl** | raw strength; force unshaped | 0.80 | mentor npc |  |
| **ferdafl** | force carried by motion; momentum or inertia | 0.88 | elven trial | requires: ferd, afl; guess-risk: SEVERE |

### Gravity

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **thungi** | weight; the burden of mass | 0.85 | ancient text |  |
| **thyngdarafl** | gravity; the force that pulls all things down | 0.90 | elven trial | requires: afl; guess-risk: SEVERE |

### Life

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **eitr** | poison; venom | 0.80 | ruin tablet |  |
| **vidr** | wood; the living tree | 0.80 | ruin tablet | shapes: wood; material: wood |
| **snara** | a flexible living tendril or vine | 0.82 | elven trial | requires: vidr; guess-risk: SEVERE |
| **bein** | bone | 0.85 | mentor npc | mends: bone wounds |
| **blod** | blood | 0.85 | mentor npc | mends: piercing wounds |
| **frae** | seed; life waiting | 0.85 | ancient text |  |
| **hold** | flesh | 0.85 | mentor npc | mends: gash wounds |
| **sprengd** | the burst-wound; flesh torn by the bursting force | 0.85 | elven trial | mends: blast wounds |

### Mind

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **draumr** | a dream | 0.80 | ancient text |  |
| **hugr** | the mind; the seat of thought | 0.85 | ancient text |  |
| **hugsun** | a thought | 0.85 | ancient text |  |
| **lygi** | a lie; a spoken falsehood | 0.85 | ancient text |  |
| **minni** | memory; what the mind keeps | 0.85 | elven trial | guess-risk: SEVERE |
| **sjonhverfing** | illusion; a false-seeming | 0.85 | elven trial | guess-risk: SEVERE |
| **hamr** | the skin; the living shape a body wears | 0.90 | elven trial | guess-risk: SEVERE |

### Motion

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **ferd** | motion; the act of going | 0.80 | ruin tablet |  |
| **hradi** | speed | 0.85 | ruin tablet |  |
| **stadr** | a place; a fixed point | 0.85 | ancient text |  |

### Time

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **tid** | time; the river all things stand in | 0.90 | ancient text | guess-risk: SEVERE |

### Truth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **ord** | a word; the smallest whole meaning | 0.90 | ancient text |  |
| **nafn** | a name; the shape of a thing in speech | 0.95 | elven trial | guess-risk: CATASTROPHIC |
| **sannr** | truth; what cannot be unsaid | 0.95 | elven trial | guess-risk: SEVERE |

### Void

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **thomr** | true absence; the Void where matter, force, and ordinary working are not | 0.88 | elven trial | requires: enda; guess-risk: CATASTROPHIC |

### Water

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **djup** | the deep; water beyond light | 0.80 | ancient text |  |
| **regn** | rain; water that falls | 0.80 | ruin tablet |  |
| **vatn** | water itself | 0.80 | ruin tablet |  |
| **is** | ice; water made still | 0.85 | ruin tablet |  |

### Weapon

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **haki** | pick; a hooked point for the breaking of stone | 0.80 | ruin tablet | weapon: pickaxe |
| **herfi** | hoe; a blade for the tilling of the field | 0.80 | ruin tablet | weapon: hoe |
| **skofla** | shovel; a broad blade for the turning of earth | 0.80 | ruin tablet | weapon: shovel |
| **kral** | claw or talon; a hooked cutting weapon of the hand | 0.82 | elven trial | guess-risk: SEVERE |
| **oxi** | axe; a heavy blade for the felling stroke | 0.85 | mentor npc | weapon: axe |
| **sverd** | sword; a blade for the hand | 0.85 | mentor npc | weapon: sword |
| **thrivoddr** | trident; a three-pronged spear for the deep | 0.85 | elven trial | weapon: trident; guess-risk: SEVERE |
| **voddr** | a long piercing point; the form of a spear or lance | 0.88 | elven trial | guess-risk: SEVERE |

## Modifiers & Particles (manner, degree, direction, and the sentence glue)

### Binding

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **vefja** | to wrap or weave a working closely around a thing | 0.82 | elven trial | requires: fjotur; guess-risk: SEVERE |
| **fjotbinda** | to bind a working as a flexible tether between two marks | 0.90 | elven trial | requires: fjotur; guess-risk: SEVERE |

### Force

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **ofsa** | with fury; to the utmost violence | 0.70 | elven trial | magnitude +1.0; guess-risk: SEVERE |
| **aflflyta** | to carry stolen strength into oneself | 0.85 | elven trial | requires: aflsuga; guess-risk: CATASTROPHIC |
| **telja** | to reckon what is carried, not just worn - the search widens to the whole pack | 0.85 | elven trial | requires: draga |
| **tvefalt** | twofold; doubled in measure | 0.85 | ancient text | magnitude +0.4 |
| **afla** | to empower; to pour strength into a working as stored power | 0.86 | ancient text |  |
| **thrett** | dense; compressed into little space without spreading | 0.86 | elven trial | requires: afl; magnitude +0.1; guess-risk: SEVERE |
| **aflbinda** | to bind a working to one's own strength; to sustain it from stamina instead of fixed stored power | 0.88 | elven trial | guess-risk: SEVERE |
| **margfalt** | manifold; many times over - stronger than tvefalt's doubling | 0.88 | ancient text | magnitude +0.8 |
| **samdraga** | to draw separate parts of a working together toward one point; to converge | 0.88 | elven trial | requires: draga; magnitude +0.1; guess-risk: SEVERE |
| **litla** | slightly; to a small degree | 0.90 | mentor npc | magnitude -0.6 |
| **mikla** | greatly; to a high degree | 0.90 | mentor npc | magnitude +0.6 |
| **stodugt** | with constant, unwavering force | 0.95 | ancient text |  |

### Life

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **hoegt** | gently; with care | 0.85 | ruin tablet | magnitude -0.3 |
| **lifflyta** | to carry stolen life into oneself | 0.85 | elven trial | requires: lifssuga; guess-risk: CATASTROPHIC |
| **brynna** | to feed the working from strength drawn from life nearby, not your own | 0.90 | elven trial | requires: aflsuga; guess-risk: CATASTROPHIC |

### Motion

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **sveira** | to spiral or revolve around a line or centre | 0.84 | elven trial | requires: ferd; guess-risk: SEVERE |
| **seint** | slowly; drawn out | 0.85 | ruin tablet |  |
| **snoggt** | swiftly; in a single breath | 0.85 | ruin tablet |  |
| **kringla** | a circle drawn to hold; the working stands as a lingering field rather than a single touch | 0.88 | elven trial | requires: umhverf; guess-risk: CATASTROPHIC |
| **kringferd** | motion around a centre; an orbit | 0.90 | elven trial | requires: ferd; guess-risk: SEVERE |
| **sveigja** | to bend a moving path aside without stopping it | 0.90 | elven trial | requires: ferd; guess-risk: SEVERE |
| **aftana** | backwards; away from the gaze | 0.95 | ruin tablet |  |
| **frama** | forwards; along the gaze | 0.95 | ruin tablet |  |
| **nidra** | downwards; toward the deep | 0.95 | ruin tablet |  |
| **uppa** | upwards; against the pull of the earth | 0.95 | ruin tablet |  |

### Truth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **blidr** | kind; blessed, gentle in nature | 0.85 | elven trial | requires: marka |
| **bur** | a cage; a working that holds what is within, not what is without | 0.85 | elven trial | requires: skjoldr; guess-risk: CATASTROPHIC |
| **flata** | to flatten; to draw the working out flat rather than around | 0.85 | elven trial | requires: skjoldr; guess-risk: SEVERE |
| **illr** | ill; cursed, foul in nature | 0.85 | elven trial | requires: marka; guess-risk: SEVERE |
| **kringlott** | round; drawn whole about a center, the same on every side | 0.85 | elven trial | requires: skjoldr; guess-risk: SEVERE |
| **teningr** | a cube; six faces, sharp and true | 0.85 | elven trial | requires: skjoldr; guess-risk: SEVERE |
| **kedjubinda** | to bind in a chain; leaping mark to mark | 0.88 | elven trial | guess-risk: SEVERE |
| **leitbinda** | to bind the working to seek its mark | 0.88 | elven trial | requires: marklaust; guess-risk: SEVERE |
| **marklaust** | without a bound target; released without a chosen mark | 0.88 | elven trial | guess-risk: SEVERE |
| **fran** | away from; outward from the named mark | 0.90 | elven trial | guess-risk: SEVERE |
| **samvefja** | to weave together; two elements as one working | 0.90 | elven trial | guess-risk: CATASTROPHIC |
| **varla** | barely; the least possible measure | 0.90 | ruin tablet | magnitude -0.8 |
| **an** | without; in the absence of | 1.00 | ruin tablet |  |
| **med** | with; by means of | 1.00 | ruin tablet |  |
| **ok** | and; joining one meaning to the next | 1.00 | ruin tablet |  |
| **til** | toward; unto | 1.00 | ruin tablet |  |

### Weapon

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **seida** | to conjure out of nothing; magic answering in place of matter | 0.85 | elven trial |  |
| **taka** | to take up what is already yours, rather than call it from nothing | 0.85 | mentor npc |  |

## Scope (how wide the working reaches)

### Truth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **viddum** | all within the wide reach | 0.50 | elven trial | reach 12 blocks; guess-risk: SEVERE |
| **umhverf** | all that surrounds; everything within reach | 0.55 | elven trial | reach 8 blocks |
| **naerum** | all that is near at hand | 0.70 | ancient text | reach 4 blocks |
| **sjalfan** | oneself; the speaker alone | 1.00 | mentor npc |  |
| **thetta** | this, here; the bound target at hand | 1.00 | mentor npc |  |

## Binding (wards and oaths)

### Binding

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **verja** | to ward; to bind protection around a target | 0.85 | mentor npc | wards: projectile; guess-risk: SEVERE |
| **seidverja** | to ward against workings of the old tongue | 0.90 | elven trial | requires: verja; wards: magic; guess-risk: CATASTROPHIC |

### Fire

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **eldverja** | to ward against flame and burning | 0.85 | elven trial | requires: verja; wards: fire; guess-risk: SEVERE |

### Force

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **hoggverja** | to ward against the striking blow | 0.85 | elven trial | requires: verja; wards: melee; guess-risk: SEVERE |
| **sprengverja** | to ward against the bursting force | 0.85 | elven trial | requires: verja; wards: explosion; guess-risk: SEVERE |

### Motion

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **fallverja** | to ward against the fall and the earth's pull | 0.85 | elven trial | requires: verja; wards: fall |

## Control (ending and stilling a working)

### Force

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **letta** | cease, be still | 1.00 | mentor npc |  |

### Truth

| Word | Meaning | Precision | Found via | Notes |
|---|---|---|---|---|
| **enda** | to end utterly; the final silence of a working | 1.00 | ancient text |  |

