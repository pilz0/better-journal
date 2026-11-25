package foo.pilz.freaklog

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository-wide hygiene, security, and correctness invariants enforced in fast JVM unit tests so
 * regressions fail early without needing full Lint/Detekt runs on every edit.
 */
class StaticAnalysisInvariantsTest {

  private fun repoRoot(): File {
    val direct = File("app/build.gradle.kts")
    if (direct.isFile) return File(".")
    val parent = File("../app/build.gradle.kts")
    if (parent.isFile) return File("..")
    error("Cannot locate repository root from ${File(".").absolutePath}")
  }

  private fun sourceFiles(
      root: File,
      vararg relativeDirs: String,
      ext: String = ".kt",
  ): List<File> = relativeDirs.flatMap { rel ->
    val dir = File(root, rel)
    if (!dir.exists()) emptyList()
    else dir.walkTopDown().filter { it.isFile && it.name.endsWith(ext) }.toList()
  }

  private fun stripCommentsAndStrings(source: String): String {
    val sb = StringBuilder(source.length)
    var i = 0
    while (i < source.length) {
      val c = source[i]
      val next = source.getOrNull(i + 1)
      when {
        c == '/' && next == '/' -> {
          while (i < source.length && source[i] != '\n') i++
        }
        c == '/' && next == '*' -> {
          i += 2
          while (i < source.length - 1 && !(source[i] == '*' && source[i + 1] == '/')) {
            if (source[i] == '\n') sb.append('\n')
            i++
          }
          i += 2
        }
        source.startsWith("\"\"\"", i) -> {
          i += 3
          sb.append("\"\"")
          while (i < source.length && !source.startsWith("\"\"\"", i)) {
            if (source[i] == '\n') sb.append('\n')
            i++
          }
          if (i < source.length) i += 3
        }
        c == '"' -> {
          i++
          sb.append("\"\"")
          while (i < source.length && source[i] != '"') {
            if (source[i] == '\\' && i + 1 < source.length) i += 2 else i++
          }
          if (i < source.length) i++
        }
        else -> {
          sb.append(c)
          i++
        }
      }
    }
    return sb.toString()
  }

  private fun countTopLevelCallArgs(source: String, openParenIndex: Int): Int {
    var depth = 0
    var commas = 0
    var hasTokens = false
    var inString = false
    var i = openParenIndex
    while (i < source.length) {
      val c = source[i]
      if (inString) {
        if (c == '\\') {
          i += 2
          continue
        }
        if (c == '"') inString = false
      } else {
        when (c) {
          '"' -> {
            inString = true
            hasTokens = true
          }
          '(' -> {
            depth++
            if (depth > 1) hasTokens = true
          }
          ')' -> {
            depth--
            if (depth == 0) return if (hasTokens) commas + 1 else 0
          }
          ',' -> if (depth == 1) commas++
          else -> if (!c.isWhitespace()) hasTokens = true
        }
      }
      i++
    }
    return 0
  }

  private fun extractCallArgsText(source: String, openParenIndex: Int): String {
    var depth = 0
    var inString = false
    var i = openParenIndex
    val sb = StringBuilder()
    while (i < source.length) {
      val c = source[i]
      sb.append(c)
      if (inString) {
        if (c == '\\' && i + 1 < source.length) {
          sb.append(source[i + 1])
          i += 2
          continue
        }
        if (c == '"') inString = false
      } else {
        when (c) {
          '"' -> inString = true
          '(' -> depth++
          ')' -> {
            depth--
            if (depth == 0) return sb.toString()
          }
        }
      }
      i++
    }
    return sb.toString()
  }

  @Test
  fun noLegacyPackageReferencesInSources() {
    val root = repoRoot()
    // Split so this file never triggers the repository-wide legacy namespace scan itself.
    val legacy = "com." + "isaakhanimann.journal"
    val hits =
        listOf("app/src", "freakquery-android/src").flatMap { rel ->
          File(root, rel)
              .walkTopDown()
              .filter { it.isFile }
              .filter { file -> file.readText().contains(legacy) }
              .map { it.relativeTo(root).path }
              .toList()
        }
    assertTrue("Found legacy namespace references in: $hits", hits.isEmpty())
  }

  @Test
  fun noGlobalScopeUsageInMainCode() {
    val root = repoRoot()
    val regex = Regex("""\bGlobalScope\b""")
    val hits =
        sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")
            .filter { regex.containsMatchIn(stripCommentsAndStrings(it.readText())) }
            .map { it.relativeTo(root).path }
    assertTrue("Found GlobalScope in: $hits", hits.isEmpty())
  }

  @Test
  fun noUnlifecycleCollectAsStateInMainCode() {
    val root = repoRoot()
    val regex = Regex("""(?<![A-Za-z0-9_])collectAsState\(""")
    val hits =
        sourceFiles(root, "app/src/main/java")
            .filter { regex.containsMatchIn(stripCommentsAndStrings(it.readText())) }
            .map { it.relativeTo(root).path }
    assertTrue(
        "Use collectAsStateWithLifecycle() instead of collectAsState() in: $hits",
        hits.isEmpty(),
    )
  }

  @Test
  fun noDeprecatedHiltViewModelImportInMainCode() {
    val root = repoRoot()
    val deprecated = "androidx.hilt.navigation.compose.hiltViewModel"
    val hits =
        sourceFiles(root, "app/src/main/java")
            .filter { it.readText().contains(deprecated) }
            .map { it.relativeTo(root).path }
    assertTrue("Found deprecated hiltViewModel import in: $hits", hits.isEmpty())
  }

  @Test
  fun noRunBlockingOutsideAllowlistedProviderBoundary() {
    val root = repoRoot()
    val regex = Regex("""\brunBlocking\b""")
    val allowlist = setOf("app/src/main/java/foo/pilz/freaklog/provider/JournalProvider.kt")
    val hits =
        sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")
            .filter { regex.containsMatchIn(stripCommentsAndStrings(it.readText())) }
            .map { it.relativeTo(root).path }
            .filterNot { it in allowlist }
    assertTrue("Unexpected runBlocking in main code: $hits", hits.isEmpty())
  }

  @Test
  fun androidManifestAndNetworkSecurityConfigEnforceSafeDefaults() {
    val root = repoRoot()
    val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
    assertTrue(
        "Missing android:allowBackup=\"false\"",
        manifest.contains("""android:allowBackup="false""""),
    )
    assertTrue(
        "Missing networkSecurityConfig",
        manifest.contains("""android:networkSecurityConfig="@xml/network_security_config""""),
    )
    assertTrue(
        "android:debuggable must not be set in manifest",
        !manifest.contains("android:debuggable=\"true\""),
    )
    assertTrue(
        "android:usesCleartextTraffic=\"true\" is forbidden",
        !manifest.contains("""android:usesCleartextTraffic="true""""),
    )

    val netSec = File(root, "app/src/main/res/xml/network_security_config.xml").readText()
    assertTrue(
        "Base config must disable cleartext traffic",
        netSec.contains("""<base-config cleartextTrafficPermitted="false">"""),
    )
    val allowedLoopbacks = setOf("localhost", "127.0.0.1", "::1", "[::1]")
    val domainRegex = Regex("""<domain\b[^>]*>([^<]+)</domain>""")
    val badDomains =
        domainRegex
            .findAll(netSec)
            .map { it.groupValues[1].trim() }
            .filterNot { it in allowedLoopbacks }
            .toList()
    assertTrue("Non-loopback cleartext domain exceptions found: $badDomains", badDomains.isEmpty())
  }

  @Test
  fun roomSchemasAndMigrationPairsMatchLatestVersion() {
    val root = repoRoot()
    val appDbText =
        File(root, "app/src/main/java/foo/pilz/freaklog/data/room/AppDatabase.kt").readText()
    val latestVersion =
        Regex("""LATEST_SCHEMA_VERSION\s*:\s*Int\s*=\s*(\d+)""")
            .find(appDbText)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: error("Could not parse LATEST_SCHEMA_VERSION")

    val schemaDir = File(root, "app/schemas/foo.pilz.freaklog.data.room.AppDatabase")
    for (v in 1..latestVersion) {
      if (v == 13) continue
      val schemaFile = File(schemaDir, "$v.json")
      assertTrue("Missing schema file ${schemaFile.path}", schemaFile.isFile)
      val json = JSONObject(schemaFile.readText())
      assertTrue("Invalid formatVersion in $v.json", json.getInt("formatVersion") == 1)
      assertTrue(
          "Mismatched database.version in $v.json",
          json.getJSONObject("database").getInt("version") == v,
      )
    }

    val migrationTest =
        File(
                root,
                "app/src/androidTest/java/foo/pilz/freaklog/data/room/AppDatabaseMigrationTest.kt",
            )
            .readText()
    assertTrue(
        "AppDatabaseMigrationTest must include migration ending at $latestVersion",
        migrationTest.contains("to $latestVersion"),
    )
  }

  @Test
  fun dateTimeFormatterOfPatternAlwaysSpecifiesLocale() {
    val root = repoRoot()
    val callRegex = Regex("""DateTimeFormatter\.ofPattern\(""")
    val violations = mutableListOf<String>()
    for (file in sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")) {
      val text = file.readText()
      for (match in callRegex.findAll(text)) {
        val openIdx = match.range.last
        val argCount = countTopLevelCallArgs(text, openIdx)
        if (argCount < 2) {
          val line = text.substring(0, match.range.first).count { it == '\n' } + 1
          violations += "${file.relativeTo(root).path}:$line"
        }
      }
    }
    assertTrue(
        "DateTimeFormatter.ofPattern missing explicit Locale in: $violations",
        violations.isEmpty(),
    )
  }

  @Test
  fun stringFormatCallsAlwaysSpecifyLocale() {
    val root = repoRoot()
    val violations = mutableListOf<String>()
    val stringFormatRegex = Regex("""\bString\.format\(""")
    val kotlinFormatRegex = Regex(""""[^"]*"\s*\.format\(""")

    for (file in sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")) {
      val text = file.readText()
      for (match in stringFormatRegex.findAll(text)) {
        val args = extractCallArgsText(text, match.range.last).removePrefix("(")
        val firstArg = args.substringBefore(',').trim()
        if (!firstArg.contains("locale", ignoreCase = true)) {
          val line = text.substring(0, match.range.first).count { it == '\n' } + 1
          violations += "${file.relativeTo(root).path}:$line (String.format)"
        }
      }
      for (match in kotlinFormatRegex.findAll(text)) {
        val args = extractCallArgsText(text, match.range.last).removePrefix("(")
        val firstArg = args.substringBefore(',').trim()
        if (!firstArg.contains("locale", ignoreCase = true)) {
          val line = text.substring(0, match.range.first).count { it == '\n' } + 1
          violations += "${file.relativeTo(root).path}:$line (.format)"
        }
      }
    }
    assertTrue("Formatting call missing explicit Locale in: $violations", violations.isEmpty())
  }

  @Test
  fun noDoubleBangOperatorInMainCode() {
    val root = repoRoot()
    val violations = mutableListOf<String>()
    for (file in sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")) {
      val cleaned = stripCommentsAndStrings(file.readText())
      cleaned.lines().forEachIndexed { idx, line ->
        if (line.contains("!!")) {
          violations += "${file.relativeTo(root).path}:${idx + 1}"
        }
      }
    }
    assertTrue("Non-null assertion '!!' found in main code: $violations", violations.isEmpty())
  }

  @Test
  fun workflowSecurityInvariantsHold() {
    val root = repoRoot()
    val workflowsDir = File(root, ".github/workflows")
    val workflows = workflowsDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }.orEmpty()
    assertTrue("Expected workflow files under .github/workflows", workflows.isNotEmpty())

    val shaRegex = Regex("""@[0-9a-f]{40}$""")
    val usesRegex = Regex("""^(?:-\s+)?uses:\s*([^\s#]+)""")

    for (wf in workflows) {
      val lines = wf.readLines()
      val content = lines.joinToString("\n")
      assertTrue("${wf.name} missing 'permissions: {}'", content.contains("permissions: {}"))
      assertTrue(
          "${wf.name} missing 'step-security/harden-runner'",
          content.contains("step-security/harden-runner"),
      )

      lines.forEachIndexed { idx, line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) return@forEachIndexed
        val match = usesRegex.find(trimmed) ?: return@forEachIndexed
        val ref = match.groupValues[1]
        if (!ref.startsWith("./") && !ref.startsWith("$/")) {
          assertTrue(
              "Unpinned action in ${wf.name}:${idx + 1}: $ref",
              shaRegex.containsMatchIn(ref),
          )
        }
        if (ref.startsWith("actions/checkout@")) {
          val window = lines.subList(idx, minOf(lines.size, idx + 8)).joinToString("\n")
          assertTrue(
              "actions/checkout missing persist-credentials: false in ${wf.name}:${idx + 1}",
              window.contains("persist-credentials: false"),
          )
        }
      }
    }
  }

  @Test
  fun noSecretLoggingInMainCode() {
    val root = repoRoot()
    val regex =
        Regex(
            """\bLog\.[dviwe]\([^\n]*(apiKey|passphrase|password|secret)""",
            RegexOption.IGNORE_CASE,
        )
    val hits =
        sourceFiles(root, "app/src/main/java", "freakquery-android/src/main/java")
            .filter { file ->
              val content = file.readText()
              regex.findAll(content).any { m -> m.value.contains("$") }
            }
            .map { it.relativeTo(root).path }
    assertTrue("Potential secret interpolation in Log call: $hits", hits.isEmpty())
  }
}
