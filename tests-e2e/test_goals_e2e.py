"""E2E tests: goal CRUD and field validation."""
from conftest import gql, require_data
from graphql import queries


def test_create_goal_with_required_fields(goal_factory):
    result = goal_factory(title="Learn Python", confidence=7)
    goal = require_data(result, "createGoal")

    assert goal["title"] == "Learn Python"
    assert goal["confidence"] == 7
    assert goal["description"] == ""
    assert goal["achievedAt"] is None
    assert goal["deadline"] is None
    assert goal["progress"] == 0.0
    assert goal["reality"]["actions"] == []
    assert goal["reality"]["obstacles"] == []
    assert goal["options"] == []
    assert goal["targets"] == []
    assert goal["resources"] == []
    assert len(goal["confidenceHistory"]) == 1
    assert goal["confidenceHistory"][0]["confidence"] == 7


def test_create_goal_with_all_fields(goal_factory):
    result = goal_factory(
        title="Full Goal",
        confidence=10,
        description="A complete goal description",
        deadline="2027-12-31T00:00:00Z",
    )
    goal = require_data(result, "createGoal")

    assert goal["title"] == "Full Goal"
    assert goal["confidence"] == 10
    assert goal["description"] == "A complete goal description"
    assert goal["deadline"] == "2027-12-31T00:00:00Z"
    assert goal["achievedAt"] is None
    assert goal["progress"] == 0.0
    assert goal["reality"]["actions"] == []
    assert goal["reality"]["obstacles"] == []
    assert goal["options"] == []
    assert goal["targets"] == []
    assert goal["resources"] == []
    assert len(goal["confidenceHistory"]) == 1
    assert goal["confidenceHistory"][0]["confidence"] == 10


def test_goals_query_returns_created_goal(client, created_goal):
    result = gql(client, queries.GOALS)
    goals = require_data(result, "goals")
    ids = [g["id"] for g in goals]
    assert created_goal in ids


def test_goal_by_id_returns_correct_goal(client, created_goal):
    result = gql(client, queries.GOAL_BY_ID, {"id": created_goal})
    goal = require_data(result, "goalById")
    assert goal["id"] == created_goal


def test_goal_deadline_can_be_set_and_cleared(client, created_goal):
    result = gql(client, queries.UPDATE_GOAL, {"id": created_goal, "deadline": "2028-01-01T00:00:00Z"})
    assert require_data(result, "updateGoal")["deadline"] == "2028-01-01T00:00:00Z"

    result = gql(client, queries.UPDATE_GOAL, {"id": created_goal, "deadline": None})
    assert require_data(result, "updateGoal")["deadline"] is None


def test_goal_deadline_rejects_invalid_format(client, created_goal):
    result = gql(client, queries.UPDATE_GOAL, {"id": created_goal, "deadline": "2028-01-01"})
    assert "errors" in result
    assert any("Invalid date format" in e["message"] for e in result["errors"])


def test_update_goal_achieved_at_can_be_set_and_cleared(client, created_goal):
    result = gql(client, queries.UPDATE_GOAL, {
        "id": created_goal,
        "achievedAt": "2027-06-15T00:00:00Z",
    })
    assert require_data(result, "updateGoal")["achievedAt"] == "2027-06-15T00:00:00Z"

    result = gql(client, queries.UPDATE_GOAL, {
        "id": created_goal,
        "achievedAt": None,
    })
    assert require_data(result, "updateGoal")["achievedAt"] is None


def test_update_goal_title(client, created_goal):
    result = gql(client, queries.UPDATE_GOAL, {
        "id": created_goal,
        "title": "Updated Title",
    })
    goal = require_data(result, "updateGoal")
    assert goal["title"] == "Updated Title"


def test_update_goal_confidence_adds_history_entry(client, created_goal):
    result = gql(client, queries.UPDATE_GOAL, {
        "id": created_goal,
        "confidence": 9,
    })
    goal = require_data(result, "updateGoal")
    assert goal["confidence"] == 9
    assert len(goal["confidenceHistory"]) == 2
    assert goal["confidenceHistory"][0]["confidence"] == 9


def test_update_goal_same_confidence_does_not_add_history(client, created_goal):
    initial = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    same_confidence = initial["confidence"]

    gql(client, queries.UPDATE_GOAL, {"id": created_goal, "confidence": same_confidence})
    result = gql(client, queries.GOAL_BY_ID, {"id": created_goal})
    goal = require_data(result, "goalById")
    assert len(goal["confidenceHistory"]) == 1


def test_delete_goal_removes_it_from_list(client):
    result = gql(client, queries.CREATE_GOAL, {"title": "To Delete", "confidence": 5})
    goal_id = result["data"]["createGoal"]["id"]

    gql(client, queries.DELETE_GOAL, {"id": goal_id})

    result = gql(client, queries.GOALS)
    ids = [g["id"] for g in result["data"]["goals"]]
    assert goal_id not in ids


def test_goal_by_id_returns_error_for_nonexistent_id(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.GOAL_BY_ID, {"id": non_existent})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_update_goal_rejects_blank_title_and_preserves_original(client, created_goal):
    initial = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    original_title = initial["title"]

    result = gql(client, queries.UPDATE_GOAL, {"id": created_goal, "title": "   "})
    assert "errors" in result
    assert any("Goal title is required" in e["message"] for e in result["errors"])

    current = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    assert current["title"] == original_title


def test_update_goal_rejects_invalid_confidence_and_preserves_original(client, created_goal):
    initial = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    original_confidence = initial["confidence"]

    result = gql(client, """
        mutation($id: ID!) {
          updateGoal(id: $id, input: { confidence: 11 }) { id }
        }
    """, {"id": created_goal})
    assert "errors" in result
    assert any("confidence" in e["message"].lower() for e in result["errors"])

    current = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    assert current["confidence"] == original_confidence
    assert len(current["confidenceHistory"]) == 1


def test_create_goal_rejects_blank_title(client):
    result = gql(client, queries.CREATE_GOAL, {"title": "   ", "confidence": 5})
    assert "errors" in result
    assert any("Goal title is required" in e["message"] for e in result["errors"])


def test_create_goal_rejects_invalid_confidence(client):
    result = gql(client, """
        mutation {
          createGoal(input: { title: "Bad conf", confidence: 11 }) { id }
        }
    """)
    assert "errors" in result
    assert any("confidence" in e["message"].lower() for e in result["errors"])


def test_create_goal_timestamps_are_iso8601(goal_factory):
    result = goal_factory(title="Timestamp test", confidence=5)
    goal = require_data(result, "createGoal")
    from datetime import datetime, timezone
    created_at = datetime.fromisoformat(goal["createdAt"].replace("Z", "+00:00"))
    updated_at = datetime.fromisoformat(goal["updatedAt"].replace("Z", "+00:00"))
    now = datetime.now(timezone.utc)
    assert created_at <= now
    assert updated_at >= created_at


def test_goal_created_at_does_not_change_after_update(client, created_goal):
    initial = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    created_at = initial["createdAt"]

    gql(client, queries.UPDATE_GOAL, {"id": created_goal, "title": "Updated title"})

    after = require_data(gql(client, queries.GOAL_BY_ID, {"id": created_goal}), "goalById")
    assert after["createdAt"] == created_at
    assert after["title"] == "Updated title"
