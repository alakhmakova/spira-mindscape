import { create } from "zustand";

// ─── Types ────────────────────────────────────────────────────────────────────

export type AuthUser = {
  id: number;
  email: string;
  name: string;
  pictureUrl: string | null;
};

export type AuthStatus = "loading" | "authed" | "anonymous";

type AuthStore = {
  user: AuthUser | null;
  status: AuthStatus;
  fetchMe: () => Promise<void>;
  logout: () => Promise<void>;
  /** Mark auth as anonymous (used e.g. after a 401 from the API). */
  setAnonymous: () => void;
};

// ─── CSRF helper ──────────────────────────────────────────────────────────────

/**
 * Read the XSRF-TOKEN cookie that Spring Security writes.
 * The cookie is NOT HttpOnly (by design) so the SPA can read and echo it on
 * every mutating request as the X-XSRF-TOKEN header.
 *
 * Returns an empty string in non-browser environments (SSR, Vitest Node runner)
 * where `document` is not available.
 */
export function getCsrfToken(): string {
  if (typeof document === "undefined") return "";
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : "";
}

// ─── Auth store ───────────────────────────────────────────────────────────────

export const useAuth = create<AuthStore>((set) => ({
  user: null,
  status: "loading",

  setAnonymous() {
    set({ user: null, status: "anonymous" });
  },

  /**
   * Probe the backend for the current session user.
   * - 200 → authenticated; store the user DTO.
   * - 401 → session missing/expired; set anonymous.
   * - Any error → treat as anonymous (backend may be unreachable at startup).
   */
  async fetchMe() {
    try {
      const res = await fetch("/api/auth/me", { credentials: "include" });
      if (res.status === 401) {
        set({ user: null, status: "anonymous" });
        return;
      }
      if (!res.ok) {
        set({ user: null, status: "anonymous" });
        return;
      }
      const user = (await res.json()) as AuthUser;
      set({ user, status: "authed" });
    } catch {
      // Network error at startup — show login so user can retry
      set({ user: null, status: "anonymous" });
    }
  },

  /**
   * POST /api/auth/logout (Spring Security invalidates the session and removes
   * the cookie). Marks the client as anonymous regardless of the HTTP result.
   */
  async logout() {
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: { "X-XSRF-TOKEN": getCsrfToken() },
      });
    } finally {
      set({ user: null, status: "anonymous" });
    }
  },
}));
