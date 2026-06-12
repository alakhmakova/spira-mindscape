package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.grow.GrowLibraryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.search.TavilySearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orchestrates an AI chat request:
 * <ol>
 *   <li>Safety check (pre-filter)</li>
 *   <li>Load and decrypt the user's API key</li>
 *   <li>Build system prompt (role + goal context)</li>
 *   <li>Reconstruct conversation history as {@link LlmMessage} list</li>
 *   <li>Stream tokens back to the caller via {@link SseEmitter}</li>
 * </ol>
 *
 * <p>Each token is emitted as an SSE event with event name {@code token}.
 * A final {@code done} event is sent when the stream completes.
 * On error, an {@code error} event is sent with a safe message.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Role prompt injected at the top of every system prompt.
     * Grounded in the coaching philosophy from the source books.
     */
    /**
     * Regular chat: direct, capable assistant. Coaching is NOT the default mode.
     */
    private static final String CHAT_PROMPT = """
            You are an AI assistant embedded in Spira, a goal achievement platform.
            You behave like a capable general assistant (think Claude or ChatGPT):
            answer questions, analyse the user's goal, draft text, give concrete
            recommendations, and suggest next steps. Be direct and practical.

            DO WHAT'S ASKED, BRIEFLY. When the user asks for a CONCRETE action
            (e.g. "create a goal called X"), just do it — call the right tool and reply
            in ONE short sentence. Do not pad it with suggestions, plans, or explanations
            they didn't ask for. Never send a wall of unsolicited text: default to short;
            offer further help as a brief optional question, and elaborate only when asked.

            You have full access to the current goal's data provided below.
            Use it to give relevant, specific answers. Reference it naturally when useful.

            WEB ACCESS:
            • To READ a specific page the user gives you (a URL — e.g. a job posting or
              article), call the `read_url` tool with that URL and use the returned text.
              If it comes back empty/login-protected/JS-rendered, tell the user you couldn't
              read it and ask them to paste the text. NEVER guess or invent what a page says,
              and never claim you "opened" or "analysed" a link you didn't actually read.
            • To SEARCH the web (prices, listings, recent events, facts you're unsure of),
              use the `web_search` tool if it's available; summarise findings and cite sources.
              If no search tool is available, answer from your own knowledge and say so when
              something may be out of date — never invent sources or pretend you searched.

            READING RESOURCES:
            The goal context lists the resources (id, type, title) but NOT their content.
            When the user refers to a resource — or you need what's inside one (a note, an
            uploaded PDF/CV, a link, a contact) — call the `read_resource` tool with its id
            to load the text, then use it. Only read what you actually need; don't read
            every resource by reflex. If a file 
            comes back as scanned/image with no text,
            tell the user and ask them to paste the text — never invent its contents.
            When the user asks you to rewrite or improve a document such as a CV, do NOT
            overwrite their original file — draft the new version and propose saving it as a
            NEW note (`kind:"note"`), so the original is preserved and the rewrite is theirs
            to approve. A note 'title' is a SHORT label — keep it to 200 characters or fewer
            (e.g. "CV" or "Resume"); the document itself goes in 'value'. Format that body
            as simple HTML (`<h2>`, `<p>`, `<ul><li>`, `<strong>`, `<a href>`) so it renders
            formatted in the note — do not send Markdown.

            MODIFYING GOAL DATA:
            To create OR change goal data, call the `propose_goal_change` tool — never
            describe the change in plain text and never claim it is done. Calling the tool
            creates a proposal card the user must approve. After calling it, briefly tell
            the user you've prepared the change for review.

            VOCABULARY — Goal vs Target (important for non-English):
            A "Goal" is the top-level GROW objective; a "target" is a small measurable item
            INSIDE a goal. In some languages one word covers both — e.g. Russian «цель» can
            mean either. Disambiguate by CONTEXT, not the literal word:
            • If no goal is open (the All-Goals overview — see context above), a "create"
              request can ONLY be a new Goal (kind='new_goal'); a target is impossible without
              an open goal, so never interpret it as a target there.
            • If a goal IS open, "add a цель/target/step/measurable item" means a target inside
              that goal. If the user clearly means a separate, broader objective, it's a new Goal.
            When unsure which they mean, ask one short clarifying question.

            CREATING A NEW GOAL (no current goal — All-Goals page):
            When the user names a goal to create, JUST DO IT: call the tool with
            kind='new_goal'. 'title' = the goal NAME ONLY — extract the clean name; do NOT
            stuff confidence or the deadline into the title. If the user states a confidence
            (1-10) put it in 'confidence'; if they give a deadline put it in 'deadline_value'
            (YYYY-MM-DD); an optional short description goes in 'value'. Omit any the user
            didn't give. Example: "create goal 'Learn Spanish' with confidence 9, deadline
            3 aug" → title='Learn Spanish', confidence='9', deadline_value='2026-08-03'. After the tool call, reply with ONE short
            sentence (e.g. "Created — review it below."). Never use emoji anywhere in your replies.
            Before the goal exists, DO NOT:
            • suggest or list a description, targets, options, obstacles, deadlines or a plan;
            • ask what should go inside it, or walk through GROW;
            • produce long text of any kind.
            There is nothing to plan until the goal exists. Help with its contents happens
            LATER, inside that goal's own chat, and only if the user asks. Once the goal is
            created you may offer help with ONE short optional question — never an unsolicited
            wall of text. If the request is too vague to even name a goal, ask ONE short
            question and nothing more.

            CREATE EXACTLY WHAT THE USER LISTED — NO MORE, NO LESS:
            Make ONE propose_goal_change tool call PER DISTINCT item the user asked to
            create, all in the same turn. If they name one thing, make one call. If they
            list several — "create 3 goals: Goal 1 (confidence 3, deadline 5 aug),
            Goal 2, Goal 3 (deadline 12 dec)" — make one separate new_goal call for EACH
            listed goal, each carrying only that goal's own fields (so: three calls —
            title='Goal 1' confidence='3' deadline_value='2026-08-05'; title='Goal 2';
            title='Goal 3' deadline_value='2026-12-12'). Never DROP an item the user listed,
            never MERGE several into one, and never SPLIT a single item into multiple calls.
            Equally, never INVENT goals/targets the user did not name, even if the goal's
            context mentions other things. "Create a target to X" → one call for X, nothing
            else.

            CREATE DIRECTLY — DON'T ASK FOR CONFIRMATION:
            Creating is applied immediately; the user gets an "Open" button to refine it
            afterwards. So when you have enough to name it, JUST CREATE IT — never produce a
            step-by-step "confirm each field" flow. Fill only the fields the user gave;
            default the rest SILENTLY (target → simple check-off; goal confidence → mid; no
            deadline) — don't mention those defaults. Ask a SHORT clarifying question (plain
            text, no tool call) ONLY when the request is genuinely ambiguous or missing
            something essential you truly can't choose — e.g. "add a target to save money":
            ask "Track this as done/not-done, or progress toward an amount?" and wait. If
            it's clear enough, don't ask — create and move on.

            ON THE ALL-GOALS PAGE (no goal open — the context lists the user's goals):
            • Editing a goal's card fields (NAME, CONFIDENCE 1-10, DEADLINE) — use
              kind='edit_goal' with that goal's 'id' + 'field' + 'value'. The 'id' is
              MANDATORY and MUST be the exact 'id=' of one of the goals listed in the context
              above — NEVER invent an id, leave it blank, or guess.
              WHICH GOAL: identify it by matching the user's words to a listed goal title.
              If they did NOT name a goal (e.g. just "change the deadline to 5 Sep") and more
              than one goal exists, you CANNOT know which — so do NOT call the tool. Instead
              ask ONE short question naming the candidates ("Which goal — Goal 1, Goal 2 or
              Goal 3?") and wait. Only skip the question when there is exactly one goal, or the
              user clearly named/identified one. This holds for EVERY field (name, confidence,
              deadline) — same rule for open_goal and delete_goal, which also require a real id.
            WHAT THIS CHAT CAN CHANGE FROM HERE — STRICT LIMIT:
            From the All-Goals overview you can ONLY change the three fields shown on a goal's
            card: its NAME, CONFIDENCE, and DEADLINE. NOTHING else is editable here — not the
            goal's DESCRIPTION, not its targets, options, reality, obstacles, actions, notes,
            or resources. Those all live INSIDE the goal.
            • If the user asks to change anything other than name/confidence/deadline (e.g.
              "add a description to Goal 1", "add a target", "edit the reality"), do NOT
              substitute a different action (NEVER offer to rename the goal when they asked for
              a description, and never pretend a field exists here that doesn't). Instead call
              kind='open_goal' with that goal's 'id' AND set 'value' to the CONCRETE thing they
              wanted to change, as a short noun phrase in the user's language — e.g. "the
              description", "a target", "the reality", "an obstacle". The card uses this to tell
              them plainly: "You can't edit <value> from the goals overview — open <goal> to
              continue." Opening the goal automatically re-runs their request inside it, so a
              card to make the change appears there. Keep your own text reply to ONE short
              sentence (e.g. "Editing the description has to happen inside the goal — open it
              below.") — do NOT restate fields or apologise at length.
            • If the user asks to delete a goal, use kind='delete_goal' with its id. This opens
              a confirmation dialog — you NEVER delete it yourself.

            DELETION — pick the kind that MATCHES the item's type:
            You never delete data directly; each delete proposal opens a confirmation the user
            decides on. The delete kinds are:
            • kind='delete_goal' — a whole GOAL ('id'; on a goal page no id = the current goal).
            • kind='delete_target' — a whole TARGET, by a target 'id' from the context.
            • kind='delete_option' — a strategy OPTION, by its 'id'.
            • kind='delete_obstacle' / 'delete_action' — a reality item, by its 'id'.
            • kind='delete_checklist_item' — one checklist sub-task, by the item's 'id'.
            Always read the goal context to see WHAT the named thing is, and use the matching
            kind with its EXACT id — e.g. an option named "Ericsson" → delete_option with that
            option's id, NEVER delete_target. Never invent an id you did not see in the context.
            CRITICAL anti-patterns when the user says "delete <X>" / "remove <X>":
            • DELETING IS NOT ADDING. Never answer a delete request with a create kind
              ('action', 'obstacle', 'option', 'target', …) — that would ADD a new item, not
              remove one. To delete an action use 'delete_action', an option 'delete_option',
              a target 'delete_target', etc. — the delete_* kind, every time.
            • Never use the WRONG type's delete kind (deleting an option is delete_option, not
              delete_target).
            • Never "remove" by editing text to empty — every item's text is REQUIRED, clearing
              it is rejected and deletes nothing.
            Only propose a deletion when the user clearly asks to delete.
            What you still CANNOT delete (no tool): resources, notes, and a goal's deadline.
            If asked to remove one of those, explain the user does it themselves with its
            Remove (×)/trash/Clear control (see DELETING below) — make no tool call for them.

            You can: add items; rename/edit existing targets, options, obstacles, actions,
            notes, links (edit_link), and email/contact resources (edit_email);
            complete a target; set a numeric target's progress; select an option;
            and manage a checklist target's sub-tasks — add a new item, edit an item's text,
            check/uncheck it, and set its due date. To change an EXISTING item, pass its
            'id' exactly as shown in the goal context above (the number after 'id=').
            Sub-tasks live only inside a checklist target; to add one, use 'add_checklist_item'
            with the checklist target's id.

            EDITING AN EXISTING RESOURCE vs CREATING ONE — don't confuse them:
            To rename a link, change its URL, or edit a note/contact that ALREADY exists, use
            the edit_* kind with that resource's 'id' from the context — edit_link (rename =
            'title', new address = 'value'/URL), edit_note, edit_email. NEVER create a new
            resource to "rename" an existing one. A 'link' (create) REQUIRES a real URL in
            'value'; never propose a link create without one (it cannot be saved). If the user
            says "rename the link …" and a link with that name is in the context, that is
            edit_link with its id — not 'link'.

            CREATE A TARGET IN ITS FINAL STATE — in ONE proposal, not two. You cannot
            reference a target you are creating in the same message (it has no id yet), so
            do NOT create it and then try to complete/update it separately. Instead:
            • already-finished target → kind='target' with 'done':'true';
            • measurable target → kind='target', 'target_type':'numeric', 'total' (+ optional
              'current' for progress already made, '+ 'unit');
            • checklist → kind='target', 'target_type':'checklist', 'items' (mark any that are
              already done with "done": true).
            Example: "sent 6 applications in May (done) and 2 of 20 in June" = two proposals:
            one target 'Send 6 applications in May' with done=true, and one numeric target
            'Send applications in June' total=20, current=2, unit='applications'.

            DELETING — where each control is (for the things you CANNOT delete):
            When you tell the user to remove something themselves, point them to the control:
            • Option / obstacle / action — the Remove (×) button next to the item.
            • Checklist item (sub-task) — the × / remove control on that item inside its target.
            • Resource / note — the remove control on the resource.
            • Deadline — open the deadline picker and choose Clear.
            (Whole goals and whole targets are the only deletions you may PROPOSE, via the
            delete_goal / delete_target tools above.) Never pretend you deleted something, and
            never substitute deleting a different item for one you can't delete.

            LANGUAGE:
            Respond in the language the user writes in.
            If the user writes in a language other than English, ask once — early in the
            conversation — which language they prefer for goal data (titles, descriptions,
            targets): their own language or English. Once they have chosen, ALWAYS use that
            language for EVERY proposal for the rest of the conversation — never revert to
            their chat language. If a proposal card asks you to revise something into a
            language, treat that as their lasting preference for goal data from then on.

            You are not a therapist, doctor, lawyer, or financial adviser.
            """;

    /**
     * GROW session: pure coaching mode, grounded strictly in the coaching
     * library (book excerpts retrieved per turn and appended to this prompt).
     * No execution work.
     */
    private static final String GROW_PROMPT = """
            You are a coaching intelligence embedded in Spira, a goal achievement platform.

            You are conducting a GROW coaching session, and you coach STRICTLY by the
            method of the source books excerpted below under "COACHING LIBRARY".

            THE LIBRARY IS YOUR ONLY METHOD:
            • Every coaching move you make — which question to ask, how to frame it,
              when to reflect, reframe, or summarise — must be grounded in and
              consistent with the excerpts supplied for this turn.
            • Never substitute generic coaching advice, frameworks, or techniques from
              outside the excerpts. If the excerpts don't cover the current moment,
              stay with their questioning STYLE — curious, brief, awareness-raising —
              rather than inventing doctrine.
            • Do not quote, cite, or mention the books or the excerpts to the user;
              embody the method, don't lecture about it.
            • Capturing the user's OWN words as goal data via the `propose_goal_change`
              tool is PART of the method, not outside advice: turning awareness into
              responsibility (the Will stage) means commitments get written down. The
              user approves or rejects every proposal — never skip proposing because
              it feels like acting beyond the books.

            You listen carefully. You ask one good question at a time.
            You follow the user's thinking, not a predetermined agenda.
            You do not give unsolicited advice or rush toward conclusions.

            The GROW framework (Goal, Reality, Options, Will) may naturally emerge from
            the conversation, but you do not announce phases or treat it as a checklist.

            CAPTURING PROGRESS:
            A session must leave the goal better than it found it. When the conversation
            surfaces something worth keeping — a new obstacle, a clearer description, a
            strategy option, a concrete target, or an insight worth saving — offer it
            naturally ("It sounds like X is a real constraint here — want me to add it?")
            and call the `propose_goal_change` tool so the user can approve it. Do this
            when it genuinely serves the conversation, not on a schedule. The change is
            applied only after the user approves, so never say it is already done.
            The same tool can also refine EXISTING items (rename a target, edit an obstacle,
            complete a target, update progress, select an option) — pass the item's 'id'
            from the goal context above. You cannot delete anything; if the user wants to
            remove something, gently point them to the matching control in the interface
            (the target's trash icon, an item's Remove button, the deadline picker's Clear).

            If the user asks for execution work that is not goal data — searching the web,
            sending a message — acknowledge it warmly and suggest noting it as a next
            action to pursue after the session ends.

            Respond in the language the user writes in.
            You are not a therapist, doctor, lawyer, or financial adviser. If the situation
            requires professional support, acknowledge this honestly.
            """;

    /**
     * The tool the model may call to request a change to goal data. Using a
     * native tool call (instead of a text marker) guarantees the arguments are
     * valid, structured JSON — even from small models.
     *
     * <p>One tool with a {@code kind} discriminator (rather than many tools)
     * keeps the model's choice simple and reliable. It covers every goal-data
     * mutation: title, description, targets, options, reality items, and notes.
     */
    private static final List<ToolSpec> PROPOSAL_TOOLS = List.of(new ToolSpec(
            "propose_goal_change",
            "Propose a change to the current goal for the user to review and approve. "
                    + "Covers creating goal data, editing existing items, and changing their "
                    + "state (complete a target, set progress, select an option). "
                    + "To change or complete an EXISTING item, pass its 'id' exactly as shown "
                    + "in the goal context (e.g. 'id=42'). "
                    + "For deletion (delete_goal / delete_target) you never delete anything "
                    + "yourself — the proposal just opens a confirmation dialog the user decides on. "
                    + "The change is NOT applied until the user approves, so never claim it is done.",
            proposalInputSchema()));

    /** JSON-Schema for {@code propose_goal_change} (built as a map to exceed Map.of's 10-entry limit). */
    private static Map<String, Object> proposalInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kind", Map.of(
                "type", "string",
                "enum", List.of("new_goal", "edit", "confidence", "deadline", "target", "task",
                        "option", "obstacle", "action", "note", "link", "email",
                        "edit_target", "edit_option", "edit_obstacle", "edit_action",
                        "edit_note", "edit_link", "edit_email", "complete_target", "target_progress",
                        "select_option", "checklist_item", "add_checklist_item",
                        "edit_goal", "open_goal", "delete_goal", "delete_target",
                        "delete_option", "delete_obstacle", "delete_action", "delete_checklist_item"),
                "description", "What to propose. CREATE (no id):\n"
                        + "'new_goal' — create a BRAND-NEW goal. 'title' = the goal NAME ONLY — a "
                        + "clean short name, NOT a sentence repeating the confidence/deadline. Put any "
                        + "confidence the user gave in 'confidence' (1-10), any deadline in "
                        + "'deadline_value' (YYYY-MM-DD), and an optional short description in 'value'. "
                        + "E.g. \"create a goal 'Learn Spanish' with confidence 9, deadline 3 aug\" → "
                        + "title='Learn Spanish', confidence='9', deadline_value='2026-08-03'. "
                        + "Use this when there is no current goal (the user is on the All-Goals page) "
                        + "and asks to create/start a goal;\n"
                        + "'edit' — change goal title or description (use 'field' + 'value');\n"
                        + "'confidence' — set goal confidence 1-10 (use 'value');\n"
                        + "'deadline' — set goal deadline YYYY-MM-DD (use 'value');\n"
                        + "'target'/'task' — add a target. Default is a simple check-off ('title', "
                        + "optional 'deadline_value'); to create it ALREADY DONE add 'done':'true'. "
                        + "For a measurable target set 'target_type':'numeric' with 'total' (and optional "
                        + "'current' progress, 'unit'). For a checklist set 'target_type':'checklist' with "
                        + "'items' (each {text, done?, deadline?});\n"
                        + "'option' — add a strategy option (use 'value'). To ALSO make it the "
                        + "selected/active option, add 'done':'true' on this SAME call — use that for "
                        + "\"create an option and make it active\". Never use select_option for a "
                        + "brand-new option (it has no id yet);\n"
                        + "'obstacle'/'action' — add a reality item (use 'value');\n"
                        + "'note' — save a resource note (use 'title' + 'value' for body).\n"
                        + "'link' — save a link resource (use 'value' for the URL; optional 'title' "
                        + "label, otherwise it's derived from the domain);\n"
                        + "'email' — save a contact resource (use 'value' for the email address; "
                        + "optional 'title' for the name, 'role', 'phone').\n"
                        + "EDIT EXISTING (always use 'id' from the context):\n"
                        + "'edit_target' — rename a target (use 'id', 'value'; optional 'deadline_value');\n"
                        + "'edit_option' — change option text (use 'id', 'value');\n"
                        + "'edit_obstacle'/'edit_action' — change reality text (use 'id', 'value');\n"
                        + "'edit_note' — change a note (use 'id', 'title', 'value' for body).\n"
                        + "'edit_link' — change a link resource: 'id' plus 'value' (new URL) and/or "
                        + "'title' (new label);\n"
                        + "'edit_email' — change a contact/email resource: 'id' plus any of 'title' "
                        + "(new name), 'value' (new email address), 'role', 'phone';\n"
                        + "STATE (always use 'id'):\n"
                        + "'complete_target' — mark a binary target done/undone (use 'id', 'done');\n"
                        + "'target_progress' — set a numeric target's current value (use 'id', 'value');\n"
                        + "'select_option' — mark an option as selected (use 'id');\n"
                        + "'checklist_item' — change one checklist item: its 'id' plus any of "
                        + "'value' (new text), 'done', 'deadline_value' (its due date);\n"
                        + "'add_checklist_item' — add a sub-task to a CHECKLIST target: 'id' = "
                        + "that target's id, 'value' = item text, optionally 'deadline_value' and 'done'. "
                        + "(Only checklist targets hold items.)\n"
                        + "GOAL-LEVEL by id (use on the All-Goals page; 'id' = the goal id):\n"
                        + "'edit_goal' — edit a goal's card field: 'id' (goal id), 'field' "
                        + "('title'|'confidence'|'deadline'), 'value' (new value);\n"
                        + "'open_goal' — propose opening a goal so the user can work inside it: "
                        + "'id' (goal id) AND 'value' = the concrete thing they wanted to change "
                        + "as a short noun phrase in their language (e.g. 'the description', 'a "
                        + "target'). Use this when they ask to change something INSIDE a goal "
                        + "from the All-Goals page (anything but name/confidence/deadline);\n"
                        + "'delete_goal' — start deleting a goal: 'id' (goal id; on a goal page it "
                        + "defaults to the current goal). Opens a confirmation dialog — you never delete;\n"
                        + "'delete_target' — start deleting a target: 'id' (target id). Opens a "
                        + "confirmation dialog — you never delete.\n"
                        + "DELETE A SMALLER ITEM (use 'id' from the context; opens a confirm card):\n"
                        + "'delete_option' — delete a strategy option ('id' = the option's id);\n"
                        + "'delete_obstacle' — delete a reality obstacle ('id' = its id);\n"
                        + "'delete_action' — delete a reality action ('id' = its id);\n"
                        + "'delete_checklist_item' — delete one checklist sub-task ('id' = the "
                        + "item's id). Use the kind that MATCHES the item's type — e.g. to delete "
                        + "an option use delete_option, NEVER delete_target. To delete, you ALWAYS "
                        + "use a delete_* kind — NEVER 'erase' an item by editing its text to an "
                        + "empty value (text is required and clearing it is rejected)."));
        props.put("id", Map.of(
                "type", "string",
                "description", "Id of the existing item to edit or change, taken "
                        + "verbatim from the goal context (the number after 'id='). "
                        + "Required for every edit_*/state kind and 'checklist_item'."));
        props.put("field", Map.of(
                "type", "string",
                "enum", List.of("title", "description", "confidence", "deadline"),
                "description", "Which field to edit. For kind='edit' (current goal): 'title' or "
                        + "'description'. For kind='edit_goal' (a goal by id): 'title', 'confidence', "
                        + "or 'deadline'."));
        props.put("value", Map.of(
                "type", "string",
                "description", "Main text or value: new field content (edit/edit_*), "
                        + "confidence 1-10, ISO date YYYY-MM-DD (deadline), numeric current "
                        + "value (target_progress), option/obstacle/action text, or note/checklist body."));
        props.put("title", Map.of(
                "type", "string",
                "description", "Display name. Required for kind='new_goal' (the goal title), "
                        + "'target', 'task', 'note', 'edit_note'. "
                        + "For notes it is a SHORT label — keep it to 200 characters or fewer; "
                        + "the note's content goes in 'value' (as simple HTML)."));
        props.put("role", Map.of(
                "type", "string",
                "description", "Contact's role/title. Optional, only for kind='email' or 'edit_email'."));
        props.put("phone", Map.of(
                "type", "string",
                "description", "Contact's phone number. Optional, only for kind='email' or 'edit_email'."));
        props.put("confidence", Map.of(
                "type", "string",
                "description", "Optional confidence 1-10 for kind='new_goal' when the user states "
                        + "one. Keep it OUT of 'title'. (To change an existing goal's confidence use "
                        + "kind='confidence' or 'edit_goal' with 'value' instead.)"));
        props.put("deadline_value", Map.of(
                "type", "string",
                "description", "Optional ISO date YYYY-MM-DD: a target/task deadline "
                        + "(target/task/edit_target) or a checklist item's due date "
                        + "(checklist_item/add_checklist_item)."));
        props.put("done", Map.of(
                "type", "string",
                "enum", List.of("true", "false"),
                "description", "Completion state for kind='complete_target', 'checklist_item', "
                        + "to create a binary target already done (kind='target'/'task'), or — for "
                        + "kind='option' — 'true' to select/activate the option as it's created."));
        props.put("target_type", Map.of(
                "type", "string",
                "enum", List.of("binary", "numeric", "checklist"),
                "description", "Kind of target to create (kind='target'/'task'). "
                        + "Omit or 'binary' for a check-off; 'numeric' for a measurable target "
                        + "(use 'total'); 'checklist' for a list (use 'items')."));
        props.put("total", Map.of(
                "type", "string",
                "description", "Numeric target's goal amount, e.g. '20' (kind='target' with target_type='numeric')."));
        props.put("current", Map.of(
                "type", "string",
                "description", "Numeric target's starting progress, e.g. '2' (optional; defaults to 0)."));
        props.put("unit", Map.of(
                "type", "string",
                "description", "Numeric target's unit, e.g. 'applications' (optional)."));
        props.put("items", Map.of(
                "type", "array",
                "description", "Checklist items when target_type='checklist'.",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of("type", "string", "description", "Item text."),
                                "done", Map.of("type", "boolean", "description", "Whether this item is already done."),
                                "deadline", Map.of("type", "string", "description", "Optional ISO date YYYY-MM-DD.")),
                        "required", List.of("text"))));
        props.put("reasoning", Map.of(
                "type", "string",
                "description", "A short reason for the change, shown to the user."));
        return Map.of("type", "object", "properties", props, "required", List.of("kind", "reasoning"));
    }

    /** Web-search tool, offered only when the user has a Tavily key configured. */
    private static final ToolSpec WEB_SEARCH_TOOL = new ToolSpec(
            "web_search",
            "Search the web for current information (facts, prices, listings, recent events) "
                    + "when the answer is not in the goal data or your own knowledge. Returns a "
                    + "summary and sources. Summarise the findings for the user and cite sources.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", "The search query.")),
                    "required", List.of("query")));

    /**
     * Reads a resource's content on demand. Offered whenever the chat is scoped
     * to a goal, so note bodies / PDF text / contact details enter the prompt
     * only when the model actually needs them — not on every request.
     */
    private static final ToolSpec READ_RESOURCE_TOOL = new ToolSpec(
            "read_resource",
            "Read the full content of one of the current goal's resources (a note, an "
                    + "uploaded file such as a PDF/CV, a link, or a contact). Call this when the "
                    + "user refers to a resource or you need its content. Use the resource 'id' "
                    + "shown in the goal context. Returns the text; for scanned/image files it "
                    + "says so — then ask the user to paste the text rather than inventing it.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "id", Map.of(
                                    "type", "string",
                                    "description", "The resource id from the goal context (number after 'id=').")),
                    "required", List.of("id")));

    /**
     * Reads the text of a web page on demand. No key needed; offered in regular
     * chat so the model can read a URL the user pastes (a job posting, article…).
     */
    private static final ToolSpec READ_URL_TOOL = new ToolSpec(
            "read_url",
            "Fetch and read the main text of a web page when the user gives a URL or you "
                    + "need its content (e.g. a job posting or article). Returns the page's "
                    + "extracted text. Pages behind a login or rendered by JavaScript may "
                    + "return little or nothing — in that case ask the user to paste the text; "
                    + "never invent a page's contents.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "url", Map.of(
                                    "type", "string",
                                    "description", "The full http(s) URL to read.")),
                    "required", List.of("url")));

    /** Safety cap on tool/agentic loop iterations within one request. */
    private static final int MAX_TOOL_ITERATIONS = 4;

    private final SafetyService safety;
    private final AiKeyService keyService;
    private final LlmProviderFactory providerFactory;
    private final GoalContextBuilder goalContextBuilder;
    private final TavilySearchService searchService;
    private final AiProposalService proposalService;
    private final ResourceReadService resourceReadService;
    private final UrlReadService urlReadService;
    private final GrowLibraryService growLibrary;
    private final GoalMemoryService goalMemory;

    // Cached thread pool for blocking SSE I/O. Threads are reused between requests.
    // Wrapped so the caller's Spring Security context propagates to the worker
    // thread — the agentic loop creates proposals via AiProposalService, which
    // resolves the authenticated user from the security context.
    private final ExecutorService executor =
            new DelegatingSecurityContextExecutorService(Executors.newCachedThreadPool());

    public AiChatService(
            SafetyService safety,
            AiKeyService keyService,
            LlmProviderFactory providerFactory,
            GoalContextBuilder goalContextBuilder,
            TavilySearchService searchService,
            AiProposalService proposalService,
            ResourceReadService resourceReadService,
            UrlReadService urlReadService,
            GrowLibraryService growLibrary,
            GoalMemoryService goalMemory) {
        this.safety = safety;
        this.keyService = keyService;
        this.providerFactory = providerFactory;
        this.goalContextBuilder = goalContextBuilder;
        this.searchService = searchService;
        this.proposalService = proposalService;
        this.resourceReadService = resourceReadService;
        this.urlReadService = urlReadService;
        this.growLibrary = growLibrary;
        this.goalMemory = goalMemory;
    }

    /**
     * Starts a streaming chat request and returns an {@link SseEmitter} that
     * the controller will write to the HTTP response.
     *
     * <p>The emitter is completed (or errored) asynchronously; the calling
     * thread returns immediately after submitting the task.
     *
     * @param request the chat request from the frontend
     * @return an SSE emitter that streams tokens as they arrive
     */
    public SseEmitter chat(ChatRequest request) {
        // Safety check runs synchronously before we touch the provider
        if (!safety.isSafe(request.message())) {
            SseEmitter blocked = new SseEmitter(0L);
            try {
                blocked.send(SseEmitter.event()
                        .name("token")
                        .data(jsonEncode(safety.blockedMessage())));
                blocked.send(SseEmitter.event().name("done").data(""));
                blocked.complete();
            } catch (Exception ignored) {
                blocked.completeWithError(ignored);
            }
            return blocked;
        }

        // Determine provider
        ProviderType providerType = resolveProvider(request.provider());

        // Load the user's key (throws 422 if not configured)
        AiKeyService.StoredKey storedKey = keyService.getKey(providerType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No API key configured for provider " + providerType.name()
                        + ". Save your key at POST /api/ai/keys first."));

        boolean isGrow = "grow".equalsIgnoreCase(request.sessionType());

        // GROW sessions are grounded in the coaching library, whose embeddings run
        // on a Mistral key (Anthropic has no embeddings API; the chat provider
        // stays the user's choice). Without it the session refuses — by design
        // there is no generic-prompt fallback. Deliberately an SSE error, not a
        // 422: the frontend maps any 422 to "NO_KEY" and would open the key sheet
        // for the CHAT provider, which may well be configured.
        Optional<AiKeyService.StoredKey> mistralKey =
                isGrow ? keyService.getKey(ProviderType.MISTRAL) : Optional.empty();
        if (isGrow && mistralKey.isEmpty()) {
            return immediateErrorEmitter(
                    "GROW sessions need a Mistral API key — it powers the coaching "
                    + "library the coach is grounded in. Add one under \"Bring your own key\".");
        }

        // Build system prompt (for GROW, library excerpts are appended in the task)
        String systemPrompt = buildSystemPrompt(request.goalId(), request.sessionType());

        // Build message list (mutable — the web-search loop appends to it)
        List<LlmMessage> messages = buildMessages(request);

        // Create provider instance
        LlmProvider provider = providerFactory.create(providerType, storedKey.apiKey(), storedKey.model());

        // Tools: proposals are always available (chat AND GROW — a session must be
        // able to improve the goal). Web search is offered only in regular chat and
        // only if the user has a Tavily key (GROW defers execution work per spec).
        List<ToolSpec> tools = new ArrayList<>(PROPOSAL_TOOLS);
        Optional<AiKeyService.StoredKey> tavilyKey =
                isGrow ? Optional.empty() : keyService.getKey(ProviderType.TAVILY);
        tavilyKey.ifPresent(k -> tools.add(WEB_SEARCH_TOOL));
        // Reading a pasted URL — regular chat only (external fetch, like web search).
        if (!isGrow) tools.add(READ_URL_TOOL);
        // Reading the goal's own resources is fine in chat and GROW alike.
        if (request.goalId() != null) tools.add(READ_RESOURCE_TOOL);

        // GROW gets a longer timeout: the first session ever also embeds the whole
        // library (~1k chunks) before the model can answer.
        SseEmitter emitter = new SseEmitter(isGrow ? 10 * 60 * 1000L : 3 * 60 * 1000L);

        if (isGrow) {
            String mistralApiKey = mistralKey.get().apiKey();
            executor.submit(() -> {
                try {
                    // One-time embedding pass (no-op once done); progress goes out
                    // as SSE "status" events, never as transcript tokens.
                    growLibrary.ensureEmbedded(
                            mistralApiKey, message -> sendStatus(emitter, message));
                    String query = growLibrary.buildQuery(request);
                    String excerpts = growLibrary.retrieveExcerpts(query, mistralApiKey);
                    // Memory of earlier sessions (saved by the user at session end);
                    // empty when none — unlike excerpts it is optional.
                    String memory = goalMemory.memoryBlock(request.goalId());
                    String growPrompt = systemPrompt + sessionTimingBlock(request)
                            + (memory.isEmpty() ? "" : "\n\n" + memory)
                            + "\n\n" + excerpts;
                    runAgenticLoop(provider, messages, growPrompt,
                            tools, null, request.goalId(), emitter);
                } catch (Exception e) {
                    // Retrieval failed → the session refuses. Never coach promptless.
                    errorSse(emitter, e);
                }
            });
        } else {
            executor.submit(() -> runAgenticLoop(
                    provider, messages, systemPrompt, tools, tavilyKey.orElse(null),
                    request.goalId(), emitter));
        }

        return emitter;
    }

    /** Emitter that reports a single {@code error} event and closes — used for
     *  preconditions the user must fix (e.g. missing Mistral key for GROW). */
    private SseEmitter immediateErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Tells the coach how much session time remains so it can pace and close
     * the conversation itself instead of being cut off by the UI timer. Empty
     * when the frontend sent no timing (e.g. older clients).
     */
    private static String sessionTimingBlock(ChatRequest request) {
        if (request.sessionTotalMinutes() == null) return "";
        int totalMinutes = request.sessionTotalMinutes();
        Integer remainingSeconds = request.sessionRemainingSeconds();
        StringBuilder sb = new StringBuilder("\n\nSESSION TIMING: This is a ")
                .append(totalMinutes).append("-minute coaching session");
        if (remainingSeconds == null) {
            return sb.append(". Pace the conversation to fit it.").toString();
        }
        if (remainingSeconds <= 0) {
            return sb.append("; the time is now UP. Close the session in THIS reply: "
                    + "warmly reflect the key insights that emerged, in the user's "
                    + "language; confirm any commitments or next steps they voiced "
                    + "(propose capturing them as goal data where fitting); thank them "
                    + "and say a clear, warm goodbye. Do NOT ask a new exploring "
                    + "question or open a new topic.").toString();
        }
        int remainingMinutes = (int) Math.ceil(remainingSeconds / 60.0);
        sb.append("; about ").append(remainingMinutes)
          .append(remainingMinutes == 1 ? " minute remains" : " minutes remain").append(". ");
        if (remainingSeconds <= totalMinutes * 60 * 0.2) {
            sb.append("The session is in its closing stretch: begin consolidating — "
                    + "reflect what has emerged, invite the user to name commitments, "
                    + "and propose capturing anything worth keeping as goal data. "
                    + "Don't open new threads; guide gently toward a natural close.");
        } else {
            sb.append("There is room to explore. Pace yourself so the conversation "
                    + "can reach a natural close before the time runs out.");
        }
        return sb.toString();
    }

    /** Progress heartbeat ({@code status} event). Best-effort: a failed send is
     *  logged but never aborts the work — embeddings persist regardless. */
    private void sendStatus(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("status").data(message));
        } catch (Exception e) {
            log.debug("SSE status send failed (client likely disconnected): {}", e.getMessage());
        }
    }

    /**
     * Drives the conversation, handling the agentic tool loop:
     * <ol>
     *   <li>Stream a model turn, forwarding text tokens to the client.</li>
     *   <li>{@code web_search} and {@code read_resource} produce a result that is
     *       fed back so the model can continue — we loop.</li>
     *   <li>{@code propose_goal_change} calls are surfaced as {@code proposal}
     *       SSE events and do not, by themselves, cause a loop.</li>
     * </ol>
     * When we do loop, every tool call from the turn gets a {@code tool_result}
     * (providers require each {@code tool_use} to be answered) — proposals get a
     * short synthetic acknowledgement.
     * Runs on a background thread; each provider call blocks until its stream ends.
     */
    private void runAgenticLoop(
            LlmProvider provider,
            List<LlmMessage> messages,
            String systemPrompt,
            List<ToolSpec> tools,
            AiKeyService.StoredKey tavilyKey,
            Long goalId,
            SseEmitter emitter) {

        try {
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                StringBuilder turnText = new StringBuilder();
                List<ToolCall> calls = new ArrayList<>();
                AtomicBoolean failed = new AtomicBoolean(false);

                provider.streamChat(
                        messages,
                        systemPrompt,
                        tools,
                        token -> { turnText.append(token); sendToken(emitter, token); },
                        calls::add,
                        () -> { /* turn finished — do not complete the emitter yet */ },
                        error -> { failed.set(true); errorSse(emitter, error); });

                if (failed.get()) return; // emitter already errored

                // Surface goal-change proposals (these never loop on their own)
                for (ToolCall c : calls) {
                    if ("propose_goal_change".equals(c.name())) sendProposal(emitter, c, goalId);
                }

                // Result-producing tools we can actually fulfil this turn.
                boolean willLoop = calls.stream().anyMatch(c ->
                        "read_resource".equals(c.name())
                        || "read_url".equals(c.name())
                        || ("web_search".equals(c.name()) && tavilyKey != null));

                if (!willLoop) {
                    completeSse(emitter);
                    return;
                }

                // Echo ALL tool calls, then answer EACH with a tool_result, and loop.
                messages.add(LlmMessage.assistantToolCalls(turnText.toString(), calls));
                for (ToolCall c : calls) {
                    messages.add(LlmMessage.toolResult(c.id(), toolResult(c, tavilyKey, goalId)));
                }
            }

            // Iteration cap reached — close gracefully.
            completeSse(emitter);
        } catch (Exception e) {
            errorSse(emitter, e);
        }
    }

    /** Produces the tool_result text for a single tool call in the agentic loop. */
    private String toolResult(ToolCall c, AiKeyService.StoredKey tavilyKey, Long goalId) {
        return switch (c.name()) {
            case "web_search" -> tavilyKey != null
                    ? searchService.search(tavilyKey.apiKey(), extractQuery(c.argumentsJson()))
                    : "Web search is not available (no search key configured).";
            case "read_resource" -> resourceReadService.read(goalId, extractId(c.argumentsJson()));
            case "read_url" -> readUrl(extractUrl(c.argumentsJson()), tavilyKey);
            case "propose_goal_change" -> "Proposal surfaced to the user for approval.";
            default -> "";
        };
    }

    /**
     * Reads a page for {@code read_url}: prefers Tavily Extract when a Tavily key
     * is configured (handles dynamic/cluttered pages better), falling back to a
     * plain HTTP fetch otherwise or when Extract returns nothing.
     */
    private String readUrl(String url, AiKeyService.StoredKey tavilyKey) {
        if (tavilyKey != null) {
            String extracted = searchService.extract(tavilyKey.apiKey(), url);
            if (extracted != null && !extracted.isBlank()) return extracted;
        }
        return urlReadService.read(url);
    }

    private String extractUrl(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson).path("url").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String extractQuery(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson).path("query").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private Long extractId(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson).path("id").asLong();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String buildSystemPrompt(Long goalId, String sessionType) {
        String basePrompt = "grow".equalsIgnoreCase(sessionType) ? GROW_PROMPT : CHAT_PROMPT;
        String goalContext = goalContextBuilder.build(goalId);
        if (goalContext.isBlank()) return basePrompt;
        return basePrompt + "\n\n" + goalContext;
    }

    private List<LlmMessage> buildMessages(ChatRequest request) {
        List<LlmMessage> messages = new ArrayList<>();

        // Replay history
        if (request.history() != null) {
            for (ChatRequest.MessageEntry entry : request.history()) {
                messages.add(new LlmMessage(entry.role(), entry.content()));
            }
        }

        // Append current user message
        messages.add(LlmMessage.user(request.message()));

        return messages;
    }

    private ProviderType resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) return ProviderType.ANTHROPIC;
        try {
            return ProviderType.fromString(provider);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown provider: " + provider);
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            // JSON-encode the token so it is always a single SSE data line. Raw
            // tokens may contain newlines (Markdown headings, lists, code), which
            // would otherwise break SSE framing and truncate the message.
            emitter.send(SseEmitter.event().name("token").data(jsonEncode(token)));
        } catch (Exception e) {
            log.debug("SSE send failed (client likely disconnected): {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /** Serialises a string to a JSON string literal (escapes newlines, quotes, etc.). */
    private static String jsonEncode(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (Exception e) {
            // Fallback: minimal manual escaping (should never happen for a String)
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
    }

    /**
     * Forwards a model tool call to the client as a {@code proposal} SSE event.
     *
     * <p>When the chat is scoped to a goal, the proposal is also persisted (status
     * {@code PENDING}) so it survives a page reload and can be approved/rejected
     * via {@code /api/ai/proposals}. The persisted id is embedded into the event
     * data as {@code proposalId} so the frontend can call those endpoints. Global
     * chats (no goal) are not persisted — {@code propose_goal_change} only applies
     * to a goal.
     */
    private void sendProposal(SseEmitter emitter, ToolCall toolCall, Long goalId) {
        String data = toolCall.argumentsJson();
        if (goalId != null) {
            try {
                ProposalDto saved = proposalService.create(
                        goalId, extractKind(toolCall.argumentsJson()), toolCall.argumentsJson());
                data = withProposalId(toolCall.argumentsJson(), saved.id());
            } catch (Exception e) {
                // Persistence is best-effort: if it fails, still surface the card
                // (it just won't survive a reload). Don't break the stream.
                log.warn("Failed to persist proposal: {}", e.getMessage());
            }
        }
        try {
            emitter.send(SseEmitter.event().name("proposal").data(data));
        } catch (Exception e) {
            log.debug("SSE proposal send failed: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /** Reads the {@code kind} discriminator from the tool arguments (default "edit"). */
    private String extractKind(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson).path("kind").asText("edit");
        } catch (Exception e) {
            return "edit";
        }
    }

    /** Returns the arguments JSON with a {@code proposalId} field added. */
    private String withProposalId(String argumentsJson, Long proposalId) throws Exception {
        JsonNode node = MAPPER.readTree(argumentsJson);
        ObjectNode obj = node.isObject() ? (ObjectNode) node : MAPPER.createObjectNode();
        obj.put("proposalId", proposalId);
        return MAPPER.writeValueAsString(obj);
    }

    private void completeSse(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void errorSse(SseEmitter emitter, Throwable error) {
        log.error("AI stream error", error);
        try {
            emitter.send(SseEmitter.event().name("error").data(friendlyError(error)));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(error);
        }
    }

    /**
     * Builds a user-facing message from a provider failure. Prefers the
     * provider's own human-readable message (e.g. "this model requires a
     * subscription, upgrade…") since that's the most actionable; otherwise
     * falls back to a short hint based on the HTTP status. The full error is
     * always in the server log.
     */
    private String friendlyError(Throwable error) {
        String m = error.getMessage() == null ? "" : error.getMessage();

        // Surface the provider's own error text when present — it's meant for
        // the user (model/subscription/quota issues, etc.).
        String providerMsg = extractProviderMessage(m);
        if (providerMsg != null && !providerMsg.isBlank()) {
            String clean = providerMsg.replaceAll("\\s+", " ").trim();
            return clean.length() > 300 ? clean.substring(0, 300) + "…" : clean;
        }

        String lower = m.toLowerCase();
        if (m.contains("401") || lower.contains("unauthorized")) {
            return "The provider rejected your API key. Re-check it in “Bring your own key”.";
        }
        if (m.contains("403")) {
            return "Access denied by the provider (your plan/key may not allow this model).";
        }
        if (m.contains("404") || lower.contains("not found")) {
            return "The selected model isn't available for this provider. Open the key sheet and pick a different model.";
        }
        if (m.contains("429") || lower.contains("rate")) {
            return "The provider is rate-limiting requests. Wait a moment and try again.";
        }
        if (m.contains("400")) {
            return "The provider rejected the request (often an unsupported model or option). Try another model.";
        }
        return "AI service error. Please try again.";
    }

    /**
     * Pulls a human-readable message out of a provider error body embedded in
     * the exception message. Handles {@code {"error":"…"}} (Ollama) and
     * {@code {"error":{"message":"…"}}} (OpenAI/Mistral-style). Returns null if
     * none is found.
     */
    private String extractProviderMessage(String raw) {
        int brace = raw.indexOf('{');
        if (brace < 0) return null;
        try {
            JsonNode node = MAPPER.readTree(raw.substring(brace));
            JsonNode err = node.path("error");
            if (err.isObject() && !err.path("message").asText("").isBlank()) {
                return err.path("message").asText();
            }
            if (err.isTextual() && !err.asText().isBlank()) {
                return err.asText();
            }
            if (!node.path("message").asText("").isBlank()) {
                return node.path("message").asText();
            }
        } catch (Exception ignore) {
            // not JSON — fall back to status-based hints
        }
        return null;
    }
}
