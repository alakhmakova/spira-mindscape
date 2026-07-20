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

android {
    namespace = "com.spiramindscape.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spiramindscape.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL. Defaults to production (Cloud Run) so the app works on a real
        // device on any network. To test against a LOCAL backend, override here:
        //   • Emulator:   http://10.0.2.2:8080  (10.0.2.2 = the host machine from the emulator)
        //   • Real phone: http://<your-PC-LAN-IP>:8080  (same Wi-Fi) or an ngrok HTTPS URL
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://spira-952567559986.europe-west1.run.app\"",
        )

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
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
