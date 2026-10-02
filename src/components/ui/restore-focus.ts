import * as React from "react";

/**
 * Give focus back to whatever opened the overlay, when it closes (WCAG 2.4.3, BUG-023).
 *
 * **Radix only restores focus to a `Trigger`, and nothing in this app uses one.** Every sheet and
 * dialog here is controlled by state and opened from an ordinary button — the dashboard's round +,
 * a row's ⋯ item, a card's X — so Radix's own handler runs
 * `event.preventDefault(); context.triggerRef.current?.focus()` against a ref that is `null`, the
 * default restore never happens, and focus lands on `<body>`. The next Tab then starts again from
 * the top of the page, which is the whole of the defect: a keyboard user who opens the New goal
 * sheet, changes their mind and presses Escape is thrown back to the beginning.
 *
 * `e2e/a11y-keyboard.spec.ts` pins it, and it was verified red against this file being absent.
 *
 * Spread the result onto the Radix `Content`:
 *
 * ```tsx
 * const restore = useFocusRestore(props.onOpenAutoFocus, props.onCloseAutoFocus);
 * <DialogPrimitive.Content {...props} {...restore} />
 * ```
 */
export function useFocusRestore(
  onOpenAutoFocus?: (event: Event) => void,
  onCloseAutoFocus?: (event: Event) => void,
) {
  const opener = React.useRef<HTMLElement | null>(null);

  return {
    /**
     * Fires as focus is about to move **into** the overlay, so `document.activeElement` is still
     * the element that opened it. That is the only moment this is readable from the outside —
     * by the time the content has mounted, focus is already inside.
     */
    onOpenAutoFocus: (event: Event) => {
      opener.current = document.activeElement as HTMLElement | null;
      onOpenAutoFocus?.(event);
    },
    onCloseAutoFocus: (event: Event) => {
      onCloseAutoFocus?.(event);
      if (event.defaultPrevented) return;
      const back = opener.current;
      // A control that has since gone — the empty state's "Create your first goal", which the
      // created goal replaces — is not somewhere to send focus. Leaving the event alone then
      // hands the decision back to Radix rather than focusing nothing on purpose.
      if (!back || !document.contains(back)) return;
      event.preventDefault();
      back.focus();
    },
  };
}
