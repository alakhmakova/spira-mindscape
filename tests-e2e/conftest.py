import os
import pytest
import httpx

from graphql import queries


BASE_URL = os.environ.get("SPIRA_BASE_URL", "http://localhost:8080")
GRAPHQL_URL = f"{BASE_URL}/graphql"

# The app requires a Google login and is user-scoped. Real OAuth can't run in CI,
# so the backend runs under the 'e2e' Spring profile, which authenticates requests
# carrying this header as a seeded test user (see E2eTestAuthFilter); CSRF is
# disabled there. Locally, run the jar with SPRING_PROFILES_ACTIVE=e2e to use these.
E2E_AUTH_EMAIL = os.environ.get("SPIRA_E2E_AUTH", "e2e@test.local")


@pytest.fixture(scope="session")
def client():
    headers = {"X-E2E-Auth": E2E_AUTH_EMAIL}
    with httpx.Client(base_url=BASE_URL, timeout=10.0, headers=headers) as c:
        yield c


def gql(client: httpx.Client, query: str, variables: dict | None = None) -> dict:
    payload = {"query": query}
    if variables:
        payload["variables"] = variables
    response = client.post("/graphql", json=payload)
    response.raise_for_status()
    return response.json()


def require_data(result: dict, field: str):
    assert "errors" not in result or not result["errors"], \
        f"GraphQL errors: {result.get('errors')}"
    return result["data"][field]


@pytest.fixture
def created_goal(client):
    result = gql(client, """
        mutation {
          createGoal(input: { title: "E2E fixture goal", confidence: 5 }) {
            id
          }
        }
    """)
    goal_id = result["data"]["createGoal"]["id"]
    yield goal_id
    gql(client, """
        mutation DeleteGoal($id: ID!) {
          deleteGoal(id: $id)
        }
    """, {"id": goal_id})


@pytest.fixture
def goal_factory(client):
    """Create goals during a test and guarantee they are deleted afterwards,
    even if an assertion fails before the test's own cleanup runs."""
    created_ids = []

    def _create(title="E2E goal", confidence=5, **fields):
        result = gql(client, queries.CREATE_GOAL, {
            "title": title,
            "confidence": confidence,
            **fields,
        })
        goal_id = result["data"]["createGoal"]["id"]
        created_ids.append(goal_id)
        return result

    yield _create

    for goal_id in created_ids:
        gql(client, queries.DELETE_GOAL, {"id": goal_id})
