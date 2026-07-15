// Top-level build file. Plugin versions are declared here (apply false) and applied
// in the module build scripts. Versions are pinned inline (no version catalog) so the
// whole setup is readable in one place.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.apollographql.apollo") version "4.1.0" apply false
}
