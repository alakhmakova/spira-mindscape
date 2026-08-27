"""E2E: the cap on how many goals one user may have in motion.

Owner, 2026-08-25. Nothing used to stop a user — or a looping assistant, or a script — from
creating goals without end, and the dashboard loads every one of them: 598 goals measured 162 KB
and up to two seconds cold on the owner's machine.

These run against the **real stack**, which is what makes them worth having on top of the unit and
integration tests: the cap has to survive the whole path — resolver, service, JPA count query —
and the message the client finally receives has to be the readable one, not a sanitised
"Reference: …". Only a live backend proves that.

## Which profile to run this against

The suite is meant for the backend's **`e2e` profile**, which switches the rate limiter off
(`spira.ratelimit.enabled=false`). Run it against `local` — the profile used for hand-testing —
and the limiter is on at 120 GraphQL calls a minute, so a long run starts failing with 429s that
have nothing to do with what is being tested.

## Why the goals are created in ONE request

Filling the allowance a mutation at a time is 50 requests, and cleaning up is 50 more; the backend
allows **120 GraphQL calls a minute** (`spira.ratelimit.graphql-per-minute`), so the first draft of
this file spent the whole budget and the suite started failing with 429s that looked nothing like a
cap problem. GraphQL runs the fields of a mutation **serially**, so fifty aliased `createGoal`
fields in a single document do the same work in one request — and leave room for the rest of the
suite.

The same goes for the cleanup, which is unconditional: a test that left fifty goals behind would
poison the cap for everything that ran after it, and the failure would appear somewhere else
entirely.
"""
import pytest

from conftest import gql, require_data
from graphql import queries

# Keep in step with GoalService.MAX_ACTIVE_GOALS.
MAX_ACTIVE_GOALS = 50


def _batch_create(client, count, prefix="Cap filler"):
    """Create `count` goals in ONE request; returns their ids."""
    fields = "\n".join(
        f'g{i}: createGoal(input: {{ title: "{prefix} {i}", confidence: 5 }}) {{ id }}'
        for i in range(count)
    )
    result = gql(client, "mutation { " + fields + " }")
    data = require_data({"data": result.get("data"), "errors": result.get("errors")}, "g0")
    assert data["id"]
    return [result["data"][f"g{i}"]["id"] for i in range(count)]


def _batch_delete(client, ids):
    if not ids:
        return
    fields = "\n".join(f'd{i}: deleteGoal(id: "{gid}")' for i, gid in enumerate(ids))
    gql(client, "mutation { " + fields + " }")


@pytest.fixture
def full_allowance(client):
    """Fill this user's allowance to `count`, and clear it afterwards whatever happens."""
    created = []

    def _fill(count):
        created.extend(_batch_create(client, count))
        return created

    yield _fill

    _batch_delete(client, created)


def test_creating_past_the_cap_is_refused_with_a_readable_message(client, full_allowance):
    full_allowance(MAX_ACTIVE_GOALS)

    result = gql(client, queries.CREATE_GOAL, {"title": "One too many", "confidence": 5})

    errors = result.get("errors") or []
    assert errors, "the cap must refuse the creation, not silently allow it"
    message = errors[0]["message"]
    # The user has to be told what to do about it — a bare "invalid" would be useless here.
    assert "50 goals in motion" in message, message
    assert "Achieve or delete one" in message, message
    # And it must arrive as a validation error, not as an internal one with a trace reference:
    # GraphQlExceptionHandler only passes the sentence through for IllegalArgumentException.
    assert errors[0]["extensions"]["classification"] == "ValidationError", errors[0]
    assert (result.get("data") or {}).get("createGoal") is None


def test_one_below_the_cap_still_goes_through(client, full_allowance):
    created = full_allowance(MAX_ACTIVE_GOALS - 1)

    goal = require_data(
        gql(client, queries.CREATE_GOAL, {"title": "The fiftieth", "confidence": 5}),
        "createGoal",
    )
    created.append(goal["id"])

    assert goal["title"] == "The fiftieth"


def test_achieving_a_goal_makes_room(client, full_allowance):
    """The cap counts goals IN MOTION, never a lifetime of them.

    This is the distinction the owner asked for: someone who achieves a lot must not be the
    person who runs out of room, with deleting their own history as the only way forward.
    """
    created = full_allowance(MAX_ACTIVE_GOALS)

    # Full now — confirm the wall is really there before proving it moves.
    refused = gql(client, queries.CREATE_GOAL, {"title": "Blocked", "confidence": 5})
    assert refused.get("errors"), "expected the cap to be reached"

    achieved = require_data(
        gql(client, queries.UPDATE_GOAL,
            {"id": created[0], "achievedAt": "2026-08-25T00:00:00Z"}),
        "updateGoal",
    )
    assert achieved["achievedAt"] is not None

    goal = require_data(
        gql(client, queries.CREATE_GOAL, {"title": "Room again", "confidence": 5}),
        "createGoal",
    )
    created.append(goal["id"])
    assert goal["title"] == "Room again"
