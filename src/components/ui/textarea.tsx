import * as React from "react";

import { cn } from "@/lib/utils";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    const localRef = React.useRef<HTMLTextAreaElement | null>(null);
    const resize = React.useCallback(() => {
      const el = localRef.current;
      if (!el) return;
      el.style.height = "auto";
      el.style.height = `${el.scrollHeight}px`;
    }, []);

    React.useEffect(() => {
      resize();
    }, [props.value, resize]);

    return (
      <textarea
        className={cn(
          "flex min-h-[88px] w-full resize-none overflow-hidden rounded-md border border-input bg-surface px-3.5 py-2.5 text-base text-foreground shadow-none placeholder:text-muted-foreground/75 focus-visible:border-primary focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        ref={(node) => {
          localRef.current = node;
          if (typeof ref === "function") ref(node);
          else if (ref) ref.current = node;
        }}
        {...props}
        onInput={(event) => {
          props.onInput?.(event);
          resize();
        }}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
