import type {
  ToolColumn,
  ToolPrimitive,
  ToolSchema,
} from "@/lib/spira/tools-api";

// ── Display helpers (declarative "expand the builder" options) ───────────────

/**
 * Fixed CSS classes per badge colour. The class strings are LITERAL (never
 * built by interpolation) so Tailwind keeps them, and an unknown/forged colour
 * can only fall through to the neutral default — never inject styles.
 */
export function badgeClasses(color?: string): string {
  switch (color) {
    case "red":
      return "bg-red-100 text-red-700";
    case "amber":
      return "bg-amber-100 text-amber-700";
    case "green":
      return "bg-green-100 text-green-700";
    case "blue":
      return "bg-blue-100 text-blue-700";
    case "purple":
      return "bg-purple-100 text-purple-700";
    case "pink":
      return "bg-pink-100 text-pink-700";
    case "teal":
      return "bg-teal-100 text-teal-700";
    case "gray":
      return "bg-gray-100 text-gray-700";
    default:
      return "bg-secondary text-foreground/80";
  }
}

/** Tailwind text-alignment class for a column's `align` (default left). */
export function alignClass(align?: string): string {
  return align === "center"
    ? "text-center"
    : align === "right"
      ? "text-right"
      : "text-left";
}

/** Compare two cell values for sorting: numeric when both numbers, else text. */
export function compareCells(a: unknown, b: unknown): number {
  const an = typeof a === "number" ? a : Number(a);
  const bn = typeof b === "number" ? b : Number(b);
  if (!Number.isNaN(an) && !Number.isNaN(bn) && a !== "" && b !== "") {
    return an - bn;
  }
  return String(a ?? "").localeCompare(String(b ?? ""));
}

// Pure, dependency-free helpers for the generic tool renderer. Kept separate so
// the data logic is unit-tested without rendering.

/** A record's data: keyed by column key. Values match the primitive. */
export type ToolRow = Record<string, unknown>;

/** A blank record shaped to the schema (sensible empty value per primitive). */
export function emptyRow(schema: ToolSchema): ToolRow {
  const row: ToolRow = {};
  for (const col of schema.columns) row[col.key] = emptyValue(col.primitive);
  return row;
}

export function emptyValue(primitive: ToolPrimitive): unknown {
  switch (primitive) {
    case "number":
    case "progress":
    case "rating":
      return null;
    case "checkbox":
      return false;
    case "checklist":
      return [] as { label: string; done: boolean }[];
    case "tags":
      return [] as string[];
    default:
      return ""; // text, textarea, date, time, select, url, table cells, chart
  }
}

/** Human-readable display of a stored value for a primitive (read-only cells). */
export function formatCell(primitive: ToolPrimitive, value: unknown): string {
  if (value == null || value === "") return "—";
  switch (primitive) {
    case "checkbox":
      return value ? "✓" : "—";
    case "checklist": {
      const items = Array.isArray(value)
        ? (value as { label: string; done: boolean }[])
        : [];
      if (items.length === 0) return "—";
      const done = items.filter((i) => i.done).length;
      return `${done}/${items.length} done`;
    }
    case "progress":
      return `${Math.max(0, Math.min(100, Number(value) || 0))}%`;
    case "rating": {
      const n = Math.max(0, Math.min(5, Math.round(Number(value) || 0)));
      return "★".repeat(n) + "☆".repeat(5 - n);
    }
    case "tags": {
      const tags = Array.isArray(value) ? (value as string[]) : [];
      return tags.length ? tags.join(", ") : "—";
    }
    default:
      return String(value); // text, textarea, date, time, url, select
  }
}

/** Which primitives are interactive inputs vs read-only/derived displays. */
export function isInputPrimitive(primitive: ToolPrimitive): boolean {
  return primitive !== "chart"; // everything else can be entered; chart is derived
}

/** Columns that should appear as editable inputs in a record form. */
export function inputColumns(schema: ToolSchema): ToolColumn[] {
  return schema.columns.filter((c) => isInputPrimitive(c.primitive));
}

/** A short label for a column (falls back to a humanized key). */
export function columnLabel(col: ToolColumn): string {
  if (col.label && col.label.trim()) return col.label;
  return col.key
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
