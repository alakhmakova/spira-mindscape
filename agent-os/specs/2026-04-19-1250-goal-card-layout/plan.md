# Goal Card & Main Page Layout Refinement

## Task 1: Update Top Navigation Header
Modify `src/components/shell/AppShell.tsx`:
- Change the main wrapper class from a centered `max-w-6xl` container to a full-width `w-full` container.
- Update the mock user profile block to display "Spira User" with the initials "SU".
- Remove the "Personal workspace" subtitle.

## Task 2: Refine Main Page Header & Background
Modify `src/routes/index.tsx`:
- Wrap the main content in a `min-h-screen` container with a `bg-[#f4f5f5]/80` background.
- Replace the static "What are you working toward?" heading with a dynamic greeting (`Good [morning/afternoon/evening/night], Spira User!`).
- Style the greeting with `font-semibold text-2xl text-foreground`.
- Align the header actions to the top (`sm:items-start`) so they do not incorrectly share a horizontal baseline with the subtitle text.

## Task 3: Update GoalCard Component
Modify `src/components/spira/GoalCard.tsx`:
- Apply a white background (`bg-card`), hairline border, and a subtle shadow (`shadow-sm`).
- Relocate the goal icon to the top left.
- Style the title to be `font-semibold text-lg text-foreground`.
- Make the title a clickable Link to navigate to the goal details.
- Move the progress bar, deadline, and confidence pills into a unified block at the bottom.
