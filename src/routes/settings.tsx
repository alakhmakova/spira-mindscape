import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { LogOut } from "@/components/spira/icons";
import { useAuth } from "@/lib/spira/auth";
import {
  APP_FONTS,
  fontStack,
  useAppFont,
  type AppFontChoice,
  type AppFontId,
} from "@/lib/spira/app-font";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/settings")({
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

type TabId = "profile" | "fonts";

const TABS: { id: TabId; label: string }[] = [
  { id: "profile", label: "My profile" },
  { id: "fonts", label: "Fonts" },
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
  const [tab, setTab] = useState<TabId>("profile");

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
        className="mt-8 flex items-end gap-6 border-b hairline"
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
                "-mb-px border-b-[3px] px-1 pb-3 text-[15px] transition-colors",
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

      {tab === "profile" ? (
        <ProfileTab email={user?.email ?? ""} name={user?.name ?? ""} />
      ) : (
        <FontsTab />
      )}
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
