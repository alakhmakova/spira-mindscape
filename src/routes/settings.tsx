import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { LogOut } from "@/components/spira/icons";
import { useAuth } from "@/lib/spira/auth";
import {
  APP_FONTS,
  fontStack,
  useAppFont,
  type AppFontChoice,
  type AppFontId,
} from "@/lib/spira/app-font";
import {
  ABOUT_FURTHER_READING,
  ABOUT_SECTIONS,
} from "@/lib/spira/about-content";
import { ARTWORK_CREDITS } from "@/lib/spira/licences";
import {
  enableWebPush,
  pushState,
  sendTestPush,
  type PushState,
} from "@/lib/spira/web-push";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/settings")({
  // `?tab=about` — how the side navigation links straight to About Spira, which is a tab
  // here rather than a page of its own (the Android twin does the same with its `about`
  // route). Anything unrecognised falls back to My profile.
  validateSearch: (search: Record<string, unknown>): { tab?: TabId } => {
    const tab = search.tab;
    return tab === "notifications" ||
      tab === "fonts" ||
      tab === "about" ||
      tab === "licences"
      ? { tab }
      : {};
  },
  head: () => ({
    meta: [
      { title: "Settings — Spira" },
      {
        name: "description",
        content: "Your Spira account, and the face the app is set in.",
      },
    ],
  }),
  component: SettingsPage,
});

type TabId = "profile" | "notifications" | "fonts" | "about" | "licences";

const TABS: { id: TabId; label: string }[] = [
  { id: "profile", label: "My profile" },
  { id: "notifications", label: "Notifications" },
  { id: "fonts", label: "Fonts" },
  { id: "about", label: "About Spira" },
  { id: "licences", label: "Licences" },
];

/**
 * The account page: **My profile** (the address, and nothing to edit) and **Fonts** (the body face
 * the whole app is set in). Shaped after the reference the owner supplied — a page title, a row of
 * underlined tabs, then label-on-the-left / value-on-the-right rows.
 *
 * The Android twin is `ui/settings/UserSettingsScreen.kt`; the two carry the same two tabs and the
 * same font list on purpose, because the point of the Fonts tab is comparing a candidate on a
 * phone against the same candidate on a laptop.
 */
function SettingsPage() {
  const user = useAuth((s) => s.user);
  const { tab: requested } = Route.useSearch();
  const [tab, setTab] = useState<TabId>(requested ?? "profile");

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 sm:py-12">
      <h1 className="font-heading text-4xl tracking-tight sm:text-5xl">
        Settings
      </h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        Your account, and how Spira is set.
      </p>

      {/* The tab row: the current one carries a Guava underline, the same mark the goal
          workspace's GROW tabs use, so a tab means the same thing everywhere in the app. */}
      <div
        role="tablist"
        aria-label="Settings sections"
        // Five tabs do not fit across a phone: the row scrolls inside itself rather than wrap
        // a label onto two lines or push the page sideways (CLAUDE.md 3e-quinquies).
        className="hide-scrollbar mt-8 flex items-end gap-6 overflow-x-auto border-b hairline"
      >
        {TABS.map((t) => {
          const active = t.id === tab;
          return (
            <button
              key={t.id}
              role="tab"
              aria-selected={active}
              onClick={() => setTab(t.id)}
              className={cn(
                "-mb-px shrink-0 whitespace-nowrap border-b-[3px] px-1 pb-3 text-[15px] transition-colors",
                active
                  ? "border-[#F45D48] font-bold text-foreground"
                  : "border-transparent font-medium text-muted-foreground hover:text-foreground",
              )}
            >
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === "profile" && (
        <ProfileTab email={user?.email ?? ""} name={user?.name ?? ""} />
      )}
      {tab === "notifications" && <NotificationsTab />}
      {tab === "fonts" && <FontsTab />}
      {tab === "about" && <AboutTab />}
      {tab === "licences" && <LicencesTab />}
    </div>
  );
}

/**
 * The address, and the one action on the page — Sign out.
 *
 * The address is deliberately **not editable**: the account is a Google account, and Spira has no
 * say over it. Sign out lives here (not in a header menu) so this page mirrors the Android Settings
 * screen, where the account figure opens exactly this — profile plus sign-out — rather than a menu.
 */
function ProfileTab({ email, name }: { email: string; name: string }) {
  const navigate = useNavigate();
  const logout = useAuth((s) => s.logout);

  return (
    <div className="pt-8" role="tabpanel">
      <SettingRow label="Email">
        <p className="text-[15px] text-foreground">{email || "—"}</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Signed in with Google{name ? ` as ${name}` : ""}. Spira can&apos;t
          change your address — it belongs to that account.
        </p>
      </SettingRow>

      <div className="mt-8">
        <button
          type="button"
          onClick={async () => {
            await logout();
            void navigate({ to: "/login" });
          }}
          className="inline-flex items-center gap-2 rounded-md border-2 border-border px-4 py-2 text-sm font-semibold text-destructive transition-colors hover:border-destructive/60"
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </button>
      </div>
    </div>
  );
}

/** What this browser does with a reminder, in words — the state the Notifications tab reports. */
const PUSH_STATUS: Record<PushState, string> = {
  granted:
    "On. Reminders reach this browser as notifications, even with Spira closed.",
  ask: "Not set up in this browser yet. Allow notifications so reminders reach you with Spira closed.",
  denied:
    "Blocked in this browser’s settings. Reminders show only inside Spira until notifications are allowed for this site again.",
  unsupported:
    "This browser can’t receive notifications. On an iPhone, add Spira to the Home Screen (Share → Add to Home Screen) and open it from there.",
  insecure:
    "This address isn’t secure (https), and browsers allow notifications only on secure addresses.",
  off: "The server can’t send notifications right now. Reminders show only inside Spira until it can — try again in a minute.",
};

/**
 * Whether reminders can reach THIS device, and a way to prove it. A reminder that never arrived
 * used to leave no clue why — server off, browser unsupported, permission refused and a
 * subscription made under old keys all looked the same: nothing. This tab says which, and the
 * test button sends a real push through the whole path to this device.
 */
function NotificationsTab() {
  const [state, setState] = useState<PushState | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  useEffect(() => {
    void pushState().then(setState);
  }, []);

  const allow = async () => {
    setBusy(true);
    setResult(null);
    try {
      setState(await enableWebPush());
    } catch {
      setResult("Couldn’t subscribe this browser. Please try again.");
    }
    setBusy(false);
  };

  const test = async () => {
    setBusy(true);
    setResult(null);
    try {
      // Make sure this browser's subscription is on the server first.
      setState(await enableWebPush());
      const r = await sendTestPush();
      setResult(
        !r.enabled
          ? PUSH_STATUS.off
          : r.sent > 0
            ? `Sent to ${r.sent} ${r.sent === 1 ? "device" : "devices"}. It should appear within a few seconds.`
            : r.subscriptions === 0
              ? "No device of yours is subscribed yet."
              : "None of your devices accepted it. Allow notifications here again and retry.",
      );
    } catch {
      setResult("The test notification couldn’t be sent. Please try again.");
    }
    setBusy(false);
  };

  return (
    <div className="space-y-8 pt-8" role="tabpanel">
      <SettingRow label="This device">
        <p className="text-[15px] text-foreground" aria-live="polite">
          {state ? PUSH_STATUS[state] : "Checking…"}
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          {state === "ask" ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void allow()}
              className="h-11 rounded-md bg-primary px-5 text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
            >
              Allow notifications
            </button>
          ) : null}
          {state === "granted" ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void test()}
              className="h-11 rounded-md border-2 border-border px-5 text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
            >
              Send a test notification
            </button>
          ) : null}
        </div>
        {result ? (
          <p className="mt-3 text-sm text-foreground/80" role="status">
            {result}
          </p>
        ) : null}
      </SettingRow>
    </div>
  );
}

/**
 * The body face the whole app is set in.
 *
 * Every row is **written in the face it offers**, so the list is itself the specimen; picking one
 * re-fonts the page under the cursor, which is the fastest way to judge a candidate — the tabs,
 * the labels and this very paragraph change with it.
 */
function FontsTab() {
  const font = useAppFont((s) => s.font);
  const setFont = useAppFont((s) => s.setFont);

  return (
    <div className="pt-8" role="tabpanel">
      <SettingRow label="App font">
        <p className="mb-4 text-xs text-muted-foreground">
          Sets the body face everywhere — cards, labels, buttons and this page.
          Headings stay on ITC Clearface. The choice is remembered on this
          device.
        </p>
        {/* Split by alphabet, because the two groups aren't comparable: a Latin-only face draws
            Russian in the system sans, so those rows show two fonts side by side and can't be
            judged as one. */}
        <FontGroup
          heading="With Cyrillic"
          caption="Draws Russian in its own letterforms."
          choices={APP_FONTS.filter((c) => c.cyrillic)}
          font={font}
          setFont={setFont}
        />
        <FontGroup
          heading="Latin only"
          caption="Russian falls back to the system sans, GCentra included."
          choices={APP_FONTS.filter((c) => !c.cyrillic)}
          font={font}
          setFont={setFont}
        />
      </SettingRow>
    </div>
  );
}

function FontGroup({
  heading,
  caption,
  choices,
  font,
  setFont,
}: {
  heading: string;
  caption: string;
  choices: AppFontChoice[];
  font: AppFontId;
  setFont: (id: AppFontId) => void;
}) {
  if (choices.length === 0) return null;
  return (
    <div className="mb-6 last:mb-0">
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
        {heading}
      </p>
      <p className="mb-2 text-xs text-muted-foreground">{caption}</p>
      <div className="space-y-2">
        {choices.map((choice) => (
          <FontRow
            key={choice.id}
            id={choice.id}
            label={choice.label}
            note={choice.note}
            cyrillic={choice.cyrillic ?? false}
            selected={choice.id === font}
            onSelect={() => setFont(choice.id)}
          />
        ))}
      </div>
    </div>
  );
}

function FontRow({
  id,
  label,
  note,
  cyrillic,
  selected,
  onSelect,
}: {
  id: AppFontId;
  label: string;
  note: string;
  cyrillic: boolean;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      // The row is set in the face it offers — the list IS the specimen sheet.
      style={{ fontFamily: fontStack(id) }}
      className={cn(
        "flex w-full items-center gap-4 rounded-md border px-4 py-3 text-left transition-colors",
        selected
          ? "border-primary bg-primary-soft"
          : "border-border bg-surface hover:border-primary/50",
      )}
    >
      <span
        className={cn(
          "grid h-5 w-5 shrink-0 place-items-center rounded-full border-2",
          selected ? "border-primary" : "border-border-strong",
        )}
      >
        {selected && <span className="h-2.5 w-2.5 rounded-full bg-primary" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-base font-semibold text-foreground">
          {label}
        </span>
        {/* A pangram, so every letter of the alphabet is on show in the candidate. */}
        <span className="mt-0.5 block truncate text-sm text-foreground/80">
          The quick brown fox jumps over the lazy dog — 0123456789
        </span>
        {cyrillic && (
          <span className="mt-0.5 block truncate text-sm text-foreground/80">
            Съешь ещё этих мягких французских булок да выпей чаю
          </span>
        )}
        <span className="mt-1 block text-xs text-muted-foreground">{note}</span>
      </span>
    </button>
  );
}

/**
 * **About Spira** — what coaching is, what GROW is, how a session runs, and how the app
 * fits together. The copy lives in `lib/spira/about-content.ts`; the Android twin
 * (`ui/settings/AboutTab.kt`) renders the identical text.
 *
 * It carries more weight than a normal help page. The coach is deliberately barred from
 * explaining its own method during a session — naming the technique is what stops it
 * working — so this is the only place the user can find out what is being done to them
 * and why. It is also where the coach points anyone who asks how it works.
 */
function AboutTab() {
  return (
    <div className="pt-8 space-y-10" role="tabpanel">
      {ABOUT_SECTIONS.map((section) => (
        <SettingRow key={section.id} label={section.heading}>
          <div className="space-y-4">
            {section.blocks.map((block, i) =>
              block.kind === "p" ? (
                <p
                  key={i}
                  className="max-w-prose text-[15px] leading-[1.62] text-foreground"
                >
                  {block.text}
                </p>
              ) : (
                <dl key={i} className="max-w-prose space-y-3">
                  {block.items.map((item) => (
                    <div key={item.term}>
                      <dt className="text-[15px] font-semibold text-foreground">
                        {item.term}
                      </dt>
                      <dd className="mt-0.5 text-[15px] leading-[1.62] text-muted-foreground">
                        {item.text}
                      </dd>
                    </div>
                  ))}
                </dl>
              ),
            )}
          </div>
        </SettingRow>
      ))}

      <SettingRow label="Further reading">
        <div className="max-w-prose space-y-4">
          <p className="text-[15px] leading-[1.62] text-muted-foreground">
            Two standard texts on coaching, if you want to read further. Neither
            is required to use Spira.
          </p>
          {ABOUT_FURTHER_READING.map((book) => (
            <div key={book.title}>
              <a
                href={book.href}
                target="_blank"
                rel="noreferrer noopener"
                className="text-[15px] font-semibold text-primary underline underline-offset-2 hover:text-primary/80"
              >
                {book.title}
              </a>
              <p className="mt-0.5 text-[15px] leading-[1.62] text-muted-foreground">
                {book.author} — {book.note}
              </p>
            </div>
          ))}
        </div>
      </SettingRow>
    </div>
  );
}

/**
 * **Licences** — what Spira uses that someone else made, and the credit each one asks for.
 *
 * A tab of its own rather than a note on About Spira (owner, 2026-10-09): About Spira is where
 * the coaching method is explained to the person being coached, and a licence notice has no
 * business in the middle of that. CC BY wants the author named in a manner reasonable for the
 * medium; for an app whose source its users never read, that means a page in the app.
 *
 * `lib/spira/licences.ts` holds the lines. Anything else third-party belongs here as it arrives.
 */
function LicencesTab() {
  return (
    <div className="pt-8 space-y-10" role="tabpanel">
      <SettingRow label="Artwork">
        <div className="max-w-prose space-y-4">
          {ARTWORK_CREDITS.map((credit) => (
            // Keyed by the work: two lines share an author.
            <div key={credit.work}>
              <p className="text-[15px] leading-[1.62] text-foreground">
                {credit.work} by {credit.author} from{" "}
                <a
                  href={credit.href}
                  title={`${credit.work} Icons`}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="font-semibold text-primary underline underline-offset-2 hover:text-primary/80"
                >
                  Noun Project
                </a>{" "}
                ({credit.licence})
              </p>
              <p className="mt-0.5 text-[15px] leading-[1.62] text-muted-foreground">
                {credit.where}
              </p>
            </div>
          ))}
        </div>
      </SettingRow>
    </div>
  );
}

/** One settings row: its name on the left, its value on the right (the reference's layout). */
function SettingRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="grid gap-2 sm:grid-cols-[160px_1fr] sm:gap-8">
      <p className="pt-0.5 text-[15px] text-muted-foreground sm:text-right">
        {label}
      </p>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
