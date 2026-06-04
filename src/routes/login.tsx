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

// ─── Icons ──────────────────────────────────────────────────────────────────

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="g-icon">
      <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
      <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
      <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
      <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
    </svg>
  );
}

function CheckMark() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true" className="feat-check">
      <path d="M5 10.5l3.2 3.2L15 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/* Target-type glyphs — Lucide icons (lucide.dev) */
function TargetIcon({ kind }: { kind: "numeric" | "checklist" | "binary" }) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    className: "tcard-glyph",
  };
  if (kind === "numeric") {
    return (<svg {...common}><path d="M16 7h6v6" /><path d="m22 7-8.5 8.5-5-5L2 17" /></svg>);
  }
  if (kind === "checklist") {
    return (<svg {...common}><path d="m3 17 2 2 4-4" /><path d="m3 7 2 2 4-4" /><path d="M13 6h8" /><path d="M13 12h8" /><path d="M13 18h8" /></svg>);
  }
  return (<svg {...common}><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" /><line x1="4" x2="4" y1="22" y2="15" /></svg>);
}

type TargetCard = {
  kind: "numeric" | "checklist" | "binary";
  title: string;
  value?: string;
  pct?: number;
  done?: boolean;
};

const TARGETS: TargetCard[] = [
  { kind: "numeric", title: "Save $25k for a car", value: "$18.2k", pct: 73 },
  { kind: "checklist", title: "Go on vacation", value: "4 / 7", pct: 57 },
  { kind: "binary", title: "Get a job offer", done: true },
];

function TargetCards() {
  return (
    <div className="tcards" aria-hidden="true">
      {TARGETS.map((t, i) => (
        <div key={t.kind} className={`tcard tcard--${i}`}>
          <span className="tcard-icon"><TargetIcon kind={t.kind} /></span>
          <div className="tcard-main">
            <div className="tcard-top">
              <span className="tcard-title">{t.title}</span>
              {t.done
                ? <span className="tcard-done"><CheckMark />Done</span>
                : <span className="tcard-value">{t.value}</span>}
            </div>
            <div className="tcard-bar"><span style={{ width: t.done ? "100%" : `${t.pct}%` }} /></div>
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Panels ───────────────────────────────────────────────────────────────────

function BrandPanel() {
  return (
    <section className="brand-panel">
      <div className="brand-wordmark">spira</div>

      <div className="brand-body">
        <div className="brand-copy">
          <h1 className="brand-headline">The app that shows you the right direction to GROW</h1>
          <p className="brand-sub">Three kinds of targets. One clear path forward.</p>
        </div>

        <TargetCards />
      </div>

      <div className="brand-footer">© 2026 Spira</div>
    </section>
  );
}

function SignInPanel() {
  return (
    <section className="signin-panel">
      <div className="signin-block">
        <div className="signin-wordmark">spira</div>

        <div className="signin-head">
          <h2 className="signin-title">Sign in to Spira</h2>
          <p className="signin-sub">Use your Google account to continue.</p>
        </div>

        {/* Real OAuth2 flow — navigates to the Spring Security authorization endpoint. */}
        <a
          href="/oauth2/authorization/google"
          className="g-button g-button--outline"
          aria-label="Continue with Google"
        >
          <GoogleIcon />
          <span>Continue with Google</span>
        </a>

        <LoginError />

        <p className="signin-legal">
          By continuing, you agree to Spira's <a href="#">Terms</a> and <a href="#">Privacy Policy</a>.
        </p>
      </div>
    </section>
  );
}

// ─── Login page ───────────────────────────────────────────────────────────────

function LoginPage() {
  return (
    <>
      <LoginStyles />
      {/* Chosen design: brand panel on the right, centered text, serif headings. */}
      <div className="login-root brand-right" data-heading="serif" data-align="center">
        <BrandPanel />
        <SignInPanel />
      </div>
    </>
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
    <p className="signin-error" role="alert">
      Sign-in failed. Please try again or contact support if the issue persists.
    </p>
  );
}

// ─── Scoped styles (ported from the design; tokens scoped to .login-root) ──────

function LoginStyles() {
  return (
    <style>{`
.login-root {
  /* Design tokens — scoped so the login renders exactly as designed and never
     overrides the rest of the app. */
  --lp-primary: oklch(0.51 0.092 194);
  --lp-primary-deep: oklch(0.45 0.09 194);
  --lp-primary-fg: oklch(0.99 0 0);
  --lp-foreground: oklch(0.321 0 0);
  --lp-muted-fg: oklch(0.51 0 0);
  --lp-bg: oklch(0.982 0 0);
  --lp-surface: oklch(1 0 0);
  --lp-border-strong: oklch(0.80 0 0);
  --lp-ring: oklch(0.51 0.092 194 / 0.45);
  --lp-radius: 10px;
  --lp-font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  --lp-font-heading: "Playfair Display", Georgia, serif;
  --lp-shadow-soft: 0 1px 2px 0 oklch(0.2 0 0 / 0.05);
  --lp-shadow-raised: 0 1px 2px 0 oklch(0.2 0 0 / 0.05), 0 12px 32px -16px oklch(0.2 0 0 / 0.18);

  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
  font-family: var(--lp-font-sans);
  color: var(--lp-foreground);
  background: var(--lp-bg);
  -webkit-font-smoothing: antialiased;
}
.login-root.brand-right .brand-panel { order: 2; }
.login-root.brand-right .signin-panel { order: 1; }

/* ── Brand (teal) panel ── */
.login-root .brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--lp-primary);
  color: var(--lp-primary-fg);
  padding: clamp(40px, 4.4vw, 72px);
  overflow: hidden;
}
.login-root .brand-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(120% 90% at 12% 8%, oklch(1 0 0 / 0.06), transparent 60%);
  pointer-events: none;
}
.login-root .brand-panel > * { position: relative; z-index: 1; }
.login-root .brand-wordmark {
  font-weight: 800;
  font-size: clamp(28px, 2.2vw, 34px);
  letter-spacing: -0.01em;
  line-height: 1;
  margin-bottom: 12px;
}
.login-root .brand-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 28px;
  max-width: 440px;
}
.login-root .brand-body > * { flex-shrink: 0; }
.login-root .brand-copy { display: flex; flex-direction: column; gap: 16px; }
.login-root .brand-headline {
  margin: 0;
  font-family: var(--lp-font-heading);
  font-weight: 700;
  font-size: clamp(28px, 2.5vw, 42px);
  line-height: 1.1;
  letter-spacing: -0.01em;
  color: var(--lp-primary-fg);
}
.login-root .brand-sub {
  margin: 0;
  font-size: clamp(15px, 1.1vw, 17px);
  line-height: 1.6;
  color: oklch(0.99 0 0 / 0.72);
}
.login-root[data-heading="sans"] .brand-headline,
.login-root[data-heading="sans"] .signin-title {
  font-family: var(--lp-font-sans);
  font-weight: 800;
  letter-spacing: -0.02em;
}

/* ── Feature lines (unused with cards, kept for completeness) ── */
.login-root .features { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
.login-root .features li { display: flex; align-items: center; gap: 12px; font-size: 15px; color: oklch(0.99 0 0 / 0.82); }
.login-root .feat-dot {
  flex-shrink: 0; width: 24px; height: 24px; border-radius: 999px;
  background: oklch(1 0 0 / 0.14); border: 1px solid oklch(1 0 0 / 0.22);
  display: grid; place-items: center; color: var(--lp-primary-fg);
}
.login-root .feat-check { width: 14px; height: 14px; }

/* ── Target-type cards (staggered, white on teal) ── */
.login-root .tcards { display: flex; flex-direction: column; gap: 30px; width: 100%; max-width: 360px; }
.login-root .tcard {
  display: flex; align-items: center; gap: 13px;
  background: var(--lp-surface); border-radius: 14px; padding: 12px 15px;
  box-shadow: 0 10px 30px -12px oklch(0.2 0.02 200 / 0.45), 0 2px 6px -2px oklch(0.2 0 0 / 0.12);
  color: var(--lp-foreground);
  transition: transform .2s ease, box-shadow .2s ease;
  transform-origin: center;
}
.login-root .tcard--0 { transform: translateX(8px) rotate(4deg); }
.login-root .tcard--1 { transform: translateX(-18px) rotate(0deg); }
.login-root .tcard--2 { transform: translateX(22px) rotate(0deg); }
.login-root .tcard:hover { transform: translateX(0) rotate(0deg); box-shadow: 0 16px 40px -14px oklch(0.2 0.02 200 / 0.5); }
.login-root .tcard-icon {
  flex-shrink: 0; width: 38px; height: 38px; border-radius: 10px;
  background: #f45d48; color: #fff; display: grid; place-items: center;
}
.login-root .tcard-glyph { width: 20px; height: 20px; }
.login-root .tcard-main { flex: 1; min-width: 0; }
.login-root .tcard-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 9px; }
.login-root .tcard-value { font-size: 13px; font-weight: 600; color: var(--lp-foreground); font-variant-numeric: tabular-nums; flex-shrink: 0; }
.login-root .tcard-done { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; color: var(--lp-primary); flex-shrink: 0; }
.login-root .tcard-done .feat-check { width: 14px; height: 14px; }
.login-root .tcard-title { flex: 1; min-width: 0; font-size: 14px; font-weight: 600; color: var(--lp-foreground); text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.login-root .tcard-bar { height: 5px; border-radius: 999px; background: oklch(0.90 0.01 200); overflow: hidden; }
.login-root .tcard-bar > span { display: block; height: 100%; border-radius: 999px; background: var(--lp-primary); }

/* ── Centered alignment ── */
.login-root[data-align="center"] .brand-panel,
.login-root[data-align="center"] .signin-block { text-align: center; }
.login-root[data-align="center"] .brand-body { margin-inline: auto; }
.login-root[data-align="center"] .tcards { margin-inline: auto; }
.login-root[data-align="center"] .brand-wordmark { text-align: left; }
.login-root .brand-footer { font-size: 12px; color: oklch(1 0 0 / 0.38); }

/* ── Sign-in (white) panel ── */
.login-root .signin-panel {
  display: flex; align-items: center; justify-content: center;
  padding: clamp(32px, 4vw, 64px); background: var(--lp-bg);
}
.login-root .signin-block { width: 100%; max-width: 360px; display: flex; flex-direction: column; gap: 28px; }
.login-root .signin-wordmark { display: none; font-weight: 800; font-size: 28px; color: var(--lp-primary); letter-spacing: -0.01em; }
.login-root .signin-head { display: flex; flex-direction: column; gap: 8px; }
.login-root .signin-title {
  margin: 0; font-family: var(--lp-font-heading); font-weight: 700;
  font-size: clamp(26px, 2.2vw, 30px); line-height: 1.1; letter-spacing: -0.005em; color: var(--lp-foreground);
}
.login-root .signin-sub { margin: 0; font-size: 15px; color: var(--lp-muted-fg); }

/* ── Google button ── */
.login-root .g-button {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  width: 100%; height: 52px; border-radius: var(--lp-radius);
  font-size: 15px; font-weight: 600; text-decoration: none; cursor: pointer;
  transition: background .16s ease, border-color .16s ease, box-shadow .16s ease, transform .04s ease;
}
.login-root .g-button:active { transform: translateY(0.5px); }
.login-root .g-button:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--lp-ring); }
.login-root .g-icon { width: 20px; height: 20px; flex-shrink: 0; }
.login-root .g-button--outline {
  background-color: var(--lp-surface); border: 1px solid var(--lp-border-strong);
  color: var(--lp-foreground); box-shadow: var(--lp-shadow-soft);
}
.login-root .g-button--outline:hover { background-color: oklch(0.97 0 0); border-color: var(--lp-muted-fg); box-shadow: var(--lp-shadow-raised); }
.login-root .g-button--solid {
  background-color: var(--lp-primary); border: 1px solid var(--lp-primary);
  color: var(--lp-primary-fg); box-shadow: var(--lp-shadow-raised);
}
.login-root .g-button--solid:hover { background-color: var(--lp-primary-deep); border-color: var(--lp-primary-deep); }

.login-root .signin-legal { margin: 0; font-size: 12.5px; line-height: 1.55; color: var(--lp-muted-fg); }
.login-root .signin-legal a { color: var(--lp-foreground); text-decoration: underline; text-underline-offset: 2px; text-decoration-color: var(--lp-border-strong); }
.login-root .signin-legal a:hover { text-decoration-color: var(--lp-foreground); }
.login-root .signin-error { margin: 0; font-size: 13px; color: oklch(0.58 0.18 25); text-align: center; }

/* ── Responsive: collapse to single column ── */
@media (max-width: 820px) {
  .login-root, .login-root.brand-right { grid-template-columns: 1fr; }
  .login-root .brand-panel { display: none; }
  .login-root.brand-right .signin-panel { order: 0; }
  .login-root .signin-panel { min-height: 100vh; position: relative; }
  /* Logo pinned to the top-left of the page; the form stays centered below it. */
  .login-root .signin-wordmark {
    display: block;
    position: absolute;
    top: clamp(20px, 5vw, 28px);
    left: clamp(20px, 5vw, 28px);
    margin: 0;
    text-align: left;
  }
}
`}</style>
  );
}
