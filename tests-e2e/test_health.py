"""Smoke tests: connectivity and basic GraphQL envelope."""
from conftest import gql


def test_graphql_endpoint_responds_without_errors(client):
    result = gql(client, "query { goals { id title } }")
    assert "data" in result
    assert "goals" in result["data"]
    assert "errors" not in result or not result["errors"]


def test_graphql_envelope_returns_errors_for_unknown_field(client):
    result = gql(client, "query { goals { nonExistentField } }")
    assert "errors" in result
    assert result["errors"]
