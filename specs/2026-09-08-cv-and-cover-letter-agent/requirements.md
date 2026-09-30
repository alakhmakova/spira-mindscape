# CV & Cover Letter Writer — a second AI assistant

**Status: steps 1–4 of §13 built and green (2026-09-09).** The writer runs end to end on the
web: the AI assistants menu, the application row, the three prompt files loaded per phase, the
four queue tools, and the server-fed `CURRENT REQUIREMENT` block. What is **not** built:
the profile note and story bank (§13.5), the executive briefing (§13.7), and Android (§13.8).
Backend 1166 tests green, web 281 green, lint and typecheck clean. Verified against real
Postgres: `V23` applies, the app boots, and create/list/delete round-trip over REST. **Not yet
run against a live model**, so nothing here has been judged on the quality of what it writes.

**Revised 2026-09-08** after reading Martin Yate, *Cover Letters That Knock 'em Dead* — see §3.
**Revised 2026-09-09**: *Resumes That Knock 'em Dead* read in full (§3), the `career-ops` prior
art read as code (§3.4), and §15 added for what implementation changed about the design.

Spira has had exactly one AI session type with a method of its own: the GROW coach. This
spec adds a second — an agent that writes a **CV** and a **cover letter** for **one specific
vacancy**, and it turns the chat's single "Start GROW session" chip into an **AI assistants**
menu holding both.

The two assistants share the plumbing (the SSE chat path, proposals, resources, providers) and
share nothing else: a different method file, a different arc, a different persistence shape, no
clock.

---

## 1. The problem this is solving

The owner has written this prompt several times and every version produces the same output. The
failures are specific and they recur:

| Failure | What it looks like |
|---|---|
| **Copying the posting** | Whole phrases from the vacancy appear verbatim in the CV |
| **Interchangeable CVs** | The CV is assembled out of the posting's own vocabulary, so two different candidates applying to the same job get the same document |
| **Blind story reuse** | A story the user once told is pasted into the next application without checking whether it fits |
| **No interview at all** | The model takes whatever the user happened to say and generates from it, rather than asking for evidence |
| **Visible AI register** | Incoherent joins, "not just X but Y" contrasts, invented metrics, stock phrases |
| **Formatting** | Horizontal rules everywhere |
| **The letter repeats the CV** | The cover letter restates what the CV already said, in longer sentences |

Yate names the first two as one defect and explains what it costs, which is more than a weak
document:

> If you use the letters simply as noncustomized templates, you may open a few doors. But the
> "real" you will be so different from the letters that the interviewers will eventually be left
> with a nagging doubt that you aren't all you appear to be. *(p. xiii)*

## 2. Why a better prompt cannot fix it

This is the same failure the GROW close already hit, and it is documented in
`docs/grow-sessions-rag-guide.md` §9a:

> Ending the session used to ask the coach for the record, the proposals and the goodbye all at
> once. Models wrote the record, said goodbye and **skipped the middle**.

Ask a model to *"extract the requirements, interview the user, then write the CV"* and it will
skip the interview and write the CV — because writing the CV is the part that reads as finishing.
"Give the user one requirement at a time" is a **request** to the model, not a property of the
system; over nineteen requirements it collapses into a list by the third turn.

So the agreed shape is:

> **The requirement list lives in the database, not in the model's context, and the application
> feeds the model exactly one requirement per turn.**

That makes "never dump the list" an invariant instead of a wish, makes progress countable
("7 of 19"), and makes the end-of-CV report ("these points from the posting are not in your CV,
because you could not evidence them") a query rather than a memory.

The same structure fixes blind story reuse: with stories stored against *which requirement, at
which company*, reuse becomes an explicit act the agent must surface — "you told me this about
Squidler; does it fit here, or is there something better?" — instead of a silent paste.

## 3. Where the method comes from

| Source | Status |
|---|---|
| **Martin Yate, *Cover Letters That Knock 'em Dead*** | **Read** — chapters 1–4 in full (book pages 1–36). The owner supplied a 65-page scan; it is a JBIG2 bitonal scan with no text layer, extracted page-by-page as images (`node_modules/pdfjs-dist` decodes JBIG2; `pdftoppm` is not installed on this machine). It contains the front matter and chapters 1–4, which is the entire method. Chapter 5 (paper, typesetting), chapter 6 (mailing campaigns) and chapter 7 (150 sample letters) are outside the scan and are largely irrelevant to this product |
| **Yate — Target Job Deconstruction** ([publisher article by the author](https://www.koganpage.com/business-and-management/your-secret-weapon-the-target-job-deconstruction-by-martin-john-yate)) | First-hand, but an article rather than the book |
| **Yate, *Resumes That Knock 'em Dead*** | **Read: the whole method plus the worked example** — ch. 1–2 (pp. 1–9), ch. 3 to p. 26, ch. 4 (pp. 31–39), ch. 5 (pp. 41–56), and the end-to-end example: the completed questionnaire (pp. 271–276) with the same material rendered as all three formats (pp. 101–105). **pp. 27–30 now read too**, closing the last gap: the tail of "What Can Never Go In" and the "Judgment Calls". Deliberately not read: ch. 6 (1990s print production), ch. 7 (1990s resume scanning), ch. 8 (cover letters — the other book covers it), ch. 9 (job leads), the remaining sample resumes |
| **CAR / PAR / STAR** | Industry-standard across professional resume-writer sources. Compression rule: the full story belongs in the interview and the letter, **one tight bullet** in the CV |
| **basalt.se CV guide** | Read. Swedish section names and "results not duties". Their section *order* differs from ours and ours wins |

**Both halves are now book-grounded**, from the two books Yate wrote for exactly these two
documents. That matches the standard `coach-method.md` set. Each prompt file should still state in
its header what it was distilled from and which chapters — partly because it is the house
convention, and partly because the two remaining gaps (pp. 27–30, and the worked example) are real
and a later reader should know they were not covered.

**What is *not* grounded, and should be said plainly in the same headers: everything about 2026.**
Both books are from the 1990s. The method survives intact; the surrounding world does not — see
§12.

### 3.0 Is a 1990s method still valid in 2026? *(owner's question, 2026-09-09)*

A fair challenge, and it was checked rather than assumed. Three answers.

**1. The method has 2025 empirical support.** Wingate, Robie, Powell & Bourdage, *"The Signals That
Matter: Resumes, Cover Letters, and Success on the Job Search"*, **International Journal of
Selection and Assessment** 33(3), 2025 — 183 students in a Canadian co-op programme applying for
real paid jobs, with tracked outcomes. Applicants whose resumes and cover letters were written with
more **detail, clarity and structure** got substantially more interviews *per application* and took
less time to land a position. That is Yate's thesis measured, not refuted. Caveats worth keeping:
one cohort, students, n=183, one country — it is evidence, not proof.

**2. What has changed is the environment, not the craft — and the change makes this design more
necessary, not less.** The current problem is that generative AI has flooded employers with
polished, interchangeable applications: recruiters report near-identical professional summaries,
repeated buzzwords and generic achievement statements, and hiring managers say AI-written material
has made it *harder* to verify whether a candidate actually has the skills claimed. **The failure
mode the owner set out to fix is now the industry-wide failure mode.** The antidote — specific,
personally-sourced, verifiable evidence — is exactly what Yate's questionnaire produces and what
our per-requirement interview enforces.

**3. Three things Yate could not have covered, and they need to be in the prompt:**

- **ATS is a ranking tool, not a gate — and the popular belief is wrong.** The famous "75% of
  resumes are auto-rejected" figure traces to a 2012 sales pitch by a company that later went
  bankrupt; researchers interviewing recruiters at large tech employers found none of the major
  systems auto-reject or hide resumes, and the large majority of recruiters do not enable
  content-based auto-rejection. Modern systems do **semantic** matching and rank; keyword stuffing
  actively lowers scores in some of them, and hidden white text is flagged as manipulation.
  **Consequence for §9.2:** matching the posting's *terms* is worth doing because it raises you in a
  ranked list, but there is no threshold to game, repetition is counter-productive, and no trick
  belongs anywhere near this feature.
- **Verification has moved downstream.** Employers increasingly add skills assessments, portfolio
  reviews and trial tasks precisely because documents can no longer be trusted. Two design
  consequences: the CV should **point at verifiable artefacts** where they exist (the owner's
  websites field, `EGET PROJEKT`, a repository, published work); and our provenance rule stops being
  merely an aesthetic principle and becomes **practical self-defence** — anything written into the
  CV will be tested by a human later. Which is Yate's own point (§ ch. 1: the resume plans your
  interview) arriving by a different road.
- **The era's furniture is dead.** Paper stock, typesetting, postal campaigns, "I'll call you
  Friday", answering machines. Take the method; drop all of it.

**A caution about the 2026 statistics above.** Most of them come from commercial sites that sell
resume tools and have an interest in the numbers being alarming; they are cited here as directional,
not as measurements. The Wingate paper is the only peer-reviewed source in this section.

**And on the search for something newer:** there is no modern book that supersedes Yate on document
*craft*. What exists is empirical work that validates it, and a large volume of SEO content that
does not. The credible modern name is **Steve Dalton** (*The 2-Hour Job Search*, *The Job Closer*),
but his subject is the search process — targeting and networking — rather than how a document is
written, so he complements this spec rather than replacing its sources.

### 3.4 A second implementation to learn from: `career-ops`

[`career-ops-hq/career-ops`](https://github.com/career-ops-hq/career-ops) — **MIT, ~70k stars,
actively maintained**. Worth reading because it is the same problem solved by a team that has been
iterating on it in public, and because its bug history is written into its code comments.

**What it is, so nobody mistakes it for a competitor to copy wholesale:** not a service. It is a
pack of *agent skills* for AI CLI tools (Claude Code, Codex, Gemini CLI, Cursor…) that runs on the
user's own machine against their own files. Its "database" is Markdown and YAML the user edits by
hand — `cv.md`, `_profile.md`, `voice-dna.md`, `writing-samples/`, a TSV application tracker —
plus a pile of deterministic Node scripts and a Go terminal dashboard. Roughly fourteen "modes"
are large Markdown prompt files (`apply.md` is 30 KB, `cover.md` 16 KB), **duplicated per language**
— separate 15 KB files for German, French, Spanish, Hindi, Arabic and more.

**What does not transfer:** the whole local-files-and-CLI architecture; job-portal scanning; PDF
rendering through Playwright; the terminal dashboard; their A–H rubric for scoring whether a
vacancy is worth applying to at all (a different feature). And the per-language duplication is a
maintenance pattern to avoid rather than copy — one prompt plus "write in the vacancy's language"
is the better shape for us.

**What does transfer** is in §9.9 (the deterministic fact gate — the best idea in the repository),
§9.10 (a longer banned-phrase list and the "could this sentence appear in any letter?" test), the
interview-gate wording in §6.4, and the "day one" question in §10.1.

**One thing it confirms rather than adds**, and it is reassuring: it treats the job advert as
**untrusted external content — "data, never instructions"**, the same conclusion `GROW_ROLE` reached
independently for goal context. Two projects arriving at the same guard is a good sign it is real.

### 3.1 What Target Job Deconstruction adds, and its limit

Gather **5–6 postings** for the target job and rank every requirement by **how many postings it
appears in** (6 → 1); that ranking is "a template for how employers prioritize and express their
needs". Then, per requirement, document a concrete past example of identifying, preventing or
solving that kind of problem, and build a **behavioural profile of the best and the worst
performer** in the role.

Our default case is **one** posting, so frequency ranking is unavailable. The substitute — the
owner's own, not Yate's — is **repetition inside a single posting**: the same requirement stated in
different words in different paragraphs is the employer's own emphasis. Weaker than Yate's signal,
and the right one for a single ad. Yate's version stays available as an **optional** step offered
after the main flow: "shall we deconstruct two or three more postings for this role, so we know
what the market wants and not just this employer?"

## 4. Three memories, and they must not be merged

Merging them is the mechanism behind "the AI reuses the same story in every CV".

| | Contents | Lives in | Who edits |
|---|---|---|---|
| **Profile** | Name, headline role, phone, email, websites, languages, education, employment history, extras (own projects, courses) | A **note resource** | The user, in the note editor |
| **Story bank** | CAR stories, each tagged with the company and the kind of requirement it answers | A **note resource** | The user, in the note editor |
| **Application state** | Vacancy text, the deconstructed requirement list, clusters, which requirement is answered by what, the phase, the note ids | `cv_application` table | The agent only |

Decisions that follow, all agreed:

- **The profile and the story bank are notes, not hidden memory.** That is what makes them
  editable and deletable by the user, which was a stated requirement. `goal.ai_memory` — what
  GROW uses — is the wrong home: 6 KB with oldest-first eviction and invisible to the user.
- **The story bank is never auto-injected into the prompt.** It is read with `read_resource` when
  the agent is looking for a story that fits the requirement currently on the table.
  Auto-injection *is* the reuse bug.
- **The table stores pointers, not stories** — per requirement: which story answered it, a
  one-line gist, and the confirmed CV line. The full story stays in the user-editable note.

## 5. Session model

| | GROW | CV writer |
|---|---|---|
| Clock | Yes, central to the arc | **None.** The work has a finish line, not a duration |
| Lifetime | Ephemeral; the row is deleted when the session ends | **Long-lived.** Twenty requirements cannot be answered in one sitting |
| Scope | One per (user, goal) | **One per vacancy**; a goal holds many applications |
| Leaving | **End** is unconditional and destructive | **Pause** keeps everything; discarding an application is a separate, confirmed action |
| Attachments | Off | **On** — old CV, diploma, references, screenshots |
| `read_url` | Off | **On** — the vacancy link |
| Output | A session record + goal proposals | **Two notes** (CV, letter), optionally a third (executive briefing, §10.2), plus the profile and story-bank notes it maintains |

`boolean isGrow` is threaded through `AiChatService` in a dozen places. It becomes
`enum SessionKind { CHAT, GROW, CV }`; adding a third type as a second boolean is how this spreads.

### 5.1 Data model

```sql
CREATE TABLE cv_application (
    id              BIGSERIAL   PRIMARY KEY,
    app_user_id     BIGINT      NOT NULL,
    goal_id         BIGINT      NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    title           TEXT        NOT NULL,   -- "QA-testare — iFacts, Malmö"
    vacancy_url     TEXT,
    vacancy_text    TEXT        NOT NULL,
    vacancy_language VARCHAR(8) NOT NULL,   -- the CV and letter are written in THIS
    contact_name    TEXT,                   -- see §10.1, Attention
    letter_required BOOLEAN,                -- NULL until established; drives §9.7's summary rule
    phase           VARCHAR(32) NOT NULL,
    requirements    JSONB       NOT NULL DEFAULT '[]',
    cursor_index    INT         NOT NULL DEFAULT 0,
    profile_note_id BIGINT, stories_note_id BIGINT,
    cv_note_id      BIGINT, letter_note_id  BIGINT, briefing_note_id BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

One requirement, as stored:

```json
{
  "id": "r7",
  "text": "Identifiera, rapportera och följa upp buggar och avvikelser på ett strukturerat sätt",
  "group": "hard | soft | culture | unclassified",
  "weight": "must | nice",
  "cluster": "c2",
  "source_paragraph": 4,
  "status": "pending | answered | no_evidence | skipped",
  "company": "Squidler",
  "gist": "manual exploratory testing; ~30 findings into GitHub Projects; drove triage at standup; ≥1/3 fixed",
  "traits": ["Drive", "Communication Skills"],
  "cv_line": "…the confirmed one-liner…",
  "story_ref": "s3"
}
```

Owner-scoping goes through `goalRepository.findByIdAndUserId`, like every other goal-owned
resource — a foreign `goalId` is a 404, and a cross-user isolation test is required
(`CrossUserIsolationIntegrationTest` is the pattern).

**`unclassified` is a real group, not a fallback.** The owner's rule: a requirement the model
cannot categorise must not be lost because of that. `unclassified` is split into must/nice
alongside `hard` and is worked through with it.

## 6. The phases

The application knows the phase; the model is told which phase it is in and is given only that
phase's instructions.

| # | Phase | What happens | Visible to the user |
|---|---|---|---|
| 1 | `profile` | If a profile note exists, read it and ask only what is missing or stale. Otherwise collect the identity block and propose it as a note | Yes |
| 2 | `vacancy` | Link (`read_url`) or pasted text. Detect the language. Get the addressee's name if the ad gives one. **Establish whether a cover letter is wanted** — from the ad, else ask; this sets `letter_required` and decides where motivation goes (§9.7) | Yes |
| 3 | `deconstruct` | Every requirement, paragraph by paragraph. Cluster the restatements. Classify into hard / soft / culture / unclassified; split hard + unclassified into must / nice. Draft the best/worst-performer profile | **No** — owner's explicit rule. The user is not shown the list |
| 4 | `evidence` | The interview loop. **One requirement (or one cluster) per turn, fed by the server** | Yes, with a counter |
| 5 | `draft` | The CV is assembled silently | No |
| 6 | `summary` | `SAMMANFATTNING` written **last**, from everything above it | No |
| 7 | `cv_review` | The finished CV as a note + **spoken aloud**: which points from the posting are absent, and why | Yes |
| 8 | `letter_strategy` | Assemble the four AIDA ingredients (§10.1) | Yes |
| 9 | `letter` | The letter as a note | Yes |

The order of phase 4 is fixed: **repetition clusters first** (the employer's own emphasis), then
the remaining `must` requirements and all `soft`, and only when those are done the `nice` ones.
That is the owner's ordering and Yate's priority logic agrees with it.

### 6.1 What the server injects during `evidence`

Every turn in phase 4, appended to the system prompt as **data**:

```
CURRENT REQUIREMENT — ask about THIS ONE and nothing else.  (7 of 19)
"Identifiera, rapportera och följa upp buggar och avvikelser på ett strukturerat sätt"

It was stated three times in the posting, in different words:
  • "Identifiera, rapportera och följa upp buggar…"                     (paragraph 4)
  • "…hjälpa till att reda ut och kommunicera kring produktionsproblem" (paragraph 2)
  • "Bidra till att förbättra testprocesser, testtäckning…"             (paragraph 6)
Say so: repetition is this employer's own emphasis, and it is the strongest
signal you have of what they actually care about.

ALREADY ANSWERED — never ask about these again: r1, r3, r4, r9
STILL TO COME: 12 more. Never mention, count, list or preview them.
```

This block is the whole design. Without it the rule is advice; with it the rule holds.

### 6.2 The interview questions — the Skills Analysis questionnaire

*(Resumes, ch. 4, pp. 33–39 — the full version. The Cover Letters book carries a condensed form
and says so.)*

**The governing principle first, because it is our architecture in the author's own words**
(*Resumes*, p. 32):

> Professional writers — and resume writers — have more in common with **sculptors**. What they
> really do to start the creative process is to write masses of notes. This great mass is the raw
> material, like a block of stone, at which you chip away to reveal the masterwork… **The more
> notes you have, the better.** Whatever you write in the note-making part of your resume
> preparation **will never suffer public scrutiny. It is for your private consumption**; from these
> notes, the finished work of art will emerge for public view.

That is the story bank and the CV, exactly (§4). The bank is the block of stone — large, private,
the user's own; the CV is what is carved out of it. It is also the answer to "why does the user not
see the draft": what they see is the **notes**, which are theirs, and the finished document. Not
the chiselling.

And the frame for every question below: **"Companies hire only one type of person — a problem
solver. Look at your work in terms of the problems you solve in the daily round, the problems that
would occur if you weren't there."** (p. 32) That last clause is itself a question worth asking
when a user cannot think of an achievement.

Yate asks this **per employer**. We ask **per requirement**, because we tailor to one posting and
because the per-requirement loop is what stops the list being dumped. His questions become the
**question bank inside a turn**; the agent asks one and follows up only where the answer has a hole
in it.

**Part One — raw materials, per employer:**

1. **Employer.** Dates. For a recent graduate or someone re-entering, this includes part-time and
   voluntary work — and **"try looking at your school as an employer and see what new information
   you reveal about yourself."** That sentence is the entry-level case the owner anticipated,
   solved.
2. **Company description** — one sentence on the products or services. (The `ARBETSLIVSERFARENHET`
   line in §9.4.)
3. **Title**, plus a one- or two-sentence description of the responsibilities.
4. **Duties** — the three major ones in this position.
5. **Methods, skills, results — for each of those three duties:**
   - What special skills or knowledge did you need to perform this task satisfactorily?
   - **What has been your biggest achievement in this area?** Think money made, money saved, time
     saved. *"Don't worry if your contributions haven't been acknowledged in writing and signed in
     triplicate, as long as you know them to be true without exaggeration."*
   - **What verbal or written comments were made about your contributions here, by peers or
     managers?**
   - **What different levels of people did you have to interact with to get the job done? How did
     you get the best out of superiors? Co-workers? Subordinates?**
   - **What aspects of your personality were brought into play?** — answered against the trait list
     in §6.3.
6. **Supporting points** — *"If you asked for a promotion or a raise while in this position, what
   arguments did you use to back up your request?"* An unusual question and a very good one: it
   retrieves the case the person has already built for themselves once, out loud, under pressure.
7. **Most recent position** — repeat 3–6 for each distinct title held with that employer.
8. **Reflecting on success**, per employer: the biggest work-related problem faced · the solution
   found · the result when implemented · the value to the employer in money earned or saved and
   efficiency gained · **the area of greatest personal improvement in this job** · the greatest
   contribution as a team player · **"who are the references you would hope to use from this
   employer, and what do you think they would say about you?"** — the indirect route to endorsement
   material when nothing was ever said out loud.
9. **Every employer**, back through the whole career, even though the finished CV covers the last
   three jobs or ten years. The surplus is not waste: it is interview preparation, and Yate names
   unpreparedness as one of the biggest causes of rejection.

**Part Two — details.** Facts only, no craft: education (highest first; scholarships and awards;
school activities and **especially leadership roles — "any example of how you 'made a difference'
with your presence"**, which matters most for recent graduates) · languages · **personal
interests** · computer literacy · patents · publications · professional associations and offices
held · **volunteer work** ("it isn't only paid work experience that makes you valuable") ·
anywhere else the person made a difference.

> **Personal interests have a filter, and it is what makes the Swedish `Privat` section work:**
> list interests "that could be **supportive to your candidacy**" — Yate's example is the internal
> auditor who plays chess or bridge, because those support the analytical bent the work needs. Not
> hobbies for their own sake; hobbies that argue for a trait.

**Part Three — where are you going.** This is the targeting step, and it is what our
`deconstruct` phase automates:

- Write the job title being aimed at, and under it the skills and qualifications needed to do that
  job successfully.
- Then **go back through Part One and flag every entry that can be used**. That flagging is exactly
  `record_evidence` (§7).
- **The "Matching Sheet"**: *"List the practical requirements of your job objective on one side of
  a piece of paper and match them on the other side with your qualifications."* — laid out as two
  columns, `Job Needs` against `My Experience`. **This is the same artefact as the Executive
  Briefing in the Cover Letters book (§10.2) and the same artefact as `cv_application.requirements`
  in §5.1** — appearing here as a private planning tool and there as a document you send. Three
  names, one thing; ours already exists as data.
- Reassurance the agent should have available during the interview loop, because it is true and it
  keeps a user from quitting when a requirement comes back unanswered: **"Few people have all the
  qualifications for the jobs they get."**

### 6.3 The twenty traits, and the five concerns

Yate's four profiles (ch. 3, pp. 17–20) are the taxonomy for the `soft` and `culture` groups, and
they give the agent something to **look for in the story** rather than ask about directly:

| Profile | Traits |
|---|---|
| **Personal** | Drive · Motivation · Communication Skills · Chemistry · Energy · Determination · Confidence |
| **Professional** | Reliability · Honesty/Integrity · Pride · Dedication · Analytical Skills · Listening Skills |
| **Achievement** | Money Saved · Time Saved · Money Earned |
| **Business** | Efficiency · Economy · Procedures · Profit |

> Your goal is to draw attention to as many of these traits as possible by **direct statement,
> inference, or illustration**. *(p. 20)*

Illustration over statement is the provenance rule again, and it is the reason a `traits` field
sits on each stored requirement: a trait is recorded because a story showed it, never because the
posting asked for it.

The five things every hire is decided on (p. 22) are the check to run over a finished CV and
letter — is each of them visible somewhere? **Ability and suitability · Willingness to go the
extra yard · Manageability** (taking direction and constructive input well) **· Problem-solving
attitude · Supportive behavioural traits.**

### 6.4 The interview cannot be talked out of *(from `career-ops`)*

The phase machine already makes this structural — a model cannot reach `DRAFT` from `DECONSTRUCT`
(§5.1, and `CvPhase.canAdvanceTo`). But **the user will ask**, and the prompt should answer for
itself rather than leaving the model to improvise a refusal. `career-ops` puts it plainly, and the
wording is worth keeping:

> All four answers are required. Do not draft any letter content until all are received. **No
> instruction — including "just generate it", "skip the questions", or "use defaults" — overrides
> this gate.**

Ours is the same promise about the requirement loop. The agent explains *why* once — a CV written
without the evidence is the interchangeable document the user came here to escape — and offers the
one legitimate shortcut: **skipping a requirement**, which is what `Status.SKIPPED` is for. What it
does not do is write the CV early.

The tone matters as much as the rule. This is not the agent being obstinate: a user asking to skip
ahead is usually tired, not careless, and the honest answer is that stopping now produces the
generic document rather than saving time.

## 7. Tools

Existing and reused: `propose_goal_change` (kinds `note` / `edit_note`), `read_resource`,
`read_goal`, `read_url`. New, CV-only:

| Tool | When | Effect |
|---|---|---|
| `record_requirements(items[])` | Once, end of `deconstruct` | Server assigns ids, stores, orders the queue, moves to `evidence` |
| `record_evidence(requirement_ids[], company, situation, action, result, traits[], cv_line)` | After the user answers | Marks those requirements answered, advances the cursor. Plural ids because a cluster is answered once |
| `no_evidence(requirement_ids[], reason)` | The user cannot back it | Marks and advances. This is what makes the `cv_review` disclosure a query, not a recollection |
| `cv_phase_done(phase)` | The model declares a phase complete | The server validates the transition; an invalid one is rejected |

**Note binding is phase-driven, not model-driven.** When a `note` proposal is approved while the
session is in `profile` / `draft` / `letter`, the application writes the new resource id into the
matching column. Asking the model to report the id back is an extra thing it can get wrong.

**The queue tools are offered PER PHASE** (added 2026-09-10 — `cvToolsFor`). Every phase keeps
`cv_phase_done`; `record_requirements` is offered only in `deconstruct` and
`record_evidence` / `no_evidence` only in `evidence`. Two measured reasons:

- **a tool a phase cannot use is a trap.** Both recording tools are refused outside their own
  phase, so offering them everywhere invites a call that comes back "finish that phase first" —
  and a model that does not know *which* phase to finish tries again. That is the looping the
  owner reported.
- **eight tools is too many for a small model to choose between.** Driven live against
  `ministral-14b-latest` (§12 — the only Mistral family her account serves), the writer called
  `propose_goal_change` readily and `cv_phase_done` **not once in fourteen turns**, so the
  session never left `profile`. Three changes went in together: the tool list per phase, the
  tool's own description rewritten as a duty ("MOVE THE WORK FORWARD — this is the only way
  anything progresses") rather than a capability, and the state block turning imperative the
  moment the server can *see* the phase is finished (`phaseReady` — a bound profile note, a
  recorded letter decision, an empty queue). A conditional instruction leaves a small model
  waiting for permission it already has.

## 8. Prompt files

Under `backend/src/main/resources/prompts/cv/`, loaded by `PromptResources`, **missing or blank
fails startup** — the same no-silent-fallback rule the coach method follows:

| File | Loaded in phases |
|---|---|
| `writer-method.md` — persona, the questionnaire, the twenty traits, the register and the Never list | all |
| `cv-format.md` — the template, section order, the one-page budget, the HTML rules | `draft`, `summary`, `cv_review` |
| `letter-method.md` — AIDA, the omissions list, the closing quote device | `letter_strategy`, `letter` |

Splitting by phase is a real token saving and follows the direction of commit `5639bc7`
("system prompt optimisation … to save tokens"): the CV format has no business being in context
during the interview, and the letter method has no business being there before the CV is done.

## 9. The writing rules

These go into the prompt files as an explicit **Never** list, because `coach-method.md` is the
evidence that such a list is what actually holds. Everything marked *(Yate)* is from the book.

**The provenance rule, in its agreed form** (owner, 2026-09-08):

> **The substance is the user's; the wording is the writer's.**

Traceability is about *facts and claims*, not about sentences. The user may be rambling, terse or
simply bad at articulating — rewriting them into CV register is the job, and copy-pasting their
answer is as much a failure as inventing one. Yate demonstrates exactly this transformation
(p. 29): "I learned to use a Wang 220VS" becomes "Analyzed and determined the need for automation
of an established law office … Within one year, I had achieved a fully automated office. This
saved forty hours a week." Same facts, written by a writer.

Two guards keep the rewrite honest:

- **Play the line back.** After each story the agent states the single line it will become and
  asks whether it is right. That is confirmation without exposing a half-built document — which
  reconciles "the user sees only the finished CV" with "the user can correct course".
- **Ask again rather than fill in.** If the answer has no result in it, ask for the result (that
  is question 4). Never supply one.

### 9.1 Measurable style rules *(Yate, ch. 4)*

- **Average sentence 10–20 words. Nothing over 20** — shorten it or split it in two. Vary the
  length so it does not read choppy.
- **Every paragraph under five lines**, most shorter.
- **Short words for short sentences.** One word where there were two. Avoid obscure words.
- **Sentences begin with or contain action verbs.** Yate lists 175+; a working subset goes in the
  prompt (*analyzed, achieved, automated, coordinated, delivered, eliminated, identified,
  implemented, improved, initiated, negotiated, reduced, resolved, saved, streamlined, trained…*).
- **Weed out jargon and acronyms — in the LETTER.** The first screener is usually a
  non-specialist, and "part of their job is to assist in the hiring of techies who can communicate
  with the rest of the human race" (*Cover Letters*, p. 36). **This does not apply to the CV's
  TEKNISK KOMPETENS block**, whose whole function is to be scanned by someone looking for the tool
  names — the distinction has to be stated in the prompt or the rule will be over-applied and gut
  that block. This was originally our inference; Yate says it outright for resumes: *"If you are in
  one of the high-technology industries, avoiding jargon and acronyms is not only impossible, it is
  often inadvisable. All the same, keep the nontechnical resume screener in mind before you wax
  lyrical about bits and bytes."* (*Resumes*, p. 26)
- **Pronoun discipline.** Do not begin every sentence with "I"; the mental focus is "you", not
  "I". Dropping pronouns ("Automated the office.") is allowed and reads less boastful.
- **Cut at the end, ruthlessly:** Can I cut a paragraph? A sentence? A superfluous word? Where
  have I repeated myself?

### 9.2 The Never list

- **No phrase from the posting appears verbatim** in the CV or the letter, beyond unavoidable
  proper nouns (tool, framework, certification and company names). **This is Yate's rule, not ours.**
  He tells the reader to consult the *Dictionary of Occupational Titles* when unsure what a job
  requires — and then: *"Make notes by all means, but **don't copy it out word for word. The book is
  full of dead prose that's copper-bottom guaranteed to send the average resume reader to sleep in
  three seconds flat.** You want to avoid letting any of this soporific stuff sneak into the final
  draft of your resume."* (*Resumes*, p. 38). He is describing a catalogue of job descriptions; a
  job posting is the same prose from the same pen, and copying it produces the same dead page. This
  is the single closest thing in either book to the defect the owner set out to fix, and it belongs
  in the prompt in roughly these words.
  - **The line is phrases, not terms.** Tool names, certifications, technologies and the job title
    itself must match the posting literally — an ATS matches terms, and Yate's own job-title rule
    (§9.6) says the exact title sought is what goes in. What must never be reused is his "dead
    prose": the posting's sentences, its adjectives, its way of describing the work.
- **No sentence enters the CV that is not traceable to something the user said.** No story, no
  line. This is what stops two candidates producing the same document.
- **No invented numbers.** Metrics only where the user gave one.
- **Banned register:** "not just X, but Y" and every em-dash contrast of that shape; triads;
  `leveraged`, `spearheaded`, `passionate about`, `proven track record`, `results-driven`,
  `dynamic professional`, `wide range of`.
- **No horizontal rules.** The note editor is TipTap StarterKit **3.22.4, which does bundle
  `@tiptap/extension-horizontal-rule`** — `<hr>` will render, so the ban must be explicit. It also
  bundles **no table extension**, which constrains §10.2.
- **No emoji** (already a project-wide rule).
- **Language splits.** The conversation runs in the user's language; the CV and the letter are
  written in **the vacancy's** language. GROW has no such split and its "answer in the language the
  user writes in" rule must not be copied here unqualified.
- **Length is a cut, not a budget — see §9.7.** *(This corrects an earlier draft of this spec, which
  said the page limit "drives selection while drafting". Yate says the opposite, and he is right
  about why: thinking about length while writing hampers the writing.)*

### 9.3 TEKNISK KOMPETENS

Agreed: the block is built **only from what the user confirmed**, in the user's own tool names
(an equivalent tool is named as the equivalent, not as the posting's tool). The posting's list is
used **as the checklist to ask against and is never written into the draft** — once a posting's
phrasing is in the draft it does not leave, and that is the contamination path.

The end state is the same as the owner's original "fill from the posting, then clean it"; the
derivation is the opposite, and the derivation is what the whole design is protecting.

At `cv_review` the agent says plainly which of the posting's technologies are absent from the CV
and why. The user may still be able to supply something — that is exactly why it must not pass
silently.

### 9.4 The CV template

**Order is the owner's; the look is `cv-exempel.pdf`'s.** The owner was explicit that the section
order should *not* follow basalt.se, and separately that the visual treatment should follow their
example file. Those are two different instructions and both hold.

```
Name Surname
Role
phone · email · websites

SAMMANFATTNING          (written last)
TEKNISK KOMPETENS
ARBETSLIVSERFARENHET    Role — Company, location, period
                        one line on what the company is
                        what the person's work there actually was
                        CAR bullets, each answering a named requirement
                        Teknik/miljö: …            ← from the example, see below
UTBILDNING
KURSER                  (when there are any)
SPRÅK
EGET PROJEKT / extras   (only when relevant to this role)
Referenser förmedlas gärna.
```

`SAMMANFATTNING` is not an introduction and not a repeat. It generalises across the body — it
carries what the employer emphasised most, states things that span several employers so the body
need not repeat them, and says what the candidate is looking for. Small, structured, with one idea.

**The example file has been read** (`C:\Users\buale\Downloads\Job search 2026\cv-exempel.pdf` — one
page, a real text layer, "Lisa Andersson, erfaren systemutvecklare"). Four things to take from it,
and one conflict to know about:

- **`Teknik/miljö:` as a line under each job.** The example names the stack *per employer* rather
  than only in a flat list. This is worth adopting for a reason beyond style: it anchors every tool
  to a place where it was used, which is the same guarantee §9.3 is protecting. Repeating a tool in
  both TEKNISK KOMPETENS and a job's `Teknik/miljö:` line is anchoring, not duplication.
- **Old, irrelevant jobs are bare one-liners** — `2012–2019 Butiksmedarbetare, ICA Handen.` with no
  description. An honest way to show continuity without spending page budget on it.
- **`Referenser förmedlas gärna.`** — exactly Yate's rule (mention availability, never list them).
- **`SPRÅK` and `KURSER` are their own sections**, not part of the contact block. The example also
  carries a `Privat` section of hobbies — **we do not use it** (owner, 2026-09-09), and neither the
  personal-interests line nor Yate's "personal paragraph" is offered. Anything from the `culture`
  requirement group goes to the letter, or to `SAMMANFATTNING` when there is no letter (§9.7).
- **The conflict:** in the example, the summary is *unlabelled prose* directly under the role, and
  **`Teknisk kompetens` sits near the end**, after Utbildning and Kurser — not second. The owner's
  stated order puts it second. Ours wins, per the instruction above; noted here so nobody later
  "fixes" the spec against the example file.

Formatting in the example is dead plain: no rules, no tables, no columns, no colour. That is the
target — and it is Yate's rule too, not just a taste: *"trying to do something out of the ordinary
with any aspect of your resume is risky business. For every interview door it opens, at least two
more may be slammed shut."* (*Resumes*, p. 5)

### 9.5 Which format — a decision, not a constant *(Resumes, ch. 2)*

There are **three** formats and no more; every "fifteen styles of resume" book is padding minor
variations of them. Two constant goals across all three: **show off achievements, attributes and
accumulated expertise to best advantage, and minimise any possible weaknesses.**

| Format | Who it is for | What it looks like |
|---|---|---|
| **Chronological** | Practical work experience, growth in one profession, no great number of job changes or long gaps. **Not** for someone straight out of school and **not** for a career changer — it would point at the weakness rather than the strength | Contact · job objective · career objective · career summary · education · work history tied to specific employers, titles and dates |
| **Functional** | Entry-level whose track record does not justify a chronological; career changers; long gap or a return to work; a stagnant stretch; a mature professional with a very large store of jobs | Skills first, employers and titles minor. Contact · objective · **functional summary** · dates · education |
| **Combination (chrono-functional)** | "The upwardly mobile professional with a track record… the strongest resume tool available" | Contact · job objective · **career summary** · **functional skills, with no reference to employers** · **chronological history** · education |

**The owner's template in §9.4 is the combination format**, and the mapping is close to exact:
`SAMMANFATTNING` is the career summary, `TEKNISK KOMPETENS` is the functional-skills block
(Yate's is explicitly employer-free, which is what our block already is), `ARBETSLIVSERFARENHET`
is the chronological history. So it stays the **default**, now with a name and a reason.

**What has to be added is one decision.** The owner's own brief anticipates the user who is
"looking for their first job", and for that user Yate's rule says the default format is the wrong
one. So before `draft`, the agent picks the format from the profile:

- relevant work history, no long gaps → **combination** (the default template, unchanged);
- first job, career change, a long absence, or a history that would read as thin against this
  posting → **functional**: the same sections, but the evidence is grouped **by skill** rather than
  by employer, and the dates move down the page.

Two rules that come with the functional variant and must not be lost:

- **Dates never disappear.** "A resume without dates waves a big red flag at every employer in the
  land." They are de-emphasised by *placement* — at the end, small, years only — never omitted.
- **The functional format needs a clear objective or it drifts.** Yate is explicit that it fails
  for someone looking for "a job, any job". We are always writing against one specific posting, so
  the objective is always present — which is precisely why the format is safe for us to offer.

The choice is stated to the user in `cv_review`, with its reason, in one sentence. It is a
judgement about how their history will read, and they may disagree with it.

### 9.6 What must go in, what never goes in *(Resumes, ch. 3, pp. 11–26)*

Yate's framing: a resume is a cake whose ingredients are mostly the same; the flavour comes from
the order and quantity. Some ingredients are always in, some are never in, some depend.

**Always in — the rules that change what our agent does:**

| Ingredient | The rule |
|---|---|
| **Name** | First and last only. No middle names, no initials, no nicknames in quotes. A title (Mr./Ms.) only when the first name is gender-ambiguous — so the employer's follow-up call does not open with an apology |
| **Job title** | **Not what the employer called you — a generic identification as many employers as possible will understand.** "Administrative Assistant" not "Secretary"; "Accountant" not "Junior Accountant Level II". Examine the current *role*, not the starting title. **Exception, and it is always ours: when you apply for a specific job whose title you know, the exact title sought is what goes in** — provided it is not misleading about capability. So the agent takes the posting's own title, and the honesty guard is the same one everything else runs on |
| **Company name** | Include it, with city; no street address or phone. **A brief description of the business line usually follows the name** — which is exactly the owner's "one line on what the company is". Where confidentiality matters, "A Major Commercial Bank" is acceptable |
| **Employment dates** | Always present, in some form. Where the history has short gaps, year-only (`2019–2022`) rather than exact dates. Be consistent |
| **Endorsements** | **A short third-party quote, even if the praise was never written down** — "Praised as 'most innovative and determined manager in the company'". **One or two, used sparingly, is very impressive; overkill sounds self-important and reduces the chance of an interview.** Most effective when the surrounding responsibilities carry facts and numbers. This is the same material as questionnaire question 6 and the letter's closing quote (§10.1) — one question, three uses |
| **Languages** | Mention even partial command, at its real granularity: *Fluent in French · Read and write Serbo-Croatian · Read German · Understand Spanish*. This validates the `SPRÅK` section taken from `cv-exempel.pdf`, and it matters for a Swedish posting that demands Swedish **and** English |
| **Education — position** | **Recently out of school with little practical experience → near the beginning**, because it is the primary asset. As experience accumulates it slides toward the end. This pairs with the format decision in §9.5 |
| **Education — content** | Reverse chronological, highest attainment first. Attended but did not graduate: list the school in its chronological place and do not draw attention to the missing degree. Graduated school, attended college without finishing → **list the college and omit the earlier history**, or the emphasis reads "drop-out". Recent graduates list majors, scholarships, societies, student roles; a seasoned professional is better off leaving majors out. "If you have five years of work experience and still feel compelled to list your chairmanship of the school's cafeteria committee, you are not concentrating on the right achievements" |
| **Professional training** | Courses and seminars under the education heading — validates the `KURSER` section |
| **Affiliations** | Professional ones. **Omit religious, political or otherwise controversial affiliations** — the resume shows the professional life, not the personal one. Community and charitable work is worth including space permitting, because it shows willingness to involve oneself |
| **Summer / part-time work** | Only for someone just entering the workforce or returning after a long absence — and the returnee should present the skills while minimising the "part-time" aspect, "probably by using a functional resume format" |

Two refinements from ch. 5 that sharpen the rows above:

- **Past titles are generalised; the sought title is exact.** Chapter 5 adds: *"stay away from
  designations such as trainee, junior, intermediate, senior — as in Junior Engineer — and just
  designate yourself as Engineer, Designer, Editor."* That applies to the **history**. The title at
  the head of the CV is the one the posting names. The two rules do not conflict — they are about
  different lines — but an agent given only one of them will get the other wrong.
- **The company description has a hard limit: "one line or ten words."**

**Never in.** These join the Never list in §9.2 and are the CV's counterpart to the letter's
omissions list in §10.3:

- **Salary, past or present — even when the ad asks for it.** If the user insists, the word is
  "competitive" or "negotiable", **and it goes in the cover letter, never in the CV.**
- **Reason for leaving a job.** Always covered at interview; on paper it can only cost.
- **Availability date.** Redundant; let it come up at the meeting.
- **Reference names.** Never listed. But Yate explicitly **disagrees** with dropping the closing
  line: *"References available upon request"* costs four words, does no harm, and sends "I have no
  skeletons in my closet" — which is precisely the `Referenser förmedlas gärna.` in the owner's
  example file.
- **Written testimonials attached to the CV** — "an absolute no-no. No one believes them anyway."
  They belong in the **story bank**, where they are the raw material for the one-line endorsement
  above. That is a clean division of labour for our three memories (§4).
- **Charts and graphs** — poor use of space and no help to the reader. With `<hr>` and tables, that
  is the whole of our formatting ban, now sourced.
- **Abbreviations**, except academic degrees. Spell things out; be consistent if space forces a
  contraction.
- **Age, race, religion, sex, national origin** — no reference to any of them.
- **Health or physical description** — *"Who cares? You are trying to get hired, not dated."* Unless
  it is immediately relevant to the work (a gym instructor, an actor).
- **Early background** — childhood and upbringing. *"The most generous excuse I can come up with for
  such anecdotes is that the resumes were prepared by the subjects' mothers."*
- **Demands** — never set out what the employer is expected to provide. That conversation belongs
  after an offer exists. *"Ask not what your employer can do for you, but rather what you can do for
  your employer."*
- **Weaknesses.** *"Never tell resume readers what you don't have or what you can't or haven't had
  the opportunity to do yet. Let them find that out for themselves."* See the correction this forces
  in §10.
- **Exaggerations.** Three resumes in ten inflate their educational claims and verification is
  increasing; being caught after hiring *"could have cost you your job and a lot more"*. The test
  question is usable verbatim in the prompt: **"Do I have a defensible position, should this matter
  come under scrutiny now or at a later date?"** For us this is not really a judgement call — the
  provenance rule already forbids anything the user did not say — but the sentence is a good way to
  explain the rule to a user pushing for more than their evidence supports.

**Judgment calls** *(pp. 28–30)* — neither in nor out; they depend on circumstances, and the agent
should decide with the user rather than silently:

- **The summary itself is optional.** Yate reports the disagreement plainly: some hold that the
  summary's content must be demonstrated by the body anyway, making it *"pointless duplication and a
  waste of space"*. He does not resolve it — *"used wisely and well, they can work."* We keep it,
  because the owner's template has it, but with his constraint: **two or three short sentences**, and
  — a rule worth having on its own — **"good summaries are short; you don't want to show all your
  aces in the first few lines."** The summary sets up the body; it does not spend it.
- **Relocation** — if the user is open to it, say so; **never state that they are not**. That only
  matters once an offer exists.
- **Marital status** — out, unless it genuinely helps for that specific job.
- **Military** — a good record with rank, for the defence sector; otherwise out.
- **Personal interests and a "personal paragraph" — NOT USED** (owner, 2026-09-09: *"приват и фото
  не нужно"*). Yate treats both as optional judgment calls anyway, so declining them costs nothing
  against the source. Recorded here only so a later reader knows it was a decision, not an
  oversight: he cites a Korn Ferry study finding executives who listed team sports averaged $3,000 a
  year more, and allows a short personal paragraph tied to traits the job needs. **The agent must
  not offer either, and must not invent a hobbies section if a user's old CV happens to have one.**
  Whatever the `culture` requirement group turns up goes to the letter instead (or to
  `SAMMANFATTNING` when there is no letter — §9.7).

**No photograph** (owner, 2026-09-09), which is also Yate's rule: *"the fashion is against
photographs; including them is a waste of space that says nothing about your ability to do a job."*
Swedish practice often differs, so this is a deliberate choice rather than an oversight — and the
owner's own reference CV has none either. The agent never adds one and never suggests one.

**Three places where Yate cannot simply be followed, and the prompt must say why:**

1. **"Never head the document 'Resume', 'Fact Sheet' or 'Curriculum Vitae'"** — he calls CV
   "outmoded and affected". That is a claim about US usage in the 1990s. In Sweden in 2026 the
   document *is* a CV, postings ask for one, and the owner's own example file is headed
   `CV- Lisa Andersson`. **Take the underlying rule — do not put a redundant label on a document
   that is obviously what it is — and drop the terminology claim.**
2. **"Keep the job objective broad and non-specific."** His reasoning is filing and retrieval: a
   narrow objective gets a generally-distributed resume filed in one narrow place, or excluded.
   **That reasoning does not apply to us** — we write against one posting every time, never for
   general distribution, and his own job-title exception says the exact title sought is what
   belongs. So: the specific title, always. Worth recording because the modern version of his
   filing worry is ATS keyword matching, and it cuts the same way — see §9.2 on terms versus
   phrases.
3. **The date-massaging advice.** Year-only dates to smooth over short gaps is a presentational
   choice a person may make about their own history, and Yate is explicit that it is not licence to
   lie. **An agent proactively coaching someone on how to obscure a gap is a different act from a
   book suggesting it.** So: year-only is our default date *format* for everyone, which achieves the
   same thing without singling anyone out — and the agent never volunteers it as a technique for
   hiding something. If the user raises a gap, it is answered honestly.

### 9.7 Writing the body *(Resumes, ch. 5, pp. 41–56)*

**The frame.** A resume is read in under thirty seconds, and both resumes and advertisements
"depart from the rules that govern all other forms of writing. First and last they are an **urgent
business communication**, and business likes to get to the point." Write like a copywriter: what
features does the product have, and **what benefit does each give the purchaser**. You may assume
the employer has a position to fill and a problem to solve, and will hire someone **able to do the
job, willing to do it, and manageable**.

**The one transformation to put in the prompt verbatim** (pp. 44–45). Yate's before-and-after is
the clearest statement in either book of what our agent is for:

> **Before** — Sales Manager: Responsible for writing branch policy, coordination of advertising
> and advertising agencies. Developed knowledge of IBM PC. Managed staff of six.
>
> **After** — Sales Manager: Hired to turn around stagnant sales force. Successfully recruited,
> trained, managed and motivated a consulting staff of six. **Result: 22 percent sales gain over
> first year.**

What was wrong with the first: *"she has explained where she was spending her time… She has
mistakenly listed everything in the reverse chronological order, not in relation to the items'
relative importance to a future employer."* What is right about the second: *"she thought like a
copywriter and quickly **identified the problem she was hired to solve**."*

From which the governing rule, and it is counter-intuitive enough that the agent will drift off it
without being told:

> **The responsibilities you list are the ones that best display achievements and problem-solving.
> "They do not necessarily correspond with how you spent the majority of your working day."**

So the interview must never ask *"what did you spend most of your time on?"* It asks what problem
the person was there to solve.

**Quantifying.** *"Business has very limited interests… reduced to a single phrase: making a
profit. Making a profit is done in just three ways: by **saving money**; by **saving time** through
some innovation, which in turn saves money; or by simply **making money**."* That is the frame for
"what was the result?", and it gives the agent three specific places to look when a user says they
have no numbers. It does **not** license ignoring contributions that cannot be quantified — "but it
does mean that you should try to quantify as much as you can".

**How much per job.** *"Pick **two to four accomplishments for each job title** and edit them down
to bite-size chunks that read like a telegram. **Write as if you had to pay for each entry by the
word.**"*

**Never explain HOW** (p. 46) — new, and the opposite of what a thorough writing agent will do
unprompted:

> While you may tell the reader about these achievements, **you should never explain how they were
> accomplished.** The idea of the resume is to pique interest and to raise as many questions as you
> answer… show the reader a little glimpse of the gold vein and let them discover the rest of the
> strike later… you have saved some of your heavy firepower for the interview, so that the meeting
> will not be an anticlimax.

This pairs with the story bank: the full CAR story is kept, and the CV deliberately publishes only
its head.

**The strongest bullet shape in the book** — a quantified fact followed by an attributed quote,
which is questionnaire question 6 landing in the CV:

> Sales volume increased from $90 million to $175 million. Acknowledged as *"the greatest single
> gain of the year."*

**Style, all measurable:**

| Rule | Value |
|---|---|
| Sentence length | under 20 words, **average around 15**; longer → restructure or split. Vary the length so it does not read choppy |
| Paragraph length | **under five lines**, many considerably shorter; in a functional/combination skills block, **an absolute maximum of four** — that is what keeps the white space |
| Words | short for long, one where there were two; common words, nothing obscure, without sounding infantile |
| Verbs | sentences begin wherever possible with an action verb. The list is 175+ (p. 50): *accomplished, achieved, administered, analyzed, automated, built, coached, completed, conceptualized, coordinated, created, cut, decreased, delegated, designed, developed, devised, diagnosed, eliminated, established, evaluated, expedited, facilitated, generated, identified, implemented, improved, increased, initiated, innovated, inspected, instituted, integrated, introduced, launched, maintained, monitored, negotiated, organized, prioritized, produced, recommended, reconciled, recorded, reduced, resolved(solved), restructured, revitalized, saved, scheduled, screened, streamlined, strengthened, summarized, supervised, systemized, taught, trained, upgraded, validated, wrote* |
| A useful device | a short phrase, then a colon, then bullets that each support it |
| Name/acronym dropping | never — *"Worked for Dr. A. Witherspoon in Sys. Gen. SNA 2.31."* is how you lose a reader. Jargon stays out **except in the education section and in a genuinely technical field** (the third statement of the §9.1 carve-out) |

**Voice and tense.** Yate reports genuine disagreement among experts and does not resolve it —
"use whatever style works best for you" — but the mechanics matter for us:

- **Dropping pronouns and articles** ("Automated the office") saves space **and lets the writer
  claim things without sounding boastful**: it reads as though someone else is writing about them.
  That is the default for CV bullets.
- If the first person is used, **not in every sentence** — monotonous and expensive in space.
- The variation he likes: **third person through the body, then a few closing words in the first
  person** to show what the person values.
- And the sentence that should govern the whole anti-slop section:
  > Many people mistake the need for professionalism with stiff-necked formality. The most effective
  > tone mixes the conversational and the formal, just as we do in our offices… **the only
  > overriding rule is to make it readable, so that another person can see the human being shining
  > through the pages.**

**Drafting.** *"Now, just write. **Don't even try for style or literacy — you can tend to that
later. Think of yourself as speaking on paper.** You'll find your personal speech rhythms will make
for a lively resume, once they have been edited."* This is our interview design from the other end:
the user speaks, the writer edits. It is the same rule as "the substance is the user's, the wording
is the writer's" — and it is a reason to let a user ramble rather than cutting them off.

**Length — and the correction.**

> **"The accepted rules for length are one page for every ten years of your experience"** — over
> twenty years, still no more than two. Three or four pages only when an employer asked for a
> dossier directly.
>
> **"Thinking too much about length considerations while you write will hamper you. Think instead
> of the story you have to tell, then layer fact upon fact until it is told. When that is done, you
> can go back and ruthlessly cut it to the bone."**

So the page limit is applied **after** the draft exists, not as a selection filter during it —
which is the opposite of what an earlier version of this spec said (§9.2). The cut runs on four
questions: *Can I cut a paragraph? A sentence? A superfluous word? Where have I repeated myself?*
And: **"If in doubt, cut it out — leave nothing but facts and action words."**

Note that for the owner's likely users — early career — "one page per ten years" and "one page"
coincide, so nothing changes in practice; what changes is **when** the constraint is applied.

**The proofreading checklist (pp. 54–56) becomes the `cv_review` gate.** It is a ready-made final
pass and the agent should actually run it before showing anything, rather than improvising a check:

- **Objective** — supported by the facts and accomplishments in the rest of the CV? (An objective
  the body does not evidence is the same defect as a bullet with no story behind it.)
- **Summary** — no more than **two or three sentences**; contains **at least one substantial
  accomplishment** supporting the goal; **refers to personality or behavioural traits critical to
  success in the field**.
- **Body** — most relevant experience prioritised; no reasons for leaving; no salary; no
  availability; discreet about the current employer.
- **Education** — correct position for the amount of experience; highest attainment first;
  professional courses that support the candidacy included.
- **Chronology** — reverse chronological; each employer starts with the most senior position held
  there; irrelevant titles and responsibilities dropped; **contributions and solved problems made
  prominent**; **at least one, possibly two or three, laudatory third-party endorsements present**;
  extraneous material eliminated; volunteer or community work included where it lends strength;
  reference lists absent; **"long enough to whet the reader's appetite, yet short enough not to
  satisfy that hunger"**; not headed "RESUME".
- **Writing style** — the table above, checked item by item.

**Two collisions with the owner's brief — both decided by the owner, 2026-09-09:**

1. **Motivation is conditional on whether a letter exists.** Yate's objective must "focus on what
   you can do for the company and avoid mention of what you want in return" (p. 43), and his scheme
   puts motivation in the cover letter. But **not every application asks for one** — owner: *"иногда
   бывает что письмо не требуется, тогда мотивацию нужно в sammanfattning"*. So:

   | A cover letter is being written | Motivation lives in the letter (§10.1, Interest + Desire). `SAMMANFATTNING` stays on what the candidate brings |
   |---|---|
   | **No letter** | `SAMMANFATTNING` carries the motivation, because otherwise it is nowhere and the application says nothing about why this employer |

   This needs a field: whether the posting asks for a *personligt brev* is established during
   `vacancy` (from the ad, or from the user), stored on `cv_application`, and read when the summary
   is written in phase 6. **The summary cannot be written before that flag is known** — which is
   already true of the phase order, but it is now load-bearing rather than incidental.

2. **The summary follows the book: two or three sentences**, containing at least one substantial
   accomplishment and a reference to the behavioural traits that matter in this field (owner: *"делай
   как по книге, я не профессионал"*).

   **One honest reconciliation is needed between the two decisions.** Three sentences carrying an
   accomplishment *and* traits *and* motivation is over-packed. The rule is therefore: **three
   sentences with a letter, four without** — the extra sentence exists only to carry what the missing
   letter would have said, and it is the last one. That keeps the book's discipline in the normal
   case and does not silently drop the owner's requirement in the case the book does not cover.

### 9.8 The worked example — one person, three documents *(Resumes, pp. 101–105 and 271–276)*

The book carries one completed questionnaire (Jane Swift, pp. 271–276) and the **same material
rendered as all three formats** (pp. 101–105). It is the only end-to-end example in either book and
it settles several layout questions the prose leaves open.

**Layout: a label column.** All three formats set the section names in a **left-hand column**
(`SUMMARY:`, `EXPERIENCE:`, `EDUCATION:`, `OBJECTIVE:`, `REFERENCES:`) with the content indented to
the right of it. That is not decoration — it is what lets a thirty-second reader jump to a section.
Our note editor cannot do a real two-column layout, so this becomes a **bold section label followed
by its block**, which is the same reading order.

**Several titles at one employer are stacked, then narrated from the top down:**

```
Technical Aid Corporation                         1988-1995
National Consulting Firm. MICRO/TEMPS Division

    Division Manager     1993-1995
    Area Manager         1990-1993
    Branch Manager       1988-1990

As Division Manager, opened additional West Coast offices… Sales increased to $20 million, from $0.
    • Achieved and maintained 30% annual growth over a 7-year period.
    • Maintained sales staff turnover at 14%.
As Area Manager, opened additional offices, hiring staff…
As Branch Manager, hired to establish the new MICRO/TEMPS operation…
```

Most senior first — which is the proofreading checklist's own requirement ("does each company
history start with details of your most senior position?"), here shown in practice. The prose
paragraph carries the shape of the job; the bullets carry the numbers.

**The functional and combination formats use SKILL HEADINGS**, and the example names them:
`SALES`, `RECRUITING`, `MANAGEMENT`, `FINANCIAL`, `PRODUCTION`, `MARKETING`. Each is one short
paragraph. In the functional version `WORK EXPERIENCE` then shrinks to dates, employer, city and
title with **no descriptions at all** — that is how dates stay present but de-emphasised (§9.5).
For our QA case the equivalent headings would come from the requirement clusters, not from a fixed
list.

**Three things the example settles for us:**

- **The objective can be a bare category.** The combination version's is literally
  `OBJECTIVE: Employment Services Management`. That is the whole line.
- **Contact details repeat on page two** — `Page 2 of 2, Jane Swift, (617) 555-1212` — which is the
  checklist item, shown.
- **`REFERENCES: Available upon request.`** appears as its own labelled line, exactly as
  `Referenser förmedlas gärna.` does in the owner's Swedish example (§9.4).

**Two cautions, and the second one matters.**

- **The combination layout uses a horizontal rule** to divide the functional half from the
  chronological half. Our formatting ban (§9.2) forbids `<hr>` — and the ban stands: the owner's
  complaint was about rules appearing everywhere as an AI tell, and one structural divider in a
  1990s typeset page is not a reason to reopen it. Use **whitespace and the section label** to make
  the same break; that is what the label column is for.
- **The book's own example is looser about facts than we will be.** The raw questionnaire says the
  division was "now generating $18,000,000 in revenues"; the finished resume says "Sales increased
  to $20 million dollars, from $0 in 1984" — and 1984 does not appear anywhere in the questionnaire's
  dates (which start in 11/88). Whether that is an authoring slip or intentional rounding, **our
  provenance rule (§9) is deliberately stricter than the example**: a number in the CV is the number
  the user gave, and nothing is carried across from a different answer. Say so in the prompt, because
  a model shown this example will otherwise imitate the drift.

**What the completed questionnaire is really for** is calibrating **how short a raw answer may be**.
Yate's fill-in is telegraphic throughout — three duties as `A) Selling software services to clients.
B) Interviewing applicants… C) Setting up interviews…`; the personality answer as five fragments
(`strong willpower · stick-to-it attitude · high achiever · aggressive · high goal setter`); the
manager's comment as `Hard-working, stay-at-it attitude.` None of that is CV prose, and none of it
needs to be. It is the block of stone (§6.2). **The agent must accept answers at this level and do
the writing itself** — which is the practical form of "the substance is the user's, the wording is
the writer's", and the reason the interview must never send a user away to compose something
polished.

### 9.9 A fact gate that is not a prompt *(from `career-ops`, MIT — see §3.4)*

Everything in §9 so far is an instruction to the model, and an instruction is something a
model can drift away from on turn forty. **One check should not be.**

`career-ops` ships `verify-cv-facts.mjs`: a deterministic, read-only, **no-LLM** checker that
extracts claims from the generated document and compares them against the user's own source
files, returning `pass` / `warn` / `block` — and `block` stops the document being produced. We
should have the same thing, and we are in a better position to build it than they are, because
our "source of truth" is already structured: `cv_requirement.gist` and `cv_line`, plus the
story-bank note. Theirs has to mine free-form Markdown.

**What it checks, in the order of how much it is worth:**

1. **Every number in the CV must appear in the evidence.** This is the single highest-value
   check, because an invented metric is both the loudest AI tell and the one thing that will
   get a candidate caught. Their implementation extracts a figure bound to a counted noun
   ("45 staff", "50k users") and their code comments carry the bug history worth learning from
   without repeating: `50k users` once normalised to the claim `50 users`, letting a
   thousand-fold inflation through the gate, and a greedy quantifier bound "15+ years scaling
   teams and platforms" to *platforms* rather than *years*, so a truthful line copied straight
   out of the source was reported as invented.
2. **No claim of authorship the evidence does not support.** Their nicest check: the document
   says *built / developed / implemented* where the source says the work was *commissioned,
   coordinated, managed, outsourced to a vendor*. That is a real and very common inflation, and
   it is exactly what a rewriting agent does by accident when it compresses a story.
3. **Every employer, title and tool named in the CV appears in the profile or the evidence.**

**How it fits our design.** The gate runs before the CV note is proposed, over the assembled
HTML, against the application's own answered requirements. A `block` means the draft is rebuilt,
not that the user is shown a warning — the point is that the model cannot argue with it. It is
also the natural home for the check §9.8 says our provenance rule needs and the book's own
example does not model.

> **BUILT, 2026-09-10 — `CvFactGate`, and it is narrower and softer than the three checks
> above. Both departures are deliberate.**
>
> **Narrower: numbers only.** Check 2 (authorship) and check 3 (employers, titles, tools) were
> dropped, because in *our* design they fight two rules that are load-bearing here and are not
> in `career-ops`:
> - the writer's whole job is to say the client's substance in **its own words** (§9.8's
>   "wording is yours"), so a token-level check against what the client typed flags the
>   rewriting rather than the invention. If she says "the browser automation tool from
>   Microsoft" and the CV says "Playwright", that is the feature working;
> - the method **requires** tool names, certifications and the job title to match the advert
>   exactly (§9.2's "phrases, not terms") — so checking them against the client's own words
>   would flag the very matching the CV needs to do.
>
> A number is the one class of fact that survives paraphrase: 40% is either something the
> client gave you or something you invented. Three kinds are ruled on — percentages, money, and
> anything of three digits or more — and a one- or two-digit number on its own is not checked,
> because those are version numbers ("Java 17"), small counts and list markers. **Percentages
> are checked strictly**: the corpus must contain that number *as a percentage*, or "cut
> defects by 40%" passes on the strength of "40 minutes" sitting elsewhere in the interview.
> That was caught by a test written against the weaker rule, which is why the rule changed.
>
> **Softer: the card is still shown, and the doubt goes to the USER.** Rebuilding the draft
> costs a turn, can loop, and throws away a whole document over one number; suppressing the
> card leaves the user with nothing to react to. Nothing is saved until they approve it, so the
> writer says, in the same turn: *"these figures are not in anything you have told me: 40%,
> 1400. Give me the real numbers, or tell me to take them out."* The model cannot prevent that
> sentence appearing, which is the property that mattered.
>
> **The corpus is the recorded evidence plus the profile note and the story bank — never the
> advert.** Including the advert would license an employer's numbers passing as the candidate's,
> which is the fabrication being guarded against.
>
> **It runs only from `draft` onwards.** The profile note and the story bank ARE the corpus, and
> the first live run proved why that matters: a profile note carrying 2022, 2023 and 2024 was
> flagged four times over, because at `profile` there is no recorded evidence yet and the note
> being proposed is not bound until the user approves it. Material cannot contradict itself.

**Two structural rules for the CV, also from their ATS checker**, and both are about parsers
rather than taste:

- **Contact details belong in the body flow, not in a header block.** Parsers routinely drop
  `<header>`/`<footer>` regions, so an email that lives only in the letterhead can vanish.
- **Normalise punctuation to ASCII** — em dashes, smart quotes, zero-width characters. We ban em
  dashes anyway (§9.2), but the normalisation is the belt to that braces.

Their ATS guidance is otherwise a confirmation of what §3.0 already established: *"optimise for
parseability and human review, not ATS hacks — no hidden text, no keyword stuffing, no white-font
tricks, no decorative layouts."*

### 9.10 The banned-phrase list, merged

Ours (§9.2) plus theirs, which is longer and was clearly assembled from real output:

**Everywhere:** `passionate about` · `results-oriented` / `results-driven` · `proven track record`
· `strong track record` · `leveraged` (use "used", or name the tool) · `spearheaded` (use "led" or
"ran") · `facilitated` (use "ran" or "set up") · `synergies` · `robust` · `seamless` ·
`cutting-edge` · `innovative` · `demonstrated ability to` · `best practices` (name the practice) ·
`in today's fast-paced world` · `dynamic professional` · `wide range of` · the
"not just X, but Y" contrast and every em-dash variant of it · triads.

**In the letter, additionally:** `holistic` · `championed` · `orchestrated` · `excited` ·
`stakeholder alignment` · `data-driven` (say what the data drove) · `actionable insights` ·
`move the needle` · `north star` · `unique opportunity` · `perfect fit`; and the filler openers
`I am pleased to` · `I am writing to express` · `I am excited to`.

**Weak bullet openings** (theirs, and it complements Yate's action-verb list from the other
direction): `helped` · `assisted` · `responsible for` · `worked on` · `participated in`.

**And the one-line test for the whole feature**, which is the best single sentence either project
has for the owner's original complaint:

> Before finalising, re-read each sentence: **could it appear in any cover letter for any
> company? If yes, rewrite it.**

## 10. The cover letter

**A correction to the brief, and it is sourced.** The owner described the letter as a literary
genre with an idea that rises to a high point. The *arc* is right — Yate's four steps are exactly
a rising structure. The *register* is not: the letters that actually worked were, in Yate's words
from a set of over four thousand collected from HR people and headhunters,

> all businesslike, with no gimmickry or cuteness. Some may even seem a little dry to you, but
> remember: They worked. *(p. xiii)*

The reader gives it **under thirty seconds** (p. 21). So: one idea, developed, arriving somewhere —
but written as urgent business correspondence, not as an essay. "Say it strong, say it straight,
and don't pussy-foot" (p. 2).

### 10.1 The arc is AIDA *(Yate, ch. 1)*

Four steps, and the `letter_strategy` phase exists to assemble the ingredients for each:

| Step | What it does | What the strategy phase must produce |
|---|---|---|
| **Attention** | Addressed to **a person by name**, never a title and never "To Whom It May Concern" — "a resume without a cover letter rarely gets any further than the trash can. A 'To Whom It May Concern' letter fares little better" (p. xii) | `contact_name`, or an explicit decision that the ad gives none |
| **Interest** | The first sentence grabs; the rest of the paragraph says what you have to offer. Evidence of **research on this company** is what gets the letter off to a fast start | One concrete thing about this employer the user actually knows or found |
| **Desire** | Tie yourself to the job category, then a short paragraph on one or two special contributions or achievements | The requirement(s) the CV served worst — see the anti-duplication rule below |
| **Action** | Say you want to talk; when, where and how you can be reached; propose the next step "clearly and without either apology or arrogance" (p. 23). Brevity — leave the reader wanting more | The specific next step, and whether the user will commit to following up on a stated date |

**One more question for the strategy phase, borrowed from `career-ops` (§3.4), because it is the
one thing no other applicant's AI can produce:**

> **How would you approach it? In one or two sentences: what is your opening move if you join on
> day one?**

It cannot be answered out of the advert, it cannot be answered out of a template, and it is the
sharpest possible expression of Yate's Desire step. Ask it after the employer's main problem has
been named, so the user has something to answer *about*.

**A word budget, which we lacked:** `career-ops` sets the letter body at **350–420 words**,
excluding the header. That sits comfortably inside Yate's "one page, never more than two" and
inside the thirty seconds a reader gives it, and it is a number the agent can actually hold itself
to while writing.

**The anti-duplication rule is Yate's own, not an invention of ours** — this is the sentence the
owner's instinct was reaching for:

> If an advertisement (or a conversation with a potential employer) reveals an aspect of a
> particular job opening that is **not addressed in your resume, it should be included in your
> cover letter.** *(p. 3–4)*

So the letter's centre of gravity is precisely what the one-page CV had no room for. It may take
at most one story already in the CV, and only to develop it further than the CV could. It must
never restate `SAMMANFATTNING`.

**The closing device** (p. 33), and it is why questionnaire question 6 matters: write the letter in
the first person, then close with a **few final words in the third person, as an attributed
quote** — *"She managed the automation procedure, and we didn't experience a moment of down time."
— Jane Ross, Department Manager.* Use it only when the user actually supplied such a comment.

**Weak flanks: discussed with the user, never named in the document.** *(Corrected 2026-09-09. An
earlier draft of this spec said gaps should be "answered in the letter, not hidden" — that is wrong
by the source.)* Yate, *Resumes* p. 27: **"Never tell resume readers what you don't have or what you
can't or haven't had the opportunity to do yet. Let them find that out for themselves."** The
strategy conversation about where this candidate is weak stays — it is what decides where the letter
spends its space — but the letter's answer to a gap is to **outweigh** it, never to raise it. No
pre-emptive apologies, no "although I have not yet worked with X". This is consistent with the
`cv_review` disclosure (§6), which tells **the user** what was left out; that is a different
audience from the employer.

### 10.2 The executive briefing — a third, optional document

Yate's own variation on the cover letter (ch. 2, pp. 10–12): a two-column list, **"Your
Requirements" against "My Skills"**, point by point, under one short opening sentence and one short
closing one. He is explicit that it is appropriate only when the job's specific requirements are
known — which is exactly our case, and **it is the artefact `cv_application.requirements` already
is**. Offering it as a third note costs almost nothing.

The same object appears in the resumes book as the **"Matching Sheet"** (*Resumes*, p. 39) — `Job
Needs` against `My Experience` — but there it is a *private planning tool* for deciding whether you
qualify at all. So: one artefact, three roles. Our table is the Matching Sheet while the interview
runs; rendered out, it is the Executive Briefing the user may send. That is why building it costs
nothing extra.

Why he built it (p. 10): the first screener often does not understand the job; a general CV is
"one-size-fits-all" and fits none; and without it, different interviewers at the same company end
up interviewing you for different jobs.

**It cannot be an HTML table** — StarterKit bundles no table extension (§9.2). Render it as paired
bullets, requirement then evidence, which is also what survives being pasted into an application
form.

### 10.3 What must stay out *(Yate's checklist, pp. 35–36)*

- **No reasons for leaving a job.** "Use this precious space to sell, not to justify."
- **No salary** — past, current or desired — unless the ad requested it. If requested, give a
  range, never a single figure, and it goes on the letter, never on the CV.
- **No availability date.**
- **No "Letter of Application" heading.**
- **No list of references** — mention availability only.
- **No irrelevant responsibilities or job titles**, and no education unless it is relevant to this
  ad.
- **Contact details on every page.**
- Long enough to whet the reader's appetite, short enough not to satisfy it. One page; never more
  than two.

## 11. UI

### Web

- The composer's left chip becomes **AI assistants** — a dropdown built on
  `src/components/ui/dropdown-menu.tsx` (Design → Components and chrome §6), two items:
  **GROW coaching session** and **CV & cover letter**. The GROW label moves into the menu; the chip
  itself no longer names a session type.
- **The second item's glyph needs the owner's approval.** Gravity's `file-text` is the closest
  candidate; per the icon rule, if nothing in Gravity suits, the owner picks it — the agent does
  not draw one or reach into another set.
- **The CV start card** is a sheet with the `Primary` (Kale) head, per §3e: the vacancy URL or
  pasted text, and — when the goal already holds applications — a list to resume one. No duration
  picker; there is no clock.
- **In session:** the timer pill's slot carries the phase and the counter ("Requirements 7 / 19").
  Attachments are enabled. The left action is **Pause** (leave, keep everything); discarding an
  application is a separate confirmed action, not the primary control.
- Every note the agent writes arrives as an ordinary proposal card the user approves — nothing is
  written without approval, and approval opens it in the note editor.

### Android

Parity, using `SpiraDropdownMenu` and `SpiraSheetHead(tone = Primary)`, with the same strings.
Per the project rule, an Android change ends with `distributeDebug`.

## 12. Risks and open questions

- **Two things the model puts in documents that no prompt reliably stops** (2026-09-10, from
  the notes it actually proposed):
  - **our own fence markers.** A writer that has read a fenced advert copies
    `<<UNTRUSTED_CONTENT>>` into its own output; one proposed profile note opened with that
    literal line. Now stripped in code before a proposal is persisted or surfaced
    (`stripFenceMarkers`) — plumbing must never reach a person's document, and stripping
    beats rejecting because the note itself was fine.
  - **emoji.** A proposed profile note carried 📍📧📞 despite the ban in §9. Not fixed:
    a deterministic strip is possible and was not built in this pass, because unlike the
    fence marker an emoji is the model's own content and silently editing what a user is
    about to approve is a different kind of act. Open.
- **The model is a constraint, not a preference — and the obvious answer was not available**
  (measured 2026-09-10). The owner's sessions were rambling and looping on
  `ministral-14b-latest`, so the plan was to move her to `mistral-medium-latest`. Driven
  against her own key, **every mid-size Mistral model is refused**: `mistral-medium-latest`,
  `magistral-medium-latest` and `mistral-small-latest` all answer "Rate limit exceeded" and
  keep answering it after our retries, while `ministral-14b-latest` and `ministral-8b-latest`
  answer normally. `GET /v1/models` lists all of them, so the model list is not the
  entitlement — this is a per-model quota on the account, and no code change reaches it.
  What follows:
  - **the prompt has to work on a ~14 B model.** A rule three hundred lines into
    `writer-method.md` does not survive one: with the turn-length rule only there, the first
    reply was 895 characters, a seven-item numbered list with bold labels — the wall of text
    the owner complained about, reproduced exactly. The rule is now stated in **three
    places**: compressed at the very top of `CV_ROLE`, in full in the method, and once more
    as the last line of `CV_PLUMBING`. Small models weight the beginning and the end.
  - **Gemini and Cohere are the fallbacks she actually has**, and her Gemini key works
    (`gemini-3.7-flash`). A provider recommendation belongs in the UI rather than in a
    comment; not built.

- **`edit_note` replaces the whole body.** Every CV revision re-emits the complete document as a
  tool argument. That is expensive, and a truncated stream produces a truncated CV. Mitigation:
  write the note at the end of `draft` and on explicit revision requests only — never
  continuously. This needs measuring on a real CV before it is trusted.
- ~~**`read_url` fails on many job boards**~~ — partly solved, 2026-09-10. Three boards that
  render the advert in the browser now have their JSON read instead (`UrlReadService.JobBoard`):
  **Workday** (`/wday/cxs/{tenant}/{site}/job/{slug}`), **Greenhouse** and **Lever**. The owner's
  own test vacancy was a Workday posting returning **zero** readable text as HTML and 3,884
  characters through its CXS endpoint — verified live. Two things go with it: a page yielding under
  200 characters of HTML text is now reported as no page at all, rather than handing the model a
  cookie banner to read as the job, and the fall-back to "paste the text" says which page and what
  would work. Still open for every other board, and a board that changes its API falls back to
  reading the page.
- **The one-page budget cannot be verified from HTML.** Approximate as a bullet/character budget
  and check the approximation against a real print.
- **Yate is from the 1990s** — checked, and answered in §3.0. The method holds and has 2025
  empirical support; the era's furniture (stationery, postal campaigns, "I'll call you Friday")
  must be stripped, and three modern facts added: ATS ranks rather than rejects, verification has
  moved to assessments and portfolios, and AI-generated sameness is now the field the CV competes
  in. A prompt that tells a Swedish applicant in 2026 to promise a phone call on Friday will read
  as absurd.
- ~~**Photographs**~~ — decided: no photo, and no `Privat`/personal-interests section (owner,
  2026-09-09). See §9.6.
- **OPEN — does a gap the employer will certainly notice get addressed, or ignored?** Yate says
  never state a weakness (§10.1). `career-ops` instead **detects the gap and asks the user how to
  handle it** — domain mismatch, notice period, a stated language level, a title mismatch — with
  "don't mention it" as one of the offered answers, and then "write only what the user confirms".
  These are not as opposed as they look: Yate wrote for a CV posted blind, where a gap is invisible
  until someone looks for it, whereas a posting that says *flytande svenska krävs* has already named
  the test. **Recommendation:** keep "never volunteer a weakness" as the default and add one
  exception — where the advert states a hard, checkable requirement the user cannot meet, ask them
  how they want it handled rather than deciding in silence. Needs the owner's call.
- **A deleted goal's applications** are cascade-deleted by the FK, but the notes they produced are
  the goal's resources and go with it — confirm that is the wanted behaviour.
- **The sources are closed.** Both books' method chapters and the worked example are read; the
  remaining open question in this list is the photograph, above.

## 13. Build order

1. ✅ `SessionKind` enum replacing `isGrow`; `sessionType: "cv"` accepted end to end; the AI
   assistants menu on web with both items live.
2. ✅ `cv_application` table + service + owner-scoped REST, following `ai/grow/session/`.
3. ✅ `prompts/cv/*.md` + `PromptResources` loading them + per-phase assembly.
4. ✅ `record_requirements` / `record_evidence` / `no_evidence` / `cv_phase_done`, and the server's
   per-turn `CURRENT REQUIREMENT` block. **This is the step that makes the feature work**; steps
   1–3 without it reproduce exactly the output the owner already has.
5. ✅ Profile note + story bank (2026-09-10). The plumbing was there from step 2 —
   `profile_note_id`, `stories_note_id`, binding by phase, `pruned()` self-healing — and
   **nothing in the prompt ever asked for a story bank, so none was ever made.** Both notes are
   now described in the method ("The two notes that are the client's memory"), and the bank is
   tied to the requirement table by a `story_handle` on `cv_requirement`: the writer coins a
   short kebab-case name per story and passes the SAME one again when an earlier story answers a
   later demand. The handle is slugged server-side, so one story cannot hide behind two
   spellings — which is the only thing that makes reuse visible.
6. 🟡 `cv_review` disclosure, `letter_strategy`, `letter` — the prompts and the phase machine
   carry all three; the phase is now shown in the panel's header (a count during the interview,
   a stage name otherwise), so what is left is a live run through the letter.
7. ✅ The executive briefing (§10.2), 2026-09-10 — a `briefingBlock` pairing every requirement
   with the line that answers it, **in the advert's own order** (`inAdvertOrder`, sorted by
   `source_paragraph`), offered once at `done` and skipped outright when there is no requirement
   list. The optional multi-posting deconstruction (§3.1) arrived with it, from the other end:
   it is how an open application gets a requirement list at all (§6.x).
8. ⬜ Android parity — **still the one part not built.** The key-delete affordance and the model
   list are done there; the CV mode itself is not.
9. ✅ The conversation is on the server (2026-09-10). `cv_application.transcript` plus
   `GET/PUT /cv/applications/{id}/transcript`: localStorage keeps a sitting through a closed
   panel, this keeps it through a changed device. The client drops the oldest messages and
   leaves attachment bytes behind; the server refuses an over-long transcript rather than
   truncating it, because half a JSON document loses the whole conversation rather than its
   newest part.
10. ✅ The deterministic fact gate (§9.9), 2026-09-10 — `CvFactGate`, and it checks **numbers
    only**, on purpose. See §9.9 for why names and technologies are out of scope, and for the
    one design change: the card is still shown and the doubt is put to the USER, who is the
    only one who knows the real figure, rather than refused back to the model.

## 15. What building it changed about the design

Four deviations from the sections above, all deliberate, recorded here rather than left for a
later reader to discover as inconsistencies.

**0. THE PHASE MACHINE DOES NOT ASK A MODEL TO DRIVE IT** (2026-09-10, and it is the largest
change the design has taken). §6 has the model declaring each phase over with
`cv_phase_done`, and the phases are described as instructions it follows. Driven against a
real model that is a real user's only option, that is not a machine — it is a request:

- **Run 1** stalled at `deconstruct`; **run 2** reached `evidence` with 17 requirements
  extracted and recorded **none** of the seventeen answers; **run 3** stalled at `vacancy`,
  where the writer re-proposed a profile note it had already had approved. Same code, same
  prompt, same scripted conversation. Before that, with the tool list at eight and the
  instructions merely clear, `cv_phase_done` was called **not once in fourteen turns**.

So three transitions moved into the server, where they were always facts it could see:
`profile` ends when the user approves the profile note, `vacancy` ends when the letter
decision is recorded (`record_letter_decision`, a new tool that exists so that phase has
something to record *other* than a phase move), and `evidence` ends when the last
requirement is settled. `cv_phase_done` keeps only the transitions that need judgement —
leaving the draft, the summary, the review and the letter all turn on whether a document is
finished and whether the person is happy with it, which nothing on the server can know.

This is the same principle the requirement queue already rested on, applied one level up:
**anything the server can observe, the server decides.** A model is asked for judgement and
for prose, never for bookkeeping.

Three smaller findings came out of the same runs, each recorded where it belongs: the tool
list is now per phase (§7), the state block turns imperative when the server can see a phase
is finished (§7), and `record_requirements` requires only each requirement's `text` — all
five fields were required, and seventeen five-field objects is the largest structured output
this feature asks for.

**And two invariants that only a live run could have suggested**, both now server-side rules
rather than instructions: `deconstruct → evidence` is refused with an empty queue (a model
declared itself into the interview and then asked questions off the advert from memory —
the failure this whole design exists to prevent, reached through a transition that was
legal), and `cv_phase_done` moves **one** phase per turn, whatever the model sends.

**A review of the same work found two more, and both were mine.** The fence-marker strip
was applied to a local variable that the persistence branch then overwrote from the raw
arguments, so on the only path that matters it did nothing — while its unit test stayed
green, because it tested the pure function rather than the method. And the first fix of the
CSAM false positive silently lost the spacing-evasion coverage it was meant to keep: with
the term compiled literally and a twelve-character floor on the separator-free fallback,
"k i l l m y s e l f" matched neither form. Terms are now compiled with a separator allowed
between every letter, anchored at a word start — one shape that needs no length floor, so
there is no length at which coverage quietly stops.

**1. Requirements are their own table, not a JSON column** (§5.1 sketched `requirements JSONB`).
`record_evidence` updates ONE requirement; against a blob that is a read-modify-write of the whole
list on every answer, and two turns racing would silently lose one. "How many are still pending"
and "which cluster is next" become queries rather than hand-written scans, and `jsonb` has no clean
H2 equivalent — the trap `book_chunk` already documents. Reasoning is in
`V23__cv_application.sql`'s own comment, where the next person to read the schema will find it.

**2. The interview queue has four tiers, not three.** §6 says "clusters, then must + soft, then
nice". `CULTURE` is none of those: it is needed, but it feeds the letter rather than the CV's body,
and the letter comes after. It sits in its own tier between the must-haves and the nice-to-haves.

**3. One turn function serves both assistants.** `sendGrow` became `sendSessionTurn`, with the
CV path passing `cv: true`. They share the proposal pipeline (deletes opened, creates
de-duplicated, superseded ones rejected server-side), the streaming placeholder, and the rule that
an error removes the empty bubble rather than filling it in — duplicating that for a second
assistant is how the two would drift on the parts nobody looks at. What stayed GROW-only is the
clock and everything hanging off it: `wrapUp`, `goodbye`, `proposals`, `end_session`.

**4. The CV start card mirrors `GrowStartOverlay` rather than using `SheetHead`.** §11 asked for the
`Primary` head. Both start cards inside the AI panel predate that system and neither uses it;
converting one would have made the two assistants' opening cards look like different apps, which is
the exact defect the head rules exist to prevent. Converting **both** is a separate change.

### What the code review caught

`/code-review medium` found nine defects after the first pass; all are fixed and each carries a
test. Three are worth remembering because they are the shape of thing this design invites:

- **Two silently destroyed the user's work.** Re-reading an advert mid-interview replaced the
  requirement queue — deleting every answered row — and, because the phase move ran in a second
  transaction that could still be refused, reported "could not be stored" over the wreckage. And an
  unrecognised `next` phase parsed to `PROFILE`, which a backward move is always allowed to reach,
  so a truncated tool argument rewound a twenty-question interview to the identity block and
  answered "Moved to profile." Both are now refused outright; `CvPhase.parse` is strict for
  anything a model supplied and `from` stays lenient only for our own stored values.
- **A cluster answered in part looped forever.** The tool asks for every id in a cluster and
  nothing made the model comply; with one of three marked, the next turn rebuilt the identical
  question with the counter unmoved. `recordEvidence` now settles the whole cluster, which is what
  "a cluster is one question" was supposed to mean.
- **The CV session inherited the coaching transcript.** `enterCv` clears the transcript and sends
  in the same tick, so the turn read the *previous* value — and `leaveGrow` deliberately never
  clears it, so opening the writer after a GROW session replayed the whole coaching conversation to
  it as opening history. The same class of bug as the application id, which had already been found
  by hand; both are now explicit overrides rather than reads of not-yet-rendered state.

Also fixed: `web_search` was offered to a CV session with no key behind it; model-supplied
`cluster`/`company`/`traits` reached a `VARCHAR` untruncated and could fail a whole deconstruction;
`letter_required` persisted from a refused transition; a missing application was reported as a
phase-order problem; and the CV composer shared the plain chat's saved draft.

### The second review round

A second `/code-review` on the same diff found ten more, all fixed. They fall into two families,
and both are worth naming because they are what this kind of feature invites.

**The first family: a second session type shares state a single one never had to think about.**
`inGrow` had been the panel's word for "a session is open", and eight places still used it that way
after the CV writer arrived — so in a CV session the panel rendered the plain chat's **empty state
on top of the writer's turns**, updated **proposal cards in the wrong transcript** (Accept fired the
server call, the card stayed pending forever, and no "Open" shortcut appeared), sent a **revision
into the invisible chat**, and let an async `resumeFromServer` **replace a live CV session with a
restored GROW one mid-stream**. The distinction the code needed was between "a GROW session"
(`inGrow` — the timer, the End button) and "any session" (`inSession` — which transcript, which
empty state, which resume). Every one of those is now `inSession`, and the ones that are genuinely
GROW-only are left alone deliberately.

**The second family: the prompt is built once, before the turn.** `cv_phase_done` was a looping
tool, so a model could move to `draft` and carry straight on in the same turn — writing the CV with
neither the format rules nor the evidence block in context, out of conversational memory. That is
precisely what the phase machine exists to prevent, arriving through the phase machine itself. A
phase move now **ends the turn**, which is also the better rhythm: the writer says "I have
everything — shall I put it together?", and the next turn is the one that carries the rules for
doing it. Only `record_requirements` still crosses a phase mid-turn, because the interview needs
nothing the writer's method does not already carry.

Also fixed: the `letter` phase got the letter's method and **no material to write it from**;
`record_requirements` bypassed the phase guard the plumbing prompt promises, so a model in
`profile` could deconstruct the advert and skip the step that establishes whether a letter is
wanted at all; **advert wording was replayed unfenced** in the current-requirement and
not-evidenced blocks, on every turn of a twenty-question interview, so an instruction smuggled into
a job advert would have been extracted as a "requirement" and then presented as though it came from
us; `record_evidence` swallowed its refusals into a generic message and, being a looping tool,
retried the same wrong ids until the iteration cap killed the turn; and opening the writer while
the chat was still streaming **silently dropped the opening turn**, leaving an empty session with a
disabled composer.

**The web fixes have no automated guard.** Catching them needs a rendered `AiPanel` or a Playwright
run against the live stack with a real model behind it. They were found by review and fixed by
reading; the honest statement is that the backend half of this feature is tested and the panel half
is not.

### What live testing found, and the one cause under most of it

The owner ran the writer against a real vacancy. Five complaints, and four of them turned out to
be the same defect wearing different clothes.

**The cause: the session was not persisted at all.** Everything else follows —

- *"I minimised the chat to look at the resources and the whole session vanished."* The panel
  unmounts when closed. Fixed above.
- *"You fixed nothing — now profile repeats three times."* It was not a double-submit (that was a
  real bug and was fixed, but a different one): a lost session forces a fresh start, and every
  fresh start made a row.
- *"There is no way to delete a session."* True, and it made the duplication permanent. The
  Continue list now has a per-row delete behind a second tap. The notes an application produced
  are goal resources and are deliberately **not** deleted with it — a finished CV outlives the
  session that wrote it.
- *"If such a session already exists, say so and ask whether I really want another."* Creating now
  answers **409 with the existing application** when the goal already has one for the same advert
  — matched on the URL, or on the derived title when there is none. Neither is a database
  constraint on purpose: the same address can carry a different job a year later, so this is a
  question, not a refusal. `?force=true` makes the second one.

**And one that was purely a wording defect:** the Continue row printed the raw phase name, so it
read *"QA-testare … profile"* — which looks like it is about the user's own profile rather than
about that vacancy. Phase names are internal machinery and belong nowhere near a user; the row now
says where the work stands ("Not started", "7 of 19 answered", "CV ready").

**Two smaller ones from the same session:** opening an application spent a model turn, so two
curious taps on a rate-limited free tier produced an error about waiting while nothing was wrong —
only a *new* application opens with a turn now. And a provider's retry delay arrived as
`57.827332745s`; nine decimal places of a wait nobody can time is not information.

### Known gaps, in the order they will be noticed

- ~~**A resumed application opens with an empty transcript.**~~ **Fixed, and it mattered far more
  than this entry implied.** Closing the panel unmounts it (`if (!isOpen) return null`), which
  destroyed `mode`, `cvApp` and the transcript — so **minimising the chat to look at the goal's
  resources dropped the user out of the session entirely**, and starting again created another
  application. Three rows for one vacancy, created at 10:23, 11:05 and 21:11 on 2026-09-09 — hours
  apart, which is what proves it was three fresh starts and not a double-tap. The session is now
  cached per goal in `localStorage` (which application, and the conversation) and restored on mount
  with **no model turn**; everything else was already server-side. **Still device-local:** GROW
  mirrors its session to `ai_grow_session` so it follows the user between devices, and the CV
  writer does not.
- ~~**Nothing shows which phase the work is in.**~~ **Fixed.** The header carries the interview's
  count in the slot the coach's timer occupies, and a short phase word before there is a count to
  show. The application is re-read after every CV turn, because the count and the phase both live
  on the server and a copy taken when the session opened is stale within a turn.
- ~~**Note binding is not wired.**~~ **Fixed.** The store's `onCreated` gives the real resource id
  at the one moment it exists, and the note is reported to the application — which files it by the
  phase it is in **now**, server-side. The client must not decide that: it holds the phase it
  opened the session with, which by the time a CV exists is several phases old. Phases that produce
  no document bind nothing, so a note the user happens to save while the advert is being read stays
  their own rather than becoming their CV.
- **The opening turn's application id has no automated guard.** `enterCv` calls `setCvApp` and
  sends in the same tick, so the turn function's own `cvApp` is still `null` — the id is passed
  explicitly instead. It was found by reading, not by a test: catching it needs either a rendered
  `AiPanel` or a Playwright run against the live stack, and the CV lifecycle is not extracted far
  enough to unit-test on its own. That extraction is the fix, not another assertion.
- ~~**No fact gate**~~ — built 2026-09-10 (`CvFactGate`), narrower and softer than §9.9 sketched;
  the reasoning for both departures is at §9.9 itself. Every OTHER anti-fabrication rule is still
  only an instruction in a prompt file, which is what a model drifts away from on turn forty — and
  two of them are now known to be drifted from in practice: emoji in a proposed note, and our own
  untrusted-content markers copied into one (§12).

## 14. Tests that have to exist

Following the precedent of `AiChatServiceGrowTest` (which uses the **real** `PromptResources`, not
a mock — a mock would only prove that a mock returns what it was told to):

| Test | Proves |
|---|---|
| `PromptResourcesCvTest` | The three CV prompt files load, are not stubs, and contain the rules named here — the Never list, the provenance rule, the no-`<hr>` rule, the AIDA steps, the jargon rule's CV carve-out |
| `AiChatServiceCvTest` | A `cv` session gets the writer method and not the coach method; a `grow` session gets neither CV file; the `CURRENT REQUIREMENT` block reaches the prompt; the requirement list itself never does; the letter method is absent before the letter phases |
| `CvApplicationServiceTest` | The queue orders clusters → must+soft → nice; `record_evidence` on a cluster marks every member; `no_evidence` still advances; an invalid phase transition is rejected |
| `CvCrossUserIsolationIntegrationTest` | Another user's application is a 404 on read and on write |
| `LoggingConventionTest` (existing) | Still green — vacancy text and the user's stories are the user's own content and must never be logged. `record_evidence` handlers are the obvious place this gets broken |
| Web Vitest + a Playwright spec | The menu opens and both items start their own session type; a CV session persists across a reload |
| `CvFactGateTest` | An invented percentage, an invented volume and an invented money figure are caught; a reformatted figure the user DID give passes; a total reword passes (only numbers are checked); the matcher itself is proved against the code that shipped broken |
| `TextNormalizerTest` | Word boundaries survive a long paste — the Swedish advert that was refused as CSAM is allowed, and `b o m b`-style evasion is still folded |
| `markdown-ink.test.ts` | Nothing in the chat's `Markdown` paints white ink on the light bubble. A convention test, because no rendering assertion in this repo can see a colour — and this shipped invisible |

**What testing at this level cannot prove, and what had to be run instead.** Every table row
above was green while the feature was unusable: the phase machine never advanced, the interview
recorded nothing, and the writer asked the user to paste an advert the app was holding. Those are
properties of a *model driving the system*, and the only way to see them is to drive it — see
§12 and §15.0. The scripted session used for that lives outside the repo (it needs a live key and
a live job board), so what protects these findings is the tests written from each one, listed
above and in `AiChatServiceCvTest`.

---

*Sources. Primary and read: Martin Yate, **Cover Letters That Knock 'em Dead** (chapters 1–4) and
**Resumes That Knock 'em Dead** (chapters 1–5 plus the worked example, pp. 101–105 and 271–276),
both Adams Media — page numbers cited above are the books' own. Peer-reviewed:*
[Wingate, Robie, Powell & Bourdage, "The Signals That Matter: Resumes, Cover Letters, and Success on the Job Search", *International Journal of Selection and Assessment* 33(3), 2025](https://onlinelibrary.wiley.com/doi/10.1111/ijsa.70022) ([preprint](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=6277978)).
*On the 2026 environment (commercial sources — directional, not measurements):*
[ATS myths and what recruiters actually do](https://www.wahresume.com/blog/resume-myths-debunked-what-ats-systems-actually-do-and-dont-do-in-2025) ·
[AI's impact on hiring, 2026](https://resumegenius.com/blog/ai-impact-on-hiring-2026) ·
[why cover letters are not dead, Forbes, Nov 2025](https://www.forbes.com/sites/josephliu/2025/11/11/hiring-experts-reveal-why-cover-letters-arent-dead-yet/).
*Prior art, read as code:*
[career-ops-hq/career-ops](https://github.com/career-ops-hq/career-ops) (MIT) — specifically
`verify-cv-facts.mjs`, `modes/_writing.md`, `modes/heuristics/recruiter-side.md`, `modes/ats.md`
and `modes/cover.md`. Ideas borrowed are marked at the point of use (§3.4, §6.4, §9.9, §9.10,
§10.1); no code or prose is copied.
*Read second-hand:*
[Kogan Page — Target Job Deconstruction, by Martin John Yate](https://www.koganpage.com/business-and-management/your-secret-weapon-the-target-job-deconstruction-by-martin-john-yate) ·
[CAR format, Distinctive Career Services](https://www.distinctiveweb.com/resume-writing/car-format-resume-writing/) ·
[CAR / STAR / PAR compared, Vitae Express](https://www.vitaeexpress.com/new-blog/2025/4/21/transforming-resume-bullets-with-car-star-and-par-models) ·
[basalt.se — så skriver du ett bättre CV](https://jobb.basalt.se/pages/sa-skriver-du-ett-battre-cv-for-att-soka-jobb-inom-it-och-teknik) ·
*Not read:* Yate, *Resumes That Knock 'em Dead*.
