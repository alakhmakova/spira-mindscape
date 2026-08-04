import { getCsrfToken } from "./auth";
import type {
  ChecklistItem,
  Confidence,
  Goal,
  Option,
  Resource,
  ResourceInput,
  Target,
} from "./types";

const GRAPHQL_ENDPOINT = import.meta.env.VITE_GRAPHQL_ENDPOINT ?? "/graphql";

type GraphqlError = {
  message: string;
  extensions?: {
    classification?: string;
  };
};

type GraphqlResponse<T> = {
  data?: T;
  errors?: GraphqlError[];
};

const DEFAULT_API_ERROR_MESSAGE =
  "We couldn't sync with the backend. Please try again.";
const BACKEND_UNAVAILABLE_MESSAGE =
  "We couldn't reach the backend. Check that it is running, then retry.";
const VALIDATION_ERROR_CLASSIFICATION = "ValidationError";

export class SpiraApiError extends Error {
  readonly details?: string;
  readonly errors?: GraphqlError[];
  readonly status?: number;
  /** "network" = fetch failed (no connection), "service" = server replied with error */
  readonly kind: "network" | "service";

  constructor(
    message: string,
    options: {
      details?: string;
      errors?: GraphqlError[];
      status?: number;
      cause?: unknown;
      kind?: "network" | "service";
    } = {},
  ) {
    super(message, { cause: options.cause });
    this.name = "SpiraApiError";
    this.details = options.details;
    this.errors = options.errors;
    this.status = options.status;
    this.kind = options.kind ?? "service";
  }
}

type GraphqlRealityItem = {
  id: string;
  text: string;
};

type GraphqlReality = {
  id: string;
  actions: GraphqlRealityItem[];
  obstacles: GraphqlRealityItem[];
};

type GraphqlOption = {
  id: string;
  text: string;
  selected: boolean;
  status: string;
  position: number;
};

type GraphqlChecklistItem = {
  id: string;
  text: string;
  done: boolean;
  deadline?: string | null;
  achievedAt?: string | null;
};

type GraphqlTarget = {
  id: string;
  type: string;
  title: string;
  start?: number | null;
  current?: number | null;
  total?: number | null;
  unit?: string | null;
  done?: boolean | null;
  items: GraphqlChecklistItem[];
  deadline?: string | null;
  achievedAt?: string | null;
  progressLocked?: boolean | null;
  createdAt?: string | null;
};

type GraphqlResource = {
  id: string;
  type: string;
  title?: string | null;
  body?: string | null;
  url?: string | null;
  mime?: string | null;
  dataUrl?: string | null;
  name?: string | null;
  role?: string | null;
  email?: string | null;
  phone?: string | null;
  driveWebViewLink?: string | null;
};

type GraphqlGoal = {
  id: string;
  title: string;
  description: string;
  confidence: number;
  deadline?: string | null;
  createdAt: string;
  achievedAt?: string | null;
  reality: GraphqlReality;
  options: GraphqlOption[];
  resources: GraphqlResource[];
  targets: GraphqlTarget[];
  confidenceHistory?: { confidence: number; at: string }[];
};

type CreateGoalInput = {
  title: string;
  description?: string;
  confidence: Confidence;
  deadline?: string;
};

type UpdateGoalInput = Partial<{
  title: string;
  description: string;
  confidence: Confidence;
  deadline: string | null;
  achievedAt: string | null;
}>;

type CreateResourceInput = ResourceInput;
type UpdateResourceInput = Partial<Resource>;
type CreateTargetInput =
  | Omit<Extract<Target, { type: "numeric" }>, "id" | "current">
  | Omit<Extract<Target, { type: "binary" }>, "id">
  | Omit<Extract<Target, { type: "checklist" }>, "id">;
type UpdateTargetInput = Partial<Target>;

const GOAL_FIELDS = `
  id
  title
  description
  confidence
  deadline
  createdAt
  achievedAt
  reality {
    id
    actions { id text }
    obstacles { id text }
  }
  options { id text selected status position }
  resources {
    id
    type
    title
    body
    url
    mime
    # dataUrl is deliberately NOT fetched here: a file's dataUrl is a multi-MB base64 blob and
    # the goals list would carry EVERY file's contents on each load, making the payload huge
    # (slow, and it can break the whole list on flaky mobile connections). File contents are
    # loaded lazily per resource via fetchResourceFile() when the user actually opens one.
    name
    role
    email
    phone
    driveWebViewLink
  }
  targets {
    id
    type
    title
    start
    current
    total
    unit
    done
    deadline
    achievedAt
    progressLocked
    createdAt
    items { id text done deadline achievedAt }
  }
  confidenceHistory { confidence at }
`;

const TARGET_FIELDS = `
  id
  type
  title
  start
  current
  total
  unit
  done
  deadline
  achievedAt
  progressLocked
  createdAt
  items { id text done deadline achievedAt }
`;

const RESOURCE_FIELDS = `
  id
  type
  title
  body
  url
  mime
  dataUrl
  name
  role
  email
  phone
`;

// Same as RESOURCE_FIELDS but WITHOUT dataUrl. Used for mutation responses: a file's dataUrl is
// a multi-MB base64 blob, and echoing it back made flaky mobile/ngrok connections reset while
// writing the response — the client then treated the (already-persisted) create as failed and
// rolled the resource back, so a just-added PDF appeared to "disappear". The client already has
// the dataUrl it sent, so we re-attach it locally instead of round-tripping it.
const RESOURCE_META_FIELDS = `
  id
  type
  title
  body
  url
  mime
  name
  role
  email
  phone
`;

const REALITY_FIELDS = `
  id
  actions { id text }
  obstacles { id text }
`;

const OPTION_FIELDS = `
  id
  text
  selected
  status
  position
`;

async function graphql<T>(
  query: string,
  variables?: Record<string, unknown>,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(GRAPHQL_ENDPOINT, {
      method: "POST",
      // Include session cookie and echo the CSRF token so Spring Security
      // accepts the request.
      credentials: "include",
      headers: {
        "content-type": "application/json",
        "X-XSRF-TOKEN": getCsrfToken(),
      },
      body: JSON.stringify({ query, variables }),
    });
  } catch (error) {
    throw new SpiraApiError(BACKEND_UNAVAILABLE_MESSAGE, {
      cause: error,
      kind: "network",
    });
  }

  if (!response.ok) {
    throw new SpiraApiError(DEFAULT_API_ERROR_MESSAGE, {
      details: `GraphQL request failed with HTTP ${response.status}`,
      status: response.status,
    });
  }

  const body = (await response.json()) as GraphqlResponse<T>;
  if (body.errors?.length) {
    const validationMessage = body.errors.find(
      (error) =>
        error.extensions?.classification === VALIDATION_ERROR_CLASSIFICATION,
    )?.message;
    throw new SpiraApiError(validationMessage ?? DEFAULT_API_ERROR_MESSAGE, {
      details: body.errors.map((error) => error.message).join("; "),
      errors: body.errors,
    });
  }
  if (!body.data) {
    throw new SpiraApiError(DEFAULT_API_ERROR_MESSAGE, {
      details: "GraphQL response did not include data",
    });
  }
  return body.data;
}

function cleanInput<T extends Record<string, unknown>>(input: T) {
  return Object.fromEntries(
    Object.entries(input).filter(([, value]) => value !== undefined),
  ) as T;
}

function nullableWhenPresent<T extends Record<string, unknown>>(
  input: T,
  field: keyof T,
) {
  return field in input ? (input[field] ?? null) : undefined;
}

function toChecklistItem(item: GraphqlChecklistItem): ChecklistItem {
  return {
    id: item.id,
    text: item.text,
    done: item.done,
    deadline: item.deadline ?? undefined,
    achievedAt: item.achievedAt ?? undefined,
  };
}

function toTarget(target: GraphqlTarget): Target {
  const base = {
    id: target.id,
    title: target.title,
    deadline: target.deadline ?? undefined,
    achievedAt: target.achievedAt ?? undefined,
    progressLocked: target.progressLocked ?? undefined,
    createdAt: target.createdAt ?? undefined,
  };

  if (target.type === "binary") {
    return {
      ...base,
      type: "binary",
      done: Boolean(target.done),
    };
  }

  if (target.type === "checklist") {
    return {
      ...base,
      type: "checklist",
      items: target.items.map(toChecklistItem),
    };
  }

  return {
    ...base,
    type: "numeric",
    start: target.start ?? undefined,
    current: target.current ?? 0,
    total: target.total ?? 0,
    unit: target.unit ?? undefined,
  };
}

function toResource(resource: GraphqlResource): Resource {
  if (resource.type === "link") {
    return {
      id: resource.id,
      type: "link",
      title: resource.title ?? "",
      url: resource.url ?? "",
    };
  }

  if (resource.type === "file") {
    return {
      id: resource.id,
      type: "file",
      title: resource.title ?? "",
      mime: resource.mime ?? "",
      dataUrl: resource.dataUrl ?? "",
    };
  }

  if (resource.type === "email" || resource.type === "contact") {
    return {
      id: resource.id,
      type: "email",
      name: resource.name ?? resource.title ?? "",
      role: resource.role ?? undefined,
      email: resource.email ?? undefined,
      phone: resource.phone ?? undefined,
    };
  }

  return {
    id: resource.id,
    type: "note",
    title: resource.title ?? "",
    body: resource.body ?? "",
    driveWebViewLink: resource.driveWebViewLink ?? null,
  };
}

function toOption(option: GraphqlOption): Option {
  return {
    id: option.id,
    text: option.text,
    selected: option.selected,
    status: (option.status as Option["status"]) ?? "none",
    position: option.position,
  };
}

function toGoal(goal: GraphqlGoal): Goal {
  return {
    id: goal.id,
    title: goal.title,
    description: goal.description,
    confidence: goal.confidence as Confidence,
    deadline: goal.deadline ?? undefined,
    createdAt: goal.createdAt,
    achievedAt: goal.achievedAt ?? undefined,
    reality: {
      actions: goal.reality.actions.map((item) => ({
        id: item.id,
        text: item.text,
      })),
      obstacles: goal.reality.obstacles.map((item) => ({
        id: item.id,
        text: item.text,
      })),
    },
    options: goal.options.map(toOption),
    resources: goal.resources.map(toResource),
    targets: goal.targets.map(toTarget),
    confidenceHistory:
      goal.confidenceHistory?.map((h) => ({ value: h.confidence, at: h.at })) ??
      [],
  };
}

function resourceInput(
  resource: CreateResourceInput | UpdateResourceInput,
  includeType: boolean,
) {
  return cleanInput({
    type: includeType && "type" in resource ? resource.type : undefined,
    title: "title" in resource ? resource.title : undefined,
    body: "body" in resource ? resource.body : undefined,
    url: "url" in resource ? resource.url : undefined,
    mime: "mime" in resource ? resource.mime : undefined,
    // Never send an EMPTY dataUrl: file contents load lazily, so a resource in memory may not
    // have its bytes yet (e.g. renaming a file whose preview was never opened). Sending "" would
    // blank the stored file. Omitting the field leaves the persisted contents untouched.
    dataUrl:
      "dataUrl" in resource && resource.dataUrl ? resource.dataUrl : undefined,
    name: "name" in resource ? resource.name : undefined,
    role: "role" in resource ? resource.role : undefined,
    email: "email" in resource ? resource.email : undefined,
    phone: "phone" in resource ? resource.phone : undefined,
  });
}

function checklistInput(items?: ChecklistItem[]) {
  return items?.map((item) =>
    cleanInput({
      id: item.id.startsWith("local-") ? undefined : item.id,
      text: item.text,
      done: item.done,
      deadline: nullableWhenPresent(item, "deadline"),
      achievedAt: nullableWhenPresent(item, "achievedAt"),
    }),
  );
}

function targetInput(
  target: CreateTargetInput | UpdateTargetInput,
  includeType: boolean,
) {
  return cleanInput({
    type: includeType && "type" in target ? target.type : undefined,
    title: "title" in target ? target.title : undefined,
    deadline: nullableWhenPresent(target, "deadline"),
    achievedAt: nullableWhenPresent(target, "achievedAt"),
    start: "start" in target ? target.start : undefined,
    current: "current" in target ? target.current : undefined,
    total: "total" in target ? target.total : undefined,
    unit: "unit" in target ? target.unit : undefined,
    done: "done" in target ? target.done : undefined,
    progressLocked:
      "progressLocked" in target ? target.progressLocked : undefined,
    items: "items" in target ? checklistInput(target.items) : undefined,
  });
}

export const spiraApi = {
  async fetchGoals(): Promise<Goal[]> {
    const data = await graphql<{ goals: GraphqlGoal[] }>(`
      query Goals {
        goals {
          ${GOAL_FIELDS}
        }
      }
    `);
    return data.goals.map(toGoal);
  },

  /**
   * A tiny change-signature for the whole goal graph ("<maxUpdatedAtMicros>:<count>").
   * Background sync polls this instead of re-fetching every goal: only when it differs
   * from the last seen value does it run the full `fetchGoals`, cutting outbound transfer.
   */
  async fetchGoalsRevision(): Promise<string> {
    const data = await graphql<{ goalsRevision: string }>(`
      query GoalsRevision {
        goalsRevision
      }
    `);
    return data.goalsRevision;
  },

  async createGoal(input: CreateGoalInput): Promise<Goal> {
    const data = await graphql<{ createGoal: GraphqlGoal }>(
      `
        mutation CreateGoal($input: CreateGoalInput!) {
          createGoal(input: $input) {
            ${GOAL_FIELDS}
          }
        }
      `,
      { input: cleanInput(input) },
    );
    return toGoal(data.createGoal);
  },

  async updateGoal(id: string, input: UpdateGoalInput): Promise<Goal> {
    const updateInput = cleanInput({
      ...input,
      deadline: nullableWhenPresent(input, "deadline"),
      achievedAt: nullableWhenPresent(input, "achievedAt"),
    });
    const data = await graphql<{ updateGoal: GraphqlGoal }>(
      `
        mutation UpdateGoal($id: ID!, $input: UpdateGoalInput!) {
          updateGoal(id: $id, input: $input) {
            ${GOAL_FIELDS}
          }
        }
      `,
      { id, input: updateInput },
    );
    return toGoal(data.updateGoal);
  },

  async deleteGoal(id: string): Promise<void> {
    await graphql<{ deleteGoal: boolean }>(
      `
        mutation DeleteGoal($id: ID!) {
          deleteGoal(id: $id)
        }
      `,
      { id },
    );
  },

  async addRealityItem(
    goalId: string,
    kind: "actions" | "obstacles",
    text: string,
  ) {
    const data = await graphql<{ addRealityItem: GraphqlReality }>(
      `
        mutation AddRealityItem($goalId: ID!, $kind: String!, $text: String!) {
          addRealityItem(goalId: $goalId, kind: $kind, text: $text) {
            ${REALITY_FIELDS}
          }
        }
      `,
      { goalId, kind, text },
    );
    return {
      actions: data.addRealityItem.actions.map((item) => ({
        id: item.id,
        text: item.text,
      })),
      obstacles: data.addRealityItem.obstacles.map((item) => ({
        id: item.id,
        text: item.text,
      })),
    };
  },

  async updateRealityItem(
    goalId: string,
    kind: "actions" | "obstacles",
    itemId: string,
    text: string,
  ) {
    const data = await graphql<{ updateRealityItem: GraphqlReality }>(
      `
        mutation UpdateRealityItem($goalId: ID!, $kind: String!, $itemId: ID!, $text: String!) {
          updateRealityItem(goalId: $goalId, kind: $kind, itemId: $itemId, text: $text) {
            ${REALITY_FIELDS}
          }
        }
      `,
      { goalId, kind, itemId, text },
    );
    return {
      actions: data.updateRealityItem.actions.map((item) => ({
        id: item.id,
        text: item.text,
      })),
      obstacles: data.updateRealityItem.obstacles.map((item) => ({
        id: item.id,
        text: item.text,
      })),
    };
  },

  async removeRealityItem(
    goalId: string,
    kind: "actions" | "obstacles",
    itemId: string,
  ) {
    const data = await graphql<{ removeRealityItem: GraphqlReality }>(
      `
        mutation RemoveRealityItem($goalId: ID!, $kind: String!, $itemId: ID!) {
          removeRealityItem(goalId: $goalId, kind: $kind, itemId: $itemId) {
            ${REALITY_FIELDS}
          }
        }
      `,
      { goalId, kind, itemId },
    );
    return {
      actions: data.removeRealityItem.actions.map((item) => ({
        id: item.id,
        text: item.text,
      })),
      obstacles: data.removeRealityItem.obstacles.map((item) => ({
        id: item.id,
        text: item.text,
      })),
    };
  },

  async addOption(goalId: string, text: string): Promise<Option> {
    const data = await graphql<{ addOption: GraphqlOption }>(
      `
        mutation AddOption($goalId: ID!, $text: String!) {
          addOption(goalId: $goalId, text: $text) {
            ${OPTION_FIELDS}
          }
        }
      `,
      { goalId, text },
    );
    return toOption(data.addOption);
  },

  async updateOption(
    goalId: string,
    optionId: string,
    input: Partial<Option>,
  ): Promise<Option> {
    const data = await graphql<{ updateOption: GraphqlOption }>(
      `
        mutation UpdateOption(
          $goalId: ID!
          $optionId: ID!
          $input: UpdateOptionInput!
        ) {
          updateOption(goalId: $goalId, optionId: $optionId, input: $input) {
            ${OPTION_FIELDS}
          }
        }
      `,
      {
        goalId,
        optionId,
        input: cleanInput({
          text: input.text,
          selected: input.selected,
          status: input.status,
        }),
      },
    );
    return toOption(data.updateOption);
  },

  async selectOption(goalId: string, optionId: string): Promise<Option> {
    const data = await graphql<{ selectOption: GraphqlOption }>(
      `
        mutation SelectOption($goalId: ID!, $optionId: ID!) {
          selectOption(goalId: $goalId, optionId: $optionId) {
            ${OPTION_FIELDS}
          }
        }
      `,
      { goalId, optionId },
    );
    return toOption(data.selectOption);
  },

  async removeOption(goalId: string, optionId: string): Promise<void> {
    await graphql<{ removeOption: boolean }>(
      `
        mutation RemoveOption($goalId: ID!, $optionId: ID!) {
          removeOption(goalId: $goalId, optionId: $optionId)
        }
      `,
      { goalId, optionId },
    );
  },

  async reorderOptions(goalId: string, optionIds: string[]): Promise<Option[]> {
    const data = await graphql<{ reorderOptions: GraphqlOption[] }>(
      `
        mutation ReorderOptions($goalId: ID!, $optionIds: [ID!]!) {
          reorderOptions(goalId: $goalId, optionIds: $optionIds) {
            ${OPTION_FIELDS}
          }
        }
      `,
      { goalId, optionIds },
    );
    return data.reorderOptions.map(toOption);
  },

  async createTarget(
    goalId: string,
    input: CreateTargetInput,
  ): Promise<Target> {
    const data = await graphql<{ createTarget: GraphqlTarget }>(
      `
        mutation CreateTarget($goalId: ID!, $input: CreateTargetInput!) {
          createTarget(goalId: $goalId, input: $input) {
            ${TARGET_FIELDS}
          }
        }
      `,
      { goalId, input: targetInput(input, true) },
    );
    return toTarget(data.createTarget);
  },

  async updateTarget(id: string, input: UpdateTargetInput): Promise<Target> {
    const data = await graphql<{ updateTarget: GraphqlTarget }>(
      `
        mutation UpdateTarget($id: ID!, $input: UpdateTargetInput!) {
          updateTarget(id: $id, input: $input) {
            ${TARGET_FIELDS}
          }
        }
      `,
      { id, input: targetInput(input, false) },
    );
    return toTarget(data.updateTarget);
  },

  async deleteTarget(id: string): Promise<void> {
    await graphql<{ deleteTarget: boolean }>(
      `
        mutation DeleteTarget($id: ID!) {
          deleteTarget(id: $id)
        }
      `,
      { id },
    );
  },

  async createResource(
    goalId: string,
    input: CreateResourceInput,
  ): Promise<Resource> {
    const data = await graphql<{ createResource: GraphqlResource }>(
      `
        mutation CreateResource($goalId: ID!, $input: CreateResourceInput!) {
          createResource(goalId: $goalId, input: $input) {
            ${RESOURCE_META_FIELDS}
          }
        }
      `,
      { goalId, input: resourceInput(input, true) },
    );
    const created = toResource(data.createResource);
    // The response omits dataUrl (see RESOURCE_META_FIELDS) — re-attach the one we just sent so
    // the persisted resource keeps its file contents locally.
    if (created.type === "file" && input.type === "file" && input.dataUrl) {
      return { ...created, dataUrl: input.dataUrl };
    }
    return created;
  },

  async updateResource(
    id: string,
    input: UpdateResourceInput,
  ): Promise<Resource> {
    const data = await graphql<{ updateResource: GraphqlResource }>(
      `
        mutation UpdateResource($id: ID!, $input: UpdateResourceInput!) {
          updateResource(id: $id, input: $input) {
            ${RESOURCE_META_FIELDS}
          }
        }
      `,
      { id, input: resourceInput(input, false) },
    );
    const updated = toResource(data.updateResource);
    // dataUrl is omitted from the response; keep whatever the caller passed (the store ignores
    // this return value for files, but preserve it for correctness).
    if (updated.type === "file" && "dataUrl" in input && input.dataUrl) {
      return { ...updated, dataUrl: input.dataUrl };
    }
    return updated;
  },

  /**
   * Fetch just the file contents (base64 data URL) of one resource. File bodies are excluded
   * from the goals list (see GOAL_FIELDS) so the list stays small; this pulls the bytes on
   * demand when the user opens/downloads/copies a specific file.
   */
  async fetchResourceFile(id: string): Promise<string> {
    const data = await graphql<{
      resourceById: { dataUrl?: string | null } | null;
    }>(
      `
        query ResourceFile($id: ID!) {
          resourceById(id: $id) {
            id
            dataUrl
          }
        }
      `,
      { id },
    );
    return data.resourceById?.dataUrl ?? "";
  },

  async deleteResource(id: string): Promise<void> {
    await graphql<{ deleteResource: boolean }>(
      `
        mutation DeleteResource($id: ID!) {
          deleteResource(id: $id)
        }
      `,
      { id },
    );
  },
};
