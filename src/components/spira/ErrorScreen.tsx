import { AlertTriangle } from "lucide-react";

type ErrorScreenProps = {
  /** Server trace id, when the failure came from a backend call the user can quote. */
  reference?: string;
  /** Shown only in development — in production the error is reported, not displayed. */
  detail?: string;
};

/**
 * The one "something went wrong" surface, shared by the router's error component and the
 * top-level {@link import("../ErrorBoundary").ErrorBoundary}.
 *
 * The raw error message is deliberately development-only. It used to be rendered for every
 * user, which leaks internals and helps nobody; now that errors report themselves to the
 * backend, a quotable reference is the useful thing to show instead.
 */
export function ErrorScreen({ reference, detail }: ErrorScreenProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="max-w-md text-center">
        <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10">
          <AlertTriangle
            className="h-8 w-8 text-destructive"
            aria-hidden="true"
          />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Something went wrong
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          A page error occurred. Reloading usually fixes it.
        </p>
        {reference && (
          <p className="mt-2 font-mono text-xs text-muted-foreground">
            Reference: {reference}
          </p>
        )}
        {import.meta.env.DEV && detail && (
          <pre className="mt-4 max-h-40 overflow-auto rounded-md bg-muted p-3 text-left font-mono text-xs text-destructive">
            {detail}
          </pre>
        )}
        <div className="mt-6 flex items-center justify-center gap-3">
          <button
            onClick={() => window.location.reload()}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            Reload page
          </button>
          <a
            href="/"
            className="inline-flex items-center justify-center rounded-md border border-input bg-background px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent"
          >
            Home
          </a>
        </div>
      </div>
    </div>
  );
}
