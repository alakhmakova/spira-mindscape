import type { ComponentType } from "react";
import { FileText, Paperclip, Mail } from "lucide-react";
import { LinkGlobe } from "@/components/spira/brand-icons";
import type { Resource } from "@/lib/spira/types";

/** Icon + label per resource type — shared by the Resources cards, the picker, and inline chips. */
export const resourceTypeMeta: Record<
  Resource["type"],
  // A plain component, not lucide's own type: the set is mixed now (the link glyph is an
  // Iconoir tracing in brand-icons.tsx), and every call site passes only a className.
  { icon: ComponentType<{ className?: string }>; label: string }
> = {
  note: { icon: FileText, label: "Note" },
  // The globe, not a chain: this marks a SAVED resource of kind link. A chain would say
  // "a link to somewhere", which is what the outbound affordances beside it already say.
  link: { icon: LinkGlobe, label: "Link" },
  file: { icon: Paperclip, label: "File" },
  email: { icon: Mail, label: "Email" },
};
