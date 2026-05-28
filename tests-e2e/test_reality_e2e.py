"""E2E tests: reality items (actions and obstacles)."""
from conftest import gql, require_data
from graphql import queries


def test_add_action_to_reality(client, created_goal):
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "Read documentation",
    })
    reality = require_data(result, "addRealityItem")
    assert len(reality["actions"]) == 1
    assert reality["actions"][0]["text"] == "Read documentation"
    assert reality["obstacles"] == []


def test_add_obstacle_to_reality(client, created_goal):
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "obstacles",
        "text": "Lack of time",
    })
    reality = require_data(result, "addRealityItem")
    assert len(reality["obstacles"]) == 1
    assert reality["obstacles"][0]["text"] == "Lack of time"
    assert reality["actions"] == []


def test_reality_by_goal_returns_both_kinds(client, created_goal):
    gql(client, queries.ADD_REALITY_ITEM, {"goalId": created_goal, "kind": "actions", "text": "Action A"})
    gql(client, queries.ADD_REALITY_ITEM, {"goalId": created_goal, "kind": "obstacles", "text": "Obstacle B"})

    result = gql(client, queries.REALITY_BY_GOAL, {"goalId": created_goal})
    reality = require_data(result, "realityByGoal")

    assert any(a["text"] == "Action A" for a in reality["actions"])
    assert any(o["text"] == "Obstacle B" for o in reality["obstacles"])


def test_update_reality_item_text(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "Original text",
    })
    item_id = add_result["data"]["addRealityItem"]["actions"][0]["id"]

    result = gql(client, queries.UPDATE_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "itemId": item_id,
        "text": "Updated text",
    })
    reality = require_data(result, "updateRealityItem")
    assert reality["actions"][0]["text"] == "Updated text"


def test_remove_reality_item(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "obstacles",
        "text": "To remove",
    })
    item_id = add_result["data"]["addRealityItem"]["obstacles"][0]["id"]

    result = gql(client, queries.REMOVE_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "obstacles",
        "itemId": item_id,
    })
    reality = require_data(result, "removeRealityItem")
    assert reality["obstacles"] == []


def test_update_reality_item_rejects_blank_text_and_preserves_original(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "Original action",
    })
    item_id = add_result["data"]["addRealityItem"]["actions"][0]["id"]

    result = gql(client, queries.UPDATE_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "itemId": item_id,
        "text": "   ",
    })
    assert "errors" in result
    assert any("text is required" in e["message"] for e in result["errors"])

    current = gql(client, queries.REALITY_BY_GOAL, {"goalId": created_goal})
    reality = require_data(current, "realityByGoal")
    assert reality["actions"][0]["text"] == "Original action"


def test_add_reality_item_rejects_blank_text(client, created_goal):
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "   ",
    })
    assert "errors" in result
    assert any("text is required" in e["message"] for e in result["errors"])


def test_add_reality_item_rejects_unknown_kind(client, created_goal):
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "risks",
        "text": "Some risk",
    })
    assert "errors" in result
    assert any("Unknown reality kind" in e["message"] for e in result["errors"])


def test_update_reality_item_wrong_kind_returns_error(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "An action",
    })
    item_id = add_result["data"]["addRealityItem"]["actions"][0]["id"]

    result = gql(client, queries.UPDATE_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "obstacles",
        "itemId": item_id,
        "text": "Wrong kind",
    })
    assert "errors" in result
    assert any("does not belong to goal/kind" in e["message"] for e in result["errors"])


def test_singular_kind_alias_works(client, created_goal):
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "action",
        "text": "Singular kind",
    })
    reality = require_data(result, "addRealityItem")
    assert len(reality["actions"]) == 1


def test_reality_item_by_id_returns_correct_item(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "actions",
        "text": "Find me by id",
    })
    item_id = add_result["data"]["addRealityItem"]["actions"][0]["id"]

    result = gql(client, queries.REALITY_ITEM_BY_ID, {"id": item_id})
    item = require_data(result, "realityItemById")

    assert item["id"] == item_id
    assert item["text"] == "Find me by id"
    assert item["createdAt"] is not None
    assert item["updatedAt"] is not None


def test_reality_item_by_id_works_for_obstacles(client, created_goal):
    add_result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": created_goal,
        "kind": "obstacles",
        "text": "This obstacle by id",
    })
    item_id = add_result["data"]["addRealityItem"]["obstacles"][0]["id"]

    result = gql(client, queries.REALITY_ITEM_BY_ID, {"id": item_id})
    item = require_data(result, "realityItemById")

    assert item["id"] == item_id
    assert item["text"] == "This obstacle by id"


def test_reality_item_by_id_returns_error_for_nonexistent_id(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.REALITY_ITEM_BY_ID, {"id": non_existent})
    assert "errors" in result
    assert any("Reality item not found" in e["message"] for e in result["errors"])


def test_add_reality_item_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.ADD_REALITY_ITEM, {
        "goalId": non_existent, "kind": "action", "text": "Orphan"
    })
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_reality_by_goal_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.REALITY_BY_GOAL, {"goalId": non_existent})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])
