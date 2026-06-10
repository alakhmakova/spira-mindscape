// Pure, framework-free logic behind the AI assistant's proposal cards.
//
// Extracted from AiPanel.tsx so it can be unit-tested in isolation (see
// proposal-logic.test.ts). Everything here is a pure function over plain data — no React,
// no store, no DOM — which is exactly what makes the card behaviour (parsing tool calls,
// keeping several creates, field "aspects", structural summaries) cheap to lock down with
// tests. See specs/2026-06-07-ai-assistant-cards-and-drawers/.

export type ProposalKind =
  // create a brand-new goal (used from the global / All-Goals chat)
  | "new_goal"
  // create / goal-level
  | "target" | "task" | "option" | "note" | "link" | "email" | "edit" | "obstacle" | "action" | "confidence" | "deadline"
  // edit existing
  | "edit_target" | "edit_option" | "edit_obstacle" | "edit_action" | "edit_note" | "edit_link" | "edit_email"
  // state changes
  | "complete_target" | "target_progress" | "select_option" | "checklist_item" | "add_checklist_item"
  // goal-level by id (All-Goals page) + deletion (opens a confirmation dialog)
  | "edit_goal" | "open_goal" | "delete_goal" | "delete_target"
  // delete smaller items inside a goal
  | "delete_option" | "delete_obstacle" | "delete_action" | "delete_checklist_item";

export type Proposal = {
  id: string;
  kind: ProposalKind;
  title: string;
  detail?: string;
  reasoning?: string;
  status: "pending" | "approved" | "rejected";
  field?: string;       // for "edit": "title" | "description"
  body?: string;        // for "note" / "edit_note"
  deadline?: string;    // ISO date for target/task
  rawValue?: string;    // raw tool argument value (number for confidence/progress, ISO date for deadline)
  serverId?: number;    // persisted ai_proposals row id (absent for global chat)
  itemId?: string;      // id of the existing item to edit/change (edit_*/state kinds)
  done?: boolean;       // for complete_target / checklist_item / binary create
  // Target creation in a final state:
  targetType?: "binary" | "numeric" | "checklist";
  total?: string;       // numeric goal amount
  current?: string;     // numeric starting progress
  unit?: string;        // numeric unit
  items?: { text: string; done?: boolean; deadline?: string }[]; // checklist items
  patch?: Record<string, string>; // resource fields to update (edit_link / edit_email)
  goalId?: string; // target goal id for goal-level ops from All-Goals (edit_goal/open_goal/delete_goal)
  goalTitle?: string; // resolved name of that goal, so the card shows WHICH goal is changing
  openSubject?: string; // open_goal: the concrete thing that can't be edited here (e.g. "the description")
  followup?: string; // open_goal: the user's request to re-run once the goal's chat is open
  confidence?: number; // for new_goal: initial confidence 1-10 the AI extracted
  // Set once a create proposal is applied — persisted in the transcript so the
  // "Open …" shortcut survives closing/reopening the chat.
  createdRef?: { kind: "goal" | "target" | "resource"; goalId: string };
};

export const uid = () => Math.random().toString(36).slice(2, 9);

// Plain-text snippet from (possibly) HTML note content, for one-line card previews.
export const stripHtml = (s: string) =>
  s.replace(/<[^>]*>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();

/** Human-readable deadline for a checkbox label (falls back to the raw ISO string). */
export function fmtDeadline(iso?: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

// Honour every DISTINCT creation the model proposes — the user may legitimately ask
// for several at once ("create 3 goals: …"). We only drop EXACT duplicates (same kind
// + same title fired twice), which some models emit by mistake. Preventing the model
// from inventing unrelated extras is the prompt's job, not this filter's.
export function dedupCreates(creates: Proposal[]): Proposal[] {
  const seen = new Set<string>();
  return creates.filter((p) => {
    const key = `${p.kind}|${(p.title || "").trim().toLowerCase()}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** Does this proposal carry an optional secondary aspect (its own checkbox)?
 *  An option can also be made the active one (the `done` flag). */
export function isOptionActivate(p: Proposal): boolean {
  return p.kind === "option" && !!p.done;
}

/**
 * The optional, individually-toggleable fields of a single create proposal. The entity
 * itself (its name) is always the first checkbox in the card; these are the extras the
 * user can include or skip — so "Goal 3, deadline 12 Dec" shows a Deadline checkbox, and
 * a goal with confidence + deadline + description shows all three. A bare goal (name only)
 * returns [] and falls back to the one-tap CreateConfirmCard.
 */
export function createAspects(p: Proposal): { id: string; label: string; body?: string }[] {
  const a: { id: string; label: string; body?: string }[] = [];
  if (p.kind === "new_goal") {
    if (p.confidence != null) a.push({ id: "confidence", label: `Confidence ${p.confidence}/10` });
    if (p.deadline) a.push({ id: "deadline", label: `Deadline · ${fmtDeadline(p.deadline)}` });
    // Description carries its own content so it can be viewed at the checkbox itself
    // (and never restated under the title).
    if (p.body && p.body.trim()) a.push({ id: "description", label: "Description", body: p.body });
  } else if (p.kind === "target" || p.kind === "task") {
    if (p.deadline) a.push({ id: "deadline", label: `Deadline · ${fmtDeadline(p.deadline)}` });
    if (p.done) a.push({ id: "done", label: "Already done" });
  }
  return a;
}

/** Structural, non-toggleable summary of a create proposal (the numeric measure or
 *  checklist count) — info that defines the target rather than an optional field. Goals
 *  and bare binary targets have none. Shown once, never duplicated as a field checkbox. */
export function createSummary(p: Proposal): string | undefined {
  if (p.kind === "target" || p.kind === "task") {
    if (p.targetType === "numeric") return `${p.current ?? 0} / ${p.total ?? "?"}${p.unit ? " " + p.unit : ""}`;
    if (p.targetType === "checklist") {
      const total = p.items?.length ?? 0;
      const done = p.items?.filter((i) => i.done).length ?? 0;
      return `Checklist · ${done}/${total} done`;
    }
  }
  return undefined;
}

/** A copy of the proposal with any unchecked aspect fields stripped, so only the fields
 *  the user kept ticked are saved. */
export function applyExcludedAspects(p: Proposal, excluded: Set<string>): Proposal {
  if (excluded.size === 0) return p;
  const out: Proposal = { ...p };
  if (excluded.has("confidence")) out.confidence = undefined;
  if (excluded.has("deadline")) out.deadline = undefined;
  if (excluded.has("description")) out.body = undefined;
  if (excluded.has("done")) out.done = false;
  return out;
}

/** Builds a Proposal from the `propose_goal_change` tool-call arguments JSON. */
export function proposalFromToolArgs(argsJson: string): Proposal | undefined {
  try {
    const data = JSON.parse(argsJson) as Record<string, string> & {
      proposalId?: number;
      items?: { text?: string; done?: boolean; deadline?: string }[];
    };
    const kind = (data.kind || "edit") as Proposal["kind"];
    const value = data.value ?? "";
    const name = data.title ?? "";
    const deadlineVal = data.deadline_value;
    const itemId = data.id != null ? String(data.id) : undefined;
    const done = data.done === "true" ? true : data.done === "false" ? false : undefined;

    // Target-creation extras (binary done / numeric / checklist).
    const total = data.total || undefined;
    const current = data.current || undefined;
    const unit = data.unit || undefined;
    const items = Array.isArray(data.items)
      ? data.items
          .filter((i) => i && typeof i.text === "string" && i.text.trim())
          .map((i) => ({ text: i.text!.trim(), done: i.done === true, deadline: i.deadline }))
      : undefined;
    const targetType: Proposal["targetType"] =
      items && items.length ? "checklist" : total ? "numeric" : "binary";

    let title = value || name;
    let detail: string | undefined;
    let body: string | undefined;
    let patch: Record<string, string> | undefined;

    switch (kind) {
      case "new_goal":
        // goal title in `title`; optional description in `value`
        title = name || value;
        body = name && value ? value : undefined; // description (only if distinct from title)
        // The "NEW GOAL" badge already names the kind; show description/deadline
        // here when present, otherwise nothing (an empty goal needs no detail line).
        detail = body
          ? (body.length > 60 ? body.slice(0, 60) + "…" : body)
          : deadlineVal ? `Due ${deadlineVal}` : undefined;
        break;
      case "edit":
        title = value;
        detail = data.field === "description" ? "New description" : "New title";
        break;
      case "confidence":
        title = `Confidence → ${value}/10`;
        detail = "Goal confidence";
        break;
      case "deadline":
        title = value; // ISO date
        detail = "Goal deadline";
        break;
      case "target":
      case "task": {
        title = name || value;
        const noun = kind === "task" ? "task" : "target";
        if (targetType === "checklist") {
          const total = items?.length ?? 0;
          const checked = items?.filter((i) => i.done).length ?? 0;
          detail = `New checklist · ${checked}/${total} done`;
        } else if (targetType === "numeric") {
          detail = `New target · ${current ?? 0}/${total}${unit ? " " + unit : ""}`;
        } else {
          detail = done ? `New ${noun} · done` : deadlineVal ? `New ${noun} · due ${deadlineVal}` : `New ${noun}`;
        }
        break;
      }
      case "option":    title = value || name; detail = "Strategy option"; break;
      case "obstacle":  title = value || name; detail = "New obstacle"; break;
      case "action":    title = value || name; detail = "Current action"; break;
      case "note": {
        title = name || "Note";
        body = value; // HTML
        const plain = stripHtml(value);
        detail = plain.length > 60 ? plain.slice(0, 60) + "…" : plain;
        break;
      }
      case "link": {
        // value = URL, title = optional label
        const next: Record<string, string> = { url: value };
        if (name) next.title = name;
        patch = next;
        title = name || value || "New link";
        detail = "New link";
        break;
      }
      case "email": {
        // value = email address, title = name, plus optional role / phone
        const next: Record<string, string> = {};
        if (name) next.name = name;
        if (value) next.email = value;
        if (data.role) next.role = data.role;
        if (data.phone) next.phone = data.phone;
        patch = next;
        title = name || value || "New contact";
        detail = "New contact";
        break;
      }
      // ── edit existing ──
      case "edit_target":   title = value || name; detail = deadlineVal ? `Edit target · due ${deadlineVal}` : "Edit target"; break;
      case "edit_option":   title = value || name; detail = "Edit option"; break;
      case "edit_obstacle": title = value || name; detail = "Edit obstacle"; break;
      case "edit_action":   title = value || name; detail = "Edit action"; break;
      case "edit_note":
        title = name || "Note";
        body = value;
        detail = "Edit note";
        break;
      case "edit_link": {
        // value = new URL, title = new label (either or both)
        const next: Record<string, string> = {};
        if (name) next.title = name;
        if (value) next.url = value;
        patch = next;
        title = name || value || "Update link";
        detail = "Edit link";
        break;
      }
      case "edit_email": {
        // title = new name, value = new email, plus optional role / phone
        const next: Record<string, string> = {};
        if (name) next.name = name;
        if (value) next.email = value;
        if (data.role) next.role = data.role;
        if (data.phone) next.phone = data.phone;
        patch = next;
        title = name || value || "Update contact";
        detail = "Edit contact";
        break;
      }
      // ── state changes ──
      case "complete_target":
        title = done === false ? "Mark target not done" : "Mark target done";
        detail = "Target status";
        break;
      case "target_progress":
        title = `Progress → ${value}`;
        detail = "Target progress";
        break;
      case "select_option":
        title = "Select this option";
        detail = "Strategy option";
        break;
      case "checklist_item":
        title = value || (done === true ? "Check item" : done === false ? "Uncheck item" : "Update item");
        detail = deadlineVal ? `Checklist item · due ${deadlineVal}` : "Checklist item";
        break;
      case "add_checklist_item":
        title = value || name;
        detail = deadlineVal ? `New sub-task · due ${deadlineVal}` : "New sub-task";
        break;
      // ── goal-level by id (All-Goals) + deletion ──
      case "edit_goal": {
        const f = data.field;
        if (f === "confidence") { title = `Confidence → ${value}/10`; detail = "Edit goal"; }
        else if (f === "deadline") { title = value; detail = "Goal deadline"; }
        else { title = value; detail = "Rename goal"; }
        break;
      }
      case "open_goal":
        title = "Open this goal";
        detail = "Open goal";
        break;
      case "delete_goal":
        title = "Delete this goal";
        detail = "Opens a confirmation";
        break;
      case "delete_target":
        title = "Delete this target";
        detail = "Opens a confirmation";
        break;
      case "delete_option":
      case "delete_obstacle":
      case "delete_action":
      case "delete_checklist_item":
        // Real text resolved from the item id in proposalDisplay.
        title = "Delete this item";
        detail = "Remove";
        break;
    }

    return {
      id: uid(),
      kind,
      title,
      detail,
      reasoning: data.reasoning,
      status: "pending",
      field: data.field,
      body,
      deadline: deadlineVal,
      rawValue: value || undefined,
      itemId,
      done,
      targetType,
      total,
      current,
      unit,
      items,
      patch,
      goalId: (kind === "edit_goal" || kind === "open_goal" || kind === "delete_goal")
        ? itemId
        : undefined,
      // open_goal: the concrete thing the user wanted to change that can't be edited from
      // the overview (e.g. "the description") — named on the card.
      openSubject: kind === "open_goal" ? (value || undefined) : undefined,
      // new_goal: confidence 1-10 the AI extracted (clamped); undefined if not given.
      confidence: kind === "new_goal" && data.confidence
        ? Math.min(10, Math.max(1, parseInt(data.confidence) || 0)) || undefined
        : undefined,
      serverId: typeof data.proposalId === "number" ? data.proposalId : undefined,
    };
  } catch {
    return undefined;
  }
}
