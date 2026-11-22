# AnodyneWiki Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate AnodyneWiki into FreakLog Android with full feature parity with FreakLog-iOS: configurable instance URL, disk caching, Anodyne search, standalone and combined substance screens (dose ladder, duration timings fallback, 2D molecule structure, reports, toxicity, pharmacology), user notes viewer, and custom substance creation.

**Architecture:** A robust repository layer (`AnodyneRepository`) with disk caching and in-memory fallback store (`AnodyneFallbackStore`) sits between the HTTP client and UI. The UI integrates into Search, Settings, Substance detail, and Custom Substance flows, with a native Compose Canvas SVG renderer for 2D molecular structures.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Kotlin Coroutines & Flow, DataStore Preferences, Room, Hilt, JUnit4, MockK, Robolectric.

---

### File Structure Map

- **Data Models & Parsing**:
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneSubstance.kt`: Core substance data models (tiers, dosing, chemistry, effects, schedules, reagents, links).
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneIndexEntry.kt`: Substance index representation.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneUserNote.kt`: User experience notes data model.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneTextCleaner.kt`: HTML entity/tag cleaner and formula subscripting.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneParser.kt`: Streaming / JSON parser for substances, index, and user notes.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneTiming.kt`: Duration range and user route timing parser.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneFallbackStore.kt`: In-memory store providing dose/duration fallbacks.
  - `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneRepository.kt`: Disk cache, network fetching, and cache eviction.
- **Preferences**:
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt`: `anodyne_base_url` and `anodyne_auto_load_enabled`.
- **UI Components & Screens**:
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/AnodyneSettingsScreen.kt`: Instance URL, auto-load toggle, cache purge.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/AnodyneSettingsViewModel.kt`: ViewModel for Anodyne settings.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeDrawing.kt`: CDK SVG path/shape model and parser.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeStructureView.kt`: Compose Canvas 2D molecule diagram.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneDoseLadder.kt`: Material 3 dose ladder (≤ Light to Extreme).
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSections.kt`: Shared UI topic sections (Effects, Reports, Toxicity, Reagents, Pharmacology, Sources).
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchScreen.kt`: Searchable wiki index.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchViewModel.kt`: ViewModel for Anodyne search.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceScreen.kt`: Standalone Anodyne substance screen.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceViewModel.kt`: ViewModel for standalone substance screen.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AddCustomSubstanceFromAnodyneSheet.kt`: Pre-filled custom substance creation.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNotesScreen.kt`: User notes list.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNoteScreen.kt`: Single user note detail screen.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneLinksScreen.kt`: Erowid, Reddit, and reference links.
- **Integration Points**:
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/SearchScreen.kt`: "Search AnodyneWiki" entry.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/substance/SubstanceScreen.kt`: Topic sections & dose ladder integration.
  - `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/custom/CustomSubstanceScreen.kt`: Anodyne topic sections integration.
  - `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt`: Navigation routes.
  - `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt`: Navigation routes.

---

### Task 1: Anodyne Models & Subscript/Text Cleaners

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneTextCleaner.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneSubstance.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneIndexEntry.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/model/AnodyneUserNote.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneTextCleanerTest.kt`

- [x] **Step 1: Write failing unit test for text cleaning and subscripting**

```kotlin
package foo.pilz.freaklog.data.anodyne

import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneTextCleanerTest {
  @Test
  fun testSubscriptFormula() {
    assertEquals("C₁₁H₁₅NO₂", AnodyneTextCleaner.subscript("C<sub>11</sub>H<sub>15</sub>NO<sub>2</sub>"))
    assertEquals("H₂O", AnodyneTextCleaner.subscript("H2O"))
  }

  @Test
  fun testCleanHtmlAndMacros() {
    val input = "Sample {-{Efn|note}-} with <b>bold</b> &amp; &nbsp; text."
    assertEquals("Sample with bold & text.", AnodyneTextCleaner.clean(input))
  }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneTextCleanerTest" --no-daemon`  
Expected: Compilation failure (class not found).

- [x] **Step 3: Implement `AnodyneTextCleaner` and the data models**

Create `AnodyneTextCleaner.kt`:

```kotlin
package foo.pilz.freaklog.data.anodyne

object AnodyneTextCleaner {
  private val MACRO_PATTERN = Regex("""\{-[^}]*-\}|\{[^{}]*\}""")
  private val HTML_TAG_PATTERN = Regex("""<[^>]+>""")
  private val WHITESPACE = Regex("""\s+""")
  private val SUBSCRIPT_MAP = mapOf(
      '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
      '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉'
  )

  fun clean(text: String): String {
    return text.replace(MACRO_PATTERN, "")
        .replace(HTML_TAG_PATTERN, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(WHITESPACE, " ")
        .trim()
  }

  fun subscript(formula: String): String {
    val cleanFormula = formula.replace("<sub>", "").replace("</sub>", "")
    val sb = StringBuilder()
    var i = 0
    while (i < cleanFormula.length) {
      val c = cleanFormula[i]
      if (c.isDigit() && i > 0 && (cleanFormula[i - 1].isLetter() || cleanFormula[i - 1] in SUBSCRIPT_MAP.values)) {
        sb.append(SUBSCRIPT_MAP[c] ?: c)
      } else {
        sb.append(c)
      }
      i++
    }
    return sb.toString()
  }
}
```

Create `AnodyneSubstance.kt`:

```kotlin
package foo.pilz.freaklog.data.anodyne.model

import foo.pilz.freaklog.data.substances.AdministrationRoute

data class AnodyneTier(
    val lower: Double,
    val upper: Double,
    val unit: String,
    val entries: Int,
)

data class AnodyneDosing(
    val method: String,
    val light: AnodyneTier?,
    val common: AnodyneTier?,
    val strong: AnodyneTier?,
    val heavy: AnodyneTier?,
    val extreme: AnodyneTier?,
) {
  val route: AdministrationRoute? = mapRoute(method)

  val unit: String? = listOfNotNull(light, common, strong, heavy, extreme)
      .firstOrNull { it.unit.isNotBlank() }?.unit

  val totalEntries: Int = listOfNotNull(light, common, strong, heavy, extreme)
      .sumOf { it.entries }

  companion object {
    fun mapRoute(method: String): AdministrationRoute? {
      val m = method.lowercase()
      return when {
        m.contains("sublingual") -> AdministrationRoute.SUBLINGUAL
        m.contains("buccal") -> AdministrationRoute.BUCCAL
        m.contains("oral") -> AdministrationRoute.ORAL
        m.contains("insufflat") || m.contains("intranasal") -> AdministrationRoute.INSUFFLATED
        m.contains("rectal") -> AdministrationRoute.RECTAL
        m.contains("transdermal") -> AdministrationRoute.TRANSDERMAL
        m.contains("subcutaneous") -> AdministrationRoute.SUBCUTANEOUS
        m.contains("intramuscular") -> AdministrationRoute.INTRAMUSCULAR
        m.contains("intravenous") -> AdministrationRoute.INTRAVENOUS
        m.contains("smok") -> AdministrationRoute.SMOKED
        m.contains("vapori") || m.contains("inhal") -> AdministrationRoute.INHALED
        else -> null
      }
    }
  }
}

data class EffectGroup(val name: String, val effects: List<String>)
data class Schedule(val country: String, val schedule: String)
data class Reagent(val name: String, val colors: List<String>)
data class AnodyneLink(val title: String, val url: String)

data class AnodyneSubstance(
    val title: String,
    val abbreviations: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val slang: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
    val dosing: List<AnodyneDosing> = emptyList(),
    val durationOfAction: String? = null,
    val halfLife: String? = null,
    val interactions: String? = null,
    val mechanismOfAction: String? = null,
    val metabolism: String? = null,
    val healthEffects: String? = null,
    val drugWarnings: String? = null,
    val reportedFatalDose: String? = null,
    val treatment: String? = null,
    val effectGroups: List<EffectGroup> = emptyList(),
    val scheduling: List<Schedule> = emptyList(),
    val reagents: List<Reagent> = emptyList(),
    val erowidReports: List<AnodyneLink> = emptyList(),
    val redditReports: List<AnodyneLink> = emptyList(),
    val references: List<AnodyneLink> = emptyList(),
    val structureSvg: String? = null,
    val formula: String? = null,
    val molarMass: String? = null,
) {
  val commonName: String
    get() {
      if (title.length > 24) {
        val short = abbreviations.firstOrNull { it.length <= 24 }
        if (short != null) return short
      }
      return title
    }
}
```

Create `AnodyneIndexEntry.kt` and `AnodyneUserNote.kt`.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneTextCleanerTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/data/anodyne/ app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneTextCleanerTest.kt
git commit -m "feat(anodyne): add models and text cleaner"
```

---

### Task 2: Parsing Substance, Index, and User Notes JSON

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneParser.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneParserTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneParser`**

```kotlin
package foo.pilz.freaklog.data.anodyne

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnodyneParserTest {
  @Test
  fun testParseSubstance() {
    val json = JSONObject("""{
      "Title": "Ketamine",
      "Abbreviation": "K",
      "Aliases": ["Special K"],
      "Classes": ["Dissociative"],
      "Dosing Info": [
        {
          "Method": "Insufflated",
          "Tiers": {
            "Light": {"Lower": 10, "Upper": 30, "Unit": "mg", "Entries": 50},
            "Common": {"Lower": 30, "Upper": 75, "Unit": "mg", "Entries": 120}
          }
        }
      ],
      "DurationOfAction": "45 - 90 minutes",
      "Biological Half-Life": "2.5 hours",
      "MolecularFormula": "C13H16ClNO",
      "MolecularWeight": "237.73 g/mol"
    }""")
    val substance = AnodyneParser.parseSubstance(json)
    assertNotNull(substance)
    assertEquals("Ketamine", substance?.title)
    assertEquals(listOf("K"), substance?.abbreviations)
    assertEquals("C₁₃H₁₆ClNO", substance?.formula)
    assertEquals(1, substance?.dosing?.size)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneParserTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneParser`**

Implement `AnodyneParser` with robust parsing of `Title`, `Aliases`, `Dosing Info`, `Subjective Effects`, `Scheduling`, `Reagents`, `Erowid Experience Reports`, `Reddit Experience Reports`, `References`, and structure SVG (handling Base64 decoding if `StructureBase64` is provided).

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneParserTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneParser.kt app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneParserTest.kt
git commit -m "feat(anodyne): add JSON parser for substances, index, and user notes"
```

---

### Task 3: Timing Parser & Fallback Store

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneTiming.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneFallbackStore.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneTimingTest.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneFallbackStoreTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneTiming`**

```kotlin
package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnodyneTimingTest {
  @Test
  fun testParseRange() {
    val range = AnodyneTiming.rangeIn("3 – 6 hours")
    assertNotNull(range)
    assertEquals(3.0, range?.min ?: 0.0, 0.01)
    assertEquals(6.0, range?.max ?: 0.0, 0.01)
    assertEquals(DurationUnits.HOURS, range?.units)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneTimingTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneTiming` and `AnodyneFallbackStore`**

Implement regex matching for units (`hours`, `hrs`, `h`, `minutes`, `mins`, `m`, `seconds`, `secs`, `s`, `days`, `d`), converting to `DurationRange` and `RoaDuration`. Implement `AnodyneFallbackStore` with thread-safe synchronized maps holding fallback doses and durations per substance and route.

- [x] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneTimingTest" --tests "foo.pilz.freaklog.data.anodyne.AnodyneFallbackStoreTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneTiming.kt app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneFallbackStore.kt app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneTimingTest.kt app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneFallbackStoreTest.kt
git commit -m "feat(anodyne): add timing range parser and fallback store"
```

---

### Task 4: UserPreferences & Disk Cache Repository

**Files:**

- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneRepository.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneRepositoryTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneRepository`**

```kotlin
package foo.pilz.freaklog.data.anodyne

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnodyneRepositoryTest {
  @Test
  fun testNormalizeInstanceUrl() {
    assertEquals("https://anodyne.wiki", AnodyneRepository.normalizeUrl("anodyne.wiki"))
    assertEquals("https://mirror.example.org", AnodyneRepository.normalizeUrl("https://mirror.example.org/"))
    assertEquals(null, AnodyneRepository.normalizeUrl("ftp://invalid"))
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneRepositoryTest" --no-daemon`

- [x] **Step 3: Update `UserPreferences` and implement `AnodyneRepository`**

Add `ANODYNE_BASE_URL` and `ANODYNE_AUTO_LOAD_ENABLED` keys and flows in `UserPreferences`. Implement `AnodyneRepository` with `@Singleton` injecting `@ApplicationContext`, managing `<cacheDir>/anodyne`, saving/reading JSON with 7-day substance TTL and 24-hr index TTL, and providing cache size and clearing.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.anodyne.AnodyneRepositoryTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt app/src/main/java/foo/pilz/freaklog/data/anodyne/AnodyneRepository.kt app/src/test/java/foo/pilz/freaklog/data/anodyne/AnodyneRepositoryTest.kt
git commit -m "feat(anodyne): add repository with disk caching and preference integration"
```

---

### Task 5: 2D Molecule SVG Parser & Compose Canvas View

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeDrawing.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeStructureView.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeDrawingTest.kt`

- [x] **Step 1: Write failing unit test for `MoleculeDrawing.parse`**

```kotlin
package foo.pilz.freaklog.ui.tabs.search.anodyne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoleculeDrawingTest {
  @Test
  fun testParseSvgLinesAndPaths() {
    val svg = """<svg viewBox="0 0 100 100"><line x1="10" y1="10" x2="90" y2="90" stroke="#000000" stroke-width="2"/></svg>"""
    val drawing = MoleculeDrawing.parse(svg)
    assertNotNull(drawing)
    assertEquals(100f, drawing?.viewBox?.width ?: 0f, 0.01f)
    assertEquals(1, drawing?.shapes?.size)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.MoleculeDrawingTest" --no-daemon`

- [x] **Step 3: Implement `MoleculeDrawing` and `MoleculeStructureView`**

Implement XML parsing for SVG shapes (paths, lines, rects, circles, polylines), viewBox extraction, stroke and fill color mapping (translating #000000 to primary text/onSurface color), and Compose `Canvas` rendering with aspect ratio preservation.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.MoleculeDrawingTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeDrawing.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeStructureView.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/MoleculeDrawingTest.kt
git commit -m "feat(anodyne): add native 2D chemical structure SVG renderer"
```

---

### Task 6: Settings Screen (`AnodyneSettingsScreen`)

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/AnodyneSettingsViewModel.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/AnodyneSettingsScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/AnodyneSettingsViewModelTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneSettingsViewModel`**

```kotlin
package foo.pilz.freaklog.ui.tabs.settings.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneSettingsViewModelTest {
  @Test
  fun testInstanceUrlValidation() {
    val prefs = mockk<UserPreferences>(relaxed = true) {
      every { anodyneBaseUrlFlow } returns flowOf("https://anodyne.wiki")
    }
    val repo = mockk<AnodyneRepository>(relaxed = true)
    val vm = AnodyneSettingsViewModel(prefs, repo)
    vm.onDraftUrlChange("invalid url ://")
    assertEquals(false, vm.isDraftValid.value)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.anodyne.AnodyneSettingsViewModelTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneSettingsScreen` and hook into Settings**

Add `AnodyneWiki` row to `SettingsScreen`. Build `AnodyneSettingsScreen` with:

- "Load Automatically" toggle.
- "Instance" OutlinedTextField with "Use anodyne.wiki" button when custom.
- "Delete Downloaded Data" button showing formatted byte count.
- Links to "Browse AnodyneWiki" and external instance URL.
  Add `AnodyneSettingsRoute` to `settingsGraph.kt`.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.anodyne.AnodyneSettingsViewModelTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/ app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/anodyne/
git commit -m "feat(anodyne): add AnodyneWiki settings screen"
```

---

### Task 7: Search Screen Integration & `AnodyneSearchScreen`

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchViewModel.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/SearchScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchViewModelTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneSearchViewModel`**

```kotlin
package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneSearchViewModelTest {
  @Test
  fun testFilterPrioritizesPrefixMatches() = runTest {
    val repo = mockk<AnodyneRepository> {
      coEvery { getIndexEntries() } returns Result.success(listOf(
          AnodyneIndexEntry("Lysergic acid diethylamide", listOf("LSD"), listOf("Psychedelic")),
          AnodyneIndexEntry("Ketamine", listOf("K"), listOf("Dissociative"))
      ))
    }
    val vm = AnodyneSearchViewModel(repo)
    vm.onSearchQueryChange("LSD")
    assertEquals("Lysergic acid diethylamide", vm.filteredEntries.value.first().title)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSearchViewModelTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneSearchScreen` and connect SearchScreen**

In `SearchScreen.kt`, add the "Search AnodyneWiki" button at the bottom of the list with a book icon. In `AnodyneSearchScreen.kt`, provide live search filtering over titles, abbreviations, and classes, navigating to `AnodyneSubstanceRoute(title)`.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSearchViewModelTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchViewModel.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchScreen.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/SearchScreen.kt app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSearchViewModelTest.kt
git commit -m "feat(anodyne): add AnodyneWiki search screen and integration in search"
```

---

### Task 8: Standalone `AnodyneSubstanceScreen` & Add Custom Substance Sheet

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceViewModel.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceScreen.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AddCustomSubstanceFromAnodyneSheet.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSections.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceViewModelTest.kt`

- [x] **Step 1: Write failing unit test for `AnodyneSubstanceViewModel`**

```kotlin
package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneSubstanceViewModelTest {
  @Test
  fun testLoadsSubstance() = runTest {
    val repo = mockk<AnodyneRepository> {
      coEvery { lookup("Ketamine", any()) } returns Result.success(AnodyneSubstance("Ketamine"))
      coEvery { getUserNotes(any()) } returns Result.success(emptyList())
    }
    val vm = AnodyneSubstanceViewModel("Ketamine", repo)
    vm.load()
    assertEquals("Ketamine", vm.substance.value?.title)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSubstanceViewModelTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneSubstanceScreen` & Add Custom Substance Sheet**

Create `AnodyneSections.kt` containing topic sections: Overview, Effects, Reports, Logged Doses, Duration, Literature Interactions, Toxicity, Health Warnings, Reagent Tests (with color chips), Legal Status, Pharmacology, and Sources. Implement `AddCustomSubstanceFromAnodyneSheet` creating a Room `CustomSubstance` prefilling dose tiers for all routes and matching categories.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSubstanceViewModelTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceViewModel.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceScreen.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AddCustomSubstanceFromAnodyneSheet.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSections.kt app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneSubstanceViewModelTest.kt
git commit -m "feat(anodyne): add standalone Anodyne substance screen and add-as-custom flow"
```

---

### Task 9: Anodyne Topic Components & Combined `SubstanceScreen` / `CustomSubstanceScreen`

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneDoseLadder.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/substance/SubstanceScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/substance/SubstanceViewModel.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/custom/CustomSubstanceScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/custom/CustomSubstanceViewModel.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/search/substance/SubstanceAnodyneIntegrationTest.kt`

- [x] **Step 1: Write failing unit test for `SubstanceAnodyneIntegration`**

```kotlin
package foo.pilz.freaklog.ui.tabs.search.substance

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubstanceAnodyneIntegrationTest {
  @Test
  fun testAutoLoadFetchesAnodyneWhenEnabled() {
    val repo = mockk<AnodyneRepository>(relaxed = true)
    coEvery { repo.lookup("LSD", any()) } returns Result.success(AnodyneSubstance("Lysergic acid diethylamide"))
    assertNotNull(repo)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.substance.SubstanceAnodyneIntegrationTest" --no-daemon`

- [x] **Step 3: Implement `AnodyneDoseLadder` and integrate into `SubstanceScreen` and `CustomSubstanceScreen`**

Build `AnodyneDoseLadder` rendering: `≤ Light`, `Common`, `Strong`, `Heavy`, `Extreme` in Material 3 styling with route title and entry log counts.
In `SubstanceScreen.kt` and `CustomSubstanceScreen.kt`:

- Display "More from AnodyneWiki" action button when not loaded.
- When loaded, inject:
  - 2D molecular structure diagram and formula/mass.
  - Dose segmented switch (PsychonautWiki vs AnodyneWiki).
  - Duration section fallback using `AnodyneFallbackStore`.
  - Subjective effects, report links, literature interactions, toxicity fatal doses/treatment, health warnings, reagents, legal status, pharmacology, and sources.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.substance.SubstanceAnodyneIntegrationTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneDoseLadder.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/substance/ app/src/main/java/foo/pilz/freaklog/ui/tabs/search/custom/ app/src/test/java/foo/pilz/freaklog/ui/tabs/search/substance/SubstanceAnodyneIntegrationTest.kt
git commit -m "feat(anodyne): integrate Anodyne data, dose ladder, and topics into substance screens"
```

---

### Task 10: User Notes & Report Links Screens

**Files:**

- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNotesScreen.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNoteScreen.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneLinksScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt`
- Test: `app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNotesScreenTest.kt`

- [x] **Step 1: Write failing unit test for user notes screen**

```kotlin
package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import org.junit.Assert.assertEquals
import org.junit.Test

class AnodyneUserNotesScreenTest {
  @Test
  fun testUserNoteSummaryFormat() {
    val note = AnodyneUserNote(
        user = "alice",
        substanceName = "MDMA",
        routes = listOf(AnodyneUserNote.Route("Oral", "120 mg", "30m", "45m", "4h"))
    )
    assertEquals("Oral 120 mg", note.routesSummary)
  }
}
```

- [x] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneUserNotesScreenTest" --no-daemon`

- [x] **Step 3: Implement User Notes & Links Screens**

Implement `AnodyneUserNotesScreen` (listing notes with user, dosage/timings summary, and preview), `AnodyneUserNoteScreen` (full userpage note display with routes, classes, forms, sources, indications, effects, after-effects, tolerance, odor, and personal notes), and `AnodyneLinksScreen` (listing Erowid/Reddit reports and references). Register destinations in `searchGraph.kt`.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneUserNotesScreenTest" --no-daemon`  
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNotesScreen.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNoteScreen.kt app/src/main/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneLinksScreen.kt app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/searchGraph.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/search/anodyne/AnodyneUserNotesScreenTest.kt
git commit -m "feat(anodyne): add user notes and external report links screens"
```

---

### Task 11: Full Verification & Static Analysis

**Files:**

- Run full test suite and checks.

- [x] **Step 1: Run complete test suite**

Run: `./gradlew testDebugUnitTest --no-daemon`  
Expected: PASS across all shards.

- [x] **Step 2: Run Detekt and Lint checks**

Run: `./gradlew detekt :app:lintDebug --no-daemon`  
Expected: Clean with no violations.

- [x] **Step 3: Commit all remaining verification changes**

```bash
git commit --allow-empty -m "chore(anodyne): complete verification of AnodyneWiki feature integration"
```
