import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "@tanstack/react-router";

import { markNotificationsHintSeen } from "@/lib/spira/notifications-hint";

/**
 * "Turn on notifications" — a one-time hint drawn as a disc spreading out of the account figure in
 * the header, with the figure itself lifted onto a white plate (owner, 2026-10-18, after a mail
 * app's feature-discovery tip). It appears the FIRST time "Add event" is pressed in a browser that
 * has not allowed notifications, because that is the moment a reminder starts to matter.
 *
 * - **Tapping the figure opens Settings → Notifications**, where permission is asked from a button
 *   and the page explains a refusal the browser remembers.
 * - **Tapping anywhere else, Escape, or "Not now" dismisses it**, and the Add event form the user
 *   asked for then opens — the hint must never cost them the thing they pressed.
 * - It is shown once per browser and never again, whichever way it was closed.
 *
 * The disc is painted with **the AI chat's own gradient** (`#83D2D2` → `#F2FFFF`, CLAUDE.md →
 * "The AI chat gradient"; owner, 2026-10-19 — it was intelligence-500 violet for a day). The type
 * on it is **brand-1100 `#003737`**, the deep teal the auxiliary sheet head sets its title in:
 * 7.6:1 against the gradient's DARKEST stop, so every point of the disc clears 4.5:1, and it
 * reads as the same family as the teal rather than as black laid on top of it.
 */

/** Plate around the figure, and the gutter the words keep from the disc's edge and the screen's. */
const PLATE = 80;
const GUTTER = 24;
const TEXT_WIDTH = 340;

type Geometry = {
  cx: number;
  cy: number;
  radius: number;
  textLeft: number;
  textTop: number;
  textWidth: number;
  src: string;
  alt: string;
};

export function NotificationsHint({
  onDismiss,
  onLeave,
}: {
  /** Closed without going anywhere: the caller carries on with what was pressed. */
  onDismiss: () => void;
  /** Left for Settings: the caller only takes the hint down. */
  onLeave: () => void;
}) {
  const navigate = useNavigate();
  const [geo, setGeo] = useState<Geometry | null>(null);
  const [shown, setShown] = useState(false);
  const textRef = useRef<HTMLDivElement>(null);
  const plateRef = useRef<HTMLButtonElement>(null);

  // Measure the figure and the words, then size the disc to reach past the words' far corner.
  useLayoutEffect(() => {
    const measure = () => {
      const target = document.querySelector<HTMLElement>(
        '[data-hint-target="account"]',
      );
      if (!target) return;
      const r = target.getBoundingClientRect();
      const img = target.querySelector("img");
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const vw = window.innerWidth;
      const textWidth = Math.min(TEXT_WIDTH, vw - GUTTER * 2);
      // Under the figure, ending a little past it on the right, and never off the screen.
      const textLeft = Math.max(
        GUTTER,
        Math.min(cx + PLATE / 2 - textWidth, vw - textWidth - GUTTER),
      );
      const textTop = cy + PLATE / 2 + 28;
      const textHeight = textRef.current?.offsetHeight ?? 160;
      const far = Math.max(
        Math.hypot(cx - textLeft, cy - (textTop + textHeight)),
        Math.hypot(cx - (textLeft + textWidth), cy - (textTop + textHeight)),
      );
      setGeo({
        cx,
        cy,
        radius: far + GUTTER * 2,
        textLeft,
        textTop,
        textWidth,
        src: img?.currentSrc || img?.src || "",
        alt: img?.alt || "Account",
      });
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, []);

  // Spread on the next frame so the transition has a start to run from; mark it seen at once.
  useLayoutEffect(() => {
    markNotificationsHintSeen();
    const id = requestAnimationFrame(() => setShown(true));
    return () => cancelAnimationFrame(id);
  }, []);

  useLayoutEffect(() => {
    if (shown) plateRef.current?.focus();
  }, [shown]);

  // Escape closes it like any dialog, and does not reach whatever is under it.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      e.stopPropagation();
      onDismiss();
    };
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [onDismiss]);

  const openSettings = () => {
    void navigate({ to: "/settings", search: { tab: "notifications" } });
    // Leaving for Settings is the answer; the Add event form is not opened behind it.
    onLeave();
  };

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="notifications-hint-title"
      aria-describedby="notifications-hint-text"
      className="fixed inset-0 z-[80]"
    >
      {/* Everything outside the disc is a dismiss target; it is not dimmed, as in the reference. */}
      <div
        aria-hidden="true"
        className="absolute inset-0"
        onClick={() => onDismiss()}
      />
      {geo ? (
        <>
          <div
            aria-hidden="true"
            onClick={() => onDismiss()}
            className="absolute rounded-full transition-transform duration-300 ease-out motion-reduce:transition-none"
            style={{
              left: geo.cx - geo.radius,
              top: geo.cy - geo.radius,
              width: geo.radius * 2,
              height: geo.radius * 2,
              backgroundImage:
                "linear-gradient(180deg, #83D2D2 3.43%, #F2FFFF 118.85%)",
              transform: `scale(${shown ? 1 : 0})`,
              boxShadow: "0 8px 24px rgba(28,28,28,.12)",
            }}
          />
          <button
            ref={plateRef}
            type="button"
            onClick={openSettings}
            aria-label="Open notification settings"
            className="absolute grid place-items-center rounded-full bg-white outline-none transition-opacity duration-300 focus-visible:ring-2 focus-visible:ring-[#003737] focus-visible:ring-offset-2 focus-visible:ring-offset-[#83D2D2]"
            style={{
              left: geo.cx - PLATE / 2,
              top: geo.cy - PLATE / 2,
              width: PLATE,
              height: PLATE,
              opacity: shown ? 1 : 0,
            }}
          >
            <img
              src={geo.src}
              alt=""
              referrerPolicy="no-referrer"
              className="h-9 w-9 rounded-full border hairline object-cover"
            />
          </button>
        </>
      ) : null}
      <div
        ref={textRef}
        className="absolute transition-opacity delay-150 duration-200 motion-reduce:transition-none"
        style={{
          left: geo?.textLeft ?? 0,
          top: geo?.textTop ?? 0,
          width: geo?.textWidth ?? TEXT_WIDTH,
          opacity: geo && shown ? 1 : 0,
        }}
      >
        <h2
          id="notifications-hint-title"
          className="text-xl font-medium leading-tight text-[#003737]"
        >
          Turn on notifications
        </h2>
        <p
          id="notifications-hint-text"
          className="mt-3 text-[15px] leading-snug text-[#003737]"
        >
          So Spira can remind you of your events even when the app is closed.
          Tap your picture to open notification settings.
        </p>
        <button
          type="button"
          onClick={() => onDismiss()}
          className="mt-4 py-1 text-sm font-semibold text-[#003737] underline decoration-1 underline-offset-[3px] transition-opacity hover:opacity-70"
        >
          Not now
        </button>
      </div>
    </div>,
    document.body,
  );
}
