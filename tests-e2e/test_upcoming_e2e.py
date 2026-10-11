"""E2E tests: the All-goals page's Upcoming column, through the running backend.

An event is an important date (a name and a date of its own) or a link to a goal, target or
checklist item that has a deadline. These run the real HTTP + GraphQL + Postgres path, including
the parts only a real database shows: the FK cascades of V40 and the deadline cleared in the same
transaction as a deleted link.
"""
import pytest
from conftest import gql, require_data

DEADLINE = "2026-12-31T00:00:00Z"

LIST = "{ upcomingEvents { id kind title date goalId targetId taskId } }"
CREATE = """
    mutation($input: CreateUpcomingEventInput!) {
      createUpcomingEvent(input: $input) { id kind title date goalId targetId taskId }
    }
"""
DELETE = "mutation($id: ID!) { deleteUpcomingEvent(id: $id) }"


def errors_of(result: dict) -> str:
    return " | ".join(e.get("message", "") for e in result.get("errors") or [])


@pytest.fixture
def events(client):
    """Ids of events created by a test, deleted afterwards whatever happened."""
    created: list[str] = []
    yield created
    for event_id in created:
        gql(client, DELETE, {"id": event_id})


def create(client, events, **input_) -> dict:
    event = require_data(gql(client, CREATE, {"input": input_}), "createUpcomingEvent")
    events.append(event["id"])
    return event


@pytest.fixture
def linked_items(client, goal_factory):
    """A goal, a checklist target and a task, each with a deadline."""
    goal_id = goal_factory("Upcoming E2E goal", deadline=DEADLINE)["data"]["createGoal"]["id"]
    target = require_data(gql(client, """
        mutation($g: ID!, $d: String) {
          createTarget(goalId: $g, input: {
            type: "checklist", title: "Upcoming E2E target", deadline: $d,
            items: [{ text: "Upcoming E2E task", deadline: $d }]
          }) { id items { id } }
        }
    """, {"g": goal_id, "d": DEADLINE}), "createTarget")
    return {"goal": goal_id, "target": target["id"], "task": target["items"][0]["id"]}


def test_important_date_round_trip(client, events):
    event = create(client, events, kind="important", title="  Mom's birthday  ", date=DEADLINE)

    assert event["kind"] == "important"
    assert event["title"] == "Mom's birthday"
    assert event["date"] == DEADLINE
    listed = require_data(gql(client, LIST), "upcomingEvents")
    assert event["id"] in [e["id"] for e in listed]


def test_links_carry_ids_and_no_title_or_date(client, events, linked_items):
    goal = create(client, events, kind="goal", goalId=linked_items["goal"])
    target = create(client, events, kind="target", targetId=linked_items["target"])
    task = create(client, events, kind="task", taskId=linked_items["task"])

    assert goal["goalId"] == linked_items["goal"]
    assert target["targetId"] == linked_items["target"]
    assert task["taskId"] == linked_items["task"]
    assert goal["title"] is None and goal["date"] is None


@pytest.mark.parametrize("input_,message", [
    ({"kind": "important", "title": "   ", "date": DEADLINE}, "Event name is required"),
    ({"kind": "important", "title": "x" * 201, "date": DEADLINE}, "200 characters"),
    ({"kind": "important", "title": "Trip"}, "Event date is required"),
    ({"kind": "important", "title": "Trip", "date": "next tuesday"}, "Invalid date format"),
    ({"kind": "recurring", "title": "Trip", "date": DEADLINE}, "Unknown event type"),
    ({"kind": "goal"}, "goalId is required"),
    ({"kind": "goal", "goalId": "999999999"}, "not found"),
])
def test_invalid_input_is_refused(client, input_, message):
    result = gql(client, CREATE, {"input": input_})
    assert message in errors_of(result)


def test_a_goal_without_a_deadline_cannot_be_linked(client, goal_factory):
    goal_id = goal_factory("Upcoming E2E bare goal")["data"]["createGoal"]["id"]
    result = gql(client, CREATE, {"input": {"kind": "goal", "goalId": goal_id}})
    assert "This goal has no deadline" in errors_of(result)


def test_the_same_goal_cannot_be_linked_twice(client, events, linked_items):
    create(client, events, kind="goal", goalId=linked_items["goal"])
    result = gql(client, CREATE, {"input": {"kind": "goal", "goalId": linked_items["goal"]}})
    assert "already in Upcoming" in errors_of(result)


def test_deleting_a_link_clears_that_deadline_only(client, events, linked_items):
    task_event = create(client, events, kind="task", taskId=linked_items["task"])

    assert require_data(gql(client, DELETE, {"id": task_event["id"]}), "deleteUpcomingEvent")
    events.remove(task_event["id"])

    goal = require_data(gql(client, """
        query($id: ID!) { goalById(id: $id) { deadline targets { deadline items { deadline } } } }
    """, {"id": linked_items["goal"]}), "goalById")
    assert goal["targets"][0]["items"][0]["deadline"] is None
    assert goal["targets"][0]["deadline"] is not None
    assert goal["deadline"] is not None


def test_deleting_the_goal_removes_its_linked_events(client, linked_items):
    gql(client, CREATE, {"input": {"kind": "goal", "goalId": linked_items["goal"]}})
    gql(client, CREATE, {"input": {"kind": "task", "taskId": linked_items["task"]}})

    gql(client, "mutation($id: ID!) { deleteGoal(id: $id) }", {"id": linked_items["goal"]})

    listed = require_data(gql(client, LIST), "upcomingEvents")
    assert not [e for e in listed if e["goalId"] == linked_items["goal"]
                or e["taskId"] == linked_items["task"]]


def test_deleting_an_unknown_event_is_not_found(client):
    result = gql(client, DELETE, {"id": "999999999"})
    assert "not found" in errors_of(result)
