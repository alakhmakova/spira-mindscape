# Roadmap

This roadmap describes a practical sequence from the current Spira frontend prototype toward a production product.

Each phase should be independently shippable and should produce a clearer, more reliable system. Agents must not skip foundational phases in order to build impressive AI features on top of missing persistence, permissions, or safety.

---

## Phase 1: Stabilize Current Frontend MVP

**Goal:** Preserve and polish the current goal workspace as the source of truth for Spira's MVP domain model.

- [ ] Keep the current goal structure: Goal, Reality, Resources, Options, Targets
- [ ] Keep targets as Numeric, Done / Not Done, and Checklist
- [ ] Keep progress calculated from targets only
- [ ] Remove or avoid unused domain fields
- [ ] Preserve inline editing patterns
- [ ] Preserve destructive confirmation dialogs
- [ ] Preserve global goals search, filtering, sorting, and overview
- [ ] Keep frontend-only data persistence as a temporary prototype mechanism
- [ ] Document any intentional domain model changes before implementing them

---

## Phase 2: Production Backend Foundation

**Goal:** Add the production backend foundation without changing the product model.

- [ ] Create Spring Boot backend
- [ ] Add GraphQL API foundation
- [ ] Add PostgreSQL persistence
- [ ] Add database migrations
- [ ] Add user identity and authentication
- [ ] Model goals, reality, resources, options, targets, confidence, and achievement dates
- [ ] Add backend unit tests
- [ ] Add integration tests for database and GraphQL resolvers
- [ ] Add CI pipeline for backend build and tests

---

## Phase 3: Frontend Backend Integration

**Goal:** Replace local prototype persistence with production GraphQL data.

- [ ] Add GraphQL client layer to the frontend
- [ ] Load goals from backend
- [ ] Persist goal edits through GraphQL
- [ ] Persist reality, resources, options, and targets through GraphQL
- [ ] Preserve current frontend UX while changing the data source
- [ ] Add loading, error, and empty states
- [ ] Keep local Zustand only for UI state where appropriate
- [ ] Add frontend tests for backend-connected flows

---

## Phase 4: Production Resource System

**Goal:** Make resources durable, readable, and useful for both users and AI.

- [ ] Implement Note resources with editable rich text or markdown-style content
- [ ] Implement Link resources with open behavior and metadata
- [ ] Implement File resources with upload, open, download, and delete
- [ ] Implement Email resources
- [ ] Add object storage for uploaded files
- [ ] Add PDF preview support
- [ ] Add DOCX open/download support
- [ ] Add text extraction for readable PDFs and DOCX files
- [ ] Store extracted text for AI access
- [ ] Add permission checks for AI resource access

---

## Phase 5: AI Proposal and Approval Workflow

**Goal:** Create the safe foundation for all AI-assisted changes.

- [ ] Add goal-scoped AI sessions
- [ ] Add AI messages and transcripts
- [ ] Add AI proposals
- [ ] Add approval and rejection flow
- [ ] Add audit log for important AI proposals and approved changes
- [ ] Allow AI to propose targets, options, resource notes, and goal edits
- [ ] Prevent AI from mutating important data without approval
- [ ] Add safety checks for harmful, illegal, unethical, or dangerous requests
- [ ] Add deterministic tests for proposal and approval behavior

---

## Phase 6: GROW Coaching Source Extraction

**Goal:** Convert the coaching source material into implementation-ready guidance.

- [ ] Extract text from `grow/Coaching for Performance.docx`
- [ ] Extract text from `grow/Coach the Person.docx`
- [ ] Create `specs/coaching/grow-method.md`
- [ ] Create `specs/coaching/coaching-principles.md`
- [ ] Create `specs/coaching/session-rules.md`
- [ ] Distill phase-specific coaching behavior for Goal, Reality, Options, and Will
- [ ] Define coaching behaviors to avoid
- [ ] Define safety and professional-boundary language
- [ ] Review the derived guidance before implementing AI GROW sessions

---

## Phase 7: Dedicated GROW Sessions

**Goal:** Add optional structured AI coaching sessions for a selected goal.

The session is a real coaching conversation — the AI does not announce GROW phases or follow a rigid script. GROW structure may emerge naturally. The backend tracks time state (ACTIVE → CLOSING → COMPLETE), not coaching phases.

- [ ] Add GROW session backend model (status: ACTIVE, CLOSING, COMPLETE, ABANDONED)
- [ ] Allow user to choose session length: 15, 30, 45, or 60 minutes
- [ ] Store session start time, closing_starts_at (80% of duration), scheduled end time, and actual end time
- [ ] Add graceful session opening behavior (no phase announcement)
- [ ] Transition to CLOSING status at 80% of session duration; shift AI to integrative questioning without announcing it
- [ ] Add graceful session closing behavior: summary, insights, decisions, proposed next steps
- [ ] Store session transcript and AI memory block (saved only if user approves)
- [ ] Add goal-page UI to start a GROW session
- [ ] Provide time-remaining context to AI prompt; do not expose it directly to user
- [ ] Keep ordinary AI chat separate from GROW sessions
- [ ] Allow users to exit a GROW session
- [ ] Generate proposed next actions or targets at session close; require approval before applying
- [ ] Add tests for time-state transitions, coaching behavior, memory save/discard, approval flow, and safety refusal

---

## Phase 8: AI Execution Agent

**Goal:** Allow AI to help users move goals forward through concrete execution support.

- [ ] Analyze goal structure and identify missing next steps
- [ ] Suggest targets and checklist items
- [ ] Compare options and recommend strategies
- [ ] Read permitted resources
- [ ] Create draft notes/resources
- [ ] Draft emails or messages with user approval
- [ ] Support research workflows such as schools, jobs, legal/admin requirements, or learning paths
- [ ] Keep all important external actions approval-based
- [ ] Add scenario tests for execution workflows

---

## Phase 9: AI-Created Mini Tools

**Goal:** Let AI create safe goal-specific mini tools through controlled primitives.

- [ ] Define mini tool schema
- [ ] Define approved UI primitives
- [ ] Add backend persistence for tool definitions and tool data
- [ ] Add goal association for mini tools
- [ ] Add frontend renderer for approved tool schemas
- [ ] Support examples such as weight tracker, habit tracker, savings calculator, application tracker, and practice log
- [ ] Require user approval before tool creation
- [ ] Prevent arbitrary unsafe code generation
- [ ] Add tests for rendering, permissions, data persistence, and safety constraints

---

## Phase 10: Notifications and Daily Engagement

**Goal:** Help users return and act without making the product noisy.

- [ ] Add deadline notifications
- [ ] Add overdue item notifications
- [ ] Add daily focus or next-action summary
- [ ] Add confidence trend visibility when confidence history is persisted
- [ ] Add calendar/timeline improvements
- [ ] Add engagement checks that support action rather than guilt
- [ ] Add notification preference controls

---

## Phase 11: Professional-Assisted Use

**Goal:** Support future use with qualified specialists while keeping individual self-use first.

- [ ] Add sharing model for a goal workspace
- [ ] Add specialist/advisor role permissions
- [ ] Allow user-controlled access to selected goals/resources
- [ ] Support human-guided structured sessions
- [ ] Preserve privacy and user control
- [ ] Add audit logs for shared access
- [ ] Add clear disclaimers that Spira is not medical, psychological, psychiatric, legal, financial, or emergency support

---

## Phase 12: Production Hardening

**Goal:** Make Spira reliable, secure, and maintainable.

- [ ] Complete frontend E2E coverage with Playwright
- [ ] Complete backend integration test coverage
- [ ] Add migration validation in CI/CD
- [ ] Add monitoring and error reporting
- [ ] Add security review for AI actions and resources
- [ ] Add backup and recovery plan
- [ ] Add data export path
- [ ] Add privacy and data retention controls
- [ ] Prepare production deployment pipeline

---

## Phase 13: Native Mobile App

**Goal:** Bring Spira to native mobile once the production backend, core goal model, resource system, and AI workflows are stable.

- [ ] Decide native mobile stack
- [ ] Reuse the production GraphQL API
- [ ] Support authentication and account sync
- [ ] Support core goal dashboard
- [ ] Support goal page structure: Goal, Reality, Resources, Options, Targets
- [ ] Support target updates and low-friction daily progress tracking
- [ ] Support GROW session participation on mobile
- [ ] Support AI chat and approval workflow
- [ ] Support notifications for deadlines, overdue items, and daily focus
- [ ] Support mobile-safe resource viewing for notes, links, files, and email resources
- [ ] Preserve Spira's calm personal operating system feel on mobile
- [ ] Add mobile E2E or equivalent automated validation
