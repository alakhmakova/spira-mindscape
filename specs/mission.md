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

It is a seamless goal-support system. The user should not manually switch between "coach mode" and "agent mode." Instead, the AI should choose the right posture based on context.

When the user needs clarity, reflection, motivation, or prioritization, the AI behaves like a structured coach using the GROW model:

- Goal
- Reality
- Options
- Will

When the user needs concrete progress, the AI behaves like an execution assistant that can research, compare options, draft messages, suggest tasks, prepare next steps, and help move a goal forward.

When the user needs a specialized interface that does not exist in the standard Spira goal model, the AI may also behave as a tool builder. It can propose and create small goal-specific frontend tools that support execution without turning the core product into a bloated generic app.

The coaching logic must be grounded in real coaching methodology, with source material maintained in:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`

The AI should guide structured thinking. It should not behave randomly, over-generalize, or produce generic motivational advice.

## GROW Sessions

Spira should support a dedicated GROW session feature for focused work on a specific goal.

Ordinary AI interaction should remain flexible. Users should not be forced to speak through the GROW model all the time.

A GROW session is different:

- the user explicitly starts it from a goal
- it is tied to one goal
- the user chooses a session length: 15, 30, 45, or 60 minutes
- it follows the GROW sequence: Goal, Reality, Options, Will
- it helps the user clarify and deepen one goal
- it saves useful outcomes such as summaries, insights, decisions, and proposed next actions
- it may propose updates to targets or goal structure, but those changes require user approval

GROW sessions should be structured enough to follow real coaching methodology, while still feeling human, focused, and useful.

The AI should open and close a GROW session gracefully, like a real professional. It should set focus at the beginning, manage the time box during the session, and end with a concise summary, decisions, and next steps when the selected time is reached.

The AI must not freely improvise the coaching structure during a GROW session. The product should guide the AI with coaching principles derived from the source materials in `grow/`.

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

Spira may allow the AI to create small frontend tools requested by the user or suggested by the AI when a goal needs a more specific interaction model than the standard goal sections provide.

These tools should be scoped to a goal and should support execution.

Examples:

- a weight tracker with daily entries and a trend chart
- a habit tracker
- a savings calculator
- an application tracker for job search
- a study streak tracker
- a practice log for sports or skills

AI-generated tools must not replace the core goal model. They are extensions that help a specific goal become easier to execute.

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
