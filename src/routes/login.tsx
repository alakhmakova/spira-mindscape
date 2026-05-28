import { createFileRoute, redirect } from "@tanstack/react-router";
import { useAuth } from "@/lib/spira/auth";

// ─── Route definition ─────────────────────────────────────────────────────────

export const Route = createFileRoute("/login")({
  /**
   * If the user is already authenticated (e.g. they bookmarked /login but
   * their session is still valid), send them home immediately.
   */
  beforeLoad: async () => {
    const auth = useAuth.getState();
    // fetchMe may not have run yet if the user navigated directly to /login;
    // in that case auth.status is still "loading" from the root guard skip.
    if (auth.status === "loading") {
      await auth.fetchMe();
    }
    if (useAuth.getState().status === "authed") {
      throw redirect({ to: "/" });
    }
  },
  component: LoginPage,
});

// ─── Google icon ──────────────────────────────────────────────────────────────

function GoogleIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      aria-hidden="true"
      className="h-5 w-5 shrink-0"
    >
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
      />
    </svg>
  );
}

// ─── Login page ───────────────────────────────────────────────────────────────

function LoginPage() {
  return (
    <div className="flex min-h-screen">

      {/* ── Left panel — brand teal ───────────────────────────────────────── */}
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between bg-primary p-12 text-white">
        {/* Wordmark */}
        <div>
          <span className="text-4xl font-extrabold tracking-normal leading-none">
            spira
          </span>
        </div>

        {/* Headline + preview card */}
        <div className="space-y-6">
          <div className="space-y-3">
            <h1 className="text-4xl font-bold leading-tight">
              Plan goals you can<br />actually measure
            </h1>
            <p className="text-white/70 text-base leading-relaxed">
              Structure your ambitions with the GROW framework.<br />
              Track progress. Stay confident.
            </p>
          </div>

          {/* Mini product card */}
          <div className="rounded-xl border border-white/20 bg-white/10 p-5 max-w-xs backdrop-blur-sm">
            <div className="text-[10px] font-semibold uppercase tracking-widest text-white/50 mb-2">
              Goal
            </div>
            <div className="font-semibold text-white text-sm mb-3">
              Launch product by Q4
            </div>
            <div className="flex items-center gap-3">
              <div className="h-1.5 flex-1 rounded-full bg-white/20 overflow-hidden">
                <div className="h-full w-3/4 rounded-full bg-white/70" />
              </div>
              <span className="text-xs text-white/60 tabular-nums">75%</span>
            </div>
            <div className="mt-3 flex items-center gap-2">
              <div className="h-5 w-5 rounded-full bg-white/30 text-[9px] font-bold grid place-items-center text-white">
                T
              </div>
              <span className="text-xs text-white/50">3 targets tracked</span>
            </div>
          </div>
        </div>

        <div className="text-xs text-white/30">© 2026 Spira</div>
      </div>

      {/* ── Right panel — sign-in form ────────────────────────────────────── */}
      <div className="flex w-full lg:w-1/2 flex-col items-center justify-center p-8">
        <div className="w-full max-w-sm space-y-8">

          {/* Mobile wordmark (hidden on desktop — left panel shows it) */}
          <div className="lg:hidden">
            <span className="text-3xl font-extrabold text-primary leading-none">
              spira
            </span>
          </div>

          {/* Heading */}
          <div>
            <h2 className="text-2xl font-bold text-foreground">
              Sign in to Spira
            </h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Use your Google account to continue
            </p>
          </div>

          {/* Google sign-in button — navigates to backend OAuth2 flow */}
          <a
            href="/oauth2/authorization/google"
            className="flex w-full items-center justify-center gap-3 rounded-md border border-input bg-background px-4 py-3 text-sm font-medium text-foreground shadow-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="Continue with Google"
          >
            <GoogleIcon />
            <span>Continue with Google</span>
          </a>

          {/* Error hint from backend redirect */}
          <LoginError />
        </div>
      </div>
    </div>
  );
}

/**
 * Show a subtle error message when the backend redirects back to
 * `/login?error` (e.g. the user denied Google access).
 */
function LoginError() {
  const search = typeof window !== "undefined" ? window.location.search : "";
  if (!search.includes("error")) return null;

  return (
    <p className="text-sm text-destructive" role="alert">
      Sign-in failed. Please try again or contact support if the issue
      persists.
    </p>
  );
}
