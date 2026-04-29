// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Detekt and Kover are *applied* in `app/build.gradle.kts`; the plugin
// versions are declared here so they're resolvable from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id ("nl.littlerobots.version-catalog-update") version "1.0.1"
}

// Convenience aggregator: `./gradlew detektAll` runs every Kotlin module's
// :detekt task. Useful as the project grows beyond a single :app module.
tasks.register("detektAll") {
    group = "verification"
    description = "Runs Detekt across all Kotlin modules."
    dependsOn(":app:detekt")
}
