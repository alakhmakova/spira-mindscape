import { Component, type ErrorInfo, type ReactNode } from "react";

import { SpiraApiError } from "../lib/spira/api";
import { logger } from "../lib/logger";
import { ErrorScreen } from "./spira/ErrorScreen";

type Props = { children: ReactNode };
type State = { error: Error | null };

/** React's component stack is long; the first frames are the ones that identify the fault. */
const COMPONENT_STACK_LINES = 10;

/**
 * The app's outermost safety net.
 *
 * TanStack Router's `defaultErrorComponent` catches errors thrown while rendering a route,
 * but nothing caught an error thrown outside that — a shared layout, a context provider,
 * the shell itself — so those blanked the page with no trace. This boundary catches them,
 * reports them, and shows the same {@link ErrorScreen}.
 *
 * A class component because `componentDidCatch` has no hook equivalent.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    logger.reportError(error, {
      kind: "render",
      componentStack: info.componentStack
        ?.split("\n")
        .slice(0, COMPONENT_STACK_LINES)
        .join("\n"),
    });
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    return (
      <ErrorScreen
        reference={
          error instanceof SpiraApiError ? error.correlationId : undefined
        }
        detail={error.message}
      />
    );
  }
}
