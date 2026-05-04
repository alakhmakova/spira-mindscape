# Tech Stack

This document is a production architecture guide for agents building Spira.

Its purpose is not only to describe the current prototype. Its purpose is to give future agents enough technical direction to build a production-ready backend and frontend while preserving the current Spira product model.

Agents must treat `specs/mission.md` as product intent and this file as technical direction.

## Product Type

Spira is a structured goal achievement platform with an AI goal-support agent.

It is not a simple CRUD app. It is a behavioral structure system for clarifying goals, organizing complex context, tracking progress, and helping users move from intention to action.

The technical architecture must support:

- complex goal workspaces
- structured GROW-based goal modeling
- dedicated GROW coaching sessions
- resources attached to goals
- progress calculation from targets
- AI-assisted coaching and execution
- AI-created goal-specific mini tools
- user approval before AI applies important changes
- production-grade persistence, testing, security, and scalability

## Production Target Stack

### Frontend

- React
- TypeScript
- TanStack Router / TanStack Start or an equivalent React routing/application framework
- Tailwind CSS
- Radix UI primitives or equivalent accessible headless UI primitives
- Playwright for E2E tests

The frontend should remain a calm, structured personal operating system. It should prioritize inline editing, strong visual hierarchy, low-friction progress tracking, and clear goal context.

### Backend

- Spring Boot
- GraphQL API
- PostgreSQL

The backend owns durable product data, user accounts, permissions, AI sessions, AI proposals, approvals, file metadata, extracted resource text, notifications, and audit logs.

### File Storage

Uploaded resources must not be stored as large blobs in PostgreSQL.

Production file handling should use object storage such as S3, Cloudflare R2, or an equivalent storage service.

PostgreSQL stores metadata and extracted text. Object storage stores the original file.

### AI System

The AI system should be implemented as a backend-controlled orchestration layer.

It may be a Spring Boot module initially, but the architecture should allow it to become a separate service later if needed.

The AI system must:

- read permitted goal data
- read permitted resources
- create proposed changes
- create or update text resources with user approval
- propose and create goal-specific mini tools with user approval
- run dedicated GROW sessions using backend-controlled session state
- never apply important mutations without user approval
- maintain goal-scoped AI sessions
- support safety checks before helping with a goal or action

## Current Frontend Prototype Note

The current frontend prototype stores data locally in the browser using Zustand persistence.

This is useful for UI development and frontend-only iteration, but it is not the production persistence model.

If an agent is asked to work on frontend only before backend exists:

- keep using the current frontend model as the source of truth
- do not invent backend-only fields in frontend state
- preserve the current goal structure
- keep data in local persisted state only as a temporary prototype mechanism
- avoid designing UI around data that does not exist in the current frontend model

When production backend work begins, local persisted state must be replaced by GraphQL data from the backend.

## Domain Model Source of Truth

The current Spira frontend model is the source of truth for the MVP goal structure.

Agents must not add fields just because they are common in generic productivity apps. Add fields only when they are required by the product model, current UI, production persistence, AI access, safety, or explicit user instruction.

## Goal System

Each goal contains:

- title
- description
- created date
- deadline
- achieved date
- confidence rating from 1 to 10
- reality
- resources
- options
- targets

The description is stored as a single description field. UX and AI should guide users toward SMART-quality goal descriptions, but the data model does not need a separate SMART object unless explicitly introduced later.

Goal progress is calculated from targets.

Goal status is not part of the current model. Achievement is represented by achieved date and calculated progress.

## Reality

Reality contains two context lists:

- Actions: actions already taken
- Obstacles: blockers or constraints

These are intentionally simple text lists.

Reality items are for context, coaching, and user reflection. They are not analytics objects and should remain simple text lists unless the product model is explicitly changed later.

## Resources

Resources are a critical part of Spira.

Resources are attached to goals and provide context for both the user and the AI.

Supported resource types:

- Note
- Link
- File
- Email

### Notes

Notes must be editable.

They should support rich text or markdown-style formatting.

The AI should be able to read notes and propose creating or updating notes. User approval is required before AI-generated changes are saved.

### Links

Links must be openable in the browser.

Production backend may fetch and extract readable page text when permitted, but link fetching must respect safety, privacy, robots/rate limits where applicable, and user intent.

### Files

Files must support:

- open
- download
- delete

PDF preview should be supported.

DOCX support may be limited to open/download and text extraction.

The AI should be able to read extractable text from:

- text files
- markdown
- non-scanned PDF
- DOCX

The AI may not fully understand:

- scanned PDFs without OCR
- complex formatted documents
- images without text extraction

### Email

Email resources represent relevant people or organizations connected to a goal.

They may include:

- name
- role or relationship
- email address
- optional phone or notes if needed by the UI

Use the product language "Email" for this resource type.

## Options

Options are possible strategies for achieving the goal.

Each option contains:

- description text
- selected state

The AI can help compare options, surface tradeoffs, and recommend a selected strategy, but user approval is required before changing selected state.

## Targets

Targets define measurable progress.

Supported target types:

- Numeric
- Done / Not Done
- Checklist

Use user-facing language in the product UI. Internal enums may use technical names, but the product should avoid exposing formal labels such as "binary" to users.

Each target:

- has a title
- is editable
- is removable
- may have a deadline
- may have an achieved date

All targets contribute equally to goal progress unless the product model is explicitly changed later.

## Progress Calculation

Progress is calculated only from targets.

Reality, resources, and options provide context but do not directly affect progress.

### Numeric Target

A numeric target tracks progress from a starting value toward a total value.

If no explicit start value exists, use the current frontend behavior as the reference:

- if current is greater than total, infer start from current
- otherwise infer start as 0

Progress is clamped between 0 and 1.

### Done / Not Done Target

Progress is:

- 1 when done
- 0 when not done

### Checklist Target

Progress is:

```text
completed checklist items / total checklist items
```

If a checklist has no items, progress is 0.

### Goal Progress

Goal progress is:

```text
average progress across all targets
```

If a goal has no targets, progress is 0.

## Goals Dashboard

The global goals page must support:

- card view
- table or timeline-style view
- search
- filtering
- sorting
- progress overview

The current frontend has card and timeline/table behavior. Future agents should preserve the intent: users must be able to scan all goals and quickly find what needs attention.

## Goal Page

Each goal has a dedicated page structured by the goal system:

- Goal
- Reality
- Resources
- Options
- Targets

The page should prioritize:

- inline editing
- visual hierarchy
- progress bars
- confidence signal
- deadlines
- low modal usage
- destructive action confirmation
- AI interaction in goal context
- goal-specific mini tools when they are useful for execution

## AI-Created Mini Tools

Spira should support AI-created mini tools for goals that need a specialized interaction model beyond the standard goal sections.

These are small frontend applications or widgets generated for a specific goal or reusable pattern.

Examples:

- weight tracker with daily entries and a trend chart
- habit tracker
- job application tracker
- savings calculator
- study streak tracker
- sports practice log

Mini tools are not a replacement for goals, targets, resources, or options. They are execution aids attached to a goal.

### Tool Placement

Mini tools may appear:

- as a section on the goal page
- as a widget on the global goals page
- as a compact dashboard element connected to a specific goal

The UI should make it clear which goal a tool belongs to.

### Tool Data

Tool data must be stored as structured data owned by the backend.

A production implementation should include:

- tool definition metadata
- goal association
- tool instance data
- user-entered records
- created by AI or user metadata
- version information

Do not store production tool data only in generated frontend code.

### Tool Generation and Approval

The AI may propose a mini tool when it identifies that the standard Spira interface is not enough for a specific goal.

The user must approve tool creation before it appears in the workspace.

The user must approve important changes to existing tool data or tool structure.

### Tool Safety and Sandboxing

AI-created tools must be constrained.

They should not be allowed to run arbitrary unsafe code, access data outside their goal context, bypass authorization, perform external actions without approval, or introduce unreviewed dependencies.

A production implementation should prefer a controlled component/schema system over unrestricted generated code.

For example, the AI can generate a tool from approved primitives:

- numeric input
- date input
- checklist
- chart
- table
- progress display
- text note
- calendar-like log

This keeps the system flexible while preserving safety, testability, and maintainability.

## AI Agent Integration

The AI must be seamless. The user should not manually switch between coach and execution modes.

The AI chooses the right posture based on context:

- coaching posture for clarity, reflection, prioritization, blockers, confidence, and GROW questions
- execution posture for research, drafts, target proposals, resource creation, comparisons, and next actions

The AI has access to:

- goal data
- targets
- reality
- options
- permitted resources
- goal-scoped AI conversation history

The AI can:

- suggest actions
- suggest targets
- analyze goal structure
- compare options
- create draft resources
- update text resources with user approval
- prepare messages or next-step plans
- propose and create goal-specific mini tools with user approval

The AI cannot:

- execute important changes without user approval
- send messages externally without user approval
- mutate goal data silently
- generate unrestricted production code that bypasses the approved tool system
- assist with illegal, unethical, exploitative, dangerous, self-harming, or harmful goals

## GROW Session System

GROW sessions are a dedicated AI feature, separate from ordinary AI chat.

Ordinary AI chat can be flexible and context-aware. A GROW session is explicitly started by the user for a specific goal when they want focused coaching work.

The backend must control GROW session structure. The AI should not freely improvise the session flow.

### Source Material

GROW coaching behavior must be derived from the source materials in:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`

Agents must not rely only on a generic prompt such as "use GROW." Before implementing production GROW sessions, the project should extract, review, and distill the source material into internal coaching guidance documents.

Recommended derived documents:

- `specs/coaching/grow-method.md`
- `specs/coaching/coaching-principles.md`
- `specs/coaching/session-rules.md`

These documents should summarize the coaching method, session rules, question style, boundaries, and behaviors to avoid.

### Session Model

A GROW session belongs to one goal.

It should persist:

- session id
- goal id
- user id
- selected duration: 15, 30, 45, or 60 minutes
- started at
- scheduled end time
- ended at
- current phase
- transcript
- phase summaries
- insights
- decisions
- proposed next actions
- proposed target changes
- completion state

Session phases:

- Goal
- Reality
- Options
- Will
- Summary
- Complete

The backend should store and advance the phase. The AI should receive phase-specific instructions.

### Phase Behavior

### Timeboxing

When starting a GROW session, the user chooses a duration:

- 15 minutes
- 30 minutes
- 45 minutes
- 60 minutes

The AI must treat this as a real session boundary.

At the beginning, the AI should:

- acknowledge the selected duration
- set a clear focus for the session
- explain that the session will end with a concise summary and next steps

During the session, the AI should:

- pace the depth of questions according to the selected duration
- avoid opening too many threads late in the session
- move toward Will and Summary as time runs out

At the end, the AI should:

- close gracefully
- summarize what was clarified
- name decisions and insights
- list proposed next steps
- create approval-ready proposals where useful
- avoid abruptly cutting off the user

The backend should track session start, scheduled end, and actual end. The frontend should show enough timing context for the user to understand the session boundary without making the experience feel stressful.

Goal phase:

- clarify the desired outcome
- improve specificity
- check ownership and importance
- avoid premature advice

Reality phase:

- explore current situation
- identify actions already taken
- identify obstacles
- surface assumptions and blockers
- check confidence where useful

Options phase:

- help generate possible strategies
- avoid judging too early
- compare tradeoffs
- support creative alternatives

Will phase:

- convert insight into commitment
- define next actions
- suggest targets or checklist items
- confirm deadlines where useful
- check confidence in the plan

Summary phase:

- summarize what was clarified
- list decisions
- list proposed next actions
- create approval-ready proposals for goal updates

### Response Rules

During a GROW session, the AI should:

- ask focused coaching questions
- keep the conversation tied to the selected goal
- avoid generic motivational monologues
- avoid solving too early before Reality and Options are explored
- avoid creating targets before the Will phase unless the user explicitly exits the session
- offer to pause or exit the session if the user asks for execution work instead
- maintain safety and professional-boundary disclaimers where needed

### Approval Rules

GROW sessions may produce proposed changes, but they do not apply important changes automatically.

Examples of approval-required outputs:

- new targets
- edited goal description
- selected option
- new resource note
- mini tool suggestion

The user must approve before changes are applied.

### Validation

GROW session implementation must be tested with deterministic scenarios:

- session starts from a specific goal
- phase order is preserved
- AI does not skip directly to advice
- target proposals appear in the Will or Summary phase
- user can pause or exit the session
- unsafe goals or unsafe requests are refused or redirected
- summaries and proposals are persisted
- approved proposals mutate goal data correctly

## Backend Responsibilities

The backend must provide:

- authentication and user identity
- GraphQL schema and resolvers
- PostgreSQL persistence
- resource metadata persistence
- object storage integration
- file upload/download flow
- text extraction pipeline for readable resources
- goal-scoped AI sessions
- GROW session state and phase persistence
- AI proposal and approval workflow
- AI-created mini tool definitions and tool data
- audit log for important AI actions
- notification/reminder system
- safety checks for AI-assisted actions

## GraphQL Responsibilities

GraphQL should expose structured operations for:

- goals
- reality actions and obstacles
- resources
- options
- targets
- confidence updates
- AI sessions
- GROW sessions
- AI messages
- AI proposals
- approvals/rejections
- goal-specific mini tools
- mini tool data records
- notifications

GraphQL should not be used to upload large files directly as base64 payloads.

File upload should use a dedicated upload flow, such as signed URLs or multipart upload endpoints coordinated by GraphQL metadata mutations.

## Testing Requirements

Production development must support:

- unit tests
- integration tests
- E2E tests with Playwright
- backend tests for GraphQL resolvers and services
- database integration tests
- AI orchestration tests using deterministic fixtures/mocks
- GROW session phase-flow tests
- CI/CD pipeline

Frontend changes must be validated across desktop and mobile behavior when they affect layout or interaction.

Backend changes must be validated with automated tests and migration checks.

AI changes must be validated with scenario-based tests, including refusal/safety cases and approval workflow cases.

AI-created mini tools must be validated with schema tests, rendering tests, permission tests, and data persistence tests.

## CI/CD

The production system should include a CI/CD pipeline that runs:

- frontend build
- frontend lint/type checks
- backend build
- backend unit and integration tests
- database migration validation
- Playwright E2E tests where appropriate

No code should be considered production-ready if it bypasses tests, type checks, or migration validation.

## Architectural Principles

- Preserve the current Spira goal structure unless the constitution is intentionally updated.
- Keep the frontend calm, structured, and low-friction.
- Keep the backend modular and testable.
- Keep AI actions explicit, reviewable, and approval-based.
- Keep resources central to the system because they provide goal context.
- Allow AI-created mini tools only through controlled, safe, reviewable primitives.
- Do not design Spira as a generic task manager.
- Do not overcomplicate Reality; it is contextual, not analytical.
- Do not expose technical enum names in user-facing UI.
- Build for production safety from the start.
