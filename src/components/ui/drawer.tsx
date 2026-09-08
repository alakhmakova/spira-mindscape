import * as React from "react";
import { Drawer as DrawerPrimitive } from "vaul";

import { cn } from "@/lib/utils";

/**
 * **`repositionInputs` is off, and that is the whole keyboard fix** (BUG-060, 2026-08-29).
 *
 * vaul turns it on by default. What it does is listen for `visualViewport` resizes and, while
 * something typeable is focused, write **inline `height` and `bottom`** onto the drawer element
 * (`onVisualViewportChange` in `vaul/dist/index.js`). An inline style beats every class, so from
 * the first keystroke onwards the sheet is no longer sized by `.sheet-h` / `.sheet-max` at all
 * — which is exactly why four rounds of CSS fixes changed nothing, and why only the sheets with
 * a field to type in ever misbehaved.
 *
 * Its arithmetic is wrong here, measured rather than argued (412x780 phone, composer focused,
 * viewport shrunk to 300 and back — `e2e/ai-drawer-height.spec.ts`):
 *
 * | viewport | what vaul wrote | drawer |
 * |---|---|---|
 * | 780 | nothing | 718 (92 %) |
 * | 300 (keyboard up) | `height: 300px` | 300 |
 * | **780 (keyboard gone)** | **`height: 300px`** | **300 — 38 % of the screen** |
 *
 * The pixel value is `initialDrawerHeight`, captured on the FIRST resize the handler sees, and
 * then reapplied forever. That is the owner's "меньше половины экрана", the jumping height, and
 * — on a sheet whose content no longer fills the box it was pinned to — the band of the drawer's
 * own background showing below the content.
 *
 * The reason we can simply switch it off is that the flag exists for browsers where the keyboard
 * does NOT resize the layout viewport. `index.html` asks for `interactive-widget=resizes-content`,
 * so Chrome does resize it, and the sheet sits above the keyboard natively — vaul's compensation
 * is a second hand on the same wheel. Turning it off also disables vaul's iOS-Safari scroll
 * workaround (`usePreventScroll` reads the same flag); everything else it guards is iOS-only, so
 * on Android and the desktop nothing but the height writer goes away. iOS Safari has no
 * `interactive-widget` support, so a sheet's composer there will sit behind the keyboard — see
 * `backlog/ios-safari-keyboard-covers-the-sheet-composer.md`.
 *
 * A caller can still pass `repositionInputs` to override this; none does.
 */
const Drawer = ({
  shouldScaleBackground = true,
  ...props
}: React.ComponentProps<typeof DrawerPrimitive.Root>) => (
  <DrawerPrimitive.Root
    shouldScaleBackground={shouldScaleBackground}
    repositionInputs={false}
    {...props}
  />
);
Drawer.displayName = "Drawer";

const DrawerTrigger = DrawerPrimitive.Trigger;

const DrawerPortal = DrawerPrimitive.Portal;

const DrawerClose = DrawerPrimitive.Close;

const DrawerOverlay = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Overlay
    ref={ref}
    // `bg-foreground/80` (the app's near-black ink token), never raw `bg-black` — pure black is
    // not in the palette (CLAUDE.md → Colour). On a sheet whose top edge sits below the viewport
    // top (`.sheet-h`/`.sheet-max`, per the sheet spec), the gap above it renders this overlay
    // directly, so an off-palette black there is not a subtle miss (BUG-071: the AI Coach drawer
    // on mobile). Shared with `sheet.tsx` / `dialog.tsx` / `alert-dialog.tsx` — fix the token in
    // one place if it ever needs to change again.
    className={cn("fixed inset-0 z-50 bg-foreground/80", className)}
    {...props}
  />
));
DrawerOverlay.displayName = DrawerPrimitive.Overlay.displayName;

const DrawerContent = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Content>
>(({ className, children, ...props }, ref) => (
  <DrawerPortal>
    <DrawerOverlay />
    <DrawerPrimitive.Content
      ref={ref}
      className={cn(
        // **No grab handle, and `overflow-hidden`** (owner, 2026-08-22). A sheet on the phone
        // opens with its own head at the very top, exactly as the Android `ModalBottomSheet`
        // does; vaul's default handle put a white strip with a pill above the teal head, so the
        // web sheet read as two stacked bars where Android has one. Dragging the sheet down
        // still works — the head itself is the drag surface. `overflow-clip` is what lets the
        // head take the rounded top corners instead of poking square ones through them. The
        // corner is `xl` (12px) because Android's sheet is `SpiraRadii.lg` (12dp) - both are the
        // base radius + 4, so the same sheet is the same shape on the phone and the laptop. The
        // hairline border went with it: over the dimmed page it drew a pale outline round the
        // teal head that Android's sheet has not got, and half the drawers already cancelled it.
        // No `h-auto` here. It is the CSS default, so it bought nothing — and it put every
        // caller that sets its own height into a fight with the base class inside one
        // `cn()` string. tailwind-merge resolves that correctly today; not relying on it
        // costs nothing.
        // **`overflow-clip`, never `overflow-hidden`** (2026-08-29). They clip identically, but
        // `hidden` still makes the box a **scroll container** — it just hides the scrollbars —
        // so anything that scrolls programmatically can move the whole sheet inside its own
        // frame. Something does: opening the deadline calendar in New goal calls
        // `scrollIntoView` on the field, which walks up and scrolls **every** scrollable
        // ancestor, and the sheet ended up at `scrollTop: 450` with its teal head at y −292 —
        // off the top of its own box, leaving a stub of a form with a floating calendar over it.
        // The browser's own "scroll the focused element into view" reaches the same box. `clip`
        // is not a scroll container at all, so none of them can move it. The body's
        // `overflow-y-auto` is the one scroller a sheet has, by design (CLAUDE.md → Sheets).
        "fixed inset-x-0 bottom-0 z-50 mt-24 flex flex-col overflow-clip rounded-t-xl bg-background",
        className,
      )}
      {...props}
    >
      {children}
    </DrawerPrimitive.Content>
  </DrawerPortal>
));
DrawerContent.displayName = "DrawerContent";

const DrawerHeader = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn("grid gap-1.5 p-4 text-center sm:text-left", className)}
    {...props}
  />
);
DrawerHeader.displayName = "DrawerHeader";

const DrawerFooter = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn("mt-auto flex flex-col gap-2 p-4", className)}
    {...props}
  />
);
DrawerFooter.displayName = "DrawerFooter";

const DrawerTitle = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Title
    ref={ref}
    className={cn(
      "text-lg font-semibold leading-none tracking-tight",
      className,
    )}
    {...props}
  />
));
DrawerTitle.displayName = DrawerPrimitive.Title.displayName;

const DrawerDescription = React.forwardRef<
  React.ElementRef<typeof DrawerPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DrawerPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DrawerPrimitive.Description
    ref={ref}
    className={cn("text-sm text-muted-foreground", className)}
    {...props}
  />
));
DrawerDescription.displayName = DrawerPrimitive.Description.displayName;

export {
  Drawer,
  DrawerPortal,
  DrawerOverlay,
  DrawerTrigger,
  DrawerClose,
  DrawerContent,
  DrawerHeader,
  DrawerFooter,
  DrawerTitle,
  DrawerDescription,
};
