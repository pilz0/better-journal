plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "foo.pilz.freaklog"
    compileSdk = 35

    defaultConfig {
        applicationId = "foo.pilz.freaklog"
        minSdk = 31
        targetSdk = 36
        versionCode = 71
        versionName = "11.18"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    lint {
        checkReleaseBuilds = true
        // Lint dependencies as well, so reports include issues in libraries we ship.
        checkDependencies = true
        // Don't fail the build on lint errors yet; the SARIF is uploaded to
        // code scanning so reviewers can triage them. Flip to `true` once the
        // existing baseline is clean.
        abortOnError = false
        sarifReport = true
    }

    testOptions {
        // Enables `Robolectric` and the AndroidX test infra against the
        // platform stub. We still set `isReturnDefaultValues = true` so any
        // un-stubbed framework call returns a sensible default rather than
        // throwing — this preserves the behaviour of the existing test
        // suite. New tests that need a real Android runtime should use
        // Robolectric (`@RunWith(RobolectricTestRunner::class)`).
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
        animationsDisabled = true
    }

    buildTypes {
        debug {
            // Coverage instrumentation for instrumented (connectedAndroidTest)
            // runs is enabled here so Kover can merge JVM + Android coverage.
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Several test/runtime dependencies (MockK, Kotlin coroutines, Robolectric, …)
            // ship duplicate license files under META-INF; exclude them so APKs build cleanly.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // Force a deterministic locale and timezone so tests that touch
    // `java.time` / `java.text` / `java.util.Locale` don't flake when CI
    // runners are spread across regions.
    systemProperty("user.timezone", "UTC")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    // Send Robolectric's internal log output to stdout so failures show
    // diagnostic context in the test report instead of being swallowed.
    systemProperty("robolectric.logging", "stdout")

    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }

    // Allow excluding test patterns from the command line for sharded CI runs:
    //   ./gradlew testDebugUnitTest -PtestExclude=foo.pilz.freaklog.data.*,foo.pilz.freaklog.ui.*
    providers.gradleProperty("testExclude").orNull
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.forEach { filter.excludeTestsMatching(it) }
}

// --- Detekt ---
detekt {
    toolVersion = libs.versions.detekt.get()
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    ignoreFailures = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required.set(true)
        html.required.set(true)
        xml.required.set(true)
        md.required.set(false)
        txt.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// --- Kover (Kotlinx coverage) ---
//
// Kover instruments JVM unit tests (`testDebugUnitTest`) and merges results
// from instrumented tests when they run. Reports land under
// `app/build/reports/kover/`.
kover {
    reports {
        filters {
            excludes {
                // Generated code we don't author and shouldn't try to cover.
                packages(
                    "*.databinding",
                    "*.di_*",
                    "hilt_aggregated_deps",
                    "dagger.hilt.*",
                    "*_HiltModules*",
                    "*_Factory*",
                    "*_MembersInjector*",
                )
                classes(
                    "*BuildConfig",
                    "*ComposableSingletons*",
                    "*\$\$serializer",
                    "*Application_HiltComponents*",
                )
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "androidx.compose.ui.tooling.preview.Preview",
                    "dagger.Module",
                    "dagger.hilt.InstallIn",
                )
            }
        }
        verify {
            // Repository-wide minimum line coverage. Set just below current
            // baseline so any regression fails CI; ratchet upward as the
            // suite grows toward the target ≥50% overall / ≥70% in `data.*`
            // and `ui.utils.*` documented in AGENTS.md.
            rule("Project line coverage") {
                minBound(8)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite.android)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // --- Unit tests (JVM) ---
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)

    // --- Instrumented tests (on-device) ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.turbine)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.compose.markdown)
    implementation(libs.androidx.biometric)

    coreLibraryDesugaring(libs.android.desugarJdkLibs)
}
