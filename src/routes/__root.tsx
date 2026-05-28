import {
  Outlet,
  Link,
  createRootRoute,
  redirect,
  useRouterState,
} from "@tanstack/react-router";
import { AppShell } from "@/components/shell/AppShell";
import { Toaster } from "@/components/ui/sonner";
import { useAuth } from "@/lib/spira/auth";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="max-w-md text-center">
        <h1 className="font-display text-7xl">404</h1>
        <h2 className="mt-4 font-display text-2xl">Lost the path</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          The page you're looking for doesn't exist.
        </p>
        <div className="mt-6">
          <Link
            to="/"
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            Back to goals
          </Link>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRoute({
  /**
   * Auth guard: runs before any child route renders.
   *
   * - /login is public — skip the check.
   * - All other routes require an active session. We call fetchMe() only once
   *   (status starts as "loading"); subsequent navigations reuse the cached
   *   status. If the backend says the user is anonymous we redirect to /login.
   */
  beforeLoad: async ({ location }) => {
    if (location.pathname === "/login") return;

    const auth = useAuth.getState();
    if (auth.status === "loading") {
      await auth.fetchMe();
    }
    if (useAuth.getState().status === "anonymous") {
      throw redirect({ to: "/login" });
    }
  },
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
});

/**
 * The login page is rendered without the AppShell (no nav bar, no goal list).
 * Every other route is wrapped in AppShell.
 */
function RootComponent() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  if (pathname === "/login") {
    return (
      <>
        <Outlet />
        <Toaster />
      </>
    );
  }

  return (
    <AppShell>
      <Outlet />
      <Toaster />
    </AppShell>
  );
}
