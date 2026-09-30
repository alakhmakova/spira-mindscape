import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CvMapActions } from "./AiPanel";
import type { CvApplication, CvCardText } from "./ai-api";

const copy: CvCardText = {
  useAsDetails: "Use “{title}” as my details",
  openMap: "Open the vacancy map",
};

/** Only the fields the map step's buttons read; the rest of the DTO does not matter here. */
function app(over: Partial<CvApplication>): CvApplication {
  return {
    id: 1,
    goalId: 7,
    phase: "map",
    intakeStatus: "none",
    intakeCandidateId: null,
    intakeCandidateTitle: null,
    mapResourceId: null,
    ...over,
  } as CvApplication;
}

describe("CvMapActions", () => {
  it("opens the map she answers on", async () => {
    const onOpenMap = vi.fn();
    render(
      <CvMapActions
        app={app({ mapResourceId: 362 })}
        copy={copy}
        onOpenMap={onOpenMap}
        onAdopt={vi.fn()}
      />,
    );

    await userEvent.click(
      screen.getByRole("button", { name: /Open the vacancy map/ }),
    );
    expect(onOpenMap).toHaveBeenCalledWith(362);
  });

  it("offers the note the SERVER named as her details, with its title filled in", async () => {
    const onAdopt = vi.fn();
    render(
      <CvMapActions
        app={app({
          intakeStatus: "candidate",
          intakeCandidateId: 55,
          intakeCandidateTitle: "Profil 2025",
        })}
        copy={copy}
        onOpenMap={vi.fn()}
        onAdopt={onAdopt}
      />,
    );

    await userEvent.click(
      screen.getByRole("button", { name: "Use “Profil 2025” as my details" }),
    );
    expect(onAdopt).toHaveBeenCalledWith("55");
    expect(screen.queryByRole("button", { name: /vacancy map/ })).toBeNull();
  });

  it("draws nothing when there is no map yet and no note to offer", () => {
    const { container } = render(
      <CvMapActions
        app={app({ intakeStatus: "exists" })}
        copy={copy}
        onOpenMap={vi.fn()}
        onAdopt={vi.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
