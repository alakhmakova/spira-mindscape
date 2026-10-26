// Top-level build file. Plugin versions are declared here (apply false) and applied
// in the module build scripts. Versions are pinned inline (no version catalog) so the
// whole setup is readable in one place.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.apollographql.apollo") version "4.1.0" apply false
    // Crashlytics/Analytics (applied in :app only when google-services.json is present)
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
