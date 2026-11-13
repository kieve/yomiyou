"use strict";
(() => {
  // src/main/ts/dictionary.ts
  var NAME_TAGS = /* @__PURE__ */ new Set([
    "char",
    "company",
    "creat",
    "dei",
    "doc",
    "ev",
    "fem",
    "fict",
    "given",
    "group",
    "leg",
    "male",
    "masc",
    "myth",
    "obj",
    "organization",
    "oth",
    "person",
    "place",
    "product",
    "relig",
    "serv",
    "ship",
    "station",
    "surname",
    "unclass",
    "work"
  ]);
  function isName(entry) {
    return entry.tags.length > 0 && entry.tags.every((tag) => NAME_TAGS.has(tag));
  }

  // src/main/ts/entry-view.ts
  var INFLECTIONS = {
    "te-form": "\u3066 form",
    past: "past",
    "-tara": "\u305F\u3089 (if, when)",
    "-tari": "\u305F\u308A (and so on)",
    \u3066\u3044\u308B: "\u3066\u3044\u308B (ongoing)",
    \u3066\u3057\u307E\u3046: "\u3066\u3057\u307E\u3046 (completely)",
    \u3066\u304A\u304F: "\u3066\u304A\u304F (in advance)",
    \u3066\u3042\u308B: "\u3066\u3042\u308B (done and left)",
    \u3066\u3044\u304F: "\u3066\u3044\u304F (going on)",
    \u3066\u304F\u308B: "\u3066\u304F\u308B (coming to)",
    \u3066\u307F\u308B: "\u3066\u307F\u308B (try)",
    \u3066\u304F\u308C\u308B: "\u3066\u304F\u308C\u308B (for me)",
    \u3066\u3042\u3052\u308B: "\u3066\u3042\u3052\u308B (for someone)",
    \u3066\u3082\u3089\u3046: "\u3066\u3082\u3089\u3046 (have done)",
    \u3066\u304F\u3060\u3055\u3044: "\u3066\u304F\u3060\u3055\u3044 (please)",
    polite: "polite",
    "polite negative": "polite negative",
    "polite volitional": "polite, let's",
    "polite negative past": "polite negative past",
    "polite te-form": "polite \u3066 form",
    "polite past": "polite past",
    "-tai": "\u305F\u3044 (want to)",
    "-nagara": "\u306A\u304C\u3089 (while)",
    "-nasai": "\u306A\u3055\u3044 (do!)",
    "-sou": "\u305D\u3046 (looks like)",
    negative: "negative",
    passive: "passive",
    causative: "causative",
    potential: "potential",
    adverb: "adverb",
    "-ba": "\u3070 (if)",
    volitional: "volitional (let's)",
    imperative: "imperative",
    stem: "stem (\u307E\u3059)",
    mizen: "stem (\u306A\u3044)",
    \u3059\u308B: "\u3059\u308B verb"
  };
  var BASES = /* @__PURE__ */ new Set(["te-form", "past", "stem", "mizen"]);
  function inflectionOf(reasons) {
    const named = reasons.filter((r, i) => i === 0 || !BASES.has(r));
    return named.length === 0 ? null : named.reverse().map((r) => INFLECTIONS[r] ?? r).join(", ");
  }
  var KINDS = {
    n: "noun",
    pn: "pronoun",
    "n-suf": "suffix",
    "n-pref": "prefix",
    suf: "suffix",
    pref: "prefix",
    ctr: "counter",
    num: "number",
    exp: "expression",
    int: "interjection",
    conj: "conjunction",
    prt: "particle",
    adv: "adverb",
    "adv-to": "adverb (\u3068)",
    "adj-i": "i-adjective",
    "adj-ix": "i-adjective",
    "adj-na": "na-adjective",
    "adj-no": "\u306E-adjective",
    "adj-pn": "pre-noun adjective",
    "adj-t": "\u305F\u308B-adjective",
    "adj-f": "pre-noun",
    "aux-v": "auxiliary verb",
    "aux-adj": "auxiliary adjective",
    v1: "ichidan verb",
    "v1-s": "ichidan verb",
    vk: "\u304F\u308B verb",
    vs: "\u3059\u308B verb",
    "vs-i": "\u3059\u308B verb",
    "vs-s": "\u3059\u308B verb",
    vz: "\u305A\u308B verb",
    vt: "transitive",
    vi: "intransitive",
    cop: "copula",
    // Names (JMnedict).
    surname: "surname",
    given: "given name",
    fem: "female given name",
    masc: "male given name",
    male: "male given name",
    person: "full name",
    place: "place",
    station: "station",
    company: "company",
    organization: "organization",
    product: "product",
    work: "work",
    char: "character",
    fict: "fiction",
    group: "group",
    serv: "service",
    creat: "creature",
    dei: "deity",
    myth: "mythology",
    leg: "legend",
    ev: "event",
    doc: "document",
    obj: "object",
    ship: "ship",
    relig: "religion",
    oth: "name",
    unclass: "name"
  };
  function kindOf(tag) {
    return KINDS[tag] ?? (tag.startsWith("v5") ? "godan verb" : void 0);
  }
  function kindsOf(entry) {
    return [...new Set(entry.tags.map(kindOf).filter((kind) => kind !== void 0))];
  }
  function hiragana(text) {
    return text.replace(/[ァ-ヶ]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 96));
  }
  var KANA = /^[ぁ-ゟ゠-ヿー]+$/;
  function wordsOf(found) {
    const groups = /* @__PURE__ */ new Map();
    for (const one of found) {
      const key = [one.entry.term, one.entry.reading, one.entry.sequence].join("\0");
      const group2 = groups.get(key) ?? [];
      group2.push(one);
      groups.set(key, group2);
    }
    return mergeReadings([...groups.values()].map((group2) => {
      if (group2.every((one) => isName(one.entry))) return nameOf(group2);
      const ordered = group2.map((one) => one.entry).sort((a, b) => b.score - a.score);
      const first = ordered[0];
      const each = ordered.map(kindsOf);
      const shared = each[0].filter((kind) => each.every((kinds) => kinds.includes(kind)));
      return {
        term: first.term,
        reading: KANA.test(first.term) ? null : hiragana(first.reading),
        kinds: shared,
        inflection: inflectionOf(group2[0].form.reasons),
        written: group2.some((one) => one.entry.term === one.form.text),
        rank: group2[0].entry.rank ?? 0,
        senses: ordered.map((entry, i) => ({
          kinds: each[i].filter((kind) => !shared.includes(kind)),
          meanings: entry.meanings
        })),
        sequence: first.sequence
      };
    }));
  }
  function likely(words) {
    const best = Math.min(...words.filter((w) => w.written).map((w) => w.rank));
    const written = words.filter((w) => w.written && w.rank === best);
    const shown = written.length > 0 ? written : words.slice(0, 1);
    return { shown, more: words.filter((w) => !shown.includes(w)) };
  }
  function mergeReadings(words) {
    const merged = [];
    for (const { sequence, ...word } of words) {
      const key = [word.term, sequence, JSON.stringify([word.kinds, word.inflection, word.senses])].join("\0");
      const same = merged.find((m) => m.key === key);
      if (same && same.reading !== null && word.reading !== null) {
        same.reading = `${same.reading}\u30FB${word.reading}`;
      } else {
        merged.push({ ...word, key });
      }
    }
    return merged.map(({ key: _key, ...word }) => word);
  }
  function nameOf(group2) {
    const rows = group2.map((one2) => one2.entry);
    const readingsOf = (entry) => entry.meanings.map(hiragana);
    const all = [...new Set(rows.flatMap(readingsOf))];
    const one = rows.length === 1;
    return {
      term: rows[0].term,
      reading: KANA.test(rows[0].term) ? null : all.join("\u30FB"),
      kinds: one ? kindsOf(rows[0]) : [],
      inflection: null,
      written: true,
      rank: rows[0].rank ?? 0,
      senses: one ? [] : rows.map((row) => ({ kinds: kindsOf(row), meanings: [readingsOf(row).join("\u30FB")] })),
      sequence: rows[0].sequence
    };
  }

  // src/main/ts/deinflect.ts
  var Kind = {
    /** Ichidan verb: 食べる. */
    V1: 1 << 0,
    /** Godan verb: 読む. */
    V5: 1 << 1,
    /** する, or a noun that takes it: 勉強する. */
    VS: 1 << 2,
    /** 来る. */
    VK: 1 << 3,
    /** I-adjective: 寒い. */
    AdjI: 1 << 4,
    /** The noun of a する verb: 勉強 from 勉強する. */
    VsNoun: 1 << 5,
    /** Te-form: 読んで. */
    Te: 1 << 6,
    /** Past: 読んだ. */
    Past: 1 << 7,
    /** The stem ます and たい attach to: 読み. */
    Stem: 1 << 8,
    /** The stem ない and the passive attach to: 読ま. */
    Mizen: 1 << 9,
    /** Only as written: what nothing else attaches to (ます forms, ば, volitional, imperative). */
    End: 1 << 10
  };
  var AS_WRITTEN = (1 << 11) - 1;
  var ENTRY_KINDS = /* @__PURE__ */ new Map([
    [Kind.V1, "v1"],
    [Kind.V5, "v5"],
    [Kind.VS, "vs"],
    [Kind.VK, "vk"],
    [Kind.AdjI, "adj-i"],
    [Kind.VsNoun, "vs"]
  ]);
  var rules = [];
  function rule(reason, fromKinds, toKind, ...pairs) {
    for (const [from, to] of pairs) rules.push({ from, to, fromKinds, toKind, reason });
  }
  var GODAN = [
    ["\u3046", "\u308F", "\u3044", "\u3048", "\u304A"],
    ["\u304F", "\u304B", "\u304D", "\u3051", "\u3053"],
    ["\u3050", "\u304C", "\u304E", "\u3052", "\u3054"],
    ["\u3059", "\u3055", "\u3057", "\u305B", "\u305D"],
    ["\u3064", "\u305F", "\u3061", "\u3066", "\u3068"],
    ["\u306C", "\u306A", "\u306B", "\u306D", "\u306E"],
    ["\u3076", "\u3070", "\u3073", "\u3079", "\u307C"],
    ["\u3080", "\u307E", "\u307F", "\u3081", "\u3082"],
    ["\u308B", "\u3089", "\u308A", "\u308C", "\u308D"]
  ];
  var GODAN_TE = [
    ["\u3046", "\u3063\u3066", "\u3063\u305F"],
    ["\u3064", "\u3063\u3066", "\u3063\u305F"],
    ["\u308B", "\u3063\u3066", "\u3063\u305F"],
    ["\u304F", "\u3044\u3066", "\u3044\u305F"],
    ["\u3050", "\u3044\u3067", "\u3044\u3060"],
    ["\u3059", "\u3057\u3066", "\u3057\u305F"],
    ["\u306C", "\u3093\u3067", "\u3093\u3060"],
    ["\u3076", "\u3093\u3067", "\u3093\u3060"],
    ["\u3080", "\u3093\u3067", "\u3093\u3060"]
  ];
  var END = Kind.End;
  var PLAIN = END | Kind.Te | Kind.Past;
  for (const [dict, te, past] of GODAN_TE) {
    rule("te-form", Kind.Te, Kind.V5, [te, dict]);
    rule("past", Kind.Past, Kind.V5, [past, dict]);
  }
  rule("te-form", Kind.Te, Kind.V5, ["\u884C\u3063\u3066", "\u884C\u304F"], ["\u3044\u3063\u3066", "\u3044\u304F"]);
  rule("past", Kind.Past, Kind.V5, ["\u884C\u3063\u305F", "\u884C\u304F"], ["\u3044\u3063\u305F", "\u3044\u304F"]);
  rule("te-form", Kind.Te, Kind.V1, ["\u3066", "\u308B"]);
  rule("past", Kind.Past, Kind.V1, ["\u305F", "\u308B"]);
  rule("te-form", Kind.Te, Kind.VS, ["\u3057\u3066", "\u3059\u308B"]);
  rule("past", Kind.Past, Kind.VS, ["\u3057\u305F", "\u3059\u308B"]);
  rule("te-form", Kind.Te, Kind.VK, ["\u6765\u3066", "\u6765\u308B"], ["\u304D\u3066", "\u304F\u308B"]);
  rule("past", Kind.Past, Kind.VK, ["\u6765\u305F", "\u6765\u308B"], ["\u304D\u305F", "\u304F\u308B"]);
  rule("te-form", Kind.Te, Kind.AdjI, ["\u304F\u3066", "\u3044"]);
  rule("past", Kind.Past, Kind.AdjI, ["\u304B\u3063\u305F", "\u3044"]);
  rule("-tara", END, Kind.Past, ["\u305F\u3089", "\u305F"], ["\u3060\u3089", "\u3060"]);
  rule("-tari", END, Kind.Past, ["\u305F\u308A", "\u305F"], ["\u3060\u308A", "\u3060"]);
  rule("\u3066\u3044\u308B", Kind.V1, Kind.Te, ["\u3044\u308B", ""]);
  rule("\u3066\u3044\u308B", Kind.V1, Kind.Te, ["\u3066\u308B", "\u3066"], ["\u3067\u308B", "\u3067"]);
  rule("\u3066\u3057\u307E\u3046", Kind.V5, Kind.Te, ["\u3057\u307E\u3046", ""]);
  rule("\u3066\u3057\u307E\u3046", Kind.V5, Kind.Te, ["\u3061\u3083\u3046", "\u3066"], ["\u3058\u3083\u3046", "\u3067"]);
  rule("\u3066\u304A\u304F", Kind.V5, Kind.Te, ["\u304A\u304F", ""]);
  rule("\u3066\u304A\u304F", Kind.V5, Kind.Te, ["\u3068\u304F", "\u3066"], ["\u3069\u304F", "\u3067"]);
  rule("\u3066\u3042\u308B", Kind.V5, Kind.Te, ["\u3042\u308B", ""]);
  rule("\u3066\u3044\u304F", Kind.V5, Kind.Te, ["\u3044\u304F", ""]);
  rule("\u3066\u304F\u308B", Kind.VK, Kind.Te, ["\u304F\u308B", ""]);
  rule("\u3066\u307F\u308B", Kind.V1, Kind.Te, ["\u307F\u308B", ""]);
  rule("\u3066\u304F\u308C\u308B", Kind.V1, Kind.Te, ["\u304F\u308C\u308B", ""]);
  rule("\u3066\u3042\u3052\u308B", Kind.V1, Kind.Te, ["\u3042\u3052\u308B", ""]);
  rule("\u3066\u3082\u3089\u3046", Kind.V5, Kind.Te, ["\u3082\u3089\u3046", ""]);
  rule("\u3066\u304F\u3060\u3055\u3044", END, Kind.Te, ["\u304F\u3060\u3055\u3044", ""]);
  rule("polite", END, Kind.Stem, ["\u307E\u3059", ""]);
  rule("polite negative", END, Kind.Stem, ["\u307E\u305B\u3093", ""]);
  rule("polite volitional", END, Kind.Stem, ["\u307E\u3057\u3087\u3046", ""]);
  rule("polite negative past", END, Kind.Stem, ["\u307E\u305B\u3093\u3067\u3057\u305F", ""]);
  rule("polite te-form", Kind.Te, Kind.Stem, ["\u307E\u3057\u3066", ""]);
  rule("polite past", Kind.Past, Kind.Stem, ["\u307E\u3057\u305F", ""]);
  rule("-tai", Kind.AdjI, Kind.Stem, ["\u305F\u3044", ""]);
  rule("-nagara", END, Kind.Stem, ["\u306A\u304C\u3089", ""]);
  rule("-nasai", END, Kind.Stem, ["\u306A\u3055\u3044", ""]);
  rule("-sou", END, Kind.Stem, ["\u305D\u3046", ""]);
  for (const [dict, , i] of GODAN) rule("stem", Kind.Stem, Kind.V5, [i, dict]);
  rule("stem", Kind.Stem, Kind.V1, ["", "\u308B"]);
  rule("stem", Kind.Stem, Kind.VS, ["\u3057", "\u3059\u308B"]);
  rule("stem", Kind.Stem, Kind.VK, ["\u6765", "\u6765\u308B"], ["\u304D", "\u304F\u308B"]);
  rule("negative", Kind.AdjI, Kind.Mizen, ["\u306A\u3044", ""]);
  rule("negative", END, Kind.Mizen, ["\u306A\u3044\u3067", ""], ["\u306A\u3051\u308C\u3070", ""], ["\u306A\u304D\u3083", ""], ["\u306A\u304F\u3061\u3083", ""], ["\u305A", ""]);
  rule("negative", END, Kind.Mizen, ["\u3093", ""]);
  rule("passive", Kind.V1, Kind.Mizen, ["\u308C\u308B", ""]);
  rule("passive", Kind.V1, Kind.Mizen, ["\u3089\u308C\u308B", ""]);
  rule("causative", Kind.V1, Kind.Mizen, ["\u305B\u308B", ""], ["\u3055\u305B\u308B", ""]);
  for (const [dict, a] of GODAN) rule("mizen", Kind.Mizen, Kind.V5, [a, dict]);
  rule("mizen", Kind.Mizen, Kind.V1, ["", "\u308B"]);
  rule("mizen", Kind.Mizen, Kind.VS, ["\u3057", "\u3059\u308B"], ["\u3055", "\u3059\u308B"]);
  rule("mizen", Kind.Mizen, Kind.VK, ["\u6765", "\u6765\u308B"], ["\u3053", "\u304F\u308B"]);
  for (const [dict, , , e] of GODAN) rule("potential", Kind.V1, Kind.V5, [e + "\u308B", dict]);
  rule("potential", Kind.V1, Kind.V1, ["\u308C\u308B", "\u308B"]);
  rule("potential", Kind.V1, Kind.VS, ["\u3067\u304D\u308B", "\u3059\u308B"]);
  rule("negative", Kind.AdjI, Kind.AdjI, ["\u304F\u306A\u3044", "\u3044"]);
  rule("adverb", PLAIN, Kind.AdjI, ["\u304F", "\u3044"]);
  rule("-ba", END, Kind.AdjI, ["\u3051\u308C\u3070", "\u3044"]);
  rule("-sou", END, Kind.AdjI, ["\u305D\u3046", "\u3044"]);
  for (const [dict, , , e, o] of GODAN) {
    rule("-ba", END, Kind.V5, [e + "\u3070", dict]);
    rule("volitional", END, Kind.V5, [o + "\u3046", dict]);
    rule("imperative", END, Kind.V5, [e, dict]);
  }
  rule("-ba", END, Kind.V1, ["\u308C\u3070", "\u308B"]);
  rule("-ba", END, Kind.VS, ["\u3059\u308C\u3070", "\u3059\u308B"]);
  rule("-ba", END, Kind.VK, ["\u6765\u308C\u3070", "\u6765\u308B"], ["\u304F\u308C\u3070", "\u304F\u308B"]);
  rule("volitional", END, Kind.V1, ["\u3088\u3046", "\u308B"]);
  rule("volitional", END, Kind.VS, ["\u3057\u3088\u3046", "\u3059\u308B"]);
  rule("volitional", END, Kind.VK, ["\u6765\u3088\u3046", "\u6765\u308B"], ["\u3053\u3088\u3046", "\u304F\u308B"]);
  rule("imperative", END, Kind.V1, ["\u308D", "\u308B"]);
  rule("imperative", END, Kind.VS, ["\u3057\u308D", "\u3059\u308B"]);
  rule("imperative", END, Kind.VK, ["\u6765\u3044", "\u6765\u308B"], ["\u3053\u3044", "\u304F\u308B"]);
  rule("\u3059\u308B", Kind.VS, Kind.VsNoun, ["\u3059\u308B", ""]);
  function deinflect(text) {
    const forms = [{ text, kind: AS_WRITTEN, reasons: [] }];
    const seen = /* @__PURE__ */ new Map([[text, AS_WRITTEN]]);
    for (let i = 0; i < forms.length; i++) {
      const form = forms[i];
      for (const r of rules) {
        if ((form.kind & r.fromKinds) === 0 || !form.text.endsWith(r.from)) continue;
        const stem = form.text.slice(0, form.text.length - r.from.length);
        if (stem.length === 0 && r.to.length === 0) continue;
        const next = stem + r.to;
        const known = seen.get(next) ?? 0;
        if ((known & r.toKind) !== 0) continue;
        seen.set(next, known | r.toKind);
        forms.push({ text: next, kind: r.toKind, reasons: [...form.reasons, r.reason] });
      }
    }
    return forms;
  }
  function fits(form, entryRules) {
    if (form.kind === AS_WRITTEN) return true;
    const name = ENTRY_KINDS.get(form.kind);
    return name !== void 0 && entryRules.includes(name);
  }

  // src/main/ts/lookup.ts
  var MAX_LENGTH = 16;
  var MAX_BEFORE = 4;
  var BREAK = /[\s、。，．・！？!?「」『』（）()［］【】〈〉《》…‥―—～〜"'“”‘’]/;
  async function lookAt(dictionary, text, index) {
    if (index < 0 || index >= text.length || BREAK.test(text[index] ?? "")) return null;
    let from = index;
    while (from > index - MAX_BEFORE && from > 0 && !BREAK.test(text[from - 1] ?? "")) from--;
    return longest(dictionary, text, from, index);
  }
  async function breakDown(dictionary, text) {
    const parts = [];
    let unknown = null;
    const endUnknown = (end) => {
      if (unknown !== null) parts.push({ start: unknown, end, found: [] });
      unknown = null;
    };
    for (let i = 0; i < text.length; ) {
      if (BREAK.test(text[i] ?? "")) {
        endUnknown(i);
        i++;
        continue;
      }
      const word = await longest(dictionary, text, i, i);
      if (word) {
        endUnknown(i);
        parts.push(word);
        i = word.end;
      } else {
        unknown ??= i;
        i++;
      }
    }
    endUnknown(text.length);
    return parts;
  }
  async function longest(dictionary, text, from, index) {
    let to = index + 1;
    while (to < from + MAX_LENGTH && to < text.length && !BREAK.test(text[to] ?? "")) to++;
    const spans = [];
    for (let start = from; start <= index; start++) {
      for (let end = Math.min(to, start + MAX_LENGTH); end > index; end--) {
        spans.push({ start, end, forms: deinflect(text.slice(start, end)) });
      }
    }
    const entries = await dictionary.find([...new Set(spans.flatMap((s) => s.forms.map((f) => f.text)))]);
    const byText = group(entries);
    let best = null;
    for (const span of spans) {
      const found = match(span.forms, byText);
      if (found.length === 0) continue;
      const better = best === null || span.end - span.start > best.end - best.start || span.end - span.start === best.end - best.start && Math.abs(span.start - index) < Math.abs(best.start - index);
      if (better) best = { start: span.start, end: span.end, found };
    }
    return best;
  }
  async function lookUp(dictionary, selection) {
    const forms = deinflect(selection);
    const entries = await dictionary.find([...new Set(forms.map((f) => f.text))]);
    return match(forms, group(entries));
  }
  function group(entries) {
    const byText = /* @__PURE__ */ new Map();
    for (const entry of entries) {
      for (const key of /* @__PURE__ */ new Set([entry.term, entry.reading])) {
        const list = byText.get(key) ?? [];
        list.push(entry);
        byText.set(key, list);
      }
    }
    return byText;
  }
  function match(forms, byText) {
    const found = /* @__PURE__ */ new Map();
    for (const form of forms) {
      for (const entry of byText.get(form.text) ?? []) {
        if (!fits(form, entry.rules)) continue;
        const known = found.get(entry);
        if (known === void 0 || form.reasons.length < known.form.reasons.length) found.set(entry, { entry, form });
      }
    }
    const written = (f) => f.entry.term === f.form.text ? 0 : 1;
    return [...found.values()].sort(
      (a, b) => a.form.reasons.length - b.form.reasons.length || written(a) - written(b) || (a.entry.rank ?? 0) - (b.entry.rank ?? 0) || b.entry.score - a.entry.score
    );
  }

  // src/main/ts/word-lookup.ts
  var HOLD_MS = 450;
  var HOLD_SLOP = 10;
  var BLOCK = "p, h1, h2, h3, h4, h5, h6, li, div";
  var MARGIN = 16;
  var TextMap = class {
    constructor(block) {
      this.block = block;
      const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT, {
        acceptNode: (node) => node.parentElement?.closest("rt, rp") ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
      });
      let text = "";
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        this.nodes.push({ node, start: text.length });
        text += node.data;
      }
      this.text = text;
    }
    block;
    text;
    nodes = [];
    /** The string offset of a point in the page; a point in a ruby reading counts as the start of its base. */
    indexOf(node, offset) {
      const reading = node.parentElement?.closest("rt, rp");
      if (reading) {
        const ruby = reading.closest("ruby");
        const base = this.nodes.find((n) => ruby?.contains(n.node));
        return base ? base.start : null;
      }
      const found = this.nodes.find((n) => n.node === node);
      return found ? found.start + Math.min(offset, found.node.data.length) : null;
    }
    /** The page's range for string offsets [start, end). */
    range(start, end) {
      const range = document.createRange();
      const at = (index, isEnd) => {
        for (const n of this.nodes) {
          const length = n.node.data.length;
          if (index < n.start + length || isEnd && index === n.start + length) {
            return [n.node, index - n.start];
          }
        }
        const last = this.nodes[this.nodes.length - 1];
        return [last.node, last.node.data.length];
      };
      range.setStart(...at(start, false));
      range.setEnd(...at(end, true));
      return range;
    }
    /** The box of the character at [index]. */
    box(index) {
      return this.range(index, index + 1).getBoundingClientRect();
    }
  };
  var ACTIONS_GAP = 24;
  var CHECK = '<svg viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';
  var COPY = '<svg viewBox="0 0 24 24"><path d="M16 1H4a2 2 0 0 0-2 2v14h2V3h12zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11z"/></svg>';
  function action(label, icon) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "lookup-action";
    button.title = label;
    button.setAttribute("aria-label", label);
    button.innerHTML = icon;
    return button;
  }
  async function toClipboard(text) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
    }
    const area = document.createElement("textarea");
    area.value = text;
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.append(area);
    area.select();
    document.execCommand("copy");
    area.remove();
  }
  function element(tag, className, text) {
    const el = document.createElement(tag);
    el.className = className;
    if (text !== void 0) el.textContent = text;
    return el;
  }
  function wordElement(word) {
    const item = element("section", "lookup-word");
    const head = element("div", "lookup-head");
    head.append(element("span", "lookup-term", word.term));
    if (word.reading) head.append(element("span", "lookup-reading", word.reading));
    item.append(head);
    if (word.inflection) item.append(element("div", "lookup-inflection", word.inflection));
    if (word.kinds.length > 0) item.append(element("div", "lookup-kind", word.kinds.join(", ")));
    const several = word.senses.length > 1;
    const senses = element(several ? "ol" : "div", "lookup-senses");
    for (const sense of word.senses) {
      const line = element(several ? "li" : "div", "lookup-sense");
      if (sense.kinds.length > 0) line.append(element("span", "lookup-kind", sense.kinds.join(", ")), " ");
      line.append(sense.meanings.join("; "));
      senses.append(line);
    }
    item.append(senses);
    return item;
  }
  function contains(box, x, y) {
    return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
  }
  function caretAt(x, y) {
    const doc = document;
    if (doc.caretPositionFromPoint) {
      const position = doc.caretPositionFromPoint(x, y);
      return position ? { node: position.offsetNode, offset: position.offset } : null;
    }
    const range = document.caretRangeFromPoint?.(x, y);
    return range ? { node: range.startContainer, offset: range.startOffset } : null;
  }
  var WordLookup = class {
    constructor(box, options) {
      this.box = box;
      this.options = options;
      const signal = this.events.signal;
      this.layer = document.createElement("div");
      this.layer.className = "lookup-layer";
      this.startHandle = this.handle("start");
      this.endHandle = this.handle("end");
      this.confirm = action("Look up this selection", CHECK);
      this.copy = action("Copy", COPY);
      this.actions = document.createElement("div");
      this.actions.className = `lookup-actions${options.vertical ? " vertical" : ""}`;
      this.actions.append(this.copy, this.confirm);
      this.panel = document.createElement("div");
      this.panel.className = "lookup-panel";
      const grip = document.createElement("div");
      grip.className = "lookup-grip";
      grip.title = "Drag to move";
      this.entries = document.createElement("div");
      this.entries.className = "lookup-entries";
      this.panel.append(grip, this.entries);
      this.movable(grip);
      this.entries.addEventListener("toggle", () => this.place(), { capture: true });
      this.layer.append(this.startHandle, this.endHandle, this.actions, this.panel);
      this.layer.hidden = true;
      document.body.append(this.layer);
      this.highlight = typeof Highlight === "undefined" ? null : new Highlight();
      if (this.highlight) CSS.highlights.set("lookup", this.highlight);
      box.classList.add("lookup-ready");
      box.addEventListener("pointerdown", (event) => this.pressed(event), { signal });
      addEventListener("pointermove", (event) => this.moved(event), { signal });
      addEventListener("pointerup", (event) => this.released(event), { signal });
      addEventListener("pointercancel", () => this.cancelPress(), { signal });
      box.addEventListener("contextmenu", (event) => event.preventDefault(), { signal });
      box.addEventListener(
        "click",
        (event) => {
          if (this.swallowClick) {
            this.swallowClick = false;
            event.stopImmediatePropagation();
            event.preventDefault();
          }
        },
        { capture: true, signal }
      );
      document.addEventListener(
        "pointerdown",
        (event) => {
          if (this.selection && !this.layer.contains(event.target)) {
            if (this.box.contains(event.target)) this.swallowClick = true;
            this.close();
          }
        },
        { capture: true, signal }
      );
      document.addEventListener("keydown", (event) => event.key === "Escape" && this.close(), { signal });
      this.confirm.addEventListener("click", () => void this.showEntries(true), { signal });
      this.copy.addEventListener("click", () => void this.copySelection(), { signal });
      const follow = () => this.place();
      box.addEventListener("scroll", follow, { signal, passive: true });
      addEventListener("scroll", follow, { signal, passive: true });
      addEventListener("resize", follow, { signal });
    }
    box;
    options;
    events = new AbortController();
    layer;
    startHandle;
    endHandle;
    confirm;
    copy;
    actions;
    copied = 0;
    panel;
    entries;
    /** Where the panel was dragged to, for this lookup; null while it sits where it opened. */
    panelAt = null;
    highlight;
    press = null;
    swallowClick = false;
    selection = null;
    dragging = null;
    asked = 0;
    close() {
      this.asked++;
      this.panelAt = null;
      this.selection = null;
      this.dragging = null;
      this.highlight?.clear();
      if (!this.layer.hidden) {
        this.layer.hidden = true;
        this.options.onOpen?.(false);
      }
    }
    dispose() {
      this.close();
      this.events.abort();
      this.layer.remove();
      this.box.classList.remove("lookup-ready");
      CSS.highlights?.delete("lookup");
    }
    handle(end) {
      const handle = document.createElement("div");
      handle.className = `lookup-handle ${end}`;
      handle.addEventListener("pointerdown", (event) => {
        event.preventDefault();
        event.stopPropagation();
        try {
          handle.setPointerCapture(event.pointerId);
        } catch {
        }
        this.dragging = end;
        this.layer.classList.add("dragging");
        this.panel.hidden = true;
        this.confirm.hidden = false;
        this.place();
      });
      handle.addEventListener("pointermove", (event) => this.drag(event));
      const stop = () => {
        this.dragging = null;
        this.layer.classList.remove("dragging");
      };
      handle.addEventListener("pointerup", stop);
      handle.addEventListener("pointercancel", stop);
      return handle;
    }
    pressed(event) {
      if (!event.isPrimary || event.button !== 0 || this.layer.contains(event.target)) return;
      this.cancelPress();
      const { clientX: x, clientY: y, pointerId: id } = event;
      this.press = { id, x, y, timer: window.setTimeout(() => void this.held(x, y), HOLD_MS) };
    }
    moved(event) {
      const press = this.press;
      if (press && event.pointerId === press.id && Math.hypot(event.clientX - press.x, event.clientY - press.y) > HOLD_SLOP) {
        this.cancelPress();
      }
    }
    released(event) {
      if (this.press && event.pointerId === this.press.id) this.cancelPress();
    }
    cancelPress() {
      if (this.press) clearTimeout(this.press.timer);
      this.press = null;
    }
    /** The block and string offset of the character under a point; null off the text. */
    characterAt(x, y, within) {
      const caret = caretAt(x, y);
      if (!caret || !this.box.contains(caret.node)) return null;
      const element2 = caret.node.nodeType === Node.TEXT_NODE ? caret.node.parentElement : caret.node;
      const block = element2?.closest(BLOCK);
      if (!block || !this.box.contains(block)) return null;
      const map = within && within.block === block ? within : new TextMap(block);
      if (within && map !== within) return null;
      const index = map.indexOf(caret.node, caret.offset);
      if (index === null || map.text.length === 0) return null;
      for (const candidate of [index, index - 1]) {
        if (candidate >= 0 && candidate < map.text.length && contains(map.box(candidate), x, y)) {
          return { map, index: candidate };
        }
      }
      return { map, index: Math.min(index, map.text.length - 1) };
    }
    async held(x, y) {
      this.press = null;
      const dictionary = this.options.dictionary();
      if (!dictionary) return;
      const at = this.characterAt(x, y);
      if (!at) return;
      this.swallowClick = true;
      this.options.onHold();
      const asked = ++this.asked;
      const match2 = await lookAt(dictionary, at.map.text, at.index);
      if (asked !== this.asked || !match2) return;
      this.panelAt = null;
      this.selection = { map: at.map, start: match2.start, end: match2.end };
      this.show();
      this.render(match2.found);
    }
    /**
     * A handle follows the finger, but never across the other end. Where the finger is past it (or off the text), the
     * point is read along the handle's own line instead, so drifting sideways to see what's under the finger doesn't
     * lose the selection; failing that, the handle stays put.
     */
    drag(event) {
      const selection = this.selection;
      const end = this.dragging;
      if (!end || !selection) return;
      const fits2 = (index2) => end === "start" ? index2 <= selection.end - 1 : index2 >= selection.start;
      const pick = (x, y) => {
        const at = this.characterAt(x, y, selection.map);
        return at && fits2(at.index) ? at.index : null;
      };
      const line = selection.map.box(end === "start" ? selection.start : selection.end - 1);
      const index = pick(event.clientX, event.clientY) ?? (this.options.vertical ? pick(line.left + line.width / 2, event.clientY) : pick(event.clientX, line.top + line.height / 2));
      if (index === null) return;
      if (end === "start") {
        selection.start = index;
      } else {
        selection.end = index + 1;
      }
      this.showSelection();
      this.place();
    }
    /** The selection's entries, after a correction: the selection exactly as it is now. */
    async showEntries(corrected) {
      const selection = this.selection;
      const dictionary = this.options.dictionary();
      if (!selection || !dictionary) return;
      const asked = ++this.asked;
      const text = selection.map.text.slice(selection.start, selection.end);
      const found = await lookUp(dictionary, text);
      const parts = found.length === 0 ? await breakDown(dictionary, text) : null;
      if (asked !== this.asked) return;
      if (parts && parts.length > 1) {
        this.renderParts(text, parts, corrected);
      } else {
        this.render(found, corrected);
      }
    }
    /** The selection's text to the clipboard; the button shows a check for a moment. */
    async copySelection() {
      const selection = this.selection;
      if (!selection) return;
      await toClipboard(selection.map.text.slice(selection.start, selection.end));
      const copied = ++this.copied;
      this.copy.innerHTML = CHECK;
      window.setTimeout(() => {
        if (copied === this.copied) this.copy.innerHTML = COPY;
      }, 1200);
    }
    show() {
      if (this.layer.hidden) {
        this.layer.hidden = false;
        this.options.onOpen?.(true);
      }
      this.confirm.hidden = true;
      this.panel.hidden = false;
      this.showSelection();
      this.place();
    }
    showSelection() {
      const selection = this.selection;
      if (!selection || !this.highlight) return;
      this.highlight.clear();
      this.highlight.add(selection.map.range(selection.start, selection.end));
    }
    /** The selection's words: one header per word (the word, its reading, its kind), its meanings numbered under it. */
    render(found, corrected = false) {
      const selection = this.selection;
      if (!selection) return;
      this.entries.replaceChildren();
      if (found.length === 0) {
        const none = selection.map.text.slice(selection.start, selection.end);
        this.entries.append(element("p", "lookup-none", `No entries for ${none}`));
      }
      this.entries.append(...wordsOf(found).map(wordElement));
      this.shown(corrected);
    }
    /** A selection that isn't one word: each part as it's written, its words under it. */
    renderParts(text, parts, corrected) {
      this.entries.replaceChildren();
      for (const part of parts) {
        const item = element("section", "lookup-part");
        item.append(element("div", "lookup-part-text", text.slice(part.start, part.end)));
        if (part.found.length === 0) item.append(element("p", "lookup-none", "No entries"));
        const { shown, more } = likely(wordsOf(part.found));
        item.append(...shown.map(wordElement));
        if (more.length > 0) {
          const fold = element("details", "lookup-more");
          fold.append(element("summary", "", `${more.length} more`), ...more.map(wordElement));
          item.append(fold);
        }
        this.entries.append(item);
      }
      this.shown(corrected);
    }
    shown(corrected) {
      this.confirm.hidden = true;
      this.panel.hidden = false;
      if (corrected) this.entries.scrollTop = 0;
      this.place();
    }
    /** The panel follows a drag of its grip, never leaving the screen; it stays where it's left for this lookup. */
    movable(grip) {
      let from = null;
      grip.addEventListener("pointerdown", (event) => {
        event.preventDefault();
        try {
          grip.setPointerCapture(event.pointerId);
        } catch {
        }
        const box = this.panel.getBoundingClientRect();
        from = { x: event.clientX, y: event.clientY, left: box.left, top: box.top, width: box.width };
      });
      grip.addEventListener("pointermove", (event) => {
        if (!from) return;
        const x = from.left + event.clientX - from.x;
        const y = from.top + event.clientY - from.y;
        this.panelAt = { x, y, width: from.width };
        this.place();
      });
      const stop = () => {
        from = null;
      };
      grip.addEventListener("pointerup", stop);
      grip.addEventListener("pointercancel", stop);
    }
    /**
     * The controls after the selection's end, in reading order; where that's off the screen, beside the end's line
     * instead (vertical: on the column's right, the text already read), and always on the screen.
     */
    placeActions(last) {
      const { offsetWidth: width, offsetHeight: height } = this.actions;
      let x;
      let y;
      if (this.options.vertical) {
        const below = last.bottom + ACTIONS_GAP;
        [x, y] = below + height <= innerHeight - MARGIN ? [last.left + last.width / 2 - width / 2, below] : [last.right + ACTIONS_GAP, last.bottom - height];
      } else {
        const after = last.right + ACTIONS_GAP;
        [x, y] = after + width <= innerWidth - MARGIN ? [after, last.top + last.height / 2 - height / 2] : [last.right - width, last.bottom + ACTIONS_GAP];
      }
      x = Math.min(Math.max(x, MARGIN), Math.max(innerWidth - width - MARGIN, MARGIN));
      y = Math.min(Math.max(y, MARGIN), Math.max(innerHeight - height - MARGIN, MARGIN));
      this.actions.style.left = `${x}px`;
      this.actions.style.top = `${y}px`;
    }
    /** Handles at the selection's ends, the controls by its end, the panel on the side with more room. */
    place() {
      const selection = this.selection;
      if (!selection || this.layer.hidden) return;
      const first = selection.map.box(selection.start);
      const last = selection.map.box(selection.end - 1);
      const vertical = this.options.vertical;
      const put = (el, x, y) => {
        el.style.left = `${x}px`;
        el.style.top = `${y}px`;
      };
      if (vertical) {
        put(this.startHandle, first.left + first.width / 2, first.top);
        put(this.endHandle, last.left + last.width / 2, last.bottom);
      } else {
        put(this.startHandle, first.left, first.top + first.height / 2);
        put(this.endHandle, last.right, last.top + last.height / 2);
      }
      this.placeActions(last);
      const panel = this.panel.style;
      if (this.panel.hidden) return;
      if (this.panelAt) {
        const { width } = this.panelAt;
        panel.width = `${width}px`;
        panel.right = panel.bottom = "";
        const height = this.panel.getBoundingClientRect().height;
        const x = Math.min(Math.max(this.panelAt.x, 0), Math.max(innerWidth - width, 0));
        const y = Math.min(Math.max(this.panelAt.y, 0), Math.max(innerHeight - height, 0));
        put(this.panel, x, y);
        return;
      }
      const word = selection.map.range(selection.start, selection.end).getBoundingClientRect();
      panel.left = panel.right = panel.top = panel.bottom = panel.width = "";
      let side;
      if (vertical) {
        side = innerWidth - word.right >= word.left ? "right" : "left";
        panel.top = `${MARGIN}px`;
      } else {
        side = innerHeight - word.bottom >= word.top ? "bottom" : "top";
        panel.left = panel.right = `${MARGIN}px`;
      }
      panel.setProperty(side, `${MARGIN}px`);
      this.panel.dataset.side = side;
    }
  };

  // src/main/ts/app-lookup.ts
  globalThis.yomiageLookup = { WordLookup };
})();
