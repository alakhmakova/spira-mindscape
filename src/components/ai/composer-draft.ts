import type { ChatAttachment } from "./ai-api";

/**
 * **What is half-composed in the AI composer, kept across a page reload.**
 *
 * The Android composer survives the process being killed — draft and every chip, resource or photo
 * (BUG-050). The web's lived entirely in React state, so an accidental F5, a crashed tab or a
 * restored session dropped the sentence someone was in the middle of writing and every file they
 * had attached to it, with nothing said. This is the web half of that fix (owner, 2026-08-24), and
 * it is deliberately shaped like Android's:
 *
 * | | Android | Web |
 * |---|---|---|
 * | draft + chip metadata | `SavedStateHandle` | `localStorage` |
 * | a chip's bytes | a file parked in the app cache, referenced by path | a record in **IndexedDB**, referenced by key |
 *
 * **The bytes do not go in localStorage.** Six attachments can be several megabytes and the quota
 * is about five for the whole origin — shared with the chat transcript, which is the more
 * important thing in there. Writing them would risk a `QuotaExceededError` that takes the
 * transcript down with it. IndexedDB has its own, far larger budget, and is the browser's nearest
 * equivalent to the cache directory Android parks its copies in.
 *
 * Everything here is best-effort: private browsing, a blocked store or a full disk must cost the
 * user nothing more than the behaviour they had before, which is that the draft is not kept.
 */

const DRAFT_PREFIX = "spira.ai.composer.";
const DB_NAME = "spira-composer";
const DB_VERSION = 1;
const STORE = "blobs";

/** A parked blob is collected after this long, in case a scope's draft is never cleared. */
const BLOB_TTL_MS = 7 * 24 * 60 * 60 * 1000;

/** One chip as it is written down: never bytes, only what is needed to find them again. */
type StoredChip = {
  name: string;
  mime: string;
  /** A goal's saved resource — the server already holds the file, so the id is the whole chip. */
  resourceId?: number;
  /** IndexedDB key of the record holding this chip's data URL. */
  blobKey?: string;
};

type StoredDraft = { text: string; chips: StoredChip[] };

type BlobRecord = {
  key: string;
  scope: string;
  dataUrl: string;
  savedAt: number;
};

const draftKey = (scope: string) => `${DRAFT_PREFIX}${scope}`;

// ── IndexedDB, wrapped just enough ──────────────────────────────────────────

function openDb(): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    if (typeof indexedDB === "undefined") return resolve(null);
    let req: IDBOpenDBRequest;
    try {
      req = indexedDB.open(DB_NAME, DB_VERSION);
    } catch {
      return resolve(null);
    }
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: "key" }).createIndex(
          "scope",
          "scope",
        );
      }
    };
    req.onsuccess = () => resolve(req.result);
    // A blocked or refused database is not an error the user can act on — the draft simply
    // isn't kept, exactly as before this existed.
    req.onerror = () => resolve(null);
    req.onblocked = () => resolve(null);
  });
}

/** Run `work` against the blob store, resolving to null if anything at all goes wrong. */
async function withStore<T>(
  mode: IDBTransactionMode,
  work: (store: IDBObjectStore) => IDBRequest | null,
): Promise<T | null> {
  const db = await openDb();
  if (!db) return null;
  return new Promise<T | null>((resolve) => {
    let request: IDBRequest | null = null;
    try {
      const tx = db.transaction(STORE, mode);
      request = work(tx.objectStore(STORE));
      tx.oncomplete = () => {
        db.close();
        resolve((request?.result ?? null) as T | null);
      };
      tx.onerror = tx.onabort = () => {
        db.close();
        resolve(null);
      };
    } catch {
      db.close();
      resolve(null);
    }
  });
}

/**
 * The keys of one scope's blobs — **keys only**.
 *
 * This runs on every debounced save, to find blobs whose chip has been removed. Reading the
 * records themselves here would pull every attached photo back out of the database on each
 * keystroke; the `scope` index answers it without touching a single byte of image data.
 */
async function blobKeysFor(scope: string): Promise<IDBValidKey[]> {
  const keys = await withStore<IDBValidKey[]>("readonly", (store) =>
    store.index("scope").getAllKeys(scope),
  );
  return keys ?? [];
}

/** One scope's blobs, with their bytes. Only on load — and it sweeps stale ones while it is here. */
async function readBlobs(scope: string): Promise<Map<string, string>> {
  const all = await withStore<BlobRecord[]>("readonly", (store) =>
    store.getAll(),
  );
  const out = new Map<string, string>();
  if (!all) return out;
  const stale = all.filter((r) => Date.now() - r.savedAt > BLOB_TTL_MS);
  if (stale.length) {
    await withStore("readwrite", (store) => {
      stale.forEach((r) => store.delete(r.key));
      return null;
    });
  }
  for (const r of all) {
    if (r.scope === scope && Date.now() - r.savedAt <= BLOB_TTL_MS)
      out.set(r.key, r.dataUrl);
  }
  return out;
}

async function writeBlobs(records: BlobRecord[], dropKeys: string[]) {
  await withStore("readwrite", (store) => {
    dropKeys.forEach((k) => store.delete(k));
    records.forEach((r) => store.put(r));
    return null;
  });
}

async function dropScope(scope: string) {
  const keys = await blobKeysFor(scope);
  if (keys.length) await writeBlobs([], keys as string[]);
}

// ── The three operations the composer needs ─────────────────────────────────

const newKey = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

/**
 * Write down what is in the composer. Chips that carry bytes get an IndexedDB record; a chip that
 * is already stored keeps its key, so typing a letter does not rewrite several megabytes.
 *
 * Returns the attachments with their keys attached, so the caller can hold on to them and the next
 * save is cheap.
 */
export async function saveComposerDraft(
  scope: string,
  text: string,
  attachments: ChatAttachment[],
  keyed: WeakMap<ChatAttachment, string>,
): Promise<void> {
  const chips: StoredChip[] = [];
  const records: BlobRecord[] = [];
  const live = new Set<string>();

  for (const a of attachments) {
    if (a.resourceId != null) {
      chips.push({ name: a.name, mime: a.mime, resourceId: a.resourceId });
      continue;
    }
    if (!a.dataUrl) continue;
    let key = keyed.get(a);
    if (!key) {
      key = newKey();
      keyed.set(a, key);
      records.push({ key, scope, dataUrl: a.dataUrl, savedAt: Date.now() });
    }
    live.add(key);
    chips.push({ name: a.name, mime: a.mime, blobKey: key });
  }

  if (!text && chips.length === 0) {
    await clearComposerDraft(scope);
    return;
  }

  try {
    window.localStorage.setItem(
      draftKey(scope),
      JSON.stringify({ text, chips } satisfies StoredDraft),
    );
  } catch {
    /* quota or private mode — the draft just isn't kept */
  }

  // Blobs whose chip has been removed are litter; nothing else would ever collect them.
  const existing = await blobKeysFor(scope);
  const orphans = existing.filter((k) => !live.has(String(k))) as string[];
  if (records.length || orphans.length) await writeBlobs(records, orphans);
}

/** What was left in the composer for this scope, or an empty draft. */
export async function loadComposerDraft(scope: string): Promise<{
  text: string;
  attachments: ChatAttachment[];
  keys: [ChatAttachment, string][];
}> {
  const empty = { text: "", attachments: [], keys: [] };
  let stored: StoredDraft;
  try {
    const raw = window.localStorage.getItem(draftKey(scope));
    if (!raw) return empty;
    stored = JSON.parse(raw) as StoredDraft;
  } catch {
    return empty;
  }
  if (typeof stored?.text !== "string" || !Array.isArray(stored.chips))
    return empty;

  const needsBlobs = stored.chips.some((c) => c.blobKey);
  const blobs = needsBlobs ? await readBlobs(scope) : new Map<string, string>();

  const attachments: ChatAttachment[] = [];
  const keys: [ChatAttachment, string][] = [];
  for (const chip of stored.chips) {
    if (chip.resourceId != null) {
      attachments.push({
        name: chip.name,
        mime: chip.mime,
        resourceId: chip.resourceId,
      });
      continue;
    }
    const dataUrl = chip.blobKey ? blobs.get(chip.blobKey) : undefined;
    // A chip whose bytes have gone — storage cleared, or collected as stale — is dropped
    // rather than restored as a name that would send nothing.
    if (!dataUrl || !chip.blobKey) continue;
    const a: ChatAttachment = { name: chip.name, mime: chip.mime, dataUrl };
    attachments.push(a);
    keys.push([a, chip.blobKey]);
  }
  return { text: stored.text, attachments, keys };
}

/** The message went, or the draft was emptied: forget all of it, bytes included. */
export async function clearComposerDraft(scope: string): Promise<void> {
  try {
    window.localStorage.removeItem(draftKey(scope));
  } catch {
    /* ignore */
  }
  await dropScope(scope);
}
