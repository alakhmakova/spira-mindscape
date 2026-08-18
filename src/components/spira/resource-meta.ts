import type { ComponentType } from "react";
import { FileText, Paperclip, Mail } from "@/components/spira/icons";
import { Link } from "@/components/spira/icons";
import type { Resource } from "@/lib/spira/types";

/** Icon + label per resource type — shared by the Resources cards, the picker, and inline chips. */
export const resourceTypeMeta: Record<
  Resource["type"],
  // A plain component, not a third-party icon type: the set is Gravity UI now (see
  // components/spira/icons.tsx), and every call site passes only a className.
  { icon: ComponentType<{ className?: string }>; label: string }
> = {
  note: { icon: FileText, label: "Note" },
  // The link (chain) glyph, not a globe — the owner's chosen mark for a saved link resource.
  link: { icon: Link, label: "Link" },
  file: { icon: Paperclip, label: "File" },
  email: { icon: Mail, label: "Email" },
};
