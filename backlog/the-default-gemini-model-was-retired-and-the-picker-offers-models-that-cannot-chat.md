# The default Gemini model was retired, and the picker offers models that cannot chat

- **ID:** BUG-059
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-28 — after being advised to switch to `gemini-2.5-flash` and
  getting *"This model models/gemini-2.5-flash is no longer available to new users"* — "или это
  какой-то баг в Spira?"
- **Area:** Backend (`ai/provider/google/GeminiProvider`, `ai/model/AiModelService`), Web
  (`components/ai/AiPanel.tsx`), Android (`ui/ai/ProviderSheet.kt`)
- **Severity:** **High** — a new user who saves a Gemini key without picking a model cannot send a
  single message

## Summary

Yes, it was a bug in Spira, in four places.

**1. The default model was retired by Google.** `GeminiProvider.DEFAULT_MODEL` was
`gemini-2.5-flash`, which Google no longer issues to new keys:

> This model models/gemini-2.5-flash is no longer available to new users. Please update your code
> to use models/gemini-3.6-flash.

So the model the app picks *for you* when you express no preference is one the provider refuses.
Save a key, send a message, get an error — with nothing in the app hinting that the app itself
chose the broken model.

**2 and 3. The same dead list is hard-coded in both clients**, as the fallback shown before the
live list loads: `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.0-flash`, `gemini-1.5-pro`
(`AiPanel.tsx`, `ProviderSheet.kt`). Three of the four no longer work.

**4. The live model list was not filtered.** `AiModelService.fetchGeminiModels` used Google's
OpenAI-compatibility endpoint, which returns ids and nothing else, so the picker offered **all 54**
— including `gemini-embedding-001`, `aqa`, the `-tts` voices, `-transcribe`, `-native-audio-*` and
`computer-use`. Fifteen of them cannot answer a chat message at all, and picking one failed at the
first send rather than at the moment of choosing.

## Steps to reproduce

1. Add a Gemini API key and do not pick a model.
2. Send a message. **Before the fix:** the provider refuses, naming a model the user never chose.
3. Open the key sheet before the live list loads: four models offered, three dead.
4. Let the live list load: 54 entries, including embedding and text-to-speech models.

## Root cause

A model id pinned in source is a value with an expiry date and no owner. Nothing watched it, and
nothing could have failed when Google retired it — the tests construct providers with an explicit
model, so `DEFAULT_MODEL` is never exercised against a real API.

The unfiltered list is the same shape of mistake in reverse: rather than asking the provider what a
model can do, the code passed through whatever came back and left the user to find out by failing.

## Fix approach

- **Aliases, not versions.** `DEFAULT_MODEL` is `gemini-flash-lite-latest`, and both clients' fallback
  lists are `gemini-flash-lite-latest`, `gemini-flash-latest`, `gemini-pro-latest`. Google maintains
  those pointers, so they cannot rot the same way.
- **The lite alias for the default, and that was measured rather than assumed.** Against the owner's
  real free-tier key on 2026-08-28: `gemini-flash-latest` answered 503 "experiencing high demand"
  three times running and then a quota refusal, while `gemini-flash-lite-latest` replied first time.
  A default exists to make the first message work for someone with no preference — and someone with
  no preference is, overwhelmingly, on the free tier, where the lite quota is the roomier one.
- **Ask Google what each model can do.** `fetchGeminiModels` now calls the native
  `/v1beta/models`, which returns `supportedGenerationMethods`, and keeps only models that support
  `generateContent`. That is the provider stating it, not us guessing from the name.

## What this does not fix

The filter takes the list from 54 to 39. Google still reports `generateContent` for its image, TTS
and music models (`lyria-*`, `nano-banana-*`, `*-image`), so they remain in the picker. Excluding
them needs a name-based blocklist — which is precisely the kind of hard-coded knowledge that caused
this bug in the first place, so it was deliberately not added. If the clutter becomes a real
problem, the honest fix is a modality field from the provider, not a list we maintain.

## How to verify fixed

Verified empirically against the owner's key rather than by reading documentation:

| Model | Result |
|---|---|
| `gemini-3.6-flash` | answered |
| `gemini-flash-lite-latest` | answered |
| `gemini-flash-latest` | 503 "high demand" ×3, then a quota refusal |
| `gemini-2.5-flash` | refused — retired for new users |
| `gemini-3.5-flash` | quota exhausted (20/day free tier) |

And by calling `GET /api/ai/keys/GEMINI/models` before and after: 54 entries, then 39, with
`gemini-embedding-001`, `aqa`, `-native-audio-*` and `-transcribe-live` gone.

## Resolution

Fixed 2026-08-28.

Two things worth keeping:

- **A pinned model id is a time bomb with no owner.** Use the provider's own moving alias for
  anything the app chooses on the user's behalf.
- **When a provider can tell you what a thing supports, ask it.** The unfiltered list was the app
  passing a decision to the user that it had the information to make.
