# Reading coverage reports (and not being fooled by the number)

Spira has two coverage reports, one per JVM module:

| Module | Tool | Generate | Report |
|---|---|---|---|
| `backend/` (Java) | JaCoCo (Maven) | `cd backend; .\mvnw.cmd test` | `backend/target/site/jacoco/index.html` |
| `android/` (Kotlin) | JaCoCo (Gradle) | `cd android; .\gradlew.bat :app:jacocoDebugReport` | `android/app/build/reports/jacoco/jacocoDebugReport/html/index.html` |

They are **separate** — the backend report shows only backend Java, the Android report only the
app's Kotlin. JaCoCo itself works on JVM bytecode, so it measures Kotlin and Java equally; the
split is because they're different build modules, not a Kotlin limitation.

Coverage is a **signal, not a goal**. A high number with weak assertions proves nothing; a low
number can hide strong coverage of the code that matters. Read the report, don't just read the
percentage. Here's how.

## 1. The total percentage is almost always misleading — look per-class

The headline number divides covered lines by **all** lines, including code you neither wrote nor
can meaningfully unit-test. On this project the raw Android total was **5%** — but the code we
actually wrote was well covered. The 5% was dominated by:

- **Generated code** — Apollo GraphQL models/adapters/selections (`…/graphql/**`), Compose's
  synthetic `ComposableSingletons…` lambda holders. Hundreds of lines, 0%, not our logic.
- **UI that needs a device** — Compose screens, `MainActivity`, theme constants.

**What to do:** use the *curated* report (our `:app:jacocoDebugReport` excludes generated + UI),
or in the HTML report **drill into the package/class table** and look only at the classes you
wrote. On this project the curated view shows e.g. `CsrfInterceptor` 100%, `AuthApi` 88%,
`AuthViewModel` 62% — the real picture. The single "TOTAL" line is the least useful thing on the
page.

## 2. A 0% class is not always untested — check for Robolectric

**Robolectric-run tests do not register in JaCoCo.** Robolectric loads classes through its own
sandbox classloader, which bypasses JaCoCo's instrumentation, so a class exercised only by
Robolectric tests shows **0% even though it is tested**. On this project `PersistentCookieJar`,
`Network`, and `AuthViewModel.logout` read 0% in JaCoCo but each has a passing Robolectric test.

**What to do:** before believing a "0%", check whether a test actually exercises it (grep the
test sources). Trust the **test result** (does a real test assert on it?) over the coverage
number for Robolectric-covered classes. If an accurate number is required, capture coverage via
the JaCoCo Java agent instead of offline instrumentation — but the tests passing is the thing
that matters.

## 3. Line coverage ≠ correctness — look at *what* is uncovered

100% line coverage can still miss bugs (untested branches, weak assertions). More useful than
"what percent" is "**which** lines are red":

- Red **error/branch paths** (a `catch`, a `401 → null`, an `if` that never ran) are the ones to
  care about — those are where bugs hide.
- Red **generated code / trivial getters / UI glue** — ignore.
- A method at 100% with a test that asserts nothing is worse than 60% with sharp assertions.

Open the class in the HTML report; JaCoCo colours each line green/yellow/red. Scan for red on
real logic, not for a bigger number.

## 4. Know what a JVM report *cannot* cover

Some code can only be exercised on a device/emulator and will always be 0% in a unit-test
report — that's expected, not a gap to paper over:

- **Compose screens** → covered by **Compose UI Test** (can run under Robolectric on the JVM, or
  instrumented on an emulator).
- **Credential Manager / Google sign-in** (`GoogleSignInClient`) → needs an emulator + a real
  Google account; covered by **Maestro** E2E, not unit tests.

Track these as "covered elsewhere," and don't inflate or hide the unit number to compensate.

## Quick checklist when you open a coverage report

1. Ignore the TOTAL; open the **per-class** table.
2. Filter to **code you wrote** (skip generated + framework).
3. For each 0% class, confirm whether a **Robolectric** test covers it (JaCoCo won't show it).
4. On the important classes, look at the **red lines** — are they real branches/error paths?
5. Note what's intentionally **covered elsewhere** (UI tests, Maestro) rather than by unit tests.
