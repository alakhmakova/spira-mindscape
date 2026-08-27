import { describe, it, expect, beforeEach } from "vitest";
import "fake-indexeddb/auto";
import {
  saveComposerDraft,
  loadComposerDraft,
  clearComposerDraft,
} from "./composer-draft";
import type { ChatAttachment } from "./ai-api";

/**
 * **The composer's unsent draft, across a page reload** (BUG-049).
 *
 * The rule this file guards is the one Android's `ComposerAttachmentsTest` guards on the other
 * surface: everything comes back, and the **bytes never go into the small store**. On Android the
 * small store is a Bundle and the bytes live in a cache file; here the small store is
 * `localStorage` — shared with the chat transcript, and about five megabytes for the whole origin
 * — and the bytes live in IndexedDB.
 */

const SCOPE = "spira.ai.chat.7";
const DRAFT_KEY = `spira.ai.composer.${SCOPE}`;

const photo = (name: string, body: string): ChatAttachment => ({
  name,
  mime: "image/jpeg",
  dataUrl: `data:image/jpeg;base64,${body}`,
});

const resource = (id: number, name: string): ChatAttachment => ({
  name,
  mime: "",
  resourceId: id,
});

describe("the composer draft store", () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await clearComposerDraft(SCOPE);
  });

  it("brings back the text and a resource chip", async () => {
    await saveComposerDraft(
      SCOPE,
      "look at this",
      [resource(42, "CV.pdf")],
      new WeakMap(),
    );

    const back = await loadComposerDraft(SCOPE);

    expect(back.text).toBe("look at this");
    expect(back.attachments).toEqual([
      { name: "CV.pdf", mime: "", resourceId: 42 },
    ]);
  });

  it("brings back a photo's bytes as well", async () => {
    // The half that Android needed a cache file for. A picked file has no "other road" back —
    // if it is not written down it is simply gone.
    await saveComposerDraft(
      SCOPE,
      "",
      [photo("shot.jpg", "QUJD")],
      new WeakMap(),
    );

    const back = await loadComposerDraft(SCOPE);

    expect(back.attachments).toHaveLength(1);
    expect(back.attachments[0].dataUrl).toBe("data:image/jpeg;base64,QUJD");
    expect(back.attachments[0].name).toBe("shot.jpg");
  });

  it("keeps the BYTES out of localStorage — they belong in IndexedDB", async () => {
    // localStorage is shared with the chat transcript and capped around 5 MB for the origin.
    // Six attachments would risk a QuotaExceededError that takes the transcript down too.
    await saveComposerDraft(
      SCOPE,
      "here",
      [photo("shot.jpg", "QUJD"), resource(42, "CV.pdf")],
      new WeakMap(),
    );

    const raw = window.localStorage.getItem(DRAFT_KEY) ?? "";
    expect(raw).not.toContain("base64");
    expect(raw).not.toContain("QUJD");
    // …and it is genuinely small: metadata only.
    expect(raw.length).toBeLessThan(500);
  });

  it("re-saving an unchanged chip does not write its bytes again", async () => {
    // The draft is written on a debounce as the user types, so a chip that is already stored
    // must keep its key rather than spawning a fresh multi-megabyte record per keystroke.
    const keyed = new WeakMap<ChatAttachment, string>();
    const a = photo("shot.jpg", "QUJD");

    await saveComposerDraft(SCOPE, "h", [a], keyed);
    const firstKey = keyed.get(a);
    await saveComposerDraft(SCOPE, "he", [a], keyed);

    expect(keyed.get(a)).toBe(firstKey);
    const back = await loadComposerDraft(SCOPE);
    expect(back.attachments).toHaveLength(1);
  });

  it("collects the bytes of a chip the user has removed", async () => {
    const keyed = new WeakMap<ChatAttachment, string>();
    const kept = photo("kept.jpg", "QUJD");
    const dropped = photo("dropped.jpg", "WFla");

    await saveComposerDraft(SCOPE, "", [kept, dropped], keyed);
    await saveComposerDraft(SCOPE, "", [kept], keyed);

    const back = await loadComposerDraft(SCOPE);
    expect(back.attachments.map((x) => x.name)).toEqual(["kept.jpg"]);
    // Nothing else would ever collect the orphan: no chip references it any more.
    const db: IDBDatabase = await new Promise((res) => {
      const r = indexedDB.open("spira-composer");
      r.onsuccess = () => res(r.result);
    });
    const rows: unknown[] = await new Promise((res) => {
      const r = db.transaction("blobs").objectStore("blobs").getAll();
      r.onsuccess = () => res(r.result);
    });
    db.close();
    expect(rows).toHaveLength(1);
  });

  it("forgets everything once the message is sent", async () => {
    await saveComposerDraft(
      SCOPE,
      "look",
      [photo("shot.jpg", "QUJD")],
      new WeakMap(),
    );

    await clearComposerDraft(SCOPE);

    const back = await loadComposerDraft(SCOPE);
    expect(back.text).toBe("");
    expect(back.attachments).toEqual([]);
    expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
  });

  it("emptying the composer clears the stored draft rather than saving nothing", async () => {
    await saveComposerDraft(SCOPE, "typed", [], new WeakMap());
    await saveComposerDraft(SCOPE, "", [], new WeakMap());

    expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull();
  });

  it("drops a chip whose bytes have gone rather than restoring a name that sends nothing", async () => {
    await saveComposerDraft(
      SCOPE,
      "still here",
      [photo("shot.jpg", "QUJD")],
      new WeakMap(),
    );

    // Storage cleared under it — the browser's prerogative, and the same shape as Android's
    // cache being swept.
    const db: IDBDatabase = await new Promise((res) => {
      const r = indexedDB.open("spira-composer");
      r.onsuccess = () => res(r.result);
    });
    await new Promise((res) => {
      const r = db
        .transaction("blobs", "readwrite")
        .objectStore("blobs")
        .clear();
      r.onsuccess = () => res(null);
    });
    db.close();

    const back = await loadComposerDraft(SCOPE);
    expect(back.text).toBe("still here");
    expect(back.attachments).toEqual([]);
  });

  it("keeps each chat's draft to itself", async () => {
    await saveComposerDraft(SCOPE, "goal seven", [], new WeakMap());
    await saveComposerDraft(
      "spira.ai.chat.global",
      "all goals",
      [],
      new WeakMap(),
    );

    expect((await loadComposerDraft(SCOPE)).text).toBe("goal seven");
    expect((await loadComposerDraft("spira.ai.chat.global")).text).toBe(
      "all goals",
    );
    await clearComposerDraft("spira.ai.chat.global");
  });
});
