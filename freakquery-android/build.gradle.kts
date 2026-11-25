plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.detekt)
}

android {
  namespace = "com.ndm4.freakquery"
  compileSdk = 37

  defaultConfig {
    minSdk = 23
  }

  lint {
    lintConfig = rootProject.file("config/lint/lint-library.xml")
    warningsAsErrors = true
    checkAllWarnings = true
    checkTestSources = true
    abortOnError = true
    explainIssues = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    allWarningsAsErrors.set(true)
    extraWarnings.set(true)
    progressiveMode.set(true)
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  parallel = true
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
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

dependencies {
  testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
  filter.isFailOnNoMatchingTests = false
}
