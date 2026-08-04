import type { ChecklistItem, Goal, Resource } from "./types";
import { FIELD_LIMITS } from "./limits";
import { referencesResource, replaceResourceToken } from "./links";

/** Short human label for a bare URL — the site's name, e.g. "github" for github.com/x. */
export function titleFromUrl(url: string): string {
  try {
    const host = new URL(url).hostname.replace(/^www\./, "").toLowerCase();
    return host.split(".")[0] || host;
  } catch {
    return url;
  }
}

/** The name a resource shows under — on its card, in a picker, and inside an inline chip. */
export function resourceDisplayName(resource: Resource): string {
  if (resource.type === "note") return resource.title.trim() || "Untitled note";
  if (resource.type === "link")
    return resource.title.trim() || titleFromUrl(resource.url);
  if (resource.type === "file") return resource.title.trim() || "Untitled file";
  return resource.name?.trim() || resource.email?.trim() || "Email";
}

/**
 * One element whose text references a resource that is being deleted, with the text it should
 * keep. Applying these (via the store's update actions) turns every `{{res:id}}` chip into plain
 * text so nothing disappears and no broken reference is left behind.
 */
export type DetachPatch =
  | { kind: "option"; optionId: string; text: string }
  | {
      kind: "reality";
      realityKind: "actions" | "obstacles";
      itemId: string;
      text: string;
    }
  | { kind: "targetTitle"; targetId: string; title: string }
  | { kind: "checklist"; targetId: string; items: ChecklistItem[] };

/** Keep a replacement inside the field's server limit; a very long title is cut, never rejected. */
function fit(text: string, max: number): string {
  return text.length <= max ? text : text.slice(0, max - 1).trimEnd() + "…";
}

/**
 * Plan the text changes needed to detach `resourceId` from every element of the goal that
 * references it, replacing each token with `label` (the resource's title).
 *
 * Pure and client-side: a goal's whole graph is loaded, so no server sweep is needed.
 */
export function planResourceDetach(
  goal: Goal,
  resourceId: string,
  label: string,
): DetachPatch[] {
  const text = label.trim() || "resource";
  const patches: DetachPatch[] = [];
  const replace = (value: string, max: number) =>
    fit(replaceResourceToken(value, resourceId, text), max);

  for (const option of goal.options) {
    if (referencesResource(option.text, resourceId)) {
      patches.push({
        kind: "option",
        optionId: option.id,
        text: replace(option.text, FIELD_LIMITS.optionText),
      });
    }
  }

  for (const realityKind of ["actions", "obstacles"] as const) {
    for (const item of goal.reality[realityKind]) {
      if (referencesResource(item.text, resourceId)) {
        patches.push({
          kind: "reality",
          realityKind,
          itemId: item.id,
          text: replace(item.text, FIELD_LIMITS.realityText),
        });
      }
    }
  }

  for (const target of goal.targets) {
    if (referencesResource(target.title, resourceId)) {
      patches.push({
        kind: "targetTitle",
        targetId: target.id,
        title: replace(target.title, FIELD_LIMITS.targetTitle),
      });
    }
    if (
      target.type === "checklist" &&
      target.items.some((item) => referencesResource(item.text, resourceId))
    ) {
      patches.push({
        kind: "checklist",
        targetId: target.id,
        items: target.items.map((item) =>
          referencesResource(item.text, resourceId)
            ? { ...item, text: replace(item.text, FIELD_LIMITS.checklistText) }
            : item,
        ),
      });
    }
  }

  return patches;
}

/** How many elements of the goal have this resource attached (0 = safe to delete silently). */
export function countResourceAttachments(
  goal: Goal,
  resourceId: string,
): number {
  return planResourceDetach(goal, resourceId, "x").length;
}
