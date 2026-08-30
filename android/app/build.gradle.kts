plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.apollographql.apollo")
    jacoco
}

// Crashlytics + Analytics need the google-services plugin, which requires google-services.json.
// That file is gitignored (holds an API key) and absent on CI, so apply these plugins ONLY when
// it's present: local/dev builds get crash reporting; CI still builds fine without the file.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// ── Which backend a build talks to ────────────────────────────────────────────────────────────
//
// This is the Android half of the web's Spring profiles, and it is what decides whether the app
// asks for a login at all. The app has no bypass of its own and must never grow one: it asks
// `/api/auth/me` on start, and the answer depends on the backend it is pointed at.
//
//   • Production answers 401 → the sign-in screen, real Google OAuth.
//   • A backend on the `local` profile signs EVERY request in as `dev@local`
//     (`LocalDevAuthFilter`) → `me` returns a user and the app lands straight on All goals.
//
// So "dev has no login" is one line of Gradle — which backend the URL points at — and exactly
// the same mechanism the web has, where `npm run dev` proxies to whatever backend is running.
//
//   ./gradlew.bat :app:installDev     → the local backend, no login
//   ./gradlew.bat :app:installDebug   → production, real Google sign-in
//   ./gradlew.bat distributeDebug     → production (and it refuses to run with the override
//                                       below, so a distributed APK can never carry a local URL)
//
// `-PspiraApiBaseUrl=…` overrides either, for the cases neither default covers: a real phone on
// the same Wi-Fi (`http://<your-PC-LAN-IP>:8080`) or a cloudflared tunnel URL.
val prodApiBaseUrl = "https://spira-952567559986.europe-west1.run.app"

/** 10.0.2.2 is the host machine as seen from inside the emulator. */
val devApiBaseUrl = "http://10.0.2.2:8080"

val apiBaseUrlOverride = project.findProperty("spiraApiBaseUrl") as String?

android {
    namespace = "com.spiramindscape.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spiramindscape.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Production is the DEFAULT, for every build type but `dev` — so a build made without
        // thinking about it is the safe one. It used to be a hardcoded literal, so reproducing
        // anything against a local backend meant editing this file and remembering to put it
        // back; an agent that forgot shipped a debug APK pointing at localhost (2026-08-24).
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrlOverride ?: prodApiBaseUrl}\"")

        // The project's WEB OAuth client ID (from google-services.json). The app requests a
        // Google ID token whose audience is this ID, and the backend verifies against it (see
        // GoogleMobileTokenVerifier). OAuth client IDs are public, so this is safe to commit.
        buildConfigField(
            "String",
            "WEB_CLIENT_ID",
            "\"952567559986-pv46g2sr17vltmsnbcoq5scdjbat2l3d.apps.googleusercontent.com\"",
        )
    }

    buildTypes {
        debug {
            // JaCoCo coverage for JVM unit tests → :app:createDebugUnitTestCoverageReport
            enableUnitTestCoverage = true
        }
        // The dev build: the same debug app, pointed at a backend on the `local` profile, so it
        // needs no sign-in. See the note at the top of this file for why that is all it takes.
        //
        // **A build type, not a product flavor**, though a flavor is the textbook answer for an
        // environment. A flavor renames every task there is — `assembleDebug` becomes
        // `assembleProdDebug`, `testDebugUnitTest` becomes `testProdDebugUnitTest` — and those
        // names are written into CI, `distributeDebug`, the JaCoCo report and the docs. A third
        // build type adds `installDev` and leaves `debug` and `release` untouched, which is the
        // whole of what is wanted here: one app, one extra environment.
        create("dev") {
            initWith(getByName("debug"))
            // Libraries publish `debug` and `release` only, so a custom build type has nothing
            // of theirs to resolve against without being told what it resembles.
            matchingFallbacks += "debug"
            // Visible in `adb shell dumpsys package`, so which build is on a device is a fact
            // rather than a memory.
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrlOverride ?: devApiBaseUrl}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // `dev` is `debug` plus a different backend, so it takes the debug overlay as well:
    // cleartext HTTP (a local backend is plain `http://` on 10.0.2.2 or a LAN IP, which Android
    // 9+ blocks) and the exported note-editor probe. `initWith` copies a build type's
    // PROPERTIES and never its source set, so without this the dev build compiles and installs
    // and then cannot reach the backend at all.
    sourceSets.getByName("dev") {
        manifest.srcFile("src/debug/AndroidManifest.xml")
        java.srcDir("src/debug/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest + resources available to JVM unit tests.
            isIncludeAndroidResources = true
        }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

// Robolectric NATIVE-graphics tests in one class share a single JVM by default, and something
// in that shared sandbox was leaking between VisualCheckTest methods — every test passes
// standalone, but 2-4 of the 5 randomly fail with AppNotIdleException when the class runs
// together (see backlog/android-visual-test-suite-flaky-appnotidle.md, BUG-009). Tried
// force-clearing focus on dispose first (a real, separate bug worth keeping — see
// InlineComponents.kt/GoalWorkspaceScreen.kt), but that alone didn't stop the cross-test
// failures, and Compose UI tests run under a TestCoroutineScheduler where a plain delay()-loop
// (which is how the cursor blink is actually implemented, not frame-clock-driven) wouldn't be
// silenced by mainClock.autoAdvance=false either. forkEvery = 1 sidesteps the question of what
// exactly leaks by making it structurally impossible: every test method gets its own JVM, so
// nothing can carry over between them. Trade-off: the full class takes longer to run (repeated
// JVM/Robolectric cold starts instead of one) — acceptable for correctness over speed here.
tasks.withType<Test> {
    forkEvery = 1
}

// Curated coverage report (like the backend's JaCoCo): measures OUR code and excludes what a
// JVM unit test can't cover — Apollo-generated GraphQL classes and Compose/Activity UI (those
// belong to Compose UI Test / Maestro on an emulator). Run: ./gradlew :app:jacocoDebugReport
// Report: app/build/reports/jacoco/jacocoDebugReport/html/index.html
tasks.register<JacocoReport>("jacocoDebugReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "JaCoCo coverage for debug unit tests, excluding generated + UI code."
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
    val excludes = listOf(
        "**/graphql/**",             // Apollo-generated GraphQL models/adapters
        "**/*ComposableSingletons*", // Compose-generated lambda holders
        "**/ui/theme/**",            // theme constants
        "**/ui/SpiraAppKt*",         // Compose screens — covered by UI tests, not JVM
        "**/ui/auth/LoginScreenKt*",
        "**/MainActivity*",
        "**/BuildConfig.*",
    )
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(excludes) },
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec") })
}

// One-click distribution: build the debug APK and upload it to Firebase App Distribution.
// Uses your `firebase login` (no service-account file needed). Run:
//   .\gradlew.bat distributeDebug -PreleaseNotes="what changed in this build"
// Override testers with -PdistTesters="a@x.com,b@y.com".
tasks.register<Exec>("distributeDebug") {
    group = "distribution"
    description = "Build the debug APK and upload it to Firebase App Distribution."
    dependsOn("assembleDebug")

    val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val appId = "1:952567559986:android:eff4c02ebb5a77b38a892b"
    val testers = (project.findProperty("distTesters") as String?)
        ?: "anastasiya.lakhmakova@gmail.com,alakhmakova@gmail.com"
    val notes = (project.findProperty("releaseNotes") as String?) ?: "Spira mobile debug build."

    doFirst {
        // **A distributed APK must never carry a local backend URL.** `-PspiraApiBaseUrl` is
        // baked into `BuildConfig` at assemble time, so a build made for the emulator and then
        // distributed sends the owner an app pointing at 10.0.2.2, which resolves to nothing on
        // a phone. CLAUDE.md asked the reader to remember to rebuild without the flag; this
        // makes remembering unnecessary. Local work has its own build type — use `installDev`.
        require(apiBaseUrlOverride == null) {
            "distributeDebug must not run with -PspiraApiBaseUrl: it would ship an APK " +
                "pointing at $apiBaseUrlOverride. Drop the flag, or use :app:installDev."
        }
        val firebaseArgs = listOf(
            "appdistribution:distribute", apk.get().asFile.absolutePath,
            "--app", appId,
            "--testers", testers,
            "--release-notes", notes,
        )
        // firebase is a Node CLI; on Windows it's a .cmd shim, so invoke through cmd.
        commandLine = if (System.getProperty("os.name").lowercase().contains("win")) {
            listOf("cmd", "/c", "firebase") + firebaseArgs
        } else {
            listOf("firebase") + firebaseArgs
        }
    }
}

// Apollo Kotlin generates typed models + clients from the GraphQL schema and the .graphql
// operations under src/main/graphql/. The schema is a copy of the backend's
// backend/src/main/resources/graphql/schema.graphqls — the shared contract between web and app.
apollo {
    service("spira") {
        packageName.set("com.spiramindscape.android.graphql")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("com.apollographql.apollo:apollo-runtime:4.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // `implementation`, not `debugImplementation`: Network.kt lives in the main source set,
    // so a debug-only dependency would break the release compile. BuildConfig.DEBUG is what
    // keeps the interceptor from ever being installed in a release build.
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Firebase Crashlytics + Analytics (crash reporting). Active only when google-services.json
    // is present and the plugins above are applied; the libraries are harmless otherwise.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    // Push notifications (FCM). await() on the Firebase Task APIs comes from coroutines-play-services.
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    // createSavedStateHandle(): the composer's pending attachments have to survive the process
    // being killed, which the camera routinely causes (BUG-042).
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Google sign-in on-device (Credential Manager) → Google ID token
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Android stubs org.json in unit tests ("not mocked"); pull the real impl for JVM tests.
    testImplementation("org.json:json:20240303")
    // Robolectric provides a real Android runtime (Context/SharedPreferences) for JVM tests.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    // Compose UI testing on the JVM (via Robolectric) — no emulator needed.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
