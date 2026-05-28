"""E2E tests: goal progress calculated from targets."""
import pytest
from conftest import gql, require_data
from graphql import queries


def _create_binary_target(client, goal_id, title):
    return gql(client, queries.CREATE_TARGET, {
        "goalId": goal_id, "type": "binary", "title": title
    })["data"]["createTarget"]["id"]


def _create_numeric_target(client, goal_id, title, start, total):
    return gql(client, queries.CREATE_TARGET, {
        "goalId": goal_id, "type": "numeric", "title": title, "start": start, "total": total
    })["data"]["createTarget"]["id"]


def _goal_progress(client, goal_id):
    result = gql(client, """
        query($id: ID!) {
          goalById(id: $id) { progress }
        }
    """, {"id": goal_id})
    return result["data"]["goalById"]["progress"]


def test_goal_with_no_targets_has_zero_progress(client, created_goal):
    assert _goal_progress(client, created_goal) == 0.0


def test_goal_progress_is_zero_when_binary_target_not_done(client, created_goal):
    _create_binary_target(client, created_goal, "Not done")
    assert _goal_progress(client, created_goal) == 0.0


def test_goal_progress_is_one_when_binary_target_is_done(client, created_goal):
    target_id = _create_binary_target(client, created_goal, "Done task")
    gql(client, queries.UPDATE_TARGET, {"id": target_id, "done": True})
    assert _goal_progress(client, created_goal) == pytest.approx(1.0)


def test_goal_progress_is_average_of_all_target_progress(client, created_goal):
    numeric_id = _create_numeric_target(client, created_goal, "Read pages", 0.0, 10.0)
    gql(client, queries.UPDATE_TARGET, {"id": numeric_id, "current": 5.0})

    _create_binary_target(client, created_goal, "Not done")

    progress = _goal_progress(client, created_goal)
    assert progress == pytest.approx(0.5 / 2)


def test_goal_progress_recalculates_after_target_update(client, created_goal):
    target_id = _create_binary_target(client, created_goal, "Toggle me")
    assert _goal_progress(client, created_goal) == 0.0

    gql(client, queries.UPDATE_TARGET, {"id": target_id, "done": True})
    assert _goal_progress(client, created_goal) == pytest.approx(1.0)

    gql(client, queries.UPDATE_TARGET, {"id": target_id, "done": False})
    assert _goal_progress(client, created_goal) == 0.0


def test_goal_progress_after_target_deletion(client, created_goal):
    t1 = _create_binary_target(client, created_goal, "Keep")
    t2 = _create_binary_target(client, created_goal, "Delete")
    gql(client, queries.UPDATE_TARGET, {"id": t1, "done": True})

    progress_before = _goal_progress(client, created_goal)
    assert progress_before == pytest.approx(0.5)

    gql(client, queries.DELETE_TARGET, {"id": t2})

    progress_after = _goal_progress(client, created_goal)
    assert progress_after == pytest.approx(1.0)


def test_goal_progress_reaches_full_then_drops_when_target_is_reverted(client, created_goal):
    t1 = _create_binary_target(client, created_goal, "Task 1")
    t2 = _create_binary_target(client, created_goal, "Task 2")
    gql(client, queries.UPDATE_TARGET, {"id": t1, "done": True})
    gql(client, queries.UPDATE_TARGET, {"id": t2, "done": True})

    assert _goal_progress(client, created_goal) == pytest.approx(1.0)

    gql(client, queries.UPDATE_TARGET, {"id": t1, "done": False})

    assert _goal_progress(client, created_goal) == pytest.approx(0.5)


def test_goal_progress_with_checklist_target(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Checklist"
            items: [{ text: "A" }, { text: "B" }]
          }) { id items { id } }
        }
    """, {"goalId": created_goal})
    target_data = result["data"]["createTarget"]
    target_id = target_data["id"]
    item_ids = [i["id"] for i in target_data["items"]]

    assert _goal_progress(client, created_goal) == 0.0

    gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "A", done: true }
              { id: $i1, text: "B", done: false }
            ]
          }) { id }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert _goal_progress(client, created_goal) == pytest.approx(0.5)

    gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "A", done: true }
              { id: $i1, text: "B", done: true }
            ]
          }) { id }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert _goal_progress(client, created_goal) == pytest.approx(1.0)


def test_goal_progress_is_one_when_single_numeric_target_reaches_total(client, created_goal):
    target_id = _create_numeric_target(client, created_goal, "Read pages", 0.0, 10.0)
    gql(client, queries.UPDATE_TARGET, {"id": target_id, "current": 10.0})
    assert _goal_progress(client, created_goal) == pytest.approx(1.0)


def test_goal_progress_drops_when_new_incomplete_checklist_target_is_added(client, created_goal):
    binary_id = _create_binary_target(client, created_goal, "Done task")
    gql(client, queries.UPDATE_TARGET, {"id": binary_id, "done": True})
    assert _goal_progress(client, created_goal) == pytest.approx(1.0)

    gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Not started"
            items: [{ text: "Step 1" }, { text: "Step 2" }]
          }) { id }
        }
    """, {"goalId": created_goal})
    assert _goal_progress(client, created_goal) == pytest.approx(0.5)


def test_goal_progress_drops_when_checklist_item_is_reverted(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Full checklist"
            items: [{ text: "Step 1", done: true }, { text: "Step 2", done: true }]
          }) { id items { id } }
        }
    """, {"goalId": created_goal})
    target_data = result["data"]["createTarget"]
    target_id = target_data["id"]
    item_ids = [i["id"] for i in target_data["items"]]

    assert _goal_progress(client, created_goal) == pytest.approx(1.0)

    gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: true }
              { id: $i1, text: "Step 2", done: false }
            ]
          }) { id }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert _goal_progress(client, created_goal) == pytest.approx(0.5)


def test_numeric_descending_target_progress(client, created_goal):
    target_id = _create_numeric_target(client, created_goal, "Lose weight", 70.0, 60.0)
    gql(client, queries.UPDATE_TARGET, {"id": target_id, "current": 65.0})

    result = gql(client, queries.TARGET_BY_ID, {"id": target_id})
    target = result["data"]["targetById"]
    assert target["progress"] == pytest.approx(0.5)
