# The AI panel closes itself when the camera opens, and looks like the app restarting

- **ID:** BUG-046
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-23 — "прикладываю ресурс, делаю фото, перезагрузка приложения",
  and, when asked, "перезагрузка приложения — это баг, она у меня происходит сама по себе"
- **Area:** Android (`ui/goals/GoalWorkspaceScreen.kt`, `ui/goals/GoalsDashboardScreen.kt`)
- **Severity:** High — it reads as the app crashing and restarting, mid-task, in the one flow that
  is hardest to redo (you have already taken the photo)

## Summary

Open the AI panel, attach something, tap the paperclip and take a photo. Coming back from the
camera, the panel is **shut** and the conversation is off screen. Nothing crashed; the user has no
way to know that, and describes it as the app restarting on its own.

`MainActivity` declares no `android:configChanges`, which is normal for a Compose app — recreation
is expected and is meant to be survived through `rememberSaveable` and the view model. A camera app
opening in landscape triggers exactly that recreation, essentially every time. But the flag holding
the panel open was a plain `remember`:

```kotlin
var assistantOpen by remember { mutableStateOf(false) }   // both screens
```

so recreation reset it to `false` and the panel slid shut by itself.

## Steps to reproduce

1. Open a goal on Android, open the AI coach.
2. Paperclip → **Take a photo**, and take one.
3. Return to the app.
4. The AI panel is closed. (Rotating the device while the panel is open does the same thing, and is
   the quicker way to see it.)

## Root cause

`remember` survives recomposition, not **recreation**. The camera reliably causes recreation, so the
one piece of state that decides whether the panel is on screen was the one piece not saved. The
transcript, the draft and the attachments all lived in the view model and survived; only the "is it
open" flag did not, which is why it looked like the whole app had gone rather than one flag.

## Fix approach

`rememberSaveable` in both screens, with a comment naming the camera, so nobody restores the plain
`remember` while tidying.

## How to verify fixed

`ui/ai/AssistantSurvivesRecreationTest` recreates the activity with the panel open and asserts it is
still open. By hand: open the panel and rotate the device — it stays.

## Resolution

Fixed 2026-08-23 alongside BUG-050, which is the other half of the same trip through the camera.
