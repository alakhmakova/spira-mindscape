import {
  createContext,
  useContext,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { toast } from "sonner";
import type { Resource } from "@/lib/spira/types";
import { resourceDisplayName } from "@/lib/spira/resources";
import {
  resourceToken,
  rewriteResourceTokens,
  stripResourceTokens,
} from "@/lib/spira/links";

/*
 * The non-component half of `inline-resources.tsx`: the context, the token helpers, the measuring
 * hooks and the reveal classes. Split out so that file exports only components — React's fast
 * refresh cannot hot-swap a module that mixes the two, and reloads the page instead.
 */

/**
 * Goal-scoped services every inline field needs to render and manage attached resources.
 * Provided by `InlineResourcesProvider` (Resources.tsx), which owns the preview panel.
 * Absent outside a goal workspace — components must degrade gracefully when it is null.
 */
export type InlineResourcesValue = {
  goalId: string;
  resources: Resource[];
  /** Create a `link` resource for the URL; resolves with its id (null if the create failed). */
  createLinkResource: (url: string) => Promise<string | null>;
  /** "open" follows a link out to the site; "preview" always opens the editable preview panel. */
  openResource: (id: string, mode?: "open" | "preview") => void;
};

const InlineResourcesContext = createContext<InlineResourcesValue | null>(null);

export const InlineResourcesContextProvider = InlineResourcesContext.Provider;

export function useInlineResources(): InlineResourcesValue | null {
  return useContext(InlineResourcesContext);
}

/**
 * Append a `{{res:id}}` reference to a field's text. Returns null (and explains why) when the
 * field has no room left — the token must never push a value past the server's limit.
 */
export function appendResourceToken(
  text: string,
  resourceId: string,
  maxLength?: number,
): string | null {
  const base = text.trim();
  const next = base
    ? `${base} ${resourceToken(resourceId)}`
    : resourceToken(resourceId);
  if (maxLength !== undefined && next.length > maxLength) {
    toast.error(
      `No room to attach a resource here — this field is limited to ${maxLength} characters.`,
    );
    return null;
  }
  return next;
}

/**
 * The text a field shows while being edited: each tag carries the resource's **name** instead of
 * its id, so `Read {{res:Job ad}} first` is readable. A tag whose id can't be resolved is left
 * untouched rather than guessed at.
 */
export function tokensToNames(text: string, resources: Resource[]): string {
  return rewriteResourceTokens(text, (inner) => {
    const resource = resources.find((r) => r.id === inner);
    return resource ? resourceDisplayName(resource) : inner;
  });
}

/**
 * The text a field stores: each tag's name (or id, if the user left one in place) is mapped back
 * to the resource id. A tag naming something that no longer exists degrades to plain text — the
 * words stay and no dangling reference is written.
 */
export function namesToTokens(text: string, resources: Resource[]): string {
  return rewriteResourceTokens(text, (inner) => {
    const byId = resources.find((r) => r.id === inner);
    if (byId) return byId.id;
    const lower = inner.toLowerCase();
    const byName = resources.find(
      (r) => resourceDisplayName(r).toLowerCase() === lower,
    );
    return byName ? byName.id : null;
  });
}

/**
 * A field's text as plain prose: resource tags become the resource's name (or vanish, if it no
 * longer exists). Use wherever a value is quoted rather than rendered — dialogs, toasts — so a raw
 * `{{res:42}}` never reaches the user.
 */
export function useReadableText(text: string): string {
  const ctx = useInlineResources();
  const resources = ctx?.resources ?? [];
  return stripResourceTokens(text, (inner) => {
    const resource = resources.find((r) => r.id === inner);
    return resource ? resourceDisplayName(resource) : "";
  });
}

/**
 * Tracks whether the element the ref is on currently renders as a single line of text. Row
 * controls sit vertically centred beside one-line content and jump to the top-right corner as soon
 * as the text wraps, so they never float in the middle of a paragraph.
 */
export function useIsSingleLine<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);
  const [singleLine, setSingleLine] = useState(true);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
      // 1.5 line-heights: comfortably above one line, comfortably below two.
      setSingleLine(el.getBoundingClientRect().height <= lineHeight * 1.5);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return { ref, singleLine } as const;
}

/**
 * True once the element runs past `lines` lines of text. Used where a control should stop being
 * vertically centred beside a long title and ride to the top instead.
 */
export function useTallText<T extends HTMLElement>(lines: number) {
  const ref = useRef<T | null>(null);
  const [tall, setTall] = useState(false);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const lineHeight = parseFloat(getComputedStyle(el).lineHeight);
      if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
      setTall(el.getBoundingClientRect().height > lineHeight * (lines + 0.5));
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [lines]);

  return { ref, tall } as const;
}

/** Vertical placement for a row's floating controls, from `useIsSingleLine`. */
export function rowControlPlacement(singleLine: boolean): string {
  return singleLine ? "top-1/2 -translate-y-1/2" : "top-1";
}

/**
 * Classes that keep an element's menu out of the way until the row is actually being worked on:
 * hidden by default, revealed on hover, on focus anywhere inside the row (so tabbing in shows it
 * at the same moment the inline editing caret appears), on its own keyboard focus, and while its
 * menu is open. `pointer-events-none` while hidden means the invisible trigger never swallows a
 * click meant for the text it floats over.
 */
export const REVEAL_ON_ROW_ACTIVITY = [
  "opacity-0 pointer-events-none transition-opacity",
  "group-hover:opacity-100 group-hover:pointer-events-auto",
  "group-focus-within:opacity-100 group-focus-within:pointer-events-auto",
  "focus-visible:opacity-100 focus-visible:pointer-events-auto",
  "data-[state=open]:opacity-100 data-[state=open]:pointer-events-auto",
].join(" ");

/**
 * The same reveal, minus `group-focus-within` — for a row that holds controls of its own (an
 * Options card has a rating badge and a select radio). Focus anywhere in such a row would pop the
 * menu open on a tap that had nothing to do with editing; the caller pairs this with
 * {@link REVEAL_ACTIVE} keyed to the *text's* focus, so the menu appears with the editing caret
 * and not before.
 */
export const REVEAL_ON_ROW_HOVER = [
  "opacity-0 pointer-events-none transition-opacity",
  "group-hover:opacity-100 group-hover:pointer-events-auto",
  "focus-visible:opacity-100 focus-visible:pointer-events-auto",
  "data-[state=open]:opacity-100 data-[state=open]:pointer-events-auto",
].join(" ");

/** Shown outright. Chosen INSTEAD of a reveal class, never alongside one — two `opacity-*`
 *  utilities on the same element resolve by stylesheet order, not by the order written here. */
export const REVEAL_ACTIVE =
  "opacity-100 pointer-events-auto transition-opacity";
