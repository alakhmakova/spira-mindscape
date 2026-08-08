import { useEffect } from "react";
import { createRouter } from "@tanstack/react-router";
import { Loader } from "lucide-react";

import { ErrorScreen } from "./components/spira/ErrorScreen";
import { SpiraApiError } from "./lib/spira/api";
import { logger } from "./lib/logger";
import { routeTree } from "./routeTree.gen";

function DefaultPendingComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <Loader className="h-8 w-8 text-[#ea580c] animate-spin" />
    </div>
  );
}

function DefaultErrorComponent({ error }: { error: Error }) {
  // Reported from an effect, not during render: StrictMode renders twice in development
  // and a route can re-render on its own, either of which would double-report.
  useEffect(() => {
    logger.reportError(error, { kind: "router" });
  }, [error]);

  return (
    <ErrorScreen
      reference={
        error instanceof SpiraApiError ? error.correlationId : undefined
      }
      detail={error.message}
    />
  );
}

export const getRouter = () => {
  const router = createRouter({
    routeTree,
    context: {},
    scrollRestoration: true,
    defaultPreloadStaleTime: 0,
    defaultErrorComponent: DefaultErrorComponent,
    defaultPendingComponent: DefaultPendingComponent,
  });

  return router;
};
