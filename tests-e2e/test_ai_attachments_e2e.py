"""E2E tests: the contract for attaching a saved resource to an AI message (BUG-030).

A resource attachment is an **id and nothing else** — no bytes travel from the client, and
the server inlines what it already holds. These tests pin that contract at the HTTP boundary,
where neither the web nor the Android client can paper over it.

What is deliberately NOT here: an actual model reply. Spira is BYOK, so `/api/ai/chat` needs a
provider key this suite has no business holding, and a test that needed one would be skipped in
CI and therefore worthless. The two halves that matter are covered where they can be done
honestly instead:

  * that the id turns into real content in the message handed to the provider —
    `AiChatServiceResourceAttachmentTest` (backend, mocked provider);
  * that a resource cannot be read across users — `ResourceReadServiceTest`.

What is left for this level is the part only a real HTTP request can prove: that a malformed
attachment is refused before anything reaches the AI layer, and that a well-formed one is
accepted and fails only for the want of a key.
"""
from conftest import gql, require_data
from graphql import queries


def _note(client, goal_id: str) -> str:
    result = gql(client, queries.CREATE_RESOURCE, {
        "goalId": goal_id,
        "type": "note",
        "title": "Interview notes",
        "body": "They asked about salary.",
    })
    return require_data(result, "createResource")["id"]


def _chat(client, goal_id: str, attachments: list[dict]):
    return client.post("/api/ai/chat", json={
        "goalId": int(goal_id),
        "message": "what does this say?",
        "history": [],
        "provider": "ANTHROPIC",
        "sessionType": "chat",
        "attachments": attachments,
    })


def test_resource_attachment_is_accepted_and_only_a_missing_key_stops_it(client, created_goal):
    """A well-formed resource chip passes validation; 422 means "no API key", not "bad request"."""
    resource_id = _note(client, created_goal)

    response = _chat(client, created_goal, [{"resourceId": int(resource_id)}])

    # 422 is the BYOK gate (no key configured for this user in the test profile). What matters
    # is that the request was well-formed enough to reach it: a 400 would mean the attachment
    # shape itself was rejected.
    assert response.status_code in (200, 422), response.text
    if response.status_code == 422:
        assert "key" in response.text.lower()


def test_an_attachment_carrying_both_a_file_and_an_id_is_refused(client, created_goal):
    """Exactly one source. Both is ambiguous — and is how a client could smuggle bytes past
    the resource path while claiming to reference a resource."""
    resource_id = _note(client, created_goal)

    response = _chat(client, created_goal, [{
        "name": "x.txt",
        "mime": "text/plain",
        "dataUrl": "data:text/plain;base64,QUJD",
        "resourceId": int(resource_id),
    }])

    assert response.status_code == 400, response.text


def test_an_empty_attachment_is_refused(client, created_goal):
    """Neither bytes nor an id: a blank chip that would reach the model as a silent gap."""
    response = _chat(client, created_goal, [{"name": "x.txt", "mime": "text/plain"}])

    assert response.status_code == 400, response.text


def test_a_resource_id_that_does_not_exist_does_not_crash_the_request(client, created_goal):
    """An unknown id must be handled as "not available", never as a 500. The id is
    user-supplied and untrusted, so this is the shape an attack takes."""
    response = _chat(client, created_goal, [{"resourceId": 999_999_999}])

    assert response.status_code in (200, 422), response.text
