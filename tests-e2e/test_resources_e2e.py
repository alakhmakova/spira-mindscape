"""E2E tests: resources (note, link, file, email)."""
from conftest import gql, require_data
from graphql import queries


def test_create_note_resource(client, created_goal):
    result = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal,
        "type": "note",
        "title": "Research notes",
        "body": "Some notes here",
    })
    resource = require_data(result, "createResource")

    assert resource["type"] == "note"
    assert resource["title"] == "Research notes"
    assert resource["body"] == "Some notes here"
    assert resource["url"] is None


def test_create_link_resource(client, created_goal):
    result = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal,
        "type": "link",
        "title": "Docs",
        "url": "https://example.com/docs",
    })
    resource = require_data(result, "createResource")

    assert resource["type"] == "link"
    assert resource["url"] == "https://example.com/docs"


def test_resources_by_goal_returns_created_resources(client, created_goal):
    gql(client, queries.CREATE_RESOURCE, {"goalId": created_goal, "type": "note", "title": "Note A"})
    gql(client, queries.CREATE_RESOURCE, {"goalId": created_goal, "type": "note", "title": "Note B"})

    result = gql(client, queries.RESOURCES_BY_GOAL, {"goalId": created_goal})
    resources = require_data(result, "resourcesByGoal")
    assert len(resources) == 2


def test_resource_by_id(client, created_goal):
    resource_id = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal, "type": "note", "title": "Find me"
    })["data"]["createResource"]["id"]

    result = gql(client, queries.RESOURCE_BY_ID, {"id": resource_id})
    resource = require_data(result, "resourceById")
    assert resource["id"] == resource_id
    assert resource["title"] == "Find me"


def test_update_resource_title_and_body(client, created_goal):
    resource_id = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal, "type": "note", "title": "Old title"
    })["data"]["createResource"]["id"]

    result = gql(client, queries.UPDATE_RESOURCE, {
        "id": resource_id,
        "title": "New title",
        "body": "Updated body",
    })
    resource = require_data(result, "updateResource")
    assert resource["title"] == "New title"
    assert resource["body"] == "Updated body"


def test_delete_resource(client, created_goal):
    resource_id = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal, "type": "note", "title": "Delete me"
    })["data"]["createResource"]["id"]

    result = gql(client, queries.DELETE_RESOURCE, {"id": resource_id})
    assert result["data"]["deleteResource"] is True

    result = gql(client, queries.RESOURCES_BY_GOAL, {"goalId": created_goal})
    assert result["data"]["resourcesByGoal"] == []


def test_create_resource_with_unknown_type_returns_error(client, created_goal):
    result = gql(client, queries.CREATE_RESOURCE, {
        "goalId": created_goal,
        "type": "contact",
        "title": "Old alias",
    })
    assert "errors" in result
    assert any("Unknown resource type" in e["message"] for e in result["errors"])


def test_create_resource_for_nonexistent_goal_returns_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.CREATE_RESOURCE, {
        "goalId": non_existent, "type": "note", "title": "Orphan"
    })
    assert "errors" in result
    assert any("Goal not found" in e["message"] for e in result["errors"])


def test_resource_by_id_returns_error_for_nonexistent_id(client):
    non_existent = str(2**63 - 1)
    result = gql(client, queries.RESOURCE_BY_ID, {"id": non_existent})
    assert "errors" in result
    assert any("Resource not found" in e["message"] for e in result["errors"])
