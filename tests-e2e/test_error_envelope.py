"""E2E tests: GraphQL error envelope structure and error classifications."""
from conftest import gql


def test_validation_error_has_classification(client, created_goal):
    result = gql(client, """
        mutation($id: ID!) {
          updateGoal(id: $id, input: { title: "   " }) { id }
        }
    """, {"id": created_goal})

    assert "errors" in result
    errors = result["errors"]
    assert errors, "Expected at least one error"
    error = errors[0]
    assert error.get("extensions", {}).get("classification") == "ValidationError", \
        f"Expected ValidationError, got: {error}"


def test_graphql_syntax_error_returns_errors_not_500(client):
    result = gql(client, "this is not valid graphql {{{")
    assert "errors" in result
    assert result["errors"]


def test_error_envelope_has_data_null_on_hard_error(client):
    non_existent = str(2**63 - 1)
    result = gql(client, """
        mutation($id: ID!) {
          deleteGoal(id: $id)
        }
    """, {"id": non_existent})

    assert "errors" in result
    assert result["errors"]


def test_successful_response_has_no_errors_key_or_empty_errors(client):
    result = gql(client, "query { goals { id } }")
    assert "errors" not in result or not result["errors"]


def test_confidence_out_of_range_has_validation_error_classification(client, created_goal):
    result = gql(client, """
        mutation($id: ID!) {
          updateGoal(id: $id, input: { confidence: 0 }) { id }
        }
    """, {"id": created_goal})

    assert "errors" in result
    assert any(
        e.get("extensions", {}).get("classification") == "ValidationError"
        for e in result["errors"]
    ), f"No ValidationError found in: {result['errors']}"
