import * as React from "react";

const MOBILE_BREAKPOINT = 768;

/**
 * Tailwind's `sm`. Use this wherever a component decides in JS what its neighbours decide with
 * `sm:` classes, or the two disagree across a 128px band: the filter panel opened as a bottom
 * drawer between 640 and 767px while the toolbar around it had already switched to its desktop
 * form.
 */
export const SM_BREAKPOINT = 640;

export function useIsMobile() {
  return useIsNarrowerThan(MOBILE_BREAKPOINT);
}

/** True while the viewport is narrower than [breakpoint] px. */
export function useIsNarrowerThan(breakpoint: number) {
  const [narrow, setNarrow] = React.useState<boolean | undefined>(undefined);

  React.useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${breakpoint - 1}px)`);
    const onChange = () => setNarrow(window.innerWidth < breakpoint);
    mql.addEventListener("change", onChange);
    setNarrow(window.innerWidth < breakpoint);
    return () => mql.removeEventListener("change", onChange);
  }, [breakpoint]);

  return !!narrow;
}
