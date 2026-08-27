# `.dockerignore` strips the coach's method, and production will not start

- **ID:** BUG-053
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-27 — "что то прошло не так с андроид тестами, я перезапустил
  джобу", and, the day before, "на андроид ключ подключен как на проде — отправила сообщение — чат
  не отвечает и на проде нет этих сообщений"
- **Area:** Build / deploy (`.dockerignore`), Backend (`ai/prompt/PromptResources.java`)
- **Severity:** **Critical** — every deploy since 2026-08-22 failed, so production has been
  running two-week-old code and nothing new has reached it

## Summary

`Deploy to Cloud Run` fails with:

```
ERROR: (gcloud.run.deploy) The user-provided container failed to start and listen on the port
defined provided by the PORT=8080 environment variable within the allocated timeout.
```

The image builds and uploads; the revision never becomes healthy. Nothing in the build log
mentions the cause.

## Root cause

`.dockerignore` carried a blanket rule to keep documentation out of the image:

```
**/*.md
```

The GROW coach's method is prompt text kept as **Markdown** — `backend/src/main/resources/prompts/
grow/coach-method.md` — because it is prose meant to be read and edited. So the Dockerfile's
`COPY backend/src src` copied an **empty** `prompts/grow/` directory, and the jar shipped without
the file.

`PromptResources` loads it at construction and **fails startup** when it is missing — deliberately,
because a coach silently running without its method is worse than a boot failure. The Spring
context therefore never came up, the container never listened on 8080, and Cloud Run rejected the
revision.

Proved rather than guessed: a two-line probe image (`FROM alpine` + `COPY backend/src /src`) built
against the same context showed `prompts/grow/` present and empty. With the fix it shows
`coach-method.md`, 33 KB.

**Why nothing caught it.** Every test level runs from `src/main/resources`, where the file plainly
exists — unit, integration, Python E2E and Playwright all passed on the commit that could not
boot. The gap was never "is the resource there?" but "does it reach the image?", and nothing was
looking at that.

## Steps to reproduce

1. Check out the merge of PR #28 (or any commit between 2026-08-24 and this fix).
2. `docker build -t spira .`
3. `docker run -p 8080:8080 spira` → `IllegalStateException: Prompt resource is missing:
   prompts/grow/coach-method.md`, and the process exits.

## Fix approach

A negation in `.dockerignore`, below the exclusion (the last matching pattern wins):

```
**/*.md
!backend/src/main/resources/prompts/**
```

The alternative — renaming the prompt to `.txt` — was rejected: the file is 33 KB of prose with
tables and headings, and it is edited far more often than the ignore file is.

## How to verify fixed

- `ai/prompt/PromptResourcePackagingTest` reads `.dockerignore` and fails when Markdown is
  excluded with nothing bringing the prompts back, or when the negation sits *above* the
  exclusion where it would have no effect. Confirmed to fail with the negation removed.
- By hand: the probe image above, or a full `docker build` followed by `docker run`.

## Resolution

Fixed 2026-08-27.

Two lessons worth keeping:

- **A runtime resource that looks like documentation will be treated as documentation.** The
  build context is a second, invisible classpath; anything the app loads at startup has to be
  checked against it, not just against the source tree.
- **"Container failed to start and listen on the port" is not a port problem.** It is the
  message Cloud Run gives for *any* failed boot — a missing bean, a bad migration, an exhausted
  pool — and the actual exception lives only in the revision's own logs. The CI job never fetched
  them, so three deploys were diagnosed by guesswork. It does now: a `Why the revision failed to
  start` step runs on failure and prints the newest revision's log lines.
