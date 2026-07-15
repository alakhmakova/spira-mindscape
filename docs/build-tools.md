# Build tools — why each module uses a different one

Spira is a **polyglot repository**: a React web app, a Spring Boot backend, and a native
Android app live side by side. Each uses the build tool that is standard (and often the only
well-supported one) for its ecosystem. This is normal and intentional — the modules are
independent and build separately, including in CI.

| Module | Build tool | Command | Why |
|---|---|---|---|
| `src/` (React + Vite web) | **npm / Vite** | `npm run dev`, `npm run build`, `npm test` | Standard for the JS/TS frontend ecosystem. |
| `backend/` (Spring Boot) | **Maven** (wrapper `mvnw`) | `.\mvnw.cmd test`, `.\mvnw.cmd spring-boot:run` | First-class for Spring/JVM services; the project uses the Maven wrapper so no global `mvn` is needed. |
| `android/` (Kotlin + Compose) | **Gradle** (wrapper `gradlew`) | `.\gradlew.bat :app:assembleDebug` | The **only** officially supported Android build system. |

## Why Gradle for Android (and not Maven)

It isn't a preference — for Android, Gradle is effectively mandatory:

- **The Android Gradle Plugin (AGP) is built and shipped by Google for Gradle only.** Android
  Studio is designed around Gradle; project sync, run/debug, and the build all go through it.
- **There is no supported Maven path for modern Android.** An unofficial `android-maven-plugin`
  existed years ago but is unmaintained and doesn't handle current Android: Jetpack Compose,
  R8/ProGuard, build variants/flavors, resource merging (AAPT2), etc.
- **The libraries we use assume Gradle.** The Jetpack Compose compiler, and the **Apollo
  (GraphQL)** and **Firebase** plugins we add for this app, are distributed as Gradle plugins.

So the backend staying on Maven and the Android app using Gradle is not an inconsistency to
"fix" — each module uses its ecosystem's idiomatic tool.

## Why Maven for the backend (and not Gradle)

Also a fit-to-ecosystem choice: Spring Boot treats Maven as a first-class citizen (Spring
Initializr, docs, and the `spring-boot-maven-plugin`), the project already ships a Maven
**wrapper** (`mvnw`) so contributors need nothing installed globally, and the whole test/CI
setup (Surefire, JaCoCo, Flyway, Allure) is wired through Maven. There's no benefit to porting
it to Gradle.

## JDK note (shared gotcha)

Both JVM builds need a compatible JDK:

- **Backend (Maven):** Java 17 (the project targets 17).
- **Android (Gradle):** JDK **17–21**. Android Studio uses its **embedded JDK** automatically;
  for terminal builds set `JAVA_HOME` to a 17–21 JDK — e.g. Android Studio's embedded one at
  `C:\Program Files\Android\Android Studio1\jbr` (JDK 21).
- A **system JDK 22** is newer than the Android Gradle Plugin supports — don't point Gradle at
  it, or builds fail with confusing errors.

## CI

Each module is built and tested independently in `.github/workflows/ci.yml` — the frontend with
npm, the backend with the Maven wrapper, Python E2E with pytest. (Android emulator builds are
heavier and are intended to run by tag/manual dispatch rather than on every push.)
