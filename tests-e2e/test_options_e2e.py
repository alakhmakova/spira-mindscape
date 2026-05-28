"""E2E tests: options (add, update, select, reorder, remove)."""
from conftest import gql, require_data
from graphql import queries


def test_add_option_to_goal(client, created_goal):
    result = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "Move to Berlin",
    })
    option = require_data(result, "addOption")

    assert option["text"] == "Move to Berlin"
    assert option["selected"] is False
    assert option["position"] == 0


def test_multiple_options_get_consecutive_positions(client, created_goal):
    gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Option 1"})
    gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Option 2"})
    gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Option 3"})

    result = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    options = require_data(result, "optionsByGoal")

    assert [o["position"] for o in options] == [0, 1, 2]
    assert [o["text"] for o in options] == ["Option 1", "Option 2", "Option 3"]


def test_select_option_marks_it_selected(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Choose me"})["data"]["addOption"]["id"]

    result = gql(client, queries.SELECT_OPTION, {"goalId": created_goal, "optionId": opt_id})
    option = require_data(result, "selectOption")
    assert option["selected"] is True


def test_select_option_deselects_others(client, created_goal):
    first = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "First"})["data"]["addOption"]["id"]
    second = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Second"})["data"]["addOption"]["id"]

    gql(client, queries.SELECT_OPTION, {"goalId": created_goal, "optionId": first})
    gql(client, queries.SELECT_OPTION, {"goalId": created_goal, "optionId": second})

    result = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    options = {o["id"]: o["selected"] for o in result["data"]["optionsByGoal"]}

    assert options[first] is False
    assert options[second] is True


def test_update_option_text(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Old text"})["data"]["addOption"]["id"]

    result = gql(client, queries.UPDATE_OPTION, {
        "goalId": created_goal,
        "optionId": opt_id,
        "text": "New text",
    })
    option = require_data(result, "updateOption")
    assert option["text"] == "New text"


def test_remove_option(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "To remove"})["data"]["addOption"]["id"]

    result = gql(client, queries.REMOVE_OPTION, {"goalId": created_goal, "optionId": opt_id})
    assert result["data"]["removeOption"] is True

    result = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    assert result["data"]["optionsByGoal"] == []


def test_reorder_options(client, created_goal):
    first = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "A"})["data"]["addOption"]["id"]
    second = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "B"})["data"]["addOption"]["id"]
    third = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "C"})["data"]["addOption"]["id"]

    result = gql(client, """
        mutation($goalId: ID!, $optionIds: [ID!]!) {
          reorderOptions(goalId: $goalId, optionIds: $optionIds) {
            id position
          }
        }
    """, {"goalId": created_goal, "optionIds": [third, first, second]})

    options = result["data"]["reorderOptions"]
    assert options[0]["id"] == third and options[0]["position"] == 0
    assert options[1]["id"] == first and options[1]["position"] == 1
    assert options[2]["id"] == second and options[2]["position"] == 2


def test_add_option_rejects_blank_text(client, created_goal):
    result = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "   ",
    })
    assert "errors" in result
    assert any("Option text is required" in e["message"] for e in result["errors"])

    options = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    assert options["data"]["optionsByGoal"] == []


def test_update_option_rejects_blank_text_and_preserves_original(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "Original text",
    })["data"]["addOption"]["id"]

    result = gql(client, queries.UPDATE_OPTION, {
        "goalId": created_goal,
        "optionId": opt_id,
        "text": "   ",
    })
    assert "errors" in result
    assert any("Option text is required" in e["message"] for e in result["errors"])

    current = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    options = require_data(current, "optionsByGoal")
    assert options[0]["text"] == "Original text"


def test_add_option_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.ADD_OPTION, {"goalId": non_existent, "text": "Orphan"})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_select_option_belonging_to_another_goal_returns_error(client):
    goal_a = gql(client, queries.CREATE_GOAL, {"title": "Goal A", "confidence": 5})["data"]["createGoal"]["id"]
    goal_b = gql(client, queries.CREATE_GOAL, {"title": "Goal B", "confidence": 5})["data"]["createGoal"]["id"]
    opt_in_a = gql(client, queries.ADD_OPTION, {"goalId": goal_a, "text": "A's option"})["data"]["addOption"]["id"]

    result = gql(client, queries.SELECT_OPTION, {"goalId": goal_b, "optionId": opt_in_a})
    assert "errors" in result
    assert any("does not belong to goal" in e["message"] for e in result["errors"])

    gql(client, queries.DELETE_GOAL, {"id": goal_a})
    gql(client, queries.DELETE_GOAL, {"id": goal_b})


def test_options_by_goal_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": non_existent})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_add_option_trims_whitespace(client, created_goal):
    result = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "   Move to Berlin   ",
    })
    option = require_data(result, "addOption")
    assert option["text"] == "Move to Berlin"


def test_update_option_trims_whitespace(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "Original",
    })["data"]["addOption"]["id"]

    result = gql(client, queries.UPDATE_OPTION, {
        "goalId": created_goal,
        "optionId": opt_id,
        "text": "   Trimmed update   ",
    })
    option = require_data(result, "updateOption")
    assert option["text"] == "Trimmed update"


def test_add_option_rejects_oversized_text(client, created_goal):
    result = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "A" * 501,
    })
    assert "errors" in result
    assert any("Option text must be 500 characters or fewer" in e["message"] for e in result["errors"])

    options = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    assert options["data"]["optionsByGoal"] == []


def test_update_option_rejects_oversized_text_and_preserves_original(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {
        "goalId": created_goal,
        "text": "Original text",
    })["data"]["addOption"]["id"]

    result = gql(client, queries.UPDATE_OPTION, {
        "goalId": created_goal,
        "optionId": opt_id,
        "text": "A" * 501,
    })
    assert "errors" in result
    assert any("Option text must be 500 characters or fewer" in e["message"] for e in result["errors"])

    current = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    options = require_data(current, "optionsByGoal")
    assert options[0]["text"] == "Original text"


def test_remove_option_preserves_remaining(client, created_goal):
    first_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Keep me"})["data"]["addOption"]["id"]
    second_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "Remove me"})["data"]["addOption"]["id"]

    result = gql(client, queries.REMOVE_OPTION, {"goalId": created_goal, "optionId": second_id})
    assert result["data"]["removeOption"] is True

    remaining = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": created_goal})
    options = require_data(remaining, "optionsByGoal")
    assert len(options) == 1
    assert options[0]["id"] == first_id
    assert options[0]["text"] == "Keep me"


def test_remove_option_belonging_to_another_goal_returns_error(client):
    goal_a = gql(client, queries.CREATE_GOAL, {"title": "Goal A", "confidence": 5})["data"]["createGoal"]["id"]
    goal_b = gql(client, queries.CREATE_GOAL, {"title": "Goal B", "confidence": 5})["data"]["createGoal"]["id"]
    opt_in_a = gql(client, queries.ADD_OPTION, {"goalId": goal_a, "text": "A's option"})["data"]["addOption"]["id"]

    result = gql(client, queries.REMOVE_OPTION, {"goalId": goal_b, "optionId": opt_in_a})
    assert "errors" in result
    assert any("does not belong to goal" in e["message"] for e in result["errors"])

    # option must still exist under goal A
    options = gql(client, queries.OPTIONS_BY_GOAL, {"goalId": goal_a})
    assert len(options["data"]["optionsByGoal"]) == 1

    gql(client, queries.DELETE_GOAL, {"id": goal_a})
    gql(client, queries.DELETE_GOAL, {"id": goal_b})


def test_select_option_nonexistent_goal_returns_error(client, created_goal):
    opt_id = gql(client, queries.ADD_OPTION, {"goalId": created_goal, "text": "An option"})["data"]["addOption"]["id"]
    non_existent_goal = str(2**63 - 1)

    result = gql(client, queries.SELECT_OPTION, {"goalId": non_existent_goal, "optionId": opt_id})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_update_option_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.UPDATE_OPTION, {
        "goalId": non_existent,
        "optionId": non_existent,
        "text": "Updated",
    })
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_remove_option_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.REMOVE_OPTION, {
        "goalId": non_existent,
        "optionId": non_existent,
    })
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_reorder_options_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, """
        mutation($goalId: ID!, $optionIds: [ID!]!) {
          reorderOptions(goalId: $goalId, optionIds: $optionIds) { id }
        }
    """, {"goalId": non_existent, "optionIds": [non_existent]})
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])
