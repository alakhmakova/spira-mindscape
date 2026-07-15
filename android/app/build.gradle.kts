plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.apollographql.apollo")
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
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
