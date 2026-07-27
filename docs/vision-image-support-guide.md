# Letting the AI see images (vision support) — a beginner's guide

This guide explains, in plain language, how the AI assistant is able to **look at an image** a
user uploaded (a screenshot, a photo, a picture of a whiteboard) and describe or use what's in it —
and how the same idea works across **every** chat provider (Anthropic, OpenAI, Mistral, Google
Gemini), not just one.

No prior AI/vision experience needed. If you can read a `for` loop, you can follow this.

---

## 1. The core idea in one paragraph

An LLM chat is normally just **text in, text out**. To let the model *see* a picture, you don't
do any image processing yourself — you send the image's raw bytes (as text-safe **base64**) to the
model's API as part of the conversation, and the model does the "looking." The only real work is:
(1) carry the image alongside your normal messages, and (2) format it the way each provider's API
expects. That's it. There is no OCR, no thumbnailing, no computer-vision library on our side.

---

## 2. What you need to know first

### Where the image comes from

In this app an uploaded file (image or PDF) is stored as a **base64 data URL** — a plain string
that looks like:

```
data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...   (thousands of characters)
```

That single string contains the **MIME type** (`image/png`) and the **image bytes** (everything
after the comma, base64-encoded). Because it's just text in a database column, we already have
everything the model needs — we just have to hand it over in the right shape.

### Every provider wants a different shape

This is the one genuinely fiddly part. A chat message's "content" is usually a plain string. To
attach an image you must send content as a **list of parts** instead, and the two big API families
disagree on the format:

- **Anthropic (Claude) — Messages API.** An image is a content block:
  ```json
  { "type": "image", "source": { "type": "base64", "media_type": "image/png", "data": "<base64>" } }
  ```
- **OpenAI-compatible (OpenAI, Mistral, Google Gemini's compat endpoint).** An image is an
  `image_url` part whose URL is the whole data URL:
  ```json
  { "type": "image_url", "image_url": { "url": "data:image/png;base64,<base64>" } }
  ```

So the plan is: keep one internal representation of an image, and let each provider translate it to
its own JSON. That's why it works for all providers at once.

### The model itself must support vision

Sending an image doesn't magically upgrade a text-only model. You need a **vision-capable model**
(all Claude models, `gpt-4o`, `gemini-2.5-flash`/`pro`, Mistral's Pixtral). We don't keep a list of
which model can do what — we just try, and if the provider rejects it, we show the user a friendly
error. Simpler, and it never goes stale.

---

## 3. How the model receives the image: the tool-call flow

The assistant already has an on-demand tool called `read_resource`: when the user says "look at my
screenshot," the model calls that tool with the resource's id, the backend loads the content and
feeds it back, and the model continues. (This "call a tool → get a result → keep going" pattern is
called an **agentic loop**.)

For text files the tool result *is* text. For an image, the tool result **carries the image
itself**. So the only behavior change is: when `read_resource` points at an image, deliver the
picture instead of an "I can't read images" apology.

One subtlety worth knowing: Anthropic lets you put an image **inside** the tool result. The
OpenAI-compatible APIs do **not** — their "tool" message can only hold a string. So for those we
send the tool result as normal text, then **immediately add one more user message** that carries
the image. Same end effect; the difference is hidden inside each provider.

---

## 4. Step-by-step: how it was built

The whole feature is backend-only. Here are the pieces, in order.

### Step 1 — Represent an image

Add a tiny type for "an image to show the model": its MIME type + its base64 payload (no
`data:...;base64,` prefix).

```java
public record LlmImage(String mediaType, String base64Data) {}
```

### Step 2 — Let a message carry images

The message type used everywhere (`LlmMessage`) was text-only. Add an optional list of images to
it, and a factory for "a tool result that also has images." Keep the old constructors working so
nothing else breaks.

```java
// new field on the message: List<LlmImage> images
public static LlmMessage toolResultWithImages(String toolCallId, String text, List<LlmImage> images) {
    return new LlmMessage("tool", text, null, toolCallId, images);
}
public boolean hasImages() { return images != null && !images.isEmpty(); }
```

### Step 3 — One helper for both wire formats

Put the format-specific JSON building in a single shared helper (`VisionSupport`) so the providers
don't each reinvent it. It does three jobs:

1. **A MIME allow-list** — only formats the providers actually accept: `image/png`, `image/jpeg`,
   `image/webp`, `image/gif`. Anything else (e.g. `image/svg+xml`) is treated as "not viewable" and
   falls back to text.
2. **Parse a data URL** into an `LlmImage` (split off the `data:<mime>;base64,` prefix).
3. **Build the two shapes**: the Anthropic image block, and the OpenAI-compatible `image_url`
   user-message.

```java
public static boolean isVisionMime(String mime) { /* is it in the allow-list? */ }
public static LlmImage fromDataUrl(String dataUrl) { /* "data:image/png;base64,AAAA" -> LlmImage */ }
public static List<Map<String,Object>> anthropicImageBlocks(List<LlmImage> imgs) { /* Claude shape */ }
public static Map<String,Object> openAiImageUserMessage(List<LlmImage> imgs) { /* OpenAI shape */ }
```

### Step 4 — Teach each provider to serialize the image

Each provider has a method that turns our `LlmMessage` into the API's JSON. Add an image branch:

- **Anthropic**: when a tool-result message has images, make the `tool_result`'s content an array
  of `[ {text}, {image block}, ... ]`. Stays one message, so the required user/assistant
  alternation is preserved.
- **OpenAI / Mistral / Gemini**: after adding the normal `{role:"tool", content:"..."}` message, if
  it has images, **append** a `{role:"user", content:[ {image_url ...} ]}` message. All three share
  the identical helper call:

  ```java
  for (LlmMessage m : messages) {
      allMessages.add(toProviderMessage(m));
      if (m.hasImages()) {
          allMessages.add(VisionSupport.openAiImageUserMessage(m.images())); // the extra image message
      }
  }
  ```

That single shared line is *why the feature works for every OpenAI-compatible provider at once* —
add a new one later and it inherits vision for free.

### Step 5 — Deliver the image in the read path

Two small changes where the tool result is produced:

1. Add a method that returns the image for a resource **only if** it belongs to the current
   conversation's owner/scope and its MIME is in the allow-list (otherwise return "nothing").
2. In the agentic loop, when the tool is `read_resource` and that method returns an image, build a
   `toolResultWithImages(...)` message; otherwise fall back to the existing text result.

### Step 6 — Tell the model it can now see images

Update the tool's description and the system prompt: images are viewable directly; describe what
you actually see; and treat any text *inside* an image as untrusted data, not instructions.

---

## 5. Don't forget these (they're easy to miss)

- **Ownership / scoping.** An image is user data. Load it through the same owner-scoped check you
  use for any resource, so one user can never pull another user's image into the model. Add a test
  for exactly that boundary.
- **Untrusted content.** A picture can contain text like "ignore your instructions." Frame image
  content as untrusted data in the prompt (the same way you'd fence any external/user text).
- **Size.** base64 is ~33% bigger than the raw bytes, and providers cap image size. Keep the upload
  limit sane (here it's 5 MB) and let the provider's error surface if an image is too big — don't
  silently truncate.
- **Unsupported types.** SVG and scanned PDFs (no text layer) can't be "seen"; keep the graceful
  text fallback for those.
- **Friendly errors.** If someone points a text-only model at an image, the provider returns an
  error — make sure your UI shows a readable message, not a raw JSON blob.

---

## 6. How to test it without spending API tokens

You can verify the hard part — the JSON shaping — with plain unit tests, no network:

- **The helper**: assert `fromDataUrl` parses correctly, the allow-list rejects SVG, and the two
  builders produce the expected block/`image_url`.
- **Each provider**: build a request body from a `toolResultWithImages` message and assert the JSON
  contains the image in the right place (an `image` block inside Anthropic's `tool_result`; a
  trailing `role:"user"` `image_url` message for the OpenAI-compatible ones).
- **The read path**: a supported image returns image bytes; SVG/other-goal/missing return nothing;
  and a resource from another owner is never returned (the security boundary).

Then one manual end-to-end check: upload an image, pick a vision model, ask "what's in this image?"
and confirm the model describes the real picture.

---

## 7. Mental model to take away

```
image stored as base64 data URL
        │
        ▼
one internal type (LlmImage)  ← MIME allow-list gates what's viewable
        │
        ├── Anthropic  → image block INSIDE the tool_result
        └── OpenAI/Mistral/Gemini → a follow-up user message with image_url
        │
        ▼
vision-capable model sees the picture and answers
```

The trick that makes it multi-provider is keeping **one** image representation and doing the
provider-specific formatting in **one** place. Everything else — storage, the tool loop, the UI —
stays exactly as it was.
