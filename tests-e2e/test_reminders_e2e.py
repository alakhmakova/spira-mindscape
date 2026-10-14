"""E2E tests: reminders on Upcoming events, through the running backend.

The fire-time rules and the dispatcher are pinned by the backend's own tests with a mocked clock;
what these add is the real HTTP + GraphQL + Postgres path — V41's columns and CHECK constraints,
the REST endpoints the service worker calls, and the two boundaries a deployed app has that a
unit test cannot reach: the scheduler's endpoint and the push-endpoint allowlist.
"""
import pytest
from conftest import gql, require_data

DATE = "2030-06-18T22:00:00Z"  # midnight of 2030-06-19 in Berlin

FIELDS = "id title time reminders { amount unit atMinuteOfDay channel }"
CREATE = f"""
    mutation($input: CreateUpcomingEventInput!) {{ createUpcomingEvent(input: $input) {{ {FIELDS} }} }}
"""
UPDATE = f"""
    mutation($id: ID!, $input: UpdateUpcomingEventInput!) {{
      updateUpcomingEvent(id: $id, input: $input) {{ {FIELDS} }}
    }}
"""
DELETE = "mutation($id: ID!) { deleteUpcomingEvent(id: $id) }"
COUNT = "{ upcomingEvents { id } }"


def errors_of(result: dict) -> str:
    return " | ".join(e.get("message", "") for e in result.get("errors") or [])


@pytest.fixture
def events(client):
    created: list[str] = []
    yield created
    for event_id in created:
        gql(client, DELETE, {"id": event_id})


def create(client, events, **input_) -> dict:
    event = require_data(gql(client, CREATE, {"input": input_}), "createUpcomingEvent")
    events.append(event["id"])
    return event


def test_time_and_reminders_round_trip(client, events):
    event = create(client, events, kind="important", title="Flight", date=DATE, time="14:30",
                   reminders=[{"amount": 1, "unit": "hour"}, {"amount": 1, "unit": "day"}])

    assert event["time"] == "14:30"
    assert [(r["amount"], r["unit"], r["atMinuteOfDay"], r["channel"]) for r in event["reminders"]] == [
        (1, "hour", None, "notification"),
        (1, "day", None, "notification"),
    ]


def test_all_day_reminder_defaults_to_nine(client, events):
    event = create(client, events, kind="important", title="Birthday", date=DATE,
                   reminders=[{"amount": 0, "unit": "day"}])
    assert event["time"] is None
    assert event["reminders"][0]["atMinuteOfDay"] == 540


@pytest.mark.parametrize("fields, message", [
    ({"reminders": [{"amount": 1000, "unit": "day"}]}, "from 0 to 999"),
    ({"reminders": [{"amount": 1, "unit": "year"}]}, "Unknown reminder unit"),
    ({"reminders": [{"amount": 5, "unit": "minute"}]}, "need the event to have a time"),
    ({"reminders": [{"amount": 1, "unit": "day", "atMinuteOfDay": 1440}]}, "from 00:00 to 23:59"),
    ({"time": "25:00"}, "Event time must be HH:mm"),
    ({"reminders": [{"amount": n, "unit": "day"} for n in range(1, 7)]}, "at most 5 reminders"),
])
def test_invalid_reminders_are_refused_and_write_nothing(client, fields, message):
    before = len(require_data(gql(client, COUNT), "upcomingEvents"))
    result = gql(client, CREATE, {"input": {"kind": "important", "title": "Trip", "date": DATE, **fields}})
    assert message in errors_of(result)
    assert len(require_data(gql(client, COUNT), "upcomingEvents")) == before


def test_edit_replaces_the_schedule(client, events):
    event = create(client, events, kind="important", title="Exam", date=DATE,
                   reminders=[{"amount": 0, "unit": "day"}])
    edited = require_data(gql(client, UPDATE, {"id": event["id"], "input": {
        "time": "10:00", "reminders": [{"amount": 2, "unit": "hour"}]}}), "updateUpcomingEvent")
    assert edited["time"] == "10:00"
    assert [(r["amount"], r["unit"]) for r in edited["reminders"]] == [(2, "hour")]

    cleared = require_data(gql(client, UPDATE, {"id": event["id"], "input": {}}), "updateUpcomingEvent")
    assert cleared["time"] is None and cleared["reminders"] == []


def test_due_answer_and_time_zone(client, events):
    assert require_data(gql(client, 'mutation { setTimeZone(zone: "Europe/Berlin") }'), "setTimeZone")
    assert "Unknown time zone" in errors_of(gql(client, 'mutation { setTimeZone(zone: "Mars/Base") }'))

    due = require_data(gql(client, "{ dueReminders { eventId title firedAt } }"), "dueReminders")
    assert isinstance(due, list)

    event = create(client, events, kind="important", title="Later", date=DATE,
                   reminders=[{"amount": 0, "unit": "day"}])
    answered = require_data(gql(client, "mutation($e: ID!) { answerReminder(eventId: $e, action: \"done\") }",
                                {"e": event["id"]}), "answerReminder")
    assert answered is False  # nothing of it is due yet, in 2030


def test_service_worker_endpoints(client, events):
    event = create(client, events, kind="important", title="Dentist", date=DATE, time="14:00")
    notice = client.get(f"/api/reminders/{event['id']}/notice")
    assert notice.status_code == 200
    assert notice.json()["title"] == "Dentist"
    assert notice.json()["body"].endswith("at 14:00 · Important date")

    assert client.get("/api/reminders/999999999/notice").status_code == 404
    bad = client.post(f"/api/reminders/{event['id']}/answer", json={"action": "delete"})
    assert bad.status_code == 400


def test_push_subscribe_refuses_endpoints_off_the_allowlist(client):
    for endpoint in ("http://169.254.169.254/computeMetadata/v1/", "https://internal.example/push"):
        res = client.post("/api/push/web/subscribe", json={
            "endpoint": endpoint,
            "keys": {"p256dh": "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                     "auth": "BTBZMqHH6r4Tts7J_aSIgg"},
        })
        assert res.status_code == 400


def test_dispatcher_is_not_a_public_trigger(client):
    # The client's own auth header is not the scheduler's token.
    assert client.post("/internal/reminders/dispatch").status_code == 401
    forged = client.post("/internal/reminders/dispatch", headers={"Authorization": "Bearer forged"})
    assert forged.status_code == 403
