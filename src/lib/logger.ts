import { SpiraApiError } from "./spira/api";

/**
 * Client-side logging and error reporting.
 *
 * Until this existed, a JavaScript error in someone else's browser left no trace we could
 * see (BUG-005): the app wrote to that user's own devtools console and nothing more.
 *
 * In development everything goes to the console with full context — including
 * `SpiraApiError.details`, which the app computes and otherwise never shows anywhere.
 * In production, `reportError` posts a small, fixed-shape record to our own backend
 * (`POST /api/client-errors`), which logs it into Cloud Logging next to the server line
 * that shares its trace id. No third-party service, no user data leaving our infrastructure.
 *
 * Three rules matter more than the transport:
 *  - **Never send user content.** `details` and raw GraphQL messages can echo what someone
 *    typed, so they stay in the dev console; only classifications and status codes are sent.
 *  - **Never flood.** Identical errors are sent once, and a page session sends at most a
 *    handful — a render loop would otherwise fire hundreds of beacons.
 *  - **Never throw.** A failing reporter must not be able to break the app it reports on.
 */

const ENDPOINT = "/api/client-errors";

/** Mirrors the server's ClientErrorReport caps; over-long fields are rejected with 400. */
const MAX_MESSAGE = 300;
const MAX_STACK = 4000;
const MAX_URL = 300;
const MAX_NAME = 120;

/** A crash loop must not become a log flood. The server also rate-limits, at 10/min. */
const MAX_REPORTS_PER_SESSION = 5;

export type ClientErrorKind =
  | "render"
  | "window-error"
  | "unhandled-rejection"
  | "router"
  | "api"
  | "crash-trail";

export type ReportContext = {
  kind: ClientErrorKind;
  /** Appended to the stack; React's component stack goes here. */
  componentStack?: string;
  /** Server trace id, when the failure came from a backend call. */
  correlationId?: string;
};

const seen = new Set<string>();
let sent = 0;

/** Test seam: Vitest resets the per-session caps between cases. */
export function __resetLoggerState() {
  seen.clear();
  sent = 0;
}

function isDev(): boolean {
  return import.meta.env.DEV;
}

function truncate(value: string | undefined, max: number): string | undefined {
  if (!value) return undefined;
  return value.length > max ? value.slice(0, max) : value;
}

/** The path only — a query string can carry ids or search terms the user typed. */
function currentUrl(): string | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const { origin, pathname } = new URL(window.location.href);
    return truncate(`${origin}${pathname}`, MAX_URL);
  } catch {
    return undefined;
  }
}

function toError(value: unknown): Error {
  if (value instanceof Error) return value;
  if (typeof value === "string") return new Error(value);
  try {
    return new Error(JSON.stringify(value));
  } catch {
    return new Error("Unknown error");
  }
}

/**
 * What may be sent to the server. Deliberately narrow: no `details`, no GraphQL error
 * messages, no request payloads — only a classification and a status code, which describe
 * the failure without repeating anything the user wrote.
 */
function safeSummary(error: Error, context: ReportContext): string {
  if (!(error instanceof SpiraApiError)) return error.message;
  const parts = [`api ${error.kind}`];
  if (error.status) parts.push(`status=${error.status}`);
  const classification = error.errors?.[0]?.extensions?.classification;
  if (classification) parts.push(`classification=${classification}`);
  if (context.correlationId ?? error.correlationId) {
    parts.push(`ref=${context.correlationId ?? error.correlationId}`);
  }
  return parts.join(" ");
}

function buildReport(error: Error, context: ReportContext) {
  const stack = [error.stack, context.componentStack]
    .filter(Boolean)
    .join("\n");
  return {
    kind: context.kind,
    name: truncate(error.name, MAX_NAME),
    message:
      truncate(safeSummary(error, context), MAX_MESSAGE) || "Unknown error",
    stack: truncate(stack, MAX_STACK),
    url: currentUrl(),
  };
}

/**
 * Fire-and-forget. `sendBeacon` is used first because it is the only transport that
 * survives the page being unloaded or torn down mid-crash; `keepalive` fetch is the
 * fallback where it is unavailable or refuses the payload.
 */
function send(payload: unknown): void {
  const json = JSON.stringify(payload);

  // sendBeacon can return false (payload over the UA's quota) or throw outright (blocked
  // by an extension). Both mean "not sent", so both must fall through to fetch — catching
  // around the whole block would let a throw skip the fallback silently.
  try {
    if (
      typeof navigator !== "undefined" &&
      typeof navigator.sendBeacon === "function" &&
      navigator.sendBeacon(
        ENDPOINT,
        new Blob([json], { type: "application/json" }),
      )
    ) {
      return;
    }
  } catch {
    // fall through to fetch
  }

  try {
    void fetch(ENDPOINT, {
      method: "POST",
      keepalive: true,
      headers: { "content-type": "application/json" },
      body: json,
    }).catch(() => {
      // Reporting an error must never produce another one. Legitimately silent.
    });
  } catch {
    // Same: a transport that throws must not propagate into the caller's error path.
  }
}

/** True when the failure is expected and reporting it would only add noise. */
function isExpected(error: Error): boolean {
  if (!(error instanceof SpiraApiError)) return false;
  // 401 = the session expired; the store already redirects to /login.
  // "network" = the user is offline or the backend is down; not a defect in the app.
  return error.status === 401 || error.kind === "network";
}

export const logger = {
  debug(message: string, context?: unknown) {
    if (isDev()) console.debug(`[spira] ${message}`, context ?? "");
  },

  info(message: string, context?: unknown) {
    if (isDev()) console.info(`[spira] ${message}`, context ?? "");
  },

  warn(message: string, context?: unknown) {
    // Warnings stay local by design: they are things worth seeing while developing,
    // not incidents worth a server round-trip.
    if (isDev()) console.warn(`[spira] ${message}`, context ?? "");
  },

  /**
   * Records an error and, in production, reports it to the backend. Safe to call from a
   * `catch`, an event handler, or an error boundary — it never throws and never awaits.
   */
  reportError(value: unknown, context: ReportContext) {
    const error = toError(value);

    if (isDev()) {
      // The dev console is the one place `details` may appear: it holds the backend's raw
      // GraphQL messages, which are invaluable when debugging and unsendable in production.
      console.error(`[spira] ${context.kind}`, error, {
        ...context,
        details: error instanceof SpiraApiError ? error.details : undefined,
      });
      return;
    }

    if (isExpected(error)) return;

    const fingerprint = `${context.kind}|${error.name}|${error.message}|${
      error.stack?.split("\n")[1] ?? ""
    }`;
    if (seen.has(fingerprint) || sent >= MAX_REPORTS_PER_SESSION) return;
    seen.add(fingerprint);
    sent += 1;

    send(buildReport(error, context));
  },
};
