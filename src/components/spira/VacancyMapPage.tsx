import { useEffect, useMemo, useRef, useState } from "react";
import { format } from "date-fns";
import {
  Calendar,
  Check,
  ChevronRight,
  CircleCheck,
  CircleCheckFill,
  CirclePlus,
  Comment,
  CommentDot,
  CommentPlus,
  CopyPlus,
  Download,
  Eraser,
  Info,
  Pencil,
  PencilToLine,
  Plus,
  Trash2,
  X,
} from "@/components/spira/icons";
import { useSpira } from "@/lib/spira/store";
import { FIELD_LIMITS } from "@/lib/spira/limits";
import {
  companyLabel,
  DEFAULT_COMPANY_SLOTS,
  newAdditionalItem,
  newCheckItem,
  newComment,
  newCompanySlot,
  newCustomFact,
  newNote,
  newRequirement,
  parseVacancyMap,
  requirementLabel,
  serializeVacancyMap,
  setAt,
  removeAt,
  type MapAdditionalItem,
  type MapAdditionalTag,
  type MapCheckItem,
  type MapComment,
  type MapCustomFact,
  type MapPatchOp,
  type MapRequirement,
  type VacancyMap,
} from "@/lib/spira/vacancy-map";
import { AutoTextarea, InlineText } from "@/components/spira/Inline";
import {
  vacancyMapFileName,
  vacancyMapHtml,
} from "@/lib/spira/vacancy-map-html";
import { InlineResourcesProvider } from "@/components/spira/Resources";
import { ResourceHead } from "@/components/spira/ResourceHead";
import { DeadlinePopover } from "@/components/spira/DeadlinePopover";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { Pill, TagPill, type PillTone } from "@/components/spira/Pill";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

/**
 * The vacancy map — one per job advert, opened as a resizable side panel like every other
 * resource (owner, 2026-09-18). It was a page of its own until then, which had no way back to the
 * goal and, because leaving the goal page clears the coach's goal, switched an open chat to the
 * All-goals one.
 *
 * **Its breakpoints are the PANEL's, not the window's** (`@container` below): the two-column
 * layout turns on at a panel wide enough to hold it, whatever the screen is.
 *
 * **One typeface inside it, Montserrat** (owner, 2026-09-18 — "только grotesk font"): the
 * `vacancy-map` class re-points the heading, display and body font tokens for everything inside.
 *
 * **Corners are 4px** (CLAUDE.md → 3g); **pills stay fully round** — the qualities cloud and the
 * Cover letter / Profile tags are the app's one pill shape, never squared off.
 *
 * **Every edit writes ONE field.** `commit` sends a JSON-Pointer patch to the server and the
 * already-updated document to the store, so the user and the CV writer can fill in different parts
 * of the same map at the same time without either silently discarding the other's work — the
 * defect that made the requirement-map note unusable. See `vacancy-map.ts` and `VacancyMapPatch`.
 *
 * **Free text is `InlineText`/`AutoTextarea`, which means resource chips come for free.** The panel
 * is wrapped in `InlineResourcesProvider`, so the user can point any answer at another resource
 * ("my education is written up in {{that note}}") and it renders as a chip that opens it.
 */
export function VacancyMapPanel({
  goalId,
  resourceId,
  onClose,
  onOpenResource,
}: {
  goalId: string;
  resourceId: string;
  onClose: () => void;
  /** Switch the panel to another resource — the copy, after Duplicate. */
  onOpenResource?: (id: string) => void;
}) {
  const goal = useSpira((s) => s.goals.find((g) => g.id === goalId));
  const resource = useSpira((s) =>
    s.goals
      .find((g) => g.id === goalId)
      ?.resources.find((r) => r.id === resourceId),
  );
  const loadResourceMap = useSpira((s) => s.loadResourceMap);
  const patchMap = useSpira((s) => s.patchVacancyMap);
  const updateResource = useSpira((s) => s.updateResource);
  const removeResource = useSpira((s) => s.removeResource);
  const duplicateResource = useSpira((s) => s.duplicateResource);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const isVacancy = resource?.type === "vacancy";
  const mapData = isVacancy ? resource.mapData : undefined;

  // The document is left out of every list read, so the panel fetches it on open.
  useEffect(() => {
    if (isVacancy && mapData === undefined) {
      void loadResourceMap(goalId, resourceId);
    }
  }, [isVacancy, mapData, goalId, resourceId, loadResourceMap]);

  const map = useMemo(() => parseVacancyMap(mapData), [mapData]);
  const { grid, leftColumn, requirements, roomBeside } =
    useRoomBesideAdditional();

  // **One answer box per requirement, not three** (owner, 2026-09-24). Every document written
  // before that carries two blank extras on each requirement, and a blank box is not an answer
  // — it is an empty form the user never asked for.
  //
  // They are dropped by a real write the first time the map is opened, not by reading around
  // them: every patch path is an INDEX into the stored array, so a view that quietly showed
  // fewer boxes than the document holds would make each of those paths point at the wrong slot.
  //
  // **Only the untouched shape, and only once per document** (owner, 2026-09-29). A blank box
  // is a blank box: nothing tells the old padding apart from one "Add a company" has just made.
  // So the clean-up fires solely where EVERY box on a requirement is still empty — which is
  // what the old padding looks like and what a requirement she has answered never does. Written
  // wider than that, it deleted the box she had just asked for: it survived the press, then went
  // the next time the map was opened, and the button looked broken.
  const tidied = useRef(new Set<string>());
  useEffect(() => {
    if (mapData === undefined || tidied.current.has(resourceId)) return;
    tidied.current.add(resourceId);
    const ops: MapPatchOp[] = [];
    const requirements = map.requirements.map((item, index) => {
      const untouched = item.companies.every(
        (company) => !company.text.trim() && !company.label.trim(),
      );
      const keep = untouched
        ? item.companies.slice(0, DEFAULT_COMPANY_SLOTS)
        : item.companies;
      if (keep.length === item.companies.length) return item;
      // Highest index first: the ops are applied in order, and removing a lower one would
      // shift every index after it.
      item.companies.forEach((company, slot) => {
        if (!keep.includes(company)) {
          ops.unshift(removeAt(`/requirements/${index}/companies/${slot}`));
        }
      });
      return { ...item, companies: keep };
    });
    if (ops.length) {
      // `commit` is declared below the panel's early return, so the write goes
      // through the store directly.
      patchMap(
        goalId,
        resourceId,
        ops,
        serializeVacancyMap({ ...map, requirements }),
      );
    }
    // `map` is derived from `mapData`, which is what actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapData]);

  if (!goal || !resource || !isVacancy) return null;

  /** One field written: the ops the server merges, and the same change applied locally. */
  const commit = (ops: MapPatchOp[], next: VacancyMap) =>
    patchMap(goalId, resourceId, ops, serializeVacancyMap(next));

  const loading = mapData === undefined;

  return (
    <InlineResourcesProvider goal={goal}>
      <div className="vacancy-map @container flex min-h-0 flex-1 flex-col">
        <PanelHead
          title={resource.title}
          onTitle={(title) => updateResource(goalId, resourceId, { title })}
          onDownload={
            // Only once the document is here: downloading a map still being fetched would write
            // an empty file and read as the map itself having been empty.
            loading ? undefined : () => downloadMap(resource.title, map)
          }
          onDuplicate={() =>
            void duplicateResource(goalId, resourceId, (created) =>
              onOpenResource?.(created.id),
            )
          }
          onDelete={() => setConfirmDelete(true)}
          onClose={onClose}
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 @xl:px-7">
          {loading ? (
            <p className="text-sm text-muted-foreground">Loading the map…</p>
          ) : (
            <div className="space-y-4">
              {/* Above the first card (owner, 2026-09-18) — not in the head, which is the
                  panel's own chrome. Not wired yet, by the owner's instruction. The heading
                  beside it says what the panel IS and is the same words on every vacancy; the
                  head carries the vacancy's own name, which is a different thing. */}
              <div className="flex items-center justify-between gap-3">
                <h1 className="text-xl font-semibold">Requirements map</h1>
                <button
                  type="button"
                  className="h-9 rounded-[4px] border-2 border-primary px-4 text-sm font-semibold text-primary transition-colors hover:bg-primary-soft"
                >
                  Need help?
                </button>
              </div>
              <FactsCard map={map} commit={commit} />

              {/* **Additional information follows Personal qualities, not the whole grid**
                  (owner, 2026-09-23). The left column is shorter than Requirements, so waiting
                  for the grid to end left a 357px band of nothing between the two ' + EM + '
                  measured. The placement is explicit so the DOM order can stay as it reads on a
                  phone: skills, qualities, requirements, additional. */}
              <div
                ref={grid}
                className="grid items-start gap-4 @4xl:grid-cols-2"
              >
                <div
                  ref={leftColumn}
                  className="min-w-0 space-y-4 @4xl:col-start-1 @4xl:row-start-1"
                >
                  <CheckListCard
                    title="Skills"
                    branch="skills"
                    items={map.skills}
                    map={map}
                    commit={commit}
                    addLabel="Add a skill"
                    hint="The abilities and tools the advert names. Tick the ones you already have, and use a comment to say where you used each one — that is what the letter and the profile are written from."
                  />
                  <QualitiesCard map={map} commit={commit} />
                </div>
                <div
                  ref={requirements}
                  className={cn(
                    "min-w-0 @4xl:col-start-2 @4xl:row-start-1",
                    // It only reaches down beside Additional information when it is the taller
                    // of the two columns; see `roomBesideAdditional`.
                    !roomBeside && "@4xl:row-span-2",
                  )}
                >
                  <RequirementsColumn map={map} commit={commit} />
                </div>
                {/* **Full width when nothing is beside it** (owner, 2026-09-29): with a short
                    Requirements column the block sat in half the width with an empty half to its
                    right. When Requirements DOES come down to its level, it keeps its half —
                    the two are then side by side. */}
                <div
                  className={cn(
                    "min-w-0 @4xl:col-start-1 @4xl:row-start-2",
                    roomBeside && "@4xl:col-span-2",
                  )}
                >
                  <AdditionalList map={map} commit={commit} />
                </div>
              </div>

              <CompanyBlock map={map} commit={commit} />
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete this vacancy map?"
        description={
          <span>
            <strong className="font-semibold">
              &quot;{resource.title || "Untitled vacancy"}&quot;
            </strong>{" "}
            and everything written in it will be permanently deleted. You
            can&apos;t undo this.
          </span>
        }
        confirmLabel="Yes, delete"
        onConfirm={() => {
          removeResource(goalId, resourceId);
          onClose();
        }}
      />
    </InlineResourcesProvider>
  );
}

/**
 * Whether Additional information has the row to itself.
 *
 * <p>Requirements sits in the right-hand column and reaches down past Skills and Personal
 * qualities only when it is the taller of the two. When it is not, the row below holds nothing
 * but Additional information, and half the width beside it is simply empty — so the block
 * takes the lot (owner, 2026-09-29).
 *
 * <p>CSS cannot ask "is there anything beside me", so this measures the two columns. It compares
 * their HEIGHTS rather than their positions on the page, which is what keeps it from oscillating:
 * widening the block moves Requirements out of the second row, but it does not change how tall
 * either column is, so the answer it gives is the same before and after.
 *
 * <p>Only in the two-column layout. Below the grid's own breakpoint (`@4xl`, 56rem of the PANEL,
 * not the window) everything is full width already.
 */
function useRoomBesideAdditional() {
  const grid = useRef<HTMLDivElement>(null);
  const leftColumn = useRef<HTMLDivElement>(null);
  const requirements = useRef<HTMLDivElement>(null);
  const [roomBeside, setRoomBeside] = useState(false);

  useEffect(() => {
    const boxes = [grid.current, leftColumn.current, requirements.current];
    if (boxes.some((box) => !box) || typeof ResizeObserver === "undefined") {
      return;
    }
    const measure = () => {
      const twoColumns = (grid.current?.clientWidth ?? 0) >= TWO_COLUMN_WIDTH;
      const left = leftColumn.current?.offsetHeight ?? 0;
      const right = requirements.current?.offsetHeight ?? 0;
      setRoomBeside(twoColumns && right <= left);
    };
    measure();
    const observer = new ResizeObserver(measure);
    boxes.forEach((box) => box && observer.observe(box));
    return () => observer.disconnect();
  });

  return { grid, leftColumn, requirements, roomBeside };
}

/** `@4xl` in pixels — the panel width at which the map lays itself out in two columns. */
const TWO_COLUMN_WIDTH = 896;

/* ── Panel head ────────────────────────────────────────────── */

/**
 * The shared resource band (`ResourceHead`), carrying the map's own two actions. The name is
 * edited in place on it, as on Android's `ResourceTopBar`.
 */
function PanelHead({
  title,
  onTitle,
  onDownload,
  onDuplicate,
  onDelete,
  onClose,
}: {
  title: string;
  onTitle: (value: string) => void;
  /** Omitted while the document is still being fetched. */
  onDownload?: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  return (
    <ResourceHead
      title={title}
      onBack={onClose}
      // The same word on every resource, whatever the mark: the specs press "Back", and the
      // panel's own cross is gone (see `closeButton={false}` in Resources.tsx).
      backLabel="Back"
      actions={[
        {
          key: "download",
          label: "Download as HTML",
          icon: <Download className="h-4 w-4" />,
          onClick: onDownload,
        },
        {
          key: "duplicate",
          label: "Duplicate",
          icon: <CopyPlus className="h-4 w-4" />,
          onClick: onDuplicate,
        },
        {
          key: "delete",
          label: "Delete",
          icon: <Trash2 className="h-4 w-4" />,
          onClick: onDelete,
          tone: "destructive",
        },
      ]}
    >
      {/* The map's name IS the vacancy's name, edited in place like every other field. */}
      <InlineText
        value={title}
        onChange={onTitle}
        ariaLabel="Vacancy name"
        maxLength={FIELD_LIMITS.resourceLabel}
        maxLengthLabel="Vacancy name"
        className="text-lg font-bold !text-white"
      />
    </ResourceHead>
  );
}

/**
 * Hand the map over as a file (owner, 2026-09-24).
 *
 * A blob and a click: there is no server round trip, because the document is already here —
 * and nothing about the map needs the backend to render it. The object URL is released on the
 * next frame; revoking it in the same turn can cancel the download in some browsers.
 */
function downloadMap(title: string, map: VacancyMap) {
  const blob = new Blob([vacancyMapHtml(title || "Requirements map", map)], {
    type: "text/html;charset=utf-8",
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = vacancyMapFileName(title);
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/* ── Card shell ────────────────────────────────────────────────────────── */

function Card({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        "rounded-[4px] border hairline bg-white p-5 @xl:p-6",
        className,
      )}
    >
      {children}
    </section>
  );
}

/**
 * A card's heading — in the map's one typeface, Montserrat, never the serif (owner,
 * 2026-09-18) — with the block's explainer beside it (owner, 2026-09-20). Every heading on the
 * map carries one: the words "Additional information" do not say what the block is FOR, and the
 * answer belongs where the question is asked rather than in a manual nobody opens.
 */
function CardTitle({
  children,
  hint,
}: {
  children: React.ReactNode;
  /** What this block is for, in a sentence or two. */
  hint?: React.ReactNode;
}) {
  return (
    <div className="flex min-w-0 items-center gap-1.5">
      <h2 className="min-w-0 text-xl font-semibold">{children}</h2>
      {hint && <BlockHint title={String(children)}>{hint}</BlockHint>}
    </div>
  );
}

/**
 * The explainer behind a heading's info mark — the "Get answers now" card, verbatim (owner,
 * 2026-09-20): a plain white card, the block's own name in bold, the explanation under it, and
 * **"Got it" as a worded link** rather than a button. It explains; it does not ask for a decision,
 * so it must not look like something to agree to.
 */
function BlockHint({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={`What "${title}" is for`}
          className="grid h-6 w-6 shrink-0 place-items-center rounded-full text-muted-foreground/70 transition-colors hover:bg-black/5 hover:text-foreground"
        >
          <Info className="h-4 w-4" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-4">
        <p className="text-[15px] font-bold text-foreground">{title}</p>
        <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
          {children}
        </p>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="mt-3 text-sm font-semibold text-primary underline underline-offset-2"
        >
          Got it
        </button>
      </PopoverContent>
    </Popover>
  );
}

/* ── The vacancy's own facts ───────────────────────────────────────────── */

function FactsCard({
  map,
  commit,
}: {
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
}) {
  const facts = map.facts;
  const set = (key: keyof VacancyMap["facts"], value: string | null) =>
    commit([setAt(`/facts/${key}`, value)], {
      ...map,
      facts: { ...facts, [key]: value },
    });

  const writeCustom = (next: MapCustomFact[], ops: MapPatchOp[]) =>
    commit(ops, { ...map, facts: { ...facts, custom: next } });

  // The field she has just added, so it opens with the caret in its NAME — the tap on
  // "Add a field" already said she has one in mind, and a blank row carries no placeholder to
  // tell her where to start.
  const [fresh, setFresh] = useState<string | null>(null);

  const addCustom = () => {
    const item = newCustomFact();
    setFresh(item.id);
    writeCustom([...facts.custom, item], [setAt("/facts/custom/-", item)]);
  };

  const setCustom = (index: number, patch: Partial<MapCustomFact>) =>
    writeCustom(
      facts.custom.map((f, i) => (i === index ? { ...f, ...patch } : f)),
      Object.entries(patch).map(([field, value]) =>
        setAt(`/facts/custom/${index}/${field}`, value),
      ),
    );

  const removeCustom = (index: number) =>
    writeCustom(
      facts.custom.filter((_, i) => i !== index),
      [removeAt(`/facts/custom/${index}`)],
    );

  // **Closed, the card states the role and nothing else** (owner, 2026-09-20, tightened
  // 2026-09-23): the role, the way to the rest, and the deadline. Not the location, not the
  // link — those are "the rest".
  const [open, setOpen] = useState(false);

  return (
    <Card>
      {/* **The picture leads, and everything else is one column beside it** (owner, 2026-09-23):
          the facts line up with the role rather than running under the drawing, so the space
          under the picture stays empty and the card reads as one block of answers.

          **And it is centred on the words, not hung from their top edge** (owner, 2026-09-24).
          The drawing is taller than the role beside it, so aligning their tops left it reaching
          well below the last line. It is therefore drawn OUT of the flow: its cell is a column
          of width only, which stretches to whatever the words need, and the picture is centred
          on that cell — so the row is sized by the text alone and the drawing overhangs into the
          card's own padding, by however much it has to. A plain `self-center` cannot do this: a
          picture taller than the words defines the row's height and then centres against
          itself. */}
      <div className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4">
        <div className="relative col-start-1 row-start-1 w-14 @xl:w-20">
          <img
            src="/illustrations/vacancy.svg"
            alt=""
            aria-hidden="true"
            className="absolute top-1/2 h-14 w-14 -translate-y-1/2 select-none @xl:h-20 @xl:w-20"
          />
        </div>
        <div className="col-start-2 row-start-1 min-w-0">
          {/* **The deadline sits BESIDE the role on a laptop and under it on a phone** (owner,
              2026-09-22). `@lg` is the PANEL's width, not the window's — a panel dragged
              narrow gets the phone's arrangement, which is the point of the container queries. */}
          <div className="flex flex-col gap-3 @lg:flex-row @lg:items-start @lg:justify-between @lg:gap-6">
            <div className="min-w-0 flex-1">
              <Fact
                label="Job title"
                value={facts.jobTitle}
                onChange={(v) => set("jobTitle", v)}
              />
              {!open && (
                <button
                  type="button"
                  onClick={() => setOpen(true)}
                  className="mt-1 text-sm font-semibold text-primary"
                >
                  View more
                </button>
              )}
            </div>
            <DeadlinePopover
              iso={facts.deadline ?? undefined}
              onChange={(next) => set("deadline", next ?? null)}
              align="end"
              className="inline-flex h-10 shrink-0 items-center gap-2 self-start rounded-[4px] bg-[#0A8080] px-5 text-sm font-semibold text-white transition-colors hover:bg-[#005961]"
              renderTrigger={() => (
                <>
                  <Calendar className="h-4 w-4" />
                  {/* "Apply by" is how a vacancy states its closing date; once there IS one the
                      date says it by itself, so the word stands down. */}
                  {facts.deadline
                    ? format(new Date(facts.deadline), "MMM d, yyyy")
                    : "Apply by"}
                </>
              )}
            />
          </div>
        </div>

        {open && (
          <div className="col-start-2 mt-4 space-y-4">
            <Fact
              label="Location"
              value={facts.location}
              onChange={(v) => set("location", v)}
            />

            <Fact
              label="Link"
              value={facts.link}
              onChange={(v) => set("link", v)}
              ariaLabel="Link to the advert"
              maxLength={FIELD_LIMITS.resourceUrl}
              maxLengthLabel="Link"
            />

            <Fact
              label="Education"
              value={facts.education}
              onChange={(v) => set("education", v)}
              note={facts.educationNote}
              onNote={(v) => set("educationNote", v)}
            />

            <Fact
              label="Years of experience"
              value={facts.experience}
              onChange={(v) => set("experience", v)}
              note={facts.experienceNote}
              onNote={(v) => set("experienceNote", v)}
            />

            <Fact
              label="Languages"
              value={facts.language}
              onChange={(v) => set("language", v)}
              note={facts.languageNote}
              onNote={(v) => set("languageNote", v)}
            />

            {facts.custom.map((item, index) => (
              <CustomFact
                key={item.id}
                item={item}
                autoFocus={item.id === fresh}
                onChange={(patch) => setCustom(index, patch)}
                onRemove={() => removeCustom(index)}
              />
            ))}

            {/* **The advert's facts are fixed; hers are not** (owner, 2026-09-24). Salary, notice
                period, who the interview is with — whatever this particular application turns
                on, named by her, and behaving exactly like the rows above it. */}
            {/* A column: both are inline-level buttons, and side by side they read as one
                sentence ("Add a fieldView less"). */}
            <div className="flex flex-col items-start">
              <AddLine label="Add a field" onClick={addCustom} />
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="mt-3 text-sm font-semibold text-primary"
              >
                View less
              </button>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

/**
 * One fact: its name, the answer, and — where the fact takes one — her own aside about it.
 *
 * **The aside is not a line of its own until there is one** (owner, 2026-09-24). It used to be a
 * second, permanently empty row under every answer, so a card with nothing written in it showed
 * twice as many blank lines as it had facts. A comment glyph beside the pencil asks for it, and
 * the words themselves take the glyph's place once they exist.
 */
function Fact({
  label,
  head,
  value,
  onChange,
  ariaLabel,
  note,
  onNote,
  noteLabel,
  maxLength,
  maxLengthLabel,
}: {
  label: string;
  /** Replaces the plain `Label:` line — a custom fact writes its own name there. */
  head?: React.ReactNode;
  value: string;
  onChange: (v: string) => void;
  ariaLabel?: string;
  /** Omit both `note` and `onNote` for a fact that carries no aside. */
  note?: string;
  onNote?: (v: string) => void;
  noteLabel?: string;
  maxLength?: number;
  maxLengthLabel?: string;
}) {
  // Set by the comment glyph: the aside's line exists from that tap, before there are any words
  // in it to keep it on screen.
  const [asked, setAsked] = useState(false);
  const takesNote = onNote !== undefined && note !== undefined;
  const showNote = takesNote && (note.trim() !== "" || asked);

  return (
    <FactRow label={label} head={head}>
      <EditableLine>
        <InlineText
          trailing={
            <>
              {value.trim() ? editMark : writeMark}
              {/* **Nothing to comment on yet** (owner, 2026-09-24): an empty line offers one
                  mark, the one that invites the answer itself. */}
              {takesNote && value.trim() && !showNote && (
                <NoteMark
                  label={`Comment on ${label.toLowerCase()}`}
                  onClick={() => setAsked(true)}
                />
              )}
            </>
          }
          value={value}
          onChange={onChange}
          ariaLabel={ariaLabel ?? label}
          // Every field on this page may be blank: the advert may simply not say.
          required={false}
          maxLength={maxLength}
          maxLengthLabel={maxLengthLabel}
          className="text-sm"
        />
      </EditableLine>
      {showNote && (
        <FactNote
          value={note}
          onChange={onNote}
          ariaLabel={noteLabel ?? `Comment on ${label.toLowerCase()}`}
          autoFocus={asked}
        />
      )}
    </FactRow>
  );
}

/** A fact the user adds and names herself. Below its own name it is an ordinary fact. */
function CustomFact({
  item,
  autoFocus,
  onChange,
  onRemove,
}: {
  item: MapCustomFact;
  /** Set on the field this page has just added: it opens with the caret in its name. */
  autoFocus?: boolean;
  onChange: (patch: Partial<MapCustomFact>) => void;
  onRemove: () => void;
}) {
  return (
    <Fact
      label={item.label.trim() || "Field"}
      head={
        <div className="flex min-w-0 items-start gap-1">
          <p className="flex min-w-0 flex-1 text-sm font-semibold">
            <InlineText
              value={item.label}
              onChange={(v) => onChange({ label: v })}
              ariaLabel="Field name"
              required={false}
              autoFocus={autoFocus}
              trailing={
                <>
                  {item.label.trim() ? ":" : null}
                  {item.label.trim() ? editMark : writeMark}
                </>
              }
            />
          </p>
          <IconButton
            label="Delete this field"
            onClick={onRemove}
            tone="destructive"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </IconButton>
        </div>
      }
      value={item.value}
      onChange={(v) => onChange({ value: v })}
      ariaLabel={item.label.trim() || "Field"}
      note={item.note}
      onNote={(v) => onChange({ note: v })}
    />
  );
}

/** The glyph that asks for an aside, sitting in the text flow immediately after the pencil. */
function NoteMark({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      // It is drawn INSIDE the field's read view, so the tap that asks for a comment must not
      // also open the field it is sitting in.
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      aria-label={label}
      title={label}
      // The pencil's own colour: two marks side by side in two greys read as two different
      // kinds of control (owner, 2026-09-24).
      className="mx-0.5 inline-flex -translate-y-px items-center align-middle text-primary/70 transition-colors hover:text-primary"
    >
      <CommentPlus className="h-3.5 w-3.5" />
    </button>
  );
}

function FactRow({
  label,
  head,
  children,
}: {
  label: string;
  head?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    // The words sit UNDER their label, never beside it (owner, 2026-09-20): a two-column row
    // left the answers in a narrow ribbon down the right of the card.
    <div>
      {head ?? <p className="text-sm font-semibold">{label}:</p>}
      <div className="mt-0.5 min-w-0 space-y-1">{children}</div>
    </div>
  );
}

/** The optional second line under a fact — the user's own note about it. */
function FactNote({
  value,
  onChange,
  ariaLabel,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  ariaLabel: string;
  autoFocus?: boolean;
}) {
  return (
    <EditableLine>
      <InlineText
        trailing={value.trim() ? editMark : writeMark}
        value={value}
        onChange={onChange}
        ariaLabel={ariaLabel}
        required={false}
        autoFocus={autoFocus}
        className="text-sm text-muted-foreground"
      />
    </EditableLine>
  );
}

/* ── Skills: a checklist whose items carry comments ────────────────────── */

function CheckListCard({
  title,
  branch,
  items,
  map,
  commit,
  addLabel,
  hint,
}: {
  title: string;
  branch: "skills" | "qualities";
  items: MapCheckItem[];
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
  addLabel: string;
  hint?: React.ReactNode;
}) {
  const write = (next: MapCheckItem[], ops: MapPatchOp[]) =>
    commit(ops, { ...map, [branch]: next });

  const add = () => {
    const item = newCheckItem("");
    write([...items, item], [setAt(`/${branch}/-`, item)]);
  };
  const update = (index: number, patch: Partial<MapCheckItem>) => {
    const next = items.map((item, i) =>
      i === index ? { ...item, ...patch } : item,
    );
    write(
      next,
      Object.entries(patch).map(([key, value]) =>
        setAt(`/${branch}/${index}/${key}`, value),
      ),
    );
  };
  const remove = (index: number) =>
    write(
      items.filter((_, i) => i !== index),
      [removeAt(`/${branch}/${index}`)],
    );

  return (
    <Card>
      <div className="mb-4">
        <CardTitle hint={hint}>{title}</CardTitle>
      </div>
      {items.length === 0 ? (
        <EmptyLine>Nothing here yet.</EmptyLine>
      ) : (
        <ul className="divide-y hairline">
          {items.map((item, index) => (
            <li key={item.id} className="flex items-start gap-3 py-2.5">
              <Checkbox
                checked={item.checked}
                onCheckedChange={(checked) =>
                  update(index, { checked: checked === true })
                }
                aria-label={`Mark "${item.text || "this item"}"`}
                className="mt-1 rounded-[2px]"
              />
              <EditableLine className="flex-1">
                <InlineText
                  trailing={item.text.trim() ? editMark : writeMark}
                  value={item.text}
                  onChange={(text) => update(index, { text })}
                  ariaLabel="Item"
                  required={false}
                  className="text-sm"
                />
              </EditableLine>
              <CommentsButton
                comments={item.comments}
                title={item.text || title}
                {...commentHandlers(item, (patch) => update(index, patch))}
              />
              <IconButton
                label="Delete this item"
                onClick={() => remove(index)}
                tone="destructive"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </IconButton>
            </li>
          ))}
        </ul>
      )}
      <AddLine label={addLabel} onClick={add} />
    </Card>
  );
}

/** Add / edit / remove on one item's comment thread, as patches to that item. */
function commentHandlers(
  item: MapCheckItem,
  update: (patch: Partial<MapCheckItem>) => void,
) {
  return {
    onAdd: (text: string, company: string) =>
      update({ comments: [...item.comments, newComment(text, company)] }),
    onRemove: (commentIndex: number) =>
      update({
        comments: item.comments.filter((_, i) => i !== commentIndex),
      }),
    onEdit: (commentIndex: number, text: string) =>
      update({
        comments: item.comments.map((comment, i) =>
          i === commentIndex ? { ...comment, text } : comment,
        ),
      }),
  };
}

/* ── Personal qualities: a cloud of pills ──────────────────────────────── */

function QualitiesCard({
  map,
  commit,
}: {
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
}) {
  const items = map.qualities;

  const write = (next: MapCheckItem[], ops: MapPatchOp[]) =>
    commit(ops, { ...map, qualities: next });

  const add = () => {
    const item = newCheckItem("");
    write([...items, item], [setAt("/qualities/-", item)]);
  };
  const update = (index: number, patch: Partial<MapCheckItem>) => {
    const next = items.map((item, i) =>
      i === index ? { ...item, ...patch } : item,
    );
    write(
      next,
      Object.entries(patch).map(([key, value]) =>
        setAt(`/qualities/${index}/${key}`, value),
      ),
    );
  };
  const remove = (index: number) =>
    write(
      items.filter((_, i) => i !== index),
      [removeAt(`/qualities/${index}`)],
    );

  return (
    <Card>
      <div className="mb-4">
        <CardTitle hint="The character the advert asks for — a team player, someone who works unsupervised. Tap a quality to mark it as one of yours; the ones you keep are the words your application should use.">
          Personal qualities
        </CardTitle>
      </div>
      {/* The cloud ends in its own "Add +" tag, not a button in the head (owner, 2026-09-18).

          **There is no "being named" state** (owner, 2026-09-23): a new quality IS a pill, empty,
          with its words edited in place like every other line on this page. The capsule it used
          to pass through had a tick of its own to confirm the name — the same tick that means
          "this one is mine" on a finished pill — so one glyph meant two things, Enter did not
          end the edit, and it took two taps to get out of a state that should not have existed. */}
      <div className="flex flex-wrap items-center gap-2">
        {items.map((item, index) => (
          <QualityPill
            key={item.id}
            item={item}
            onText={(text) => update(index, { text })}
            onToggle={() => update(index, { checked: !item.checked })}
            onRemove={() => remove(index)}
            comments={commentHandlers(item, (patch) => update(index, patch))}
          />
        ))}
        <button type="button" onClick={add}>
          <TagPill tone="teal" className="hover:bg-[#0A8080]/5">
            Add
            <Plus className="h-4 w-4" />
          </TagPill>
        </button>
      </div>
    </Card>
  );
}

/**
 * One quality as a pill — the pill IS the checkbox, and its comment and delete sit INSIDE it,
 * behind a chevron, the way a resource card hides its actions (owner, 2026-09-18). A row of
 * pills each trailing two loose icons read as a list of controls rather than as a cloud.
 */
function QualityPill({
  item,
  onText,
  onToggle,
  onRemove,
  comments,
}: {
  item: MapCheckItem;
  onText: (text: string) => void;
  onToggle: () => void;
  onRemove: () => void;
  comments: ReturnType<typeof commentHandlers>;
}) {
  const [open, setOpen] = useState(false);
  return (
    // `h-auto` and `max-w-full`: a quality can be a phrase, and the capsule grows to hold it
    // rather than letting the words run outside it (owner, 2026-09-23).
    <TagPill
      tone={item.checked ? "teal" : "ink"}
      className="h-auto max-w-full gap-0.5 py-1.5 pr-1.5"
    >
      <button
        type="button"
        aria-pressed={item.checked}
        onClick={onToggle}
        aria-label={item.checked ? "Not one of mine" : "One of mine"}
        title={item.checked ? "Not one of mine" : "One of mine"}
        className="grid h-5 w-5 shrink-0 place-items-center"
      >
        <Check
          className={cn("h-3.5 w-3.5", !item.checked && "text-transparent")}
        />
      </button>
      {/* The words ARE the field: tap them and type. */}
      <span className="min-w-0">
        <InlineText
          value={item.text}
          onChange={onText}
          ariaLabel="Quality"
          required={false}
          className="text-sm"
        />
      </span>
      {open && (
        <>
          <span aria-hidden="true" className="mx-1 h-4 w-px bg-[#C8C8C8]" />
          <CommentsButton
            compact
            comments={item.comments}
            title={item.text || "Quality"}
            {...comments}
          />
          <IconButton
            compact
            label="Delete this quality"
            onClick={onRemove}
            tone="destructive"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </IconButton>
        </>
      )}
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={open ? "Hide actions" : "Show actions"}
        className="grid h-6 w-6 place-items-center rounded-full transition-colors hover:bg-black/5"
      >
        <ChevronRight
          className={cn(
            "h-3.5 w-3.5 transition-transform",
            open && "rotate-180",
          )}
        />
      </button>
    </TagPill>
  );
}

/* ── Requirements ──────────────────────────────────────────────────────── */

/**
 * The requirements as ONE block with rules between the rows — not a stack of separate cards
 * (owner, 2026-09-18, after the "Off-cycle payroll" reference). A row is the requirement's
 * number and its words, set as a link, with the chevron on the right; it opens in place.
 */
function RequirementsColumn({
  map,
  commit,
}: {
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
}) {
  const items = map.requirements;
  // **One open at a time** (owner, 2026-09-24): every row expands into an answer box per
  // employer, and several open at once turned the block into a page of its own.
  const [openId, setOpenId] = useState<string | null>(null);
  const write = (next: MapRequirement[], ops: MapPatchOp[]) =>
    commit(ops, { ...map, requirements: next });

  const add = () => {
    const item = newRequirement("");
    write([...items, item], [setAt("/requirements/-", item)]);
  };

  return (
    <div className="min-w-0 space-y-3">
      {/* **Requirements keeps its button beside the heading** (owner, 2026-09-22) — the one
          block that does. Its rows are tall and they open, so the foot of the list is a long
          way from the words "Requirements"; the other blocks are short lists where the round +
          under the last item is where the eye already is. */}
      <div className="flex items-center justify-between gap-3">
        <CardTitle hint="What the employer asks for, one line each. Open a requirement to write, employer by employer, what you have actually done that answers it — and tap its picture to mark it as the one the job turns on, or as one you cannot meet.">
          Requirements
        </CardTitle>
        <AddButton label="Add a requirement" onClick={add} />
      </div>
      {/* There is no empty state: the map always offers three rows to fill in
          (`DEFAULT_REQUIREMENT_SLOTS`). */}
      <div className="divide-y hairline rounded-[4px] border hairline bg-white">
        {items.map((item, index) => (
          <RequirementRow
            key={item.id}
            item={item}
            index={index}
            open={openId === item.id}
            onOpen={(next) => setOpenId(next ? item.id : null)}
            onChange={(patch, ops) =>
              write(
                items.map((existing, i) =>
                  i === index ? { ...existing, ...patch } : existing,
                ),
                ops,
              )
            }
            onRemove={() =>
              write(
                items.filter((_, i) => i !== index),
                [removeAt(`/requirements/${index}`)],
              )
            }
          />
        ))}
      </div>
    </div>
  );
}

/**
 * The three states of a requirement's picture, in the order one tap walks through them. They are
 * stored as two flags rather than one word, because `important` is what the server already reads.
 */
const REQUIREMENT_MARKS = {
  plain: {
    src: "/illustrations/requirement.svg",
    title: "An ordinary requirement. Tap to mark it as one you cannot meet",
  },
  important: {
    src: "/illustrations/requirement-important.svg",
    title: "The most important requirement",
  },
  unmet: {
    src: "/illustrations/requirement-unmet.svg",
    title: "You cannot meet this. Tap to clear the mark",
  },
} as const;

type RequirementMark = keyof typeof REQUIREMENT_MARKS;

function requirementMark(item: MapRequirement): RequirementMark {
  if (item.unmet) return "unmet";
  return item.important ? "important" : "plain";
}

/**
 * Plain to cannot-meet and back, for every row but the first.
 *
 * <p>"Very important" is not in the cycle: it belongs to position 0 and cannot be moved (owner,
 * 2026-09-22), so the rows that CAN be marked have two states, not three.
 */
function nextRequirementMark(item: MapRequirement) {
  return requirementMark(item) === "unmet"
    ? { important: false, unmet: false }
    : { important: false, unmet: true };
}

function RequirementRow({
  item,
  index,
  open,
  onOpen,
  onChange,
  onRemove,
}: {
  item: MapRequirement;
  index: number;
  /** The list owns this: only one row is open at a time. */
  open: boolean;
  onOpen: (open: boolean) => void;
  onChange: (patch: Partial<MapRequirement>, ops: MapPatchOp[]) => void;
  onRemove: () => void;
}) {
  const base = `/requirements/${index}`;

  const setCompanies = (
    companies: MapRequirement["companies"],
    ops: MapPatchOp[],
  ) => onChange({ companies }, ops);

  return (
    <div>
      <div className="flex items-center gap-3 px-5 py-4 transition-colors hover:bg-[#FAFAFA]">
        {/* **The picture IS the mark, and one tap cycles the three** (owner, 2026-09-20): the
            pencil is an ordinary requirement, the loudspeaker says the application turns on it,
            and the scales with a cross say she cannot meet it at all. It replaced the flag that
            used to sit on the right of the row.

            **The FIRST requirement is the important one and that cannot be changed** (owner,
            2026-09-22), so its picture is a picture, not a button: there is no state to cycle. */}
        {index === 0 ? (
          <img
            src={REQUIREMENT_MARKS.important.src}
            alt=""
            aria-hidden="true"
            title="The first requirement is always the most important one"
            className="m-1 h-10 w-10 shrink-0 select-none"
          />
        ) : (
          <button
            type="button"
            onClick={() => {
              const next = nextRequirementMark(item);
              onChange(next, [
                setAt(`${base}/important`, next.important),
                setAt(`${base}/unmet`, next.unmet),
              ]);
            }}
            title={REQUIREMENT_MARKS[requirementMark(item)].title}
            aria-label={REQUIREMENT_MARKS[requirementMark(item)].title}
            className="shrink-0 rounded-[4px] p-1 transition-colors hover:bg-black/5"
          >
            <img
              src={REQUIREMENT_MARKS[requirementMark(item)].src}
              alt=""
              aria-hidden="true"
              className="h-10 w-10 select-none"
            />
          </button>
        )}
        <button
          type="button"
          onClick={() => onOpen(!open)}
          aria-expanded={open}
          className="flex min-w-0 flex-1 items-center gap-4 text-left"
        >
          <span className="min-w-0 flex-1">
            {/* Near-black, not Kale, and only the title underlined — the "Off-cycle payroll"
              reference (owner, 2026-09-18). */}
            {/* **The most important requirement says so in words** (owner, 2026-09-22): the
              loudspeaker beside it carries the meaning, and "Requirement 3" beside it said
              nothing the position on the page had not already said. The other two marks keep
              the number — it is how a requirement is referred to. */}
            <span className="block text-[15px] font-semibold text-foreground underline underline-offset-2">
              {requirementMark(item) === "important"
                ? "Very important!"
                : requirementLabel(index)}
            </span>
            {/* Closed, the words read as the row's link text; open, they become the field below.
                A row with nothing in it yet shows nothing — the sentence that used to stand in
                for it was a placeholder, and the map has none (owner, 2026-09-24). */}
            {!open && item.text.trim() && (
              <span className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                {item.text}
              </span>
            )}
          </span>
          <ChevronRight
            className={cn(
              "h-4 w-4 shrink-0 text-foreground transition-transform",
              open && "rotate-90",
            )}
          />
        </button>
      </div>

      {open && (
        <div className="px-5 pb-4">
          <EditableLine>
            <InlineText
              trailing={item.text.trim() ? editMark : writeMark}
              value={item.text}
              onChange={(text) =>
                onChange({ text }, [setAt(`${base}/text`, text)])
              }
              ariaLabel={requirementLabel(index)}
              required={false}
              className="text-sm"
            />
          </EditableLine>

          <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Your experience, employer by employer
          </p>
          <div className="space-y-4">
            {item.companies.map((company, companyIndex) => (
              <div key={company.id}>
                <div className="mb-1 flex items-center justify-between gap-2">
                  {/* The LABEL is editable, and **it is shown even before she names it**
                      (owner, 2026-09-24): "Company 1" is what the app calls that box everywhere
                      else — in the removal button, in the exported file — so a box with no
                      heading at all read as a field that had lost its name. Blank is still what
                      is STORED, which is why the numbering follows the list and deleting one
                      renumbers the rest; typing the position's own name back therefore stores
                      nothing. */}
                  <InlineText
                    value={
                      company.label.trim() ||
                      companyLabel(company, companyIndex)
                    }
                    onChange={(typed) => {
                      // Typing the position's own name back is not naming it: store blank, so
                      // the box keeps following its position instead of freezing "Company 2"
                      // onto a row that later moves.
                      const label =
                        typed.trim() === companyLabel(company, companyIndex)
                          ? ""
                          : typed;
                      setCompanies(
                        item.companies.map((existing, i) =>
                          i === companyIndex
                            ? { ...existing, label }
                            : existing,
                        ),
                        [
                          setAt(
                            `${base}/companies/${companyIndex}/label`,
                            label,
                          ),
                        ],
                      );
                    }}
                    ariaLabel={`Name of ${companyLabel(company, companyIndex)}`}
                    required={false}
                    className={FIELD_LABEL}
                  />
                  <IconButton
                    label={`Remove ${companyLabel(company, companyIndex)}`}
                    onClick={() =>
                      setCompanies(
                        item.companies.filter((_, i) => i !== companyIndex),
                        [removeAt(`${base}/companies/${companyIndex}`)],
                      )
                    }
                    tone="destructive"
                  >
                    <X className="h-3.5 w-3.5" />
                  </IconButton>
                </div>
                <AutoTextarea
                  // The box under the employer's name had no name of its own, so nothing but
                  // its position said what it was for — to a screen reader, or to a test.
                  aria-label={`Your experience at ${companyLabel(
                    company,
                    companyIndex,
                  )}`}
                  value={company.text}
                  onChange={(text) =>
                    setCompanies(
                      item.companies.map((existing, i) =>
                        i === companyIndex ? { ...existing, text } : existing,
                      ),
                      [setAt(`${base}/companies/${companyIndex}/text`, text)],
                    )
                  }
                  className={cn(FIELD, "min-h-[48px]")}
                />
              </div>
            ))}
          </div>

          {/* The row's actions sit at the bottom right, once it is open — as on a target card. */}
          <div className="mt-4 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                const slot = newCompanySlot();
                setCompanies(
                  [...item.companies, slot],
                  [setAt(`${base}/companies/-`, slot)],
                );
              }}
              className="inline-flex h-8 items-center gap-1.5 rounded-[4px] px-2 text-sm font-semibold text-primary transition-colors hover:bg-primary-soft"
            >
              <Plus className="h-4 w-4" />
              Add a company
            </button>
            {/* **The first requirement cannot be deleted, only emptied** (owner, 2026-09-24):
                the list always leads with the important one, so removing it would leave the
                block without the row that gives it its shape. */}
            {index === 0 ? (
              <IconButton
                label="Clear this requirement"
                onClick={() =>
                  onChange({ text: "" }, [setAt(`${base}/text`, "")])
                }
              >
                <Eraser className="h-3.5 w-3.5" />
              </IconButton>
            ) : (
              <IconButton
                label="Delete this requirement"
                onClick={onRemove}
                tone="destructive"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </IconButton>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/* ── Additional information ────────────────────────────────────────────── */

const ADDITIONAL_TAGS: { value: MapAdditionalTag; label: string }[] = [
  { value: "none", label: "No tag" },
  { value: "cover_letter", label: "Cover letter" },
  { value: "profile", label: "Profile" },
];

function AdditionalList({
  map,
  commit,
}: {
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
}) {
  const items = map.additional;
  const write = (next: MapAdditionalItem[], ops: MapPatchOp[]) =>
    commit(ops, { ...map, additional: next });

  const update = (index: number, patch: Partial<MapAdditionalItem>) =>
    write(
      items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
      Object.entries(patch).map(([key, value]) =>
        setAt(`/additional/${index}/${key}`, value),
      ),
    );

  // **Laid out like the "Review and apply" checklist** (owner, 2026-09-18): a round Kale tick,
  // the fact in near-black semibold with its detail muted underneath, generous rows with no
  // rules between them, and the add button at the FOOT, filled, like "Submit application".
  return (
    <div className="space-y-4">
      <CardTitle hint="Anything else worth saying that is not a skill or a requirement — a notice period, a relocation, a portfolio. Tag each one so it is clear whether it belongs in the cover letter or on the profile.">
        Additional information
      </CardTitle>
      {items.length === 0 ? (
        <EmptyLine>Nothing here yet.</EmptyLine>
      ) : (
        <ul className="space-y-6">
          {items.map((item, index) => (
            <li key={item.id} className="flex items-start gap-4">
              <RoundCheck
                checked={item.checked}
                onChange={(checked) => update(index, { checked })}
                label={`Mark "${item.text || "this item"}"`}
              />
              {/* **One mark per LINE, not per row.** Wrapped together, the two fields made one
                  box as wide as the longer of them, so the title's mark sat at the end of the
                  DETAIL's line — which is exactly the "somewhere at the end of the row" the
                  owner rejected (2026-09-22). */}
              <div className="min-w-0 flex-1">
                <EditableLine>
                  <InlineText
                    // The name is the page's own ink, so its mark is too (owner, 2026-09-24);
                    // the line under it is muted, and keeps the teal mark.
                    trailing={item.text.trim() ? editMarkInk : writeMarkInk}
                    value={item.text}
                    onChange={(text) => update(index, { text })}
                    ariaLabel="Information"
                    required={false}
                    className="text-base font-semibold"
                  />
                </EditableLine>
                <EditableLine className="mt-0.5">
                  <InlineText
                    trailing={item.detail.trim() ? editMark : writeMark}
                    value={item.detail}
                    onChange={(detail) => update(index, { detail })}
                    ariaLabel="Your own account of it"
                    required={false}
                    className="text-sm text-muted-foreground"
                  />
                </EditableLine>
              </div>
              <TagButton
                value={item.tag}
                onChange={(tag) => update(index, { tag })}
              />
              <IconButton
                label="Delete this item"
                onClick={() =>
                  write(
                    items.filter((_, i) => i !== index),
                    [removeAt(`/additional/${index}`)],
                  )
                }
                tone="destructive"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </IconButton>
            </li>
          ))}
        </ul>
      )}
      <AddLine
        label="Add information"
        onClick={() => {
          const item = newAdditionalItem("");
          write([...items, item], [setAt("/additional/-", item)]);
        }}
      />
    </div>
  );
}

/**
 * A round tick — **the same control a target's checklist item uses** (owner, 2026-09-22):
 * Gravity's `circle-check` / `circle-check-fill` pair at **20px**, which is the one case a solid
 * glyph may sit beside its outline twin. The hand-drawn 28px ring with a 2px wall that stood here
 * read as a button rather than as a tick. Kale when on, because in this map a tick means "this is
 * mine" and every other tick on the page is Kale.
 *
 * A real button with `role="checkbox"`, so it is announced and toggled like one.
 */
function RoundCheck({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn(
        "mt-0.5 shrink-0 rounded-full transition-colors",
        checked ? "text-primary" : "text-border-strong hover:text-primary/70",
      )}
    >
      {checked ? (
        <CircleCheckFill className="h-5 w-5" />
      ) : (
        <CircleCheck className="h-5 w-5" />
      )}
    </button>
  );
}

/** The tag's colour says where the fact goes: blue for the letter, green for the profile. */
const TAG_TONE: Record<MapAdditionalTag, PillTone> = {
  none: "neutral",
  cover_letter: "info",
  profile: "success",
};

/** Cycles none → Cover letter → Profile. One control, so the row stays narrow. */
function TagButton({
  value,
  onChange,
}: {
  value: MapAdditionalTag;
  onChange: (next: MapAdditionalTag) => void;
}) {
  const current = ADDITIONAL_TAGS.findIndex((tag) => tag.value === value);
  const next =
    ADDITIONAL_TAGS[(current + 1) % ADDITIONAL_TAGS.length]?.value ?? "none";
  return (
    <button
      type="button"
      onClick={() => onChange(next)}
      title="Change the tag"
      className="shrink-0"
    >
      <Pill
        tone={TAG_TONE[value]}
        className={cn(value === "none" && "text-muted-foreground")}
      >
        {ADDITIONAL_TAGS.find((tag) => tag.value === value)?.label ?? "No tag"}
      </Pill>
    </button>
  );
}

/* ── Company information ───────────────────────────────────────────────── */

function CompanyBlock({
  map,
  commit,
}: {
  map: VacancyMap;
  commit: (ops: MapPatchOp[], next: VacancyMap) => void;
}) {
  const company = map.company;
  const set = (key: "about" | "name" | "link", value: string) =>
    commit([setAt(`/company/${key}`, value)], {
      ...map,
      company: { ...company, [key]: value },
    });
  const setComments = (comments: typeof company.comments, ops: MapPatchOp[]) =>
    commit(ops, { ...map, company: { ...company, comments } });

  return (
    <div className="space-y-3">
      <div>
        <div className="space-y-2">
          <CardTitle hint="What you know about the employer itself — what they do, who they are, and anything you have found out that is worth bringing up at an interview.">
            Company information
          </CardTitle>
          {/* **No frame round it** (owner, 2026-09-22): it is the block's own description,
              edited in place like every other line on this page, and a boxed field inside a
              card was a second frame for something that is simply text. */}
          <EditableLine>
            <InlineText
              trailing={company.about.trim() ? editMark : writeMark}
              value={company.about}
              onChange={(about) => set("about", about)}
              ariaLabel="About the company"
              required={false}
              className="text-sm"
            />
          </EditableLine>
        </div>
      </div>
      <Card className="p-0">
        {/* **The house leads the card, and the space under it stays empty** (owner, 2026-09-23)
            — the same shape the vacancy's own card has, so the two read as one family. */}
        <div className="flex items-start gap-4 p-4">
          <img
            src="/illustrations/company.svg"
            alt=""
            aria-hidden="true"
            className="h-12 w-12 shrink-0 select-none @xl:h-16 @xl:w-16"
          />
          <div className="grid min-w-0 flex-1 gap-4 @2xl:grid-cols-2">
            {/* **The link sits under the name** (owner, 2026-09-22) — the two are one fact about
              the employer, and a column of its own for a single address left the row lopsided. */}
            <div className="min-w-0 space-y-3">
              <div className="min-w-0">
                <p className="mb-1 text-sm font-semibold">Company name</p>
                <EditableLine>
                  <InlineText
                    trailing={company.name.trim() ? editMark : writeMark}
                    value={company.name}
                    onChange={(name) => set("name", name)}
                    ariaLabel="Company name"
                    required={false}
                    className="text-sm"
                  />
                </EditableLine>
              </div>
              <div className="min-w-0">
                <p className="mb-1 text-sm font-semibold">Link</p>
                <EditableLine>
                  <InlineText
                    trailing={company.link.trim() ? editMark : writeMark}
                    value={company.link}
                    onChange={(link) => set("link", link)}
                    ariaLabel="Company link"
                    required={false}
                    maxLength={FIELD_LIMITS.resourceUrl}
                    maxLengthLabel="Link"
                    className="text-sm"
                  />
                </EditableLine>
              </div>
            </div>
            <div className="min-w-0">
              <p className="mb-1 text-sm font-semibold">Worth knowing</p>
              {company.comments.length === 0 ? (
                <EmptyLine>Nothing yet.</EmptyLine>
              ) : (
                <ul className="space-y-1.5">
                  {company.comments.map((note, index) => (
                    <li key={note.id} className="flex items-start gap-2">
                      {/* A tick, not a dot (owner, 2026-09-22): these are things she has found
                        out, not an unordered list of possibilities. */}
                      <Check
                        aria-hidden="true"
                        className="mt-1 h-3.5 w-3.5 shrink-0 text-primary"
                      />
                      <EditableLine className="flex-1">
                        <InlineText
                          trailing={note.text.trim() ? editMark : writeMark}
                          value={note.text}
                          onChange={(text) =>
                            setComments(
                              company.comments.map((existing, i) =>
                                i === index ? { ...existing, text } : existing,
                              ),
                              [setAt(`/company/comments/${index}/text`, text)],
                            )
                          }
                          ariaLabel="Comment"
                          required={false}
                          className="text-sm"
                        />
                      </EditableLine>
                      <IconButton
                        label="Delete this comment"
                        onClick={() =>
                          setComments(
                            company.comments.filter((_, i) => i !== index),
                            [removeAt(`/company/comments/${index}`)],
                          )
                        }
                        tone="destructive"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </IconButton>
                    </li>
                  ))}
                </ul>
              )}
              <AddLine
                label="Add a note"
                onClick={() => {
                  const note = newNote("");
                  setComments(
                    [...company.comments, note],
                    [setAt("/company/comments/-", note)],
                  );
                }}
              />
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

/* ── Shared small parts ────────────────────────────────────────────────── */

/**
 * The way a list of this page grows: a **round + under the last item, with the action in words**
 * (owner, 2026-09-20). A button in the heading row sat furthest from the place the new line
 * appears, and on a narrow panel it squeezed the heading; under the list it is where the eye
 * already is when the last item has been read.
 */
/**
 * The square button beside a heading — 4px corners (CLAUDE.md → 3g), a teal outline and the
 * action in words. Only **Requirements** wears one: see the note where it is used.
 */
function AddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-[4px] border border-primary px-2.5 text-sm font-semibold text-primary transition-colors hover:bg-primary-soft"
    >
      <Plus className="h-4 w-4" />
      {label}
    </button>
  );
}

function AddLine({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-3 inline-flex items-center gap-2 text-sm font-semibold text-primary transition-colors hover:text-[#005961]"
    >
      {/* **The app's own circled plus, at the app's own size** (owner, 2026-09-22): Gravity's
          18px `CirclePlus` beside a 14px semibold teal word, which is exactly what "Add a
          subtask" and "Attach resource" use on a target. The ring drawn by hand here was 28px
          with a 2px wall and read as a button rather than as a line you add on. */}
      <CirclePlus className="h-[18px] w-[18px] shrink-0" />
      {label}
    </button>
  );
}

function IconButton({
  children,
  label,
  onClick,
  tone = "default",
  compact = false,
}: {
  children: React.ReactNode;
  label: string;
  onClick: () => void;
  tone?: "default" | "destructive";
  /** Inside a pill: a hit area that fits the capsule's height. */
  compact?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        "grid shrink-0 place-items-center rounded-[2px] text-muted-foreground/60 transition-colors",
        // Inside a tag pill an action is near-black, never grey (owner, 2026-09-18).
        compact ? "h-6 w-6 text-[#222525]" : "h-8 w-8",
        tone === "destructive"
          ? "hover:text-destructive"
          : "hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

/**
 * The hint that a line can be typed into (owner, 2026-09-20): a small pencil at the end of every
 * inline-edited row, in every block. Editing itself is unchanged — the mark is not a button,
 * it only says the words are a field, which on a page made almost entirely of bare text nothing
 * else does. It turns Kale while the row has the caret, so it also says which row is being edited.
 */
/**
 * The mark that says a line can be typed into: a small Kale pencil **immediately after the last
 * word**, as part of the text rather than beside the block (owner, 2026-09-22 — twice: floated
 * to the end of the row it read as a button in a column of its own, and baseline-aligned beside
 * the box it landed at the end of a wrapped answer's FIRST line).
 *
 * It rides in `InlineText`'s own text flow (`trailing`), which is the only place that is true for
 * a line of any length — and which also means it **disappears the moment the caret appears**,
 * because the edit view is a textarea, not this span (owner, 2026-09-23).
 */
const MARK = "mx-1 inline h-3.5 w-3.5 -translate-y-px";

/**
 * The mark on a line that already has words in it.
 *
 * **Every mark on the map is drawn at one size** (owner, 2026-09-24). They are all `h-3.5`, but
 * `pencil-to-line` spends part of its 16-box on the line underneath, so at the same box it draws
 * a visibly smaller pencil than the plain glyph does — see `writeMark`.
 */
const editMark = (
  <Pencil aria-hidden="true" className={cn(MARK, "text-primary/70")} />
);

/** The same pencil in the page's own ink, for a line whose words are near-black. */
const editMarkInk = (
  <Pencil aria-hidden="true" className={cn(MARK, "text-foreground")} />
);

/**
 * The mark on a line with nothing in it yet: Gravity's `pencil-to-line`, the pencil writing ON a
 * line (owner, 2026-09-24). The plain pencil then means "there are words here to change", and the
 * two are told apart at a glance without reading the row.
 */
/**
 * The mark on a line with nothing in it yet: Gravity's `pencil-to-line`, the pencil writing ON a
 * line (owner, 2026-09-24). The plain pencil then means "there are words here to change".
 *
 * **It is drawn one step larger on purpose.** The glyph gives about a fifth of its box to the
 * line under the pencil, so at 14px its pencil reads as the small one in a row of big ones, which
 * is what the owner saw. 16px puts the two pencils at the same size on screen.
 */
const writeMark = (
  <PencilToLine
    aria-hidden="true"
    className={cn(MARK, "h-4 w-4 text-primary/70")}
  />
);

/** `writeMark` in the page's own ink. */
const writeMarkInk = (
  <PencilToLine
    aria-hidden="true"
    className={cn(MARK, "h-4 w-4 text-foreground")}
  />
);

/**
 * A row that is edited in place.
 *
 * <p><b>It takes the width it is given, in both views.</b> Sized to its content, the field
 * collapsed into a narrow column the moment it was typed into — on a laptop with the whole
 * card free (owner, 2026-09-23). `flex-1` is what stops that; the mark can be `flex-1`'s friend
 * because it rides inside the text rather than beside it.
 */
function EditableLine({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("group/edit flex min-w-0", className)}>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

function EmptyLine({ children }: { children: React.ReactNode }) {
  return <p className="text-sm italic text-muted-foreground">{children}</p>;
}

/**
 * The form-field look the owner picked (2026-09-18, the "Add question" reference, measured): a
 * warm pale-grey well (Salt-300) with NO frame — only a hairline UNDER it, which turns Kale while
 * the field has the caret — 13px type, near-square corners, two lines tall at rest. A text area
 * keeps its drag grip; it also still grows as it fills, so the grip only ever makes it taller.
 * Used for the company answers and for everything in the comment modal.
 */
const FIELD =
  "w-full resize-y rounded-t-[2px] rounded-b-none border-0 border-b border-[#ABABAB] bg-[#F4F4F3] px-2.5 py-2 text-[13px] leading-snug text-foreground outline-none transition-colors placeholder:text-[#9F9F9F] focus:border-[#0A8080]";
/** The label that sits over a FIELD. */
const FIELD_LABEL = "text-[13px] font-semibold text-foreground";

/**
 * The comment affordance: a glyph alone, or a glyph with a count once there are comments — the
 * thread itself opens in a modal so a long conversation never stretches the row it belongs to.
 *
 * **The count sits LEFT of the glyph, in a fixed-width slot** (owner, 2026-09-18): with it on
 * the right, a row with comments pushed its glyph out of line with the rows above and below.
 */
function CommentsButton({
  comments,
  title,
  onAdd,
  onEdit,
  onRemove,
  compact = false,
}: {
  comments: MapComment[];
  title: string;
  onAdd: (text: string, company: string) => void;
  onEdit: (index: number, text: string) => void;
  onRemove: (index: number) => void;
  /** Inside a pill: a smaller hit area that fits the capsule's height. */
  compact?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [company, setCompany] = useState("");

  const reset = () => {
    setDraft("");
    setCompany("");
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={
          comments.length
            ? `${comments.length} comment${comments.length === 1 ? "" : "s"}`
            : "Add a comment"
        }
        title={comments.length ? "Comments" : "Add a comment"}
        className={cn(
          "inline-flex shrink-0 items-center justify-end gap-1 rounded-[2px] text-muted-foreground/60 transition-colors hover:text-primary",
          compact ? "h-6 px-1" : "h-8 w-10 px-1.5",
          // Inside a tag pill everything is near-black, the count included (owner, 2026-09-18).
          compact
            ? "text-[#222525] hover:text-[#222525]"
            : comments.length > 0 && "text-primary",
        )}
      >
        {/* **The glyph says whether there is anything to read** (owner, 2026-09-24):
            `comment-plus` invites the first one, `comment-dot` says a thread is there — and the
            count stands in front of it, so the dot is not the only thing carrying the news. */}
        {comments.length > 0 && (
          <span className="text-xs font-semibold tabular-nums">
            {comments.length}
          </span>
        )}
        {comments.length > 0 ? (
          <CommentDot className="h-3.5 w-3.5" />
        ) : (
          <CommentPlus className="h-3.5 w-3.5" />
        )}
      </button>

      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) reset();
        }}
      >
        {/* **Modelled on the "Add question" reference** (owner, 2026-09-18): a compact card with
            a margin to the screen on every side — never the full width of a phone — the title on
            the left, labelled fields in grey wells, and the two buttons bottom right. */}
        <DialogContent
          closeButton={false}
          className="vacancy-map w-[calc(100%-2rem)] max-w-[480px] gap-0 rounded-[4px] border-0 bg-white p-5 shadow-[0_12px_40px_-12px_rgba(0,0,0,0.3)]"
        >
          <div className="flex items-start justify-between gap-3">
            <DialogTitle className="min-w-0 break-words text-base font-semibold text-foreground">
              {title}
            </DialogTitle>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Close"
              className="-mr-1 -mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-[4px] text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {comments.length > 0 && (
            <ul className="mt-3 max-h-[40vh] divide-y hairline overflow-y-auto">
              {comments.map((comment, index) => (
                <li key={comment.id} className="flex items-start gap-2 py-2">
                  <div className="min-w-0 flex-1">
                    {comment.company.trim() && (
                      <p className="text-xs font-semibold text-muted-foreground">
                        {comment.company}
                      </p>
                    )}
                    <InlineText
                      value={comment.text}
                      onChange={(text) => onEdit(index, text)}
                      ariaLabel="Comment"
                      required={false}
                      className="text-sm"
                    />
                  </div>
                  <IconButton
                    label="Delete this comment"
                    onClick={() => onRemove(index)}
                    tone="destructive"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </IconButton>
                </li>
              ))}
            </ul>
          )}

          <form
            onSubmit={(event) => {
              event.preventDefault();
              const text = draft.trim();
              if (!text) return;
              onAdd(text, company.trim());
              reset();
            }}
            className="mt-4 space-y-3"
          >
            {/* Optional: which job the experience in this comment comes from. */}
            <label className="block space-y-1">
              <span className={FIELD_LABEL}>
                Company name{" "}
                <span className="font-normal text-muted-foreground">
                  (optional)
                </span>
              </span>
              <input
                value={company}
                onChange={(event) => setCompany(event.target.value)}
                maxLength={FIELD_LIMITS.resourceLabel}
                className={cn(FIELD, "h-9 py-0")}
              />
            </label>
            <div className="space-y-1">
              <span className={FIELD_LABEL}>Comment</span>
              <AutoTextarea
                value={draft}
                onChange={setDraft}
                className={cn(FIELD, "min-h-[48px]")}
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="h-9 rounded-[4px] border border-[#0A8080] bg-white px-4 text-sm font-semibold text-[#0A8080] transition-colors hover:bg-[#0A8080]/5"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!draft.trim()}
                className="h-9 rounded-[4px] bg-[#0A8080] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#005961] disabled:opacity-40"
              >
                Add comment
              </button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
