import { afterEach, describe, expect, it, vi } from "vitest";

import { spiraApi } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("spiraApi goal mapping", () => {
  it("maps goal metadata fields", async () => {
    mockGraphqlSuccess({ goals: [goalResponse()] });

    const [goal] = await spiraApi.fetchGoals();

    expect({
      title: goal.title,
      description: goal.description,
      confidence: goal.confidence,
      deadline: goal.deadline,
      createdAt: goal.createdAt,
      achievedAt: goal.achievedAt,
    }).toEqual({
      title: "Launch Mindscape",
      description: "Ship the refreshed coaching flow",
      confidence: 8,
      deadline: "2026-06-01T00:00:00.000Z",
      createdAt: "2026-05-01T09:00:00.000Z",
      achievedAt: "2026-05-20T18:30:00.000Z",
    });
  });

  it("maps reality items and options", async () => {
    mockGraphqlSuccess({ goals: [goalResponse()] });

    const [goal] = await spiraApi.fetchGoals();

    expect(goal.reality).toEqual({
      actions: [{ id: "action-1", text: "Schedule customer interviews" }],
      obstacles: [{ id: "obstacle-1", text: "Waiting for design review" }],
    });
    expect(goal.options).toEqual([
      { id: "option-1", text: "Stay focused", selected: true, position: 0 },
      { id: "option-2", text: "Reduce scope", selected: false, position: 1 },
    ]);
  });

  it("maps all resource types", async () => {
    mockGraphqlSuccess({ goals: [goalResponse()] });

    const [goal] = await spiraApi.fetchGoals();

    expect(goal.resources).toEqual([
      {
        id: "resource-note",
        type: "note",
        title: "Plan",
        body: "<p>Keep the launch checklist updated.</p>",
        driveWebViewLink: null,
      },
      {
        id: "resource-link",
        type: "link",
        title: "roadmap",
        url: "https://roadmap.example.com/q2",
      },
      {
        id: "resource-file",
        type: "file",
        title: "Deck",
        mime: "application/pdf",
        dataUrl: "data:application/pdf;base64,QQ==",
      },
      {
        id: "resource-email",
        type: "email",
        name: "coach@example.com",
        role: "Coach",
        email: "coach@example.com",
        phone: "+1-555-0100",
      },
    ]);
  });

  it("maps all target types and checklist task dates", async () => {
    mockGraphqlSuccess({ goals: [goalResponse()] });

    const [goal] = await spiraApi.fetchGoals();

    expect(goal.targets).toEqual([
      {
        id: "target-numeric",
        type: "numeric",
        title: "Reach 10 sessions",
        start: 2,
        current: 6,
        total: 10,
        unit: "sessions",
        deadline: "2026-05-30T00:00:00.000Z",
      },
      {
        id: "target-binary",
        type: "binary",
        title: "Publish onboarding page",
        done: true,
        achievedAt: "2026-05-18T12:00:00.000Z",
      },
      {
        id: "target-checklist",
        type: "checklist",
        title: "Launch checklist",
        items: [
          {
            id: "task-1",
            text: "Draft email",
            done: true,
            deadline: "2026-05-22T00:00:00.000Z",
            achievedAt: "2026-05-21T08:00:00.000Z",
          },
          {
            id: "task-2",
            text: "Publish FAQ",
            done: false,
          },
        ],
      },
    ]);
  });
});

describe("spiraApi input serialization", () => {
  it("serializes note resource create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createResource: noteResourceResponse(),
    });

    await spiraApi.createResource("goal-1", {
      type: "note",
      title: "Plan",
      body: "<p>Draft</p>",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "note",
      title: "Plan",
      body: "<p>Draft</p>",
    });
  });

  it("serializes link resource create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createResource: linkResourceResponse(),
    });

    await spiraApi.createResource("goal-1", {
      type: "link",
      title: "roadmap",
      url: "https://roadmap.example.com/q2",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "link",
      title: "roadmap",
      url: "https://roadmap.example.com/q2",
    });
  });

  it("serializes file resource create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createResource: fileResourceResponse(),
    });

    await spiraApi.createResource("goal-1", {
      type: "file",
      title: "Deck",
      mime: "application/pdf",
      dataUrl: "data:application/pdf;base64,QQ==",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "file",
      title: "Deck",
      mime: "application/pdf",
      dataUrl: "data:application/pdf;base64,QQ==",
    });
  });

  it("serializes email resource create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createResource: emailResourceResponse(),
    });

    await spiraApi.createResource("goal-1", {
      type: "email",
      name: "coach@example.com",
      role: "Coach",
      email: "coach@example.com",
      phone: "+1-555-0100",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "email",
      name: "coach@example.com",
      role: "Coach",
      email: "coach@example.com",
      phone: "+1-555-0100",
    });
  });

  it("omits resource type on update", async () => {
    const fetchMock = mockGraphqlSuccess({
      updateResource: emailResourceResponse(),
    });

    await spiraApi.updateResource("resource-email", {
      name: "coach@example.com",
      email: "coach@example.com",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      name: "coach@example.com",
      email: "coach@example.com",
    });
  });

  it("serializes numeric target create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createTarget: numericTargetResponse(),
    });

    await spiraApi.createTarget("goal-1", {
      type: "numeric",
      title: "Reach 10 sessions",
      start: 2,
      total: 10,
      unit: "sessions",
      deadline: "2026-05-30T00:00:00.000Z",
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "numeric",
      title: "Reach 10 sessions",
      start: 2,
      total: 10,
      unit: "sessions",
      deadline: "2026-05-30T00:00:00.000Z",
    });
  });

  it("serializes binary target create input", async () => {
    const fetchMock = mockGraphqlSuccess({
      createTarget: binaryTargetResponse(),
    });

    await spiraApi.createTarget("goal-1", {
      type: "binary",
      title: "Publish onboarding page",
      done: false,
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "binary",
      title: "Publish onboarding page",
      done: false,
    });
  });

  it("omits local checklist task ids when creating checklist targets", async () => {
    const fetchMock = mockGraphqlSuccess({
      createTarget: checklistTargetResponse(),
    });

    await spiraApi.createTarget("goal-1", {
      type: "checklist",
      title: "Launch checklist",
      items: [
        {
          id: "local-task-1",
          text: "Draft email",
          done: true,
          deadline: "2026-05-22T00:00:00.000Z",
          achievedAt: "2026-05-21T08:00:00.000Z",
        },
        {
          id: "task-2",
          text: "Publish FAQ",
          done: false,
        },
      ],
    });

    expect(requestBody(fetchMock).variables.input).toEqual({
      type: "checklist",
      title: "Launch checklist",
      items: [
        {
          text: "Draft email",
          done: true,
          deadline: "2026-05-22T00:00:00.000Z",
          achievedAt: "2026-05-21T08:00:00.000Z",
        },
        {
          id: "task-2",
          text: "Publish FAQ",
          done: false,
        },
      ],
    });
  });
});

function mockGraphqlSuccess<T>(data: T) {
  const fetchMock = vi.fn(async () => jsonResponse({ data }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function requestBody(fetchMock: ReturnType<typeof vi.fn>) {
  return JSON.parse(fetchMock.mock.calls[0][1].body as string);
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

function goalResponse() {
  return {
    id: "goal-1",
    title: "Launch Mindscape",
    description: "Ship the refreshed coaching flow",
    confidence: 8,
    deadline: "2026-06-01T00:00:00.000Z",
    createdAt: "2026-05-01T09:00:00.000Z",
    achievedAt: "2026-05-20T18:30:00.000Z",
    reality: {
      id: "goal-1",
      actions: [{ id: "action-1", text: "Schedule customer interviews" }],
      obstacles: [{ id: "obstacle-1", text: "Waiting for design review" }],
    },
    options: [
      { id: "option-1", text: "Stay focused", selected: true, position: 0 },
      { id: "option-2", text: "Reduce scope", selected: false, position: 1 },
    ],
    resources: [
      noteResourceResponse(),
      linkResourceResponse(),
      fileResourceResponse(),
      emailResourceResponse(),
    ],
    targets: [
      numericTargetResponse(),
      binaryTargetResponse(),
      checklistTargetResponse(),
    ],
  };
}

function noteResourceResponse() {
  return {
    id: "resource-note",
    type: "note",
    title: "Plan",
    body: "<p>Keep the launch checklist updated.</p>",
  };
}

function linkResourceResponse() {
  return {
    id: "resource-link",
    type: "link",
    title: "roadmap",
    url: "https://roadmap.example.com/q2",
  };
}

function fileResourceResponse() {
  return {
    id: "resource-file",
    type: "file",
    title: "Deck",
    mime: "application/pdf",
    dataUrl: "data:application/pdf;base64,QQ==",
  };
}

function emailResourceResponse() {
  return {
    id: "resource-email",
    type: "email",
    name: "coach@example.com",
    role: "Coach",
    email: "coach@example.com",
    phone: "+1-555-0100",
  };
}

function numericTargetResponse() {
  return {
    id: "target-numeric",
    type: "numeric",
    title: "Reach 10 sessions",
    start: 2,
    current: 6,
    total: 10,
    unit: "sessions",
    deadline: "2026-05-30T00:00:00.000Z",
    achievedAt: null,
    items: [],
  };
}

function binaryTargetResponse() {
  return {
    id: "target-binary",
    type: "binary",
    title: "Publish onboarding page",
    done: true,
    deadline: null,
    achievedAt: "2026-05-18T12:00:00.000Z",
    items: [],
  };
}

function checklistTargetResponse() {
  return {
    id: "target-checklist",
    type: "checklist",
    title: "Launch checklist",
    deadline: null,
    achievedAt: null,
    items: [
      {
        id: "task-1",
        text: "Draft email",
        done: true,
        deadline: "2026-05-22T00:00:00.000Z",
        achievedAt: "2026-05-21T08:00:00.000Z",
      },
      {
        id: "task-2",
        text: "Publish FAQ",
        done: false,
        deadline: null,
        achievedAt: null,
      },
    ],
  };
}
