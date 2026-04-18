import { Link, useRouterState } from "@tanstack/react-router";
import { Target as TargetIcon, CalendarDays, Sparkles } from "lucide-react";
import { useAi } from "@/components/ai/ai-store";
import { cn } from "@/lib/utils";

const items = [
  { to: "/", label: "Goals", icon: TargetIcon },
  { to: "/calendar", label: "Calendar", icon: CalendarDays },
] as const;

export function AppShell({ children }: { children: React.ReactNode }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const openAi = useAi((s) => s.open);

  return (
    <div className="min-h-screen flex flex-col">
      {/* Top bar */}
      <header className="sticky top-0 z-30 backdrop-blur bg-background/70 border-b hairline">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 h-14 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <div className="h-7 w-7 rounded-md bg-primary/15 border hairline-strong grid place-items-center">
              <span className="font-display text-primary text-lg leading-none">S</span>
            </div>
            <span className="font-display text-xl tracking-tight">Spira</span>
          </Link>

          {/* Desktop nav */}
          <nav className="hidden md:flex items-center gap-1">
            {items.map((it) => {
              const active = it.to === "/" ? path === "/" : path.startsWith(it.to);
              const Icon = it.icon;
              return (
                <Link
                  key={it.to}
                  to={it.to}
                  className={cn(
                    "px-3 py-1.5 rounded-md text-sm flex items-center gap-2 transition-colors",
                    active
                      ? "bg-accent text-foreground"
                      : "text-muted-foreground hover:text-foreground hover:bg-accent/60",
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {it.label}
                </Link>
              );
            })}
          </nav>

          <button
            onClick={() => openAi()}
            className="hidden md:inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-primary/10 border border-primary/30 text-primary text-sm hover:bg-primary/15 transition-colors"
          >
            <Sparkles className="h-4 w-4" />
            Assistant
          </button>
        </div>
      </header>

      <main className="flex-1 pb-24 md:pb-10">{children}</main>

      {/* Mobile bottom nav */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-30 border-t hairline bg-background/85 backdrop-blur pb-safe">
        <div className="grid grid-cols-3 max-w-md mx-auto">
          {items.map((it) => {
            const active = it.to === "/" ? path === "/" : path.startsWith(it.to);
            const Icon = it.icon;
            return (
              <Link
                key={it.to}
                to={it.to}
                className={cn(
                  "flex flex-col items-center gap-1 py-2.5 text-xs",
                  active ? "text-primary" : "text-muted-foreground",
                )}
              >
                <Icon className="h-5 w-5" />
                {it.label}
              </Link>
            );
          })}
          <button
            onClick={() => openAi()}
            className="flex flex-col items-center gap-1 py-2.5 text-xs text-muted-foreground"
          >
            <Sparkles className="h-5 w-5" />
            Assistant
          </button>
        </div>
      </nav>
    </div>
  );
}
