# Add IM and SQ Injection Sites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add anatomically appropriate injection site options for intramuscular (IM) and subcutaneous (SQ) routes, replacing the shared IV-focused site list with route-specific options.

**Architecture:** The `AdministrationRoute` enum currently returns the same `INJECTION_SITE_OPTIONS` (IV vein sites) for all three injection routes. We add two new companion object constants — `INTRAMUSCULAR_SITE_OPTIONS` (8 muscle sites) and `SUBCUTANEOUS_SITE_OPTIONS` (8 tissue sites) — and update the `siteOptions` property to dispatch route-appropriately. Aliases in `DefaultAliases.kt` are extended so the freakquery parser normalizes the new site names.

**Tech Stack:** Kotlin, JUnit 4 (existing test framework)

---

### Task 1: Add IM and SQ site option constants and update siteOptions dispatch

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/data/substances/AdministrationRoute.kt:168-203`

- [ ] **Step 1: Add IM and SQ site option constants and update siteOptions dispatch**

Replace the `siteOptions` property and `companion object` block in `AdministrationRoute.kt`:

In `AdministrationRoute.kt`, find the `siteOptions` property (lines 168-174) and the `companion object` block (lines 176-203). Replace the `siteOptions` getter to dispatch to separate lists, and add the new constants:

```kotlin
    val siteOptions: List<String>
        get() = when (this) {
            INSUFFLATED -> NOSTRIL_OPTIONS
            INTRAVENOUS -> INTRAVENOUS_SITE_OPTIONS
            INTRAMUSCULAR -> INTRAMUSCULAR_SITE_OPTIONS
            SUBCUTANEOUS -> SUBCUTANEOUS_SITE_OPTIONS
            TRANSDERMAL -> TRANSDERMAL_OPTIONS
            else -> emptyList()
        }

    companion object {
        const val PSYCHONAUT_WIKI_ARTICLE_URL =
            "https://psychonautwiki.org/wiki/Route_of_administration"
        const val SAFER_INJECTION_ARTICLE_URL = "https://psychonautwiki.org/wiki/Safer_injection_guide"
        const val SAFER_PLUGGING_ARTICLE_URL = "https://wiki.tripsit.me/wiki/Quick_Guide_to_Plugging"

        val NOSTRIL_OPTIONS = listOf("Left nostril", "Right nostril", "Both nostrils")
        val INTRAVENOUS_SITE_OPTIONS = listOf(
            "Left Median Cubital",
            "Left Cephalic",
            "Left Basilic",
            "Left Dorsal Hand",
            "Left Median Antebrachial",
            "Right Median Cubital",
            "Right Cephalic",
            "Right Basilic",
            "Right Dorsal Hand",
            "Right Median Antebrachial",
        )
        val INTRAMUSCULAR_SITE_OPTIONS = listOf(
            "Left Deltoid",
            "Right Deltoid",
            "Left Vastus Lateralis",
            "Right Vastus Lateralis",
            "Left Ventrogluteal",
            "Right Ventrogluteal",
            "Left Dorsogluteal",
            "Right Dorsogluteal",
        )
        val SUBCUTANEOUS_SITE_OPTIONS = listOf(
            "Left abdomen",
            "Right abdomen",
            "Left thigh",
            "Right thigh",
            "Left upper arm",
            "Right upper arm",
            "Left lower back",
            "Right lower back",
        )
        val TRANSDERMAL_OPTIONS = listOf(
            "Left arm",
            "Right arm",
            "Chest",
            "Back",
            "Left thigh",
            "Right thigh",
        )
    }
```

- [ ] **Step 2: Run existing tests to see which ones fail**

Run: `./gradlew :app:testDebugUnitTest --tests "foo.pilz.freaklog.data.substances.AdministrationRouteTest"`

Expected: Tests `testIntramuscular_hasInjectionSiteOptions`, `testSubcutaneous_hasInjectionSiteOptions`, and `testInjectionSiteOptionsCount` will fail because they expect count 10 but IM now has 8, SQ has 8, and the constant was renamed to `INTRAVENOUS_SITE_OPTIONS`.

---

### Task 2: Update AdministrationRouteTest for new site counts

**Files:**
- Modify: `app/src/test/java/foo/pilz/freaklog/data/substances/AdministrationRouteTest.kt:190-232`

- [ ] **Step 1: Update test assertions for renamed constant and new counts**

In `AdministrationRouteTest.kt`, update the following tests:

**Line 191-196** — Rename test and update to check IV-specific options:
```kotlin
    @Test
    fun testIntravenous_hasIntravenousSiteOptions() {
        val options = AdministrationRoute.INTRAVENOUS.siteOptions
        assertEquals(10, options.size)
        assertTrue(options.contains("Left Median Cubital"))
        assertTrue(options.contains("Right Median Cubital"))
    }
```

**Lines 198-201** — Update expected count for IM:
```kotlin
    @Test
    fun testIntramuscular_hasIntramuscularSiteOptions() {
        val options = AdministrationRoute.INTRAMUSCULAR.siteOptions
        assertEquals(8, options.size)
        assertTrue(options.contains("Left Deltoid"))
        assertTrue(options.contains("Right Vastus Lateralis"))
    }
```

**Lines 204-207** — Update expected count for SQ:
```kotlin
    @Test
    fun testSubcutaneous_hasSubcutaneousSiteOptions() {
        val options = AdministrationRoute.SUBCUTANEOUS.siteOptions
        assertEquals(8, options.size)
        assertTrue(options.contains("Left abdomen"))
        assertTrue(options.contains("Right thigh"))
    }
```

**Lines 229-231** — Update constant name and count:
```kotlin
    @Test
    fun testIntravenousSiteOptionsCount() {
        assertEquals(10, AdministrationRoute.INTRAVENOUS_SITE_OPTIONS.size)
    }
```

Add new tests after line 231:

```kotlin
    @Test
    fun testIntramuscularSiteOptionsCount() {
        assertEquals(8, AdministrationRoute.INTRAMUSCULAR_SITE_OPTIONS.size)
    }

    @Test
    fun testSubcutaneousSiteOptionsCount() {
        assertEquals(8, AdministrationRoute.SUBCUTANEOUS_SITE_OPTIONS.size)
    }

    @Test
    fun testIntramuscularSitesAreMuscleBased() {
        val options = AdministrationRoute.INTRAMUSCULAR_SITE_OPTIONS
        assertTrue(options.all { it.contains("Deltoid") || it.contains("Vastus Lateralis") || it.contains("Ventrogluteal") || it.contains("Dorsogluteal") })
    }

    @Test
    fun testSubcutaneousSitesAreTissueBased() {
        val options = AdministrationRoute.SUBCUTANEOUS_SITE_OPTIONS
        assertTrue(options.all { it.contains("abdomen") || it.contains("thigh") || it.contains("upper arm") || it.contains("lower back") })
    }

    @Test
    fun testIntravenousSiteOptionsAreVeinBased() {
        val options = AdministrationRoute.INTRAVENOUS_SITE_OPTIONS
        assertTrue(options.all { it.contains("Median Cubital") || it.contains("Cephalic") || it.contains("Basilic") || it.contains("Dorsal Hand") || it.contains("Median Antebrachial") })
    }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "foo.pilz.freaklog.data.substances.AdministrationRouteTest"`

Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/data/substances/AdministrationRoute.kt app/src/test/java/foo/pilz/freaklog/data/substances/AdministrationRouteTest.kt
git commit -m "feat: add IM and SQ injection site options with route-specific lists"
```

---

### Task 3: Add site aliases for new IM and SQ sites in DefaultAliases

**Files:**
- Modify: `freakquery-android/src/main/java/com/ndm4/freakquery/DefaultAliases.kt:34-45`

- [ ] **Step 1: Add alias mappings for IM and SQ sites**

In `DefaultAliases.kt`, replace the `site` map (lines 34-45) with the expanded version:

```kotlin
    val site: Map<String, String> = normalized(
        mapOf(
            "l nostril" to "left nostril",
            "left nostril" to "left nostril",
            "r nostril" to "right nostril",
            "right nostril" to "right nostril",
            "both nose" to "both nostrils",
            "both nostrils" to "both nostrils",
            "left hand" to "left dorsal hand",
            "right hand" to "right dorsal hand",
            // IM sites
            "left delt" to "left deltoid",
            "left deltoid" to "left deltoid",
            "right delt" to "right deltoid",
            "right deltoid" to "right deltoid",
            "left quad" to "left vastus lateralis",
            "left vastus lateralis" to "left vastus lateralis",
            "right quad" to "right vastus lateralis",
            "right vastus lateralis" to "right vastus lateralis",
            "left vg" to "left ventrogluteal",
            "left ventrogluteal" to "left ventrogluteal",
            "left ventro gluteal" to "left ventrogluteal",
            "right vg" to "right ventrogluteal",
            "right ventrogluteal" to "right ventrogluteal",
            "right ventro gluteal" to "right ventrogluteal",
            "left dg" to "left dorsogluteal",
            "left dorsogluteal" to "left dorsogluteal",
            "left dorso gluteal" to "left dorsogluteal",
            "right dg" to "right dorsogluteal",
            "right dorsogluteal" to "right dorsogluteal",
            "right dorso gluteal" to "right dorsogluteal",
            "left glute" to "left dorsogluteal",
            "right glute" to "right dorsogluteal",
            // SQ sites
            "left ab" to "left abdomen",
            "left abdomen" to "left abdomen",
            "right ab" to "right abdomen",
            "right abdomen" to "right abdomen",
            "left sq thigh" to "left thigh",
            "right sq thigh" to "right thigh",
            "left sq arm" to "left upper arm",
            "right sq arm" to "right upper arm",
            "left sq back" to "left lower back",
            "right sq back" to "right lower back",
        )
    )
```

- [ ] **Step 2: Run freakquery tests to verify aliases work**

Run: `./gradlew :freakquery-android:testDebugUnitTest`

Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add freakquery-android/src/main/java/com/ndm4/freakquery/DefaultAliases.kt
git commit -m "feat: add IM and SQ site aliases for freakquery parser"
```
