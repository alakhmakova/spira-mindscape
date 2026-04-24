# Goal Card Layout Refinement — Shaping Notes

## Scope

Refine the layout and design for the main dashboard page and the goal cards (`GoalCard.tsx`). The design shifts to a light off-white background with stark white cards, crisp typography, and standard web composition patterns.

## Decisions

- **Main Dashboard Layout**:
  - The main container background is updated to a solid light gray/off-white (`#f4f5f5`) to contrast against the white cards.
  - The page header uses standard compositional web design: Title and text stacked vertically on the left, action buttons aligned to the top right of the flex container.
  - The static title is replaced with a dynamic, time-aware greeting (e.g., "Good morning, Spira User!") styled with a bold, clean typography (`font-semibold text-2xl`).
- **Global Header**:
  - The top navigation header is expanded to full width (`w-full` instead of bounded in the center).
  - The placeholder user information is explicitly updated to display "Spira User" with the initials "SU".
- **Card Design**:
  - Cards transition to a pure white background (`bg-card`) with a subtle `shadow-sm` and a hairline border.
  - The main icon for the goal is repositioned to the top left of the card.
  - Padding is adjusted to `p-5` with a vertical flex layout.
- **Action Buttons**:
  - Removed explicit action buttons. The primary interaction (navigating to the goal) is placed directly on the goal title.
- **Keep Existing Content**: All core Spira goal data logic (progress bar, deadline popover, confidence pill, delete action) remains fully intact and functional.

## Context

- **Visuals**: A reference design was analyzed, emphasizing pure white cards on a light gray background, strong hierarchy, and explicit action buttons.
- **References**: `src/components/spira/GoalCard.tsx`, `src/routes/index.tsx`, `src/components/shell/AppShell.tsx`.
- **Product alignment**: Follows modern clean SaaS aesthetics.

## Standards Applied

- Ensure horizontal composition on the header properly aligns action buttons to the top rather than the baseline to adhere to web design layout standards.
