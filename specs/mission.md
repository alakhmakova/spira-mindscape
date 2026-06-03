# Mission

Spira is a structured goal achievement platform for people who have meaningful ambitions but struggle to turn intention into clear plans and consistent action.

Spira helps users clarify goals, organize complex life situations, create realistic roadmaps, and maintain progress through a calm personal operating system and a seamless AI goal-support agent.

The product exists to turn ambition into structure.

## Purpose

Many people do not fail because they lack desire. They fail because their goals remain emotional, abstract, overloaded, or disconnected from executable next steps. Without visible structure, priorities shift, motivation drops, and behavior becomes chaotic.

Spira helps users move from:

- vague desire to a clear goal
- emotional overwhelm to visible structure
- scattered effort to concrete next steps
- passive planning to consistent execution

Spira should encourage action, not just planning.

## Primary User

The first real user is the product creator, using Spira as an individual self-use product.

The broader intended audience is people who have ambitions but struggle to hold a plan, sequence, and next actions clearly in mind. This may include people pursuing work, education, relocation, skill development, personal growth, or other complex life goals.

Spira is designed for individual use first. Professional-assisted use may come later, where coaches, teachers, employment advisors, social workers, or other qualified specialists can use Spira as a structured goal workspace with a client. In that setting, a human specialist may temporarily replace or guide the AI role. The user should later be able to continue independently with AI support.

## Product Promise

The MVP promise is:

> Clarify a meaningful goal and turn it into a structured roadmap for achievement.

Spira should not merely store tasks. It should help users understand what they want, where they are, what options exist, and what they will do next.

## Goal Structure

Each goal has its own dedicated workspace.

A goal may be complex and should support:

- goal details and description
- current reality, including actions already taken and obstacles
- resources such as notes, links, files, emails, and reference material
- options and possible strategies
- targets and concrete next steps
- deadlines, progress, confidence, and achievement state
- AI interaction in the context of that goal

There must also be a global goals page that supports:

- search
- filtering
- sorting
- overview of all goals
- progress visibility across goals

## AI Philosophy

Spira's AI is not a generic chatbot.

It is a goal-support system with two clearly separated modes.

**Regular goal chat** is always on and always the default. The AI acts as an execution assistant: it helps the user move the goal forward through research, drafts, target proposals, option analysis, next steps, and resource creation. The user never selects this mode — it is simply how the AI behaves in the goal chat panel.

**GROW sessions** are a separate feature, explicitly started by the user via a dedicated "Start GROW session" button on the goal page. In a GROW session, the AI becomes a coaching intelligence — it raises awareness through questions, follows the user's thinking, and does not give unsolicited advice. GROW sessions are time-bounded: the user chooses 15, 30, 45, or 60 minutes.

There is no mode toggle inside the AI chat panel. The two modes are separated by UI surface, not by a setting.

The coaching behavior in GROW sessions is grounded in the source books:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`

In a GROW session, the AI behaves like a real coach — asking one good question at a time, following the user's thinking rather than a predetermined agenda. The GROW framework may naturally emerge from the conversation, but the AI never announces phases or leads the user through a checklist.

The AI must never give unsolicited advice, over-generalize, or produce generic motivational content — whether in regular chat or in a GROW session.

## GROW Sessions

Spira supports a dedicated GROW session feature for focused, time-bounded coaching on a specific goal.

Regular goal chat is flexible — the user can ask anything, the AI adapts. GROW sessions are explicitly different and are explicitly started by the user via a dedicated **"Start GROW session"** button on the goal page. There is no mode toggle inside the AI chat panel — the default is always regular chat.

A GROW session is:

- started explicitly by the user via a button on the goal page (not a toggle inside the AI panel)
- tied to one specific goal
- time-bounded: the user chooses 15, 30, 45, or 60 minutes
- conducted in full coaching mode for its entire duration
- saved: if the user approves, the session produces an AI memory block, proposed goal updates, and proposed next actions

The AI opens and closes the session gracefully — like a skilled professional coach. It does not announce GROW phases ("Now we are in the Reality phase"). It conducts a real coaching conversation that, if done well, naturally covers the ground that GROW describes — but the user experiences it as a human conversation, not a structured process.

At the end of the session, the AI asks the user whether to save the session memory. If the user says no, the memory is discarded. Any proposed changes to goal data require explicit user approval before being applied.

The AI must not apply the GROW framework as a rigid script. The coaching behavior must be grounded in the source materials in `grow/`.

## AI Execution Layer

The AI agent helps users achieve goals by turning structured intent into concrete action.

Examples:

- For a job search goal, Spira may help improve a CV, prepare interview stories, track applications, search for opportunities, and suggest application strategy.
- For a relocation or citizenship goal, Spira may help track requirements, deadlines, documents, legal information sources, and administrative steps.
- For a skill-based goal, Spira may break down a learning path, suggest training plans, track skill progression, and recommend practice routines.
- For a goal such as learning to surf professionally, Spira may notice a target like "find a surf school," offer to research nearby schools with prices, schedules, location, and fit, then draft a message or propose a trial lesson target after the user chooses an option.
- For a weight loss goal, Spira may create a small weight tracking tool where the user enters daily weight and sees a chart of changes over time.
- For a habit-related goal, Spira may create a habit tracker even if habit tracking is not part of the standard goal interface.

For the MVP, AI-generated changes require user approval before they modify important goal data or take external actions.

The AI may propose:

- new targets
- edits to goal structure
- research tasks
- draft emails or messages
- suggested deadlines
- next actions
- goal-specific mini tools

The user approves before important changes are applied.

## AI Tool Builder

Spira supports two kinds of AI-created tools: goal-scoped tools and global personal tools.

**Goal-scoped tools** are created in the context of a specific goal when the standard goal interface is not enough for execution. Examples: a weight tracker for a fitness goal, an application tracker for a job search goal, a practice log for a skill goal.

**Global personal tools** are not attached to any goal. They are personal life instruments the user wants in the same space where they manage their goals — a period tracker, a habit log, a savings calculator, a reminder for recurring events. These are created from the global AI chat on the All Goals page.

Tools are built from approved UI primitives and rendered by a generic frontend renderer. The AI proposes the tool schema; the user approves before anything is created.

The user controls where each tool appears: on the goal page (goal-scoped only), pinned on the All Goals page for constant visibility, or on a dedicated Tools page for less frequent access.

AI-generated tools must not replace the core goal model. They are execution and life-management aids.

The user must approve tool creation before it is added to the workspace.

## Safety and Boundaries

Spira must be designed with safety from the beginning.

The product must not help users pursue illegal, unethical, exploitative, dangerous, self-harming, or harmful goals. If a goal or request appears unsafe, the AI should refuse to assist with the harmful path and redirect the user toward safer, legal, constructive alternatives.

Spira is not a medical, psychological, psychiatric, legal, financial, or emergency service. It does not provide professional diagnosis, treatment, therapy, legal advice, financial advice, or crisis support.

If a user is dealing with serious mental health, medical, legal, financial, or safety issues, Spira should encourage them to contact qualified professionals or appropriate emergency/support services.

## Product Feel

Spira should feel like a calm personal operating system.

The interface should be:

- modern
- clean
- structured
- motivating
- visually engaging without becoming noisy
- optimized for daily return and low-friction progress tracking

The UI should reduce cognitive load. It should make goals, progress, milestones, blockers, and next steps visible. It should help the user return to action quickly.

## Core Principles

- Do not oversimplify complex goals.
- Do not turn Spira into a generic task list.
- Do not generate generic startup-product patterns that ignore the goal achievement domain.
- Prioritize clarity, modularity, safety, and scalability.
- Encourage execution, not endless planning.
- Make structured reflection part of progress.
- Keep AI behavior grounded, explainable, and approval-based.

## Success

Spira succeeds when a user can take an important but vague ambition, clarify it, understand their current reality, choose a practical strategy, and commit to concrete next steps they can track over time.

The product should help users feel less scattered, more oriented, and more capable of acting consistently toward goals that matter.
