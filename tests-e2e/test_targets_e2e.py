"""E2E tests: targets (binary, numeric, checklist) CRUD and progress."""
import pytest
from conftest import gql, require_data
from graphql import queries


def test_create_binary_target(client, created_goal):
    result = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal,
        "type": "binary",
        "title": "Finish module",
    })
    target = require_data(result, "createTarget")

    assert target["type"] == "binary"
    assert target["title"] == "Finish module"
    assert target["done"] is False
    assert target["deadline"] is None
    assert target["progress"] == 0.0


def test_update_binary_target_to_done(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "binary", "title": "Binary task"
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "done": True})
    target = require_data(result, "updateTarget")
    assert target["done"] is True
    assert target["progress"] == 1.0


def test_create_numeric_target(client, created_goal):
    result = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal,
        "type": "numeric",
        "title": "Read pages",
        "start": 0.0,
        "total": 100.0,
        "unit": "pages",
    })
    target = require_data(result, "createTarget")

    assert target["type"] == "numeric"
    assert target["title"] == "Read pages"
    assert target["start"] == 0.0
    assert target["current"] == 0.0
    assert target["total"] == 100.0
    assert target["unit"] == "pages"
    assert target["deadline"] is None
    assert target["progress"] == 0.0


def test_update_numeric_target_current(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Pages", "start": 0.0, "total": 10.0
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "current": 5.0})
    target = require_data(result, "updateTarget")
    assert target["current"] == 5.0
    assert target["progress"] == pytest.approx(0.5)


def test_create_checklist_target(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!, $title: String!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: $title
            items: [
              { text: "Step 1", done: false }
              { text: "Step 2", done: false }
            ]
          }) {
            id type title deadline items { id text done } progress
          }
        }
    """, {"goalId": created_goal, "title": "Checklist"})
    target = require_data(result, "createTarget")

    assert target["type"] == "checklist"
    assert target["title"] == "Checklist"
    assert target["deadline"] is None
    assert len(target["items"]) == 2
    assert target["items"][0]["text"] == "Step 1"
    assert target["items"][0]["done"] is False
    assert target["items"][1]["text"] == "Step 2"
    assert target["items"][1]["done"] is False
    assert target["progress"] == 0.0


def test_delete_target(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "binary", "title": "To delete"
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.DELETE_TARGET, {"id": target_id})
    assert result["data"]["deleteTarget"] is True

    result = gql(client, queries.TARGETS_BY_GOAL, {"goalId": created_goal})
    assert result["data"]["targetsByGoal"] == []


def test_targets_by_goal_returns_created_targets(client, created_goal):
    gql(client, queries.CREATE_TARGET, {"goalId": created_goal, "type": "binary", "title": "T1"})
    gql(client, queries.CREATE_TARGET, {"goalId": created_goal, "type": "binary", "title": "T2"})

    result = gql(client, queries.TARGETS_BY_GOAL, {"goalId": created_goal})
    targets = require_data(result, "targetsByGoal")
    assert len(targets) == 2


def test_target_by_id(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "binary", "title": "Find me"
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.TARGET_BY_ID, {"id": target_id})
    target = require_data(result, "targetById")
    assert target["id"] == target_id
    assert target["title"] == "Find me"


def test_checklist_target_progress_updates_as_items_are_completed(client, created_goal):
    target_id = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Progress checklist"
            items: [{ text: "Step 1" }, { text: "Step 2" }, { text: "Step 3" }]
          }) { id items { id } progress }
        }
    """, {"goalId": created_goal})

    target_data = require_data(target_id, "createTarget")
    assert target_data["progress"] == 0.0
    item_ids = [i["id"] for i in target_data["items"]]

    result = gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!, $i2: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: true }
              { id: $i1, text: "Step 2", done: false }
              { id: $i2, text: "Step 3", done: false }
            ]
          }) { progress }
        }
    """, {"id": target_data["id"], "i0": item_ids[0], "i1": item_ids[1], "i2": item_ids[2]})
    assert require_data(result, "updateTarget")["progress"] == pytest.approx(1 / 3)

    result = gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!, $i2: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: true }
              { id: $i1, text: "Step 2", done: true }
              { id: $i2, text: "Step 3", done: true }
            ]
          }) { progress }
        }
    """, {"id": target_data["id"], "i0": item_ids[0], "i1": item_ids[1], "i2": item_ids[2]})
    assert require_data(result, "updateTarget")["progress"] == pytest.approx(1.0)


def test_numeric_target_progress_reaches_one_at_total(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Finish", "start": 0.0, "total": 10.0
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "current": 10.0})
    assert require_data(result, "updateTarget")["progress"] == pytest.approx(1.0)


def test_create_checklist_target_without_items_returns_error(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "No items"
          }) { id }
        }
    """, {"goalId": created_goal})
    assert "errors" in result
    assert any("Checklist target requires at least one item" in e["message"] for e in result["errors"])


def test_create_checklist_target_with_empty_items_returns_error(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Empty items"
            items: []
          }) { id }
        }
    """, {"goalId": created_goal})
    assert "errors" in result
    assert any("Checklist target requires at least one item" in e["message"] for e in result["errors"])


def test_update_checklist_target_to_empty_items_returns_error(client, created_goal):
    target_id = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Has items"
            items: [{ text: "Step 1" }, { text: "Step 2" }]
          }) { id }
        }
    """, {"goalId": created_goal})["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { items: [] }) { id }
        }
    """, {"id": target_id})
    assert "errors" in result
    assert any("Checklist target requires at least one item" in e["message"] for e in result["errors"])

    items = gql(client, """
        query($id: ID!) {
          targetById(id: $id) { items { id } }
        }
    """, {"id": target_id})["data"]["targetById"]["items"]
    assert len(items) == 2


def test_create_binary_target_as_done_returns_error(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "binary"
            title: "Already done"
            done: true
          }) { id }
        }
    """, {"goalId": created_goal})
    assert "errors" in result
    assert any("Cannot create binary target as already done" in e["message"] for e in result["errors"])


def test_create_binary_target_with_blank_title_returns_error(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "binary"
            title: "   "
          }) { id }
        }
    """, {"goalId": created_goal})
    assert "errors" in result
    assert any("Target title is required" in e["message"] for e in result["errors"])


def test_update_target_rejects_blank_title_and_preserves_original(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal,
        "type": "binary",
        "title": "Original title",
    })["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { title: "   " }) { id title }
        }
    """, {"id": target_id})
    assert "errors" in result
    assert any("Target title is required" in e["message"] for e in result["errors"])

    current = gql(client, queries.TARGET_BY_ID, {"id": target_id})
    assert require_data(current, "targetById")["title"] == "Original title"


def test_target_deadline_can_be_set_and_cleared(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "binary", "title": "Task with deadline"
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "deadline": "2028-01-01T00:00:00Z"})
    assert require_data(result, "updateTarget")["deadline"] == "2028-01-01T00:00:00Z"

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "deadline": None})
    assert require_data(result, "updateTarget")["deadline"] is None


def test_target_deadline_rejects_invalid_format(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "binary", "title": "Task"
    })["data"]["createTarget"]["id"]

    result = gql(client, queries.UPDATE_TARGET, {"id": target_id, "deadline": "2028-01-01"})
    assert "errors" in result
    assert any("Invalid date format" in e["message"] for e in result["errors"])


def test_checklist_task_deadline_can_be_set_and_cleared(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Checklist"
            items: [{ text: "Step 1" }, { text: "Step 2" }]
          }) { id items { id } }
        }
    """, {"goalId": created_goal})
    target_id = result["data"]["createTarget"]["id"]
    item_ids = [i["id"] for i in result["data"]["createTarget"]["items"]]

    result = gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: false, deadline: "2028-01-01T00:00:00Z" }
              { id: $i1, text: "Step 2", done: false }
            ]
          }) { id items { id deadline } }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert result["data"]["updateTarget"]["items"][0]["deadline"] == "2028-01-01T00:00:00Z"

    result = gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: false, deadline: null }
              { id: $i1, text: "Step 2", done: false }
            ]
          }) { id items { id deadline } }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert result["data"]["updateTarget"]["items"][0]["deadline"] is None


def test_checklist_task_deadline_rejects_invalid_format(client, created_goal):
    result = gql(client, """
        mutation($goalId: ID!) {
          createTarget(goalId: $goalId, input: {
            type: "checklist"
            title: "Checklist"
            items: [{ text: "Step 1" }, { text: "Step 2" }]
          }) { id items { id } }
        }
    """, {"goalId": created_goal})
    target_id = result["data"]["createTarget"]["id"]
    item_ids = [i["id"] for i in result["data"]["createTarget"]["items"]]

    result = gql(client, """
        mutation($id: ID!, $i0: ID!, $i1: ID!) {
          updateTarget(id: $id, input: {
            items: [
              { id: $i0, text: "Step 1", done: false, deadline: "2028-01-01" }
              { id: $i1, text: "Step 2", done: false }
            ]
          }) { id }
        }
    """, {"id": target_id, "i0": item_ids[0], "i1": item_ids[1]})
    assert "errors" in result
    assert any("Invalid date format" in e["message"] for e in result["errors"])


def test_create_target_for_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.CREATE_TARGET, {
        "goalId": non_existent, "type": "binary", "title": "Orphan"
    })
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_target_by_id_returns_error_for_nonexistent_id(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.TARGET_BY_ID, {"id": non_existent})
    assert "errors" in result
    assert any("Target not found" in e["message"] for e in result["errors"])


def test_update_numeric_target_start_value(client, created_goal):
    # create start=5, current=5, total=10 → update start to 2 → progress=(5-2)/(10-2)=3/8
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Start shift", "start": 5.0, "total": 10.0,
    })["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { start: 2 }) {
            start current total progress
          }
        }
    """, {"id": target_id})
    target = require_data(result, "updateTarget")

    assert target["start"] == pytest.approx(2.0)
    assert target["current"] == pytest.approx(5.0)
    assert target["total"] == pytest.approx(10.0)
    assert target["progress"] == pytest.approx(3 / 8)


def test_update_numeric_target_total_value(client, created_goal):
    # create start=0, current=0, total=10; advance to 5 → update total to 20 → progress=5/20=0.25
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Total shift", "start": 0.0, "total": 10.0,
    })["data"]["createTarget"]["id"]
    gql(client, queries.UPDATE_TARGET, {"id": target_id, "current": 5.0})

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { total: 20 }) {
            start current total progress
          }
        }
    """, {"id": target_id})
    target = require_data(result, "updateTarget")

    assert target["start"] == pytest.approx(0.0)
    assert target["current"] == pytest.approx(5.0)
    assert target["total"] == pytest.approx(20.0)
    assert target["progress"] == pytest.approx(0.25)


def test_update_numeric_target_negative_start_returns_error(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Read pages", "start": 0.0, "total": 10.0,
    })["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { start: -1 }) { id }
        }
    """, {"id": target_id})
    assert "errors" in result
    assert any("Numeric target start cannot be negative" in e["message"] for e in result["errors"])


def test_update_numeric_target_negative_total_returns_error(client, created_goal):
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Read pages", "start": 0.0, "total": 10.0,
    })["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { total: -1 }) { id }
        }
    """, {"id": target_id})
    assert "errors" in result
    assert any("Numeric target target cannot be negative" in e["message"] for e in result["errors"])


def test_update_numeric_target_equal_start_and_total_returns_error(client, created_goal):
    # start=0, current=0, total=10 → update start to 10 → start==total → invalid
    target_id = gql(client, queries.CREATE_TARGET, {
        "goalId": created_goal, "type": "numeric", "title": "Read pages", "start": 0.0, "total": 10.0,
    })["data"]["createTarget"]["id"]

    result = gql(client, """
        mutation($id: ID!) {
          updateTarget(id: $id, input: { start: 10 }) { id }
        }
    """, {"id": target_id})
    assert "errors" in result
    assert any("Numeric target start and target must be different" in e["message"] for e in result["errors"])
