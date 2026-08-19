import { Toaster as Sonner } from "sonner";
import {
  CircleCheckFill,
  CircleExclamationFilled,
  Info,
  TriangleAlert,
} from "@/components/spira/icons";

type ToasterProps = React.ComponentProps<typeof Sonner>;

/**
 * Spira's toast — the owner's reference (2026-08-17).
 *
 * One shape for every message; only the mark and its colour change with what happened:
 *
 *  - a **white card**, a hairline border, a 10px radius and a soft shadow — never a coloured
 *    block. A tinted card shouts, and the mark already says which kind it is;
 *  - a **filled semantic glyph** on the left, in the ramp's solid step: teal-green
 *    `circle-check-fill` for success, red `circle-exclamation-fill` for an error, amber
 *    `triangle-exclamation` for a warning, blue `circle-info` for a note;
 *  - **near-black text** in every kind (the mark carries the meaning, not the type — the same rule
 *    the pills follow), wrapping to as many lines as it needs;
 *  - an **X on the right** to dismiss, so a long message is never in the way.
 *
 * The colours are set as CSS variables per kind and read by the classes below, because sonner
 * renders its own DOM: the icon slot is the only place the kind can be expressed, and it must not
 * leak into the card or the type.
 */
const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      className="toaster group"
      icons={{
        success: <CircleCheckFill className="h-5 w-5 text-[#007A4B]" />,
        error: <CircleExclamationFilled className="h-5 w-5 text-[#C53336]" />,
        warning: <TriangleAlert className="h-5 w-5 text-[#896500]" />,
        info: <Info className="h-5 w-5 text-[#006CC1]" />,
        loading: <Info className="h-5 w-5 animate-pulse text-[#0A8080]" />,
      }}
      closeButton
      toastOptions={{
        classNames: {
          toast: [
            "group toast items-start gap-3 rounded-[10px] border p-4",
            "group-[.toaster]:border-border group-[.toaster]:bg-surface",
            "group-[.toaster]:text-foreground group-[.toaster]:shadow-lg",
          ].join(" "),
          // The glyph sits on the first line of the text, not centred against a two-line message.
          icon: "mt-px shrink-0",
          title: "text-[14px] font-medium leading-[1.5]",
          description:
            "group-[.toast]:text-[13px] group-[.toast]:text-muted-foreground",
          // sonner's default close button is a small circle pinned to the corner; this is the
          // reference's plain X sitting in the row.
          closeButton: [
            "group-[.toast]:right-3 group-[.toast]:left-auto group-[.toast]:top-3",
            "group-[.toast]:h-6 group-[.toast]:w-6 group-[.toast]:rounded-md",
            "group-[.toast]:border-transparent group-[.toast]:bg-transparent",
            "group-[.toast]:text-muted-foreground hover:group-[.toast]:bg-secondary",
            "hover:group-[.toast]:text-foreground",
          ].join(" "),
          actionButton:
            "group-[.toast]:bg-primary group-[.toast]:text-primary-foreground",
          cancelButton:
            "group-[.toast]:bg-muted group-[.toast]:text-muted-foreground",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
