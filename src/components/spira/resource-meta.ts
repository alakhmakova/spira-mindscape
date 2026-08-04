import { FileText, LinkIcon, Paperclip, Mail } from "lucide-react";
import type { Resource } from "@/lib/spira/types";

/** Icon + label per resource type — shared by the Resources cards, the picker, and inline chips. */
export const resourceTypeMeta: Record<
  Resource["type"],
  { icon: typeof FileText; label: string }
> = {
  note: { icon: FileText, label: "Note" },
  link: { icon: LinkIcon, label: "Link" },
  file: { icon: Paperclip, label: "File" },
  email: { icon: Mail, label: "Email" },
};
