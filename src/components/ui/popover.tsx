import * as React from "react";
import * as PopoverPrimitive from "@radix-ui/react-popover";

import { cn } from "@/lib/utils";

const Popover = PopoverPrimitive.Root;

const PopoverTrigger = PopoverPrimitive.Trigger;

const PopoverAnchor = PopoverPrimitive.Anchor;

const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(
  (
    {
      className,
      align = "center",
      sideOffset = 4,
      collisionPadding = 8,
      ...props
    },
    ref,
  ) => (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        ref={ref}
        align={align}
        sideOffset={sideOffset}
        // **Never taller than the room it has, and never against the screen edge.** Radix will
        // flip a popover above its trigger when there is no space below, but it will not shrink
        // one that does not fit either way — it simply hangs off the top. With the on-screen
        // keyboard up (the layout viewport is ~430 px then, see CLAUDE.md → Sheets → the height)
        // the deadline calendar ended up at y −136: its own head, the month arrows and the
        // weekday row were all above the screen, so the month could not be changed and the sheet
        // could not be closed. `--radix-popover-content-available-height` is Radix's measurement
        // of the space actually left, and it is only set because `avoidCollisions` is on.
        collisionPadding={collisionPadding}
        className={cn(
          "z-50 w-72 max-h-(--radix-popover-content-available-height) overflow-y-auto rounded-md border bg-popover p-4 text-popover-foreground shadow-md outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 origin-(--radix-popover-content-transform-origin)",
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  ),
);
PopoverContent.displayName = PopoverPrimitive.Content.displayName;

export { Popover, PopoverTrigger, PopoverContent, PopoverAnchor };
