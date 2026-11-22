"use strict";
// The reader page: the whole book as one scroll. ReaderPage hands it chapters as it asks for them; it keeps a few around
// the reader, and reports where the reader is and when the page is tapped.
(() => {
    const BLOCK = "p, h1, h2, h3, h4, h5, h6, li, div";
    /** How long scrolling must stop before the position is reported. */
    const SETTLE = 400;
    /** Chapters in the page at once: a long web novel can't all be. */
    const KEPT = 5;
    /** How near an end, in screens, the next chapter is asked for. */
    const AHEAD = 2;
    const text = document.getElementById("text");

    const post = (message) => window.yomiyouHost?.postMessage(JSON.stringify(message));

    /** How long a tap waits to see whether it's the first of a double tap. */
    const DOUBLE_TAP = 300;

    /** {vertical, count, lo, hi, pending, target}: the loaded chapters are lo..hi. */
    let book = null;
    let spans = [];
    /** {phrase, chapter}: the phrase being spoken, and its elements lit in the page. */
    let spoken = null;
    let lit = [];

    /** Looking words up (lookup.js, from yomiage): the app answers the dictionary's queries. */
    let lookup = null;
    let hasDictionary = false;
    const queries = new Map();
    let lastQuery = 0;
    const dictionary = {
        find: (texts) => new Promise((resolve) => {
            const id = ++lastQuery;
            queries.set(id, resolve);
            post({ type: "find", id, texts });
        }),
    };
    if (window.yomiyouHost) {
        window.yomiyouHost.onmessage = (event) => {
            const reply = JSON.parse(event.data);
            queries.get(reply.id)?.(reply.entries);
            queries.delete(reply.id);
        };
    }

    const startLookup = () => {
        lookup?.dispose();
        lookup = new yomiageLookup.WordLookup(text, {
            dictionary: () => (hasDictionary ? dictionary : null),
            vertical: vertical(),
            onHold: () => post({ type: "hold" }),
            onOpen: (open) => post({ type: "lookup", open }),
            fix: book?.server ? (selected) => post({ type: "fix", text: selected }) : undefined,
            // The app magnifies the selection's edge being dragged: in the WebView's pixels.
            onDrag: (point) =>
                post(point ? { type: "drag", x: point.x * devicePixelRatio, y: point.y * devicePixelRatio } : { type: "drag" }),
        });
    };

    const vertical = () => book?.vertical === true;

    const view = () => (vertical() ? text.getBoundingClientRect() : { left: 0, right: innerWidth, top: 0, bottom: innerHeight });

    /** The first phrase on screen. Phrases run in page order, so a binary search finds it. */
    const firstVisible = () => {
        const bounds = view();
        let low = 0;
        let high = spans.length;
        while (low < high) {
            const middle = (low + high) >> 1;
            const box = spans[middle].getBoundingClientRect();
            const before = vertical() ? box.left >= bounds.right : box.bottom <= bounds.top;
            if (before) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return spans[low] ?? null;
    };

    /** What marks a place: a phrase in a narrated book, a block (its path from its document's body) in a plain one. */
    const places = () => (book?.plain ? "[data-x]" : "[data-y]");

    const report = (place) => {
        const section = place?.closest("section");
        if (!section) return;
        const chapter = Number(section.dataset.chapter);
        if (book?.plain) {
            if (place.dataset.x) post({ type: "position", chapter, path: place.dataset.x });
            return;
        }
        const number = Number(place.dataset.y);
        if (Number.isInteger(number)) post({ type: "position", chapter, span: number });
    };

    /**
     * The block a path names in [section]: the block itself, the first block inside the element it names (a container,
     * or `id("…")` as Kavita writes one), else the nearest named block around it.
     */
    const findPlace = (section, path) => {
        if (!path) return null;
        const blocks = [...section.querySelectorAll("[data-x]")];
        const id = /^id\("(.+)"\)$/.exec(path);
        if (id) {
            const element = [...section.querySelectorAll("[id]")].find((each) => each.id === id[1]);
            return element ? (element.closest("[data-x]") ?? element.querySelector("[data-x]")) : null;
        }
        for (let prefix = path; prefix.length > "//body".length; prefix = prefix.slice(0, prefix.lastIndexOf("/"))) {
            const found = blocks.find((each) => each.dataset.x === prefix || each.dataset.x.startsWith(prefix + "/"));
            if (found) return found;
        }
        return null;
    };

    /** Content outside the view, before and after it, in pixels. */
    const room = () => {
        if (vertical()) {
            const before = -text.scrollLeft;
            return { before, after: text.scrollWidth - text.clientWidth - before };
        }
        return { before: scrollY, after: document.documentElement.scrollHeight - innerHeight - scrollY };
    };

    const screenSize = () => (vertical() ? text.clientWidth : innerHeight);

    /** The first phrase on screen and where it is, so a change elsewhere in the page can leave it where it was. */
    const anchor = () => {
        const span = firstVisible();
        return span && { span, box: span.getBoundingClientRect() };
    };

    const restore = (kept) => {
        if (!kept?.span.isConnected) {
            return;
        }
        const box = kept.span.getBoundingClientRect();
        if (vertical()) {
            text.scrollLeft += box.left - kept.box.left;
        } else {
            scrollBy(0, box.top - kept.box.top);
        }
    };

    /** Scrolls only when the phrase has left the screen, then brings it to the middle. */
    const keepInView = (element) => {
        const box = element.getBoundingClientRect();
        const bounds = view();
        if (box.left < bounds.left || box.right > bounds.right || box.top < bounds.top || box.bottom > bounds.bottom) {
            element.scrollIntoView({ block: "center", inline: "nearest", behavior: "smooth" });
        }
    };

    const applyLight = (follow) => {
        lit.forEach((element) => element.classList.remove("now"));
        lit = spoken === null ? [] : [...text.querySelectorAll(`[data-y="${spoken.phrase}"]`)];
        lit.forEach((element) => element.classList.add("now"));
        if (follow && lit[0]) {
            keepInView(lit[0]);
        }
    };

    const setStyle = (variables) => {
        for (const [name, value] of Object.entries(variables ?? {})) {
            document.documentElement.style.setProperty(name, value);
        }
    };

    const sectionOf = (chapter, html, title) => {
        const parsed = new DOMParser().parseFromString(html, "application/xhtml+xml");
        if (parsed.querySelector("parsererror")) {
            throw new Error(`Damaged chapter ${chapter}`);
        }
        const section = document.createElement("section");
        section.dataset.chapter = String(chapter);
        for (const node of [...parsed.documentElement.childNodes]) {
            section.append(document.importNode(node, true));
        }
        const first = section.querySelector(places())?.closest(BLOCK);
        if (first && first.textContent.trim() === title) {
            first.classList.add("heading");
        }
        return section;
    };

    const ask = (chapter) => {
        if (book.pending !== null || chapter < 0 || chapter >= book.count) {
            return;
        }
        book.pending = chapter;
        post({ type: "need", chapter });
    };

    /** Asks for the next chapter at whichever end is near. */
    const fill = () => {
        if (!book || book.pending !== null || book.hi < book.lo) {
            return;
        }
        const { before, after } = room();
        const near = AHEAD * screenSize();
        if (after < near && book.hi < book.count - 1) {
            ask(book.hi + 1);
        } else if (before < near && book.lo > 0) {
            ask(book.lo - 1);
        }
    };

    window.reader = {
        /**
         * @param start {vertical, plain, count, chapter, span, path, style, dictionary, server}; span: a phrase to show,
         *   else the chapter's start; plain: a book without narration, its places blocks (path: the one to show);
         *   server: the book came from the yomiage server, so a reading can be fixed
         */
        open(start) {
            book = {
                vertical: start.vertical,
                plain: start.plain === true,
                server: start.server === true,
                count: start.count,
                lo: start.chapter,
                hi: start.chapter - 1,
                pending: null,
                target: { span: start.span, path: start.path ?? null },
            };
            text.replaceChildren();
            spans = [];
            text.className = "text " + (start.vertical ? "vertical" : "horizontal");
            if (start.dictionary !== undefined) hasDictionary = start.dictionary === true;
            startLookup();
            setStyle(start.style);
            ask(start.chapter);
        },

        /** @param chapter {chapter, html, title}: the one last asked for */
        add(chapter) {
            if (!book || chapter.chapter !== book.pending) {
                return;
            }
            book.pending = null;
            let section;
            try {
                section = sectionOf(chapter.chapter, chapter.html, chapter.title);
            } catch (error) {
                post({ type: "error", message: error.message });
                return;
            }
            const kept = book.target ? null : anchor();
            if (book.hi < book.lo) {
                text.append(section);
                book.lo = book.hi = chapter.chapter;
            } else if (chapter.chapter === book.hi + 1) {
                text.append(section);
                book.hi++;
                if (book.hi - book.lo >= KEPT) {
                    text.firstElementChild.remove();
                    book.lo++;
                }
            } else if (chapter.chapter === book.lo - 1) {
                text.prepend(section);
                book.lo--;
                if (book.hi - book.lo >= KEPT) {
                    text.lastElementChild.remove();
                    book.hi--;
                }
            } else {
                return;
            }
            spans = text.querySelectorAll(places());
            if (book.target) {
                const { span, path } = book.target;
                book.target = null;
                const at = book.plain
                    ? findPlace(section, path)
                    : span === null ? null : text.querySelector(`[data-y="${span}"]`);
                // At the start, not the center: the first phrase on screen is what's kept, so the place holds still.
                (at ?? section).scrollIntoView({ block: "start", inline: "start" });
                report(at ?? section.querySelector(places()));
            } else {
                restore(kept);
            }
            applyLight(false);
            fill();
        },

        /** Lights the phrase being spoken (null: none), going to its chapter when the page holds another part. */
        light(phrase, chapter) {
            if (phrase === null) {
                spoken = null;
                applyLight(false);
                return;
            }
            const changed = spoken?.phrase !== phrase;
            spoken = { phrase, chapter };
            if (book && (chapter < book.lo || chapter > book.hi) && book.target === null) {
                reader.open({ vertical: book.vertical, plain: book.plain, count: book.count, chapter, span: phrase });
            } else if (changed) {
                applyLight(true);
            }
        },

        jump(chapter) {
            reader.open({ vertical: book.vertical, plain: book.plain, count: book.count, chapter, span: null });
        },

        /** Whether holding a word looks it up. */
        dictionary(has) {
            hasDictionary = has;
            if (!has) lookup?.close();
        },

        closeLookup() {
            lookup?.close();
        },

        /** Applies a new style, keeping the first phrase on screen at the start of the screen. */
        style(variables) {
            const kept = firstVisible();
            setStyle(variables);
            kept?.scrollIntoView({ block: "start", inline: "start" });
            fill();
        },
    };

    let settle = 0;
    const scrolled = () => {
        fill();
        clearTimeout(settle);
        settle = setTimeout(() => report(firstVisible()), SETTLE);
    };
    text.addEventListener("scroll", scrolled);
    addEventListener("scroll", scrolled);
    let tap = 0;
    addEventListener("click", (event) => {
        // The lookup's buttons and panel aren't the page: no tap there.
        if (event.target.closest?.(".lookup-layer")) {
            return;
        }
        if (tap) {
            clearTimeout(tap);
            tap = 0;
            post({ type: "double" });
            return;
        }
        const span = event.target.closest?.("[data-y]");
        const phrase = span ? Number(span.dataset.y) : null;
        tap = setTimeout(() => {
            tap = 0;
            post({ type: "tap", span: phrase });
        }, DOUBLE_TAP);
    });
    post({ type: "ready" });
})();
