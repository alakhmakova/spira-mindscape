# AI chat: attach a file straight from the goal's Resources (both surfaces)

- **ID:** BUG-030
- **Status:** 🐞 Open
- **Reported by:** User (2026-08-07)
- **Area:** AI chat — web (`src/components/ai/AiPanel.tsx`) and Android
  (`ui/ai/AiChatScreen.kt`), plus whatever the picker needs from the resources API
- **Type:** Enhancement

## Summary

The chat composer can attach files **from the device** only. Everything already saved on the goal
as a **Resource** can be reached solely by *describing it in words* and hoping the assistant calls
`read_resource` on the right one. The user has to keep writing out which resource the AI should
read, every time.

The ask: let the paperclip also offer **"from Resources"** — pick one (or several) of this goal's
existing resources and attach them to the message directly, the same way a device file is
attached today.

## Desired behaviour

- The attach control offers two sources: **this device** and **this goal's resources**.
- Picking "resources" lists the goal's resources (name, type, size) with a search box when the
  list is long, and allows selecting more than one.
- A chosen resource becomes the same kind of chip above the input as a device file, removable
  before sending.
- On send it travels as an attachment scoped to that message — so the model reads it **without
  needing a tool call**, which is the whole point (it side-steps the tool-calling fragility and
  removes the "tell the AI which resource to read" step).
- The resource itself is untouched — attaching is a read, never a copy or a move.
- Both surfaces behave the same; the Android sheet uses the app's own components, not raw
  Material defaults.

## Notes / open questions

- Server-side, a resource's bytes already live on the backend. Sending the **resource id** and
  letting the server inline its text/image would avoid a pointless round trip through the client
  (download → base64 → upload). Prefer that over re-uploading, and check it against the existing
  `ChatRequest` attachment shape (`ai/chat/dto/ChatRequest.java`).
- Whatever the transport, the server must re-check that the resource **belongs to the requesting
  user's goal** — an attachment referencing a resource id is user-supplied input and must not be
  trusted to be theirs. Cover it with a cross-user isolation test, as
  `CrossUserIsolationIntegrationTest` does elsewhere.
- Large resources need the same size guard as direct attachments.

## How to verify

- Attach a resource on each surface; the assistant answers using its contents with no tool call
  and no instruction naming the resource.
- A second user cannot attach another user's resource by id (403/404), covered by a test.
- The resource is unchanged afterwards.
