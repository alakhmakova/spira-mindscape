import { useEffect, useMemo, useState } from "react";
import { Trash2, Pencil, Check, X } from "lucide-react";
import { toast } from "sonner";
import {
  addRecord,
  deleteRecord,
  listRecords,
  parseSchema,
  updateRecord,
  type Tool,
  type ToolColumn,
  type ToolRecord,
  type ToolSchema,
} from "@/lib/spira/tools-api";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import { useIsMobile } from "@/hooks/use-mobile";
import {
  alignClass,
  badgeClasses,
  columnLabel,
  compareCells,
  emptyRow,
  formatCell,
  inputColumns,
  type ToolRow,
} from "./tool-logic";
import { cn } from "@/lib/utils";

/**
 * One generic renderer for every Personal Tool, driven by its schema.
 * `table` layout = rows over time (trackers); `fields` = a single-record form.
 * Records load and mutate via the tools API. Existing rows are editable; dates
 * use the app's custom {@link DeadlinePopover} (never the native picker).
 *
 * `preview` renders the schema read-only (no data, no network) for the AI's
 * proposal card. Desktop shows a table; mobile shows stacked cards.
 */
export function ToolRenderer({
  tool,
  preview = false,
}: {
  tool: Tool;
  preview?: boolean;
}) {
  const schema = parseSchema(tool.schemaJson);
  const [records, setRecords] = useState<ToolRecord[]>([]);
  const [loading, setLoading] = useState(!preview);

  useEffect(() => {
    if (preview || !schema) return;
    let cancelled = false;
    listRecords(tool.id)
      .then((r) => !cancelled && setRecords(r))
      .catch(() => !cancelled && toast.error("Couldn't load this tool's data."))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [tool.id, preview, schema]);

  if (!schema) {
    return (
      <p className="text-sm text-destructive">
        This tool's definition is invalid.
      </p>
    );
  }

  // Keep only keys the CURRENT schema defines: if the tool's structure changed
  // (e.g. the AI removed a column via edit_tool), a row still holding the old
  // key would be rejected by the server validator. Projecting drops stale keys.
  const project = (data: ToolRow): ToolRow => {
    const keys = new Set(schema.columns.map((c) => c.key));
    const out: ToolRow = {};
    for (const k of Object.keys(data)) if (keys.has(k)) out[k] = data[k];
    return out;
  };

  const onAdd = async (data: ToolRow) => {
    try {
      const saved = await addRecord(tool.id, project(data));
      setRecords((prev) => [...prev, saved]);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Couldn't save the entry.");
      throw e;
    }
  };

  const onEdit = async (recordId: number, data: ToolRow) => {
    try {
      const saved = await updateRecord(tool.id, recordId, project(data));
      setRecords((prev) => prev.map((r) => (r.id === recordId ? saved : r)));
    } catch (e) {
      toast.error(
        e instanceof Error ? e.message : "Couldn't update the entry.",
      );
      throw e;
    }
  };

  const onDelete = async (recordId: number) => {
    try {
      await deleteRecord(tool.id, recordId);
      setRecords((prev) => prev.filter((r) => r.id !== recordId));
    } catch {
      toast.error("Couldn't delete the entry.");
    }
  };

  return (
    <ToolBody
      schema={schema}
      records={records}
      loading={loading}
      preview={preview}
      onAdd={onAdd}
      onEdit={onEdit}
      onDelete={onDelete}
    />
  );
}

function ToolBody({
  schema,
  records,
  loading,
  preview,
  onAdd,
  onEdit,
  onDelete,
}: {
  schema: ToolSchema;
  records: ToolRecord[];
  loading: boolean;
  preview: boolean;
  onAdd: (d: ToolRow) => Promise<void>;
  onEdit: (id: number, d: ToolRow) => Promise<void>;
  onDelete: (id: number) => void;
}) {
  const isMobile = useIsMobile();
  const cols = inputColumns(schema);
  const rows = useMemo(
    () => records.map((r) => ({ id: r.id, data: safeParse(r.dataJson) })),
    [records],
  );
  const [editingId, setEditingId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);

  // `fields` layout: a single-record form (e.g. a countdown date).
  if (schema.layout === "fields") {
    const existing = rows[0];
    if (preview) return <FieldsPreview cols={cols} />;
    if (existing && editingId !== existing.id) {
      return (
        <div className="space-y-2">
          <dl className="surface-card divide-y p-0">
            {cols.map((c) => (
              <div
                key={c.key}
                className="flex justify-between gap-3 px-3 py-2 text-sm"
              >
                <dt className="text-muted-foreground">{columnLabel(c)}</dt>
                <dd className="font-medium text-foreground">
                  <Cell col={c} value={existing.data[c.key]} />
                </dd>
              </div>
            ))}
          </dl>
          <div className="flex gap-2">
            <button
              onClick={() => setEditingId(existing.id)}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:bg-secondary"
            >
              <Pencil className="h-3.5 w-3.5" /> Edit
            </button>
            <button
              onClick={() => onDelete(existing.id)}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-destructive"
            >
              <Trash2 className="h-3.5 w-3.5" /> Clear
            </button>
          </div>
        </div>
      );
    }
    return (
      <RowForm
        cols={cols}
        initial={existing?.data}
        submitLabel={existing ? "Save" : "Save"}
        onSubmit={async (d) => {
          if (existing) await onEdit(existing.id, d);
          else await onAdd(d);
          setEditingId(null);
        }}
        onCancel={existing ? () => setEditingId(null) : undefined}
      />
    );
  }

  // `table` layout. Desktop = table, mobile = stacked cards. Newest entries
  // first; a sticky toolbar (stats + Add entry) stays visible while scrolling.
  const statusCol = cols.find((c) => c.primitive === "select");
  const total = rows.length;
  let statsText = `${total} ${total === 1 ? "entry" : "entries"}`;
  if (statusCol) {
    const counts: Record<string, number> = {};
    for (const r of rows) {
      const v = r.data[statusCol.key];
      if (typeof v === "string" && v) counts[v] = (counts[v] ?? 0) + 1;
    }
    const opts = statusCol.options ?? Object.keys(counts);
    const parts = opts.filter((o) => counts[o]).map((o) => `${o} ${counts[o]}`);
    if (parts.length) statsText += ` · ${parts.join(" · ")}`;
  }
  // Order: the schema's default sort if set, otherwise newest first. Numbered
  // from the top in whatever order is shown.
  const sort = schema.sort;
  let displayRows: typeof rows;
  if (sort && cols.some((c) => c.key === sort.key)) {
    displayRows = [...rows].sort((a, b) =>
      compareCells(a.data[sort.key], b.data[sort.key]),
    );
    if (sort.dir === "desc") displayRows.reverse();
  } else {
    displayRows = [...rows].reverse();
  }

  return (
    <div className="space-y-3">
      {!preview && (
        <div className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-border bg-surface py-2">
          <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
            {statsText}
          </span>
          <button
            onClick={() => setAdding(true)}
            className="shrink-0 rounded-md bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
          >
            Add entry
          </button>
        </div>
      )}

      {!preview && adding && (
        <div className="surface-card p-3">
          <RowForm
            cols={cols}
            submitLabel="Add"
            onSubmit={async (d) => {
              await onAdd(d);
              setAdding(false);
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      )}

      {isMobile ? (
        <div className="space-y-2">
          {displayRows.map((row, i) =>
            editingId === row.id ? (
              <div key={row.id} className="surface-card p-3">
                <RowForm
                  cols={cols}
                  initial={row.data}
                  submitLabel="Save"
                  onSubmit={async (d) => {
                    await onEdit(row.id, d);
                    setEditingId(null);
                  }}
                  onCancel={() => setEditingId(null)}
                />
              </div>
            ) : (
              <div key={row.id} className="surface-card p-3">
                <div className="mb-1 text-xs font-semibold text-muted-foreground tabular-nums">
                  #{i + 1}
                </div>
                <dl className="space-y-1">
                  {cols.map((c) => (
                    <div
                      key={c.key}
                      className="flex justify-between gap-3 text-sm"
                    >
                      <dt className="text-muted-foreground">
                        {columnLabel(c)}
                      </dt>
                      <dd className="font-medium text-foreground text-right">
                        <Cell col={c} value={row.data[c.key]} />
                      </dd>
                    </div>
                  ))}
                </dl>
                {!preview && (
                  <div className="mt-2 flex justify-end gap-1">
                    <IconBtn label="Edit" onClick={() => setEditingId(row.id)}>
                      <Pencil className="h-3.5 w-3.5" />
                    </IconBtn>
                    <IconBtn
                      label="Delete"
                      danger
                      onClick={() => onDelete(row.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconBtn>
                  </div>
                )}
              </div>
            ),
          )}
          {!preview && rows.length === 0 && !loading && (
            <p className="px-1 text-xs italic text-muted-foreground">
              No entries yet — add one above.
            </p>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b bg-muted/40 text-left">
                {!preview && (
                  <th className="w-10 px-3 py-2 font-medium text-muted-foreground">
                    #
                  </th>
                )}
                {cols.map((c) => (
                  <th
                    key={c.key}
                    className={cn(
                      "px-3 py-2 font-medium text-muted-foreground whitespace-nowrap",
                      alignClass(c.align),
                    )}
                  >
                    {columnLabel(c)}
                  </th>
                ))}
                {!preview && <th className="w-20 px-3 py-2" />}
              </tr>
            </thead>
            <tbody>
              {displayRows.map((row, i) =>
                editingId === row.id ? (
                  <tr key={row.id} className="border-b bg-primary-soft/30">
                    <td colSpan={cols.length + 2} className="p-3">
                      <RowForm
                        cols={cols}
                        initial={row.data}
                        submitLabel="Save"
                        inline
                        onSubmit={async (d) => {
                          await onEdit(row.id, d);
                          setEditingId(null);
                        }}
                        onCancel={() => setEditingId(null)}
                      />
                    </td>
                  </tr>
                ) : (
                  <tr
                    key={row.id}
                    className="border-b last:border-0 hover:bg-muted/20"
                  >
                    {!preview && (
                      <td className="px-3 py-2 align-top text-xs text-muted-foreground tabular-nums">
                        {i + 1}
                      </td>
                    )}
                    {cols.map((c) => (
                      <td
                        key={c.key}
                        className={cn(
                          "px-3 py-2 align-top text-foreground",
                          alignClass(c.align),
                        )}
                      >
                        <Cell col={c} value={row.data[c.key]} />
                      </td>
                    ))}
                    {!preview && (
                      <td className="px-3 py-2 text-right whitespace-nowrap">
                        <IconBtn
                          label="Edit"
                          onClick={() => setEditingId(row.id)}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </IconBtn>
                        <IconBtn
                          label="Delete"
                          danger
                          onClick={() => onDelete(row.id)}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </IconBtn>
                      </td>
                    )}
                  </tr>
                ),
              )}
              {!preview && rows.length === 0 && !loading && (
                <tr>
                  <td
                    colSpan={cols.length + 2}
                    className="px-3 py-3 text-xs italic text-muted-foreground"
                  >
                    No entries yet — add one above.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// A read-only cell value: a coloured badge for a `select` with configured
// colours, otherwise the plain formatted text.
function Cell({ col, value }: { col: ToolColumn; value: unknown }) {
  if (
    col.primitive === "select" &&
    col.colors &&
    value != null &&
    value !== ""
  ) {
    return (
      <span
        className={cn(
          "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
          badgeClasses(col.colors[String(value)]),
        )}
      >
        {String(value)}
      </span>
    );
  }
  return <>{formatCell(col.primitive, value)}</>;
}

// ── A row form, shared by add + edit ────────────────────────────────────────

function RowForm({
  cols,
  initial,
  submitLabel,
  inline = false,
  onSubmit,
  onCancel,
}: {
  cols: ToolColumn[];
  initial?: ToolRow;
  submitLabel: string;
  inline?: boolean;
  onSubmit: (d: ToolRow) => Promise<void>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState<ToolRow>(() => initial ?? blankRow(cols));
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      await onSubmit(draft);
      if (!initial) setDraft(blankRow(cols)); // adding: reset for the next row
    } catch {
      /* toast already shown by caller */
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className={cn(
        "gap-2",
        inline ? "grid sm:grid-cols-[1fr_auto] sm:items-end" : "space-y-2.5",
      )}
    >
      <div
        className={cn(
          inline ? "grid grid-cols-2 gap-2 md:grid-cols-3" : "space-y-2.5",
        )}
      >
        {cols.map((c) => (
          <label key={c.key} className="block">
            <span className="mb-1 block text-xs font-medium text-muted-foreground">
              {columnLabel(c)}
            </span>
            <FieldInput
              col={c}
              value={draft[c.key]}
              onChange={(v) => setDraft((d) => ({ ...d, [c.key]: v }))}
            />
          </label>
        ))}
      </div>
      <div className="flex gap-2">
        <button
          onClick={submit}
          disabled={saving}
          className="inline-flex h-9 items-center gap-1.5 rounded-md bg-primary px-3 text-sm font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
        >
          <Check className="h-4 w-4" /> {submitLabel}
        </button>
        {onCancel && (
          <button
            onClick={onCancel}
            className="inline-flex h-9 items-center gap-1.5 rounded-md border border-border px-3 text-sm font-medium hover:bg-secondary"
          >
            <X className="h-4 w-4" /> Cancel
          </button>
        )}
      </div>
    </div>
  );
}

// ── One input, switched on the column primitive ─────────────────────────────

function FieldInput({
  col,
  value,
  onChange,
}: {
  col: ToolColumn;
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const base =
    "w-full rounded-md border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-primary";

  switch (col.primitive) {
    case "number":
    case "progress":
      return (
        <input
          type="number"
          className={base}
          value={value == null ? "" : String(value)}
          onChange={(e) =>
            onChange(e.target.value === "" ? null : Number(e.target.value))
          }
        />
      );
    case "date":
      // Custom picker (native <input type="date"> is forbidden by app rules).
      // Tool dates are stored as YYYY-MM-DD; DeadlinePopover speaks ISO datetime.
      return (
        <DeadlinePopover
          iso={value ? String(value) : undefined}
          onChange={(next) => onChange(next ? next.slice(0, 10) : "")}
          variant="input"
          hideDaysLeft
          placeholder="Pick a date"
          className="w-full justify-start"
        />
      );
    case "checkbox":
      return (
        <input
          type="checkbox"
          className="h-4 w-4 accent-primary"
          checked={!!value}
          onChange={(e) => onChange(e.target.checked)}
        />
      );
    case "select":
      return (
        <select
          className={base}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        >
          <option value="">—</option>
          {(col.options ?? []).map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      );
    case "checklist":
      return <ChecklistInput value={value} onChange={onChange} />;
    case "textarea":
      return (
        <textarea
          className={cn(base, "min-h-[4.5rem] resize-y")}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case "rating":
      return <RatingInput value={value} onChange={onChange} />;
    case "tags":
      return <TagsInput value={value} onChange={onChange} />;
    case "time":
      // HH:MM as plain text (native time picker avoided, per the app's
      // custom-control rule); validated server-side against the schema.
      return (
        <input
          type="text"
          inputMode="numeric"
          placeholder="HH:MM"
          className={base}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case "url":
      return (
        <input
          type="url"
          inputMode="url"
          placeholder="https://…"
          className={base}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    default: // text, table cell, chart
      return (
        <input
          type="text"
          className={base}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}

function RatingInput({
  value,
  onChange,
}: {
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const current = Math.max(0, Math.min(5, Math.round(Number(value) || 0)));
  return (
    <div className="flex items-center gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          aria-label={`${n} ${n === 1 ? "star" : "stars"}`}
          aria-pressed={n <= current}
          // Click the current rating to clear it back to none.
          onClick={() => onChange(n === current ? null : n)}
          className="text-lg leading-none text-amber-500 hover:scale-110 transition-transform"
        >
          {n <= current ? "★" : "☆"}
        </button>
      ))}
    </div>
  );
}

function TagsInput({
  value,
  onChange,
}: {
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const tags = Array.isArray(value) ? (value as string[]) : [];
  const [draft, setDraft] = useState("");
  const commit = () => {
    const t = draft.trim();
    if (t && !tags.includes(t)) onChange([...tags, t]);
    setDraft("");
  };
  return (
    <div className="flex flex-wrap items-center gap-1.5 rounded-md border border-border bg-surface px-2 py-1.5 focus-within:border-primary">
      {tags.map((t, i) => (
        <span
          key={i}
          className="inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-0.5 text-xs font-medium"
        >
          {t}
          <button
            type="button"
            aria-label={`Remove ${t}`}
            onClick={() => onChange(tags.filter((_, j) => j !== i))}
            className="text-muted-foreground hover:text-destructive"
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
      <input
        className="min-w-[6rem] flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
        placeholder={tags.length ? "" : "Add tag…"}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if ((e.key === "Enter" || e.key === ",") && draft.trim()) {
            e.preventDefault();
            commit();
          } else if (e.key === "Backspace" && !draft && tags.length) {
            onChange(tags.slice(0, -1));
          }
        }}
        onBlur={commit}
      />
    </div>
  );
}

function ChecklistInput({
  value,
  onChange,
}: {
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const items = Array.isArray(value)
    ? (value as { label: string; done: boolean }[])
    : [];
  const [draft, setDraft] = useState("");
  return (
    <div className="space-y-1">
      {items.map((it, i) => (
        <label key={i} className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-3.5 w-3.5 accent-primary"
            checked={it.done}
            onChange={(e) =>
              onChange(
                items.map((x, j) =>
                  j === i ? { ...x, done: e.target.checked } : x,
                ),
              )
            }
          />
          <span className={it.done ? "text-muted-foreground line-through" : ""}>
            {it.label}
          </span>
        </label>
      ))}
      <input
        className="w-full rounded-md border border-border bg-surface px-2 py-1 text-sm outline-none focus:border-primary"
        placeholder="Add item…"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && draft.trim()) {
            e.preventDefault();
            onChange([...items, { label: draft.trim(), done: false }]);
            setDraft("");
          }
        }}
      />
    </div>
  );
}

// Preview of a `fields` tool (read-only labels for the AI proposal card).
function FieldsPreview({ cols }: { cols: ToolColumn[] }) {
  return (
    <dl className="space-y-1.5">
      {cols.map((c) => (
        <div key={c.key} className="flex justify-between gap-3 text-sm">
          <dt className="text-muted-foreground">{columnLabel(c)}</dt>
          <dd className="text-foreground/50">{c.primitive}</dd>
        </div>
      ))}
    </dl>
  );
}

function IconBtn({
  label,
  danger,
  onClick,
  children,
}: {
  label: string;
  danger?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        "ml-1 inline-grid h-7 w-7 place-items-center rounded-md text-muted-foreground hover:bg-secondary",
        danger ? "hover:text-destructive" : "hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

function blankRow(cols: ToolColumn[]): ToolRow {
  return emptyRow({ layout: "table", columns: cols });
}

function safeParse(json: string): ToolRow {
  try {
    return JSON.parse(json) as ToolRow;
  } catch {
    return {};
  }
}
