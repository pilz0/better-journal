# Stats and AI Settings Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the low-value More Charts and standalone Dosage stats pages, make the Stats substance drill-down readable and useful, and make the AI assistant an explicit default-off setting with provider configuration in a submenu.

**Architecture:** Flatten the Stats navigation so `StatsScreenRoute` links only to tolerance and per-substance detail. Consolidate dosage statistics into `SubstanceCompanionScreen` with pure helper/model code that is easy to unit-test, then gate AI at the preference, settings, experience-screen, and repository boundaries.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Compose typed routes, Hilt ViewModels, DataStore Preferences, kotlinx.coroutines Flow, JUnit 4, AssertK, Turbine, Robolectric where Android runtime behavior is needed.

---

## Scope and implementation choices

This plan uses existing Compose Material3 components and the existing Canvas-based chart implementation. It does not add Vico or Compose-Settings because the repository already has chart/settings components and the requested fixes can be delivered without new dependencies.

Best-practice research to apply during implementation:

- Flatten low-signal chart destinations into the existing dashboard/detail hierarchy; charts should earn their place by making trends easier to understand.
- Use progressive disclosure: top-level Stats shows ranked summaries, substance detail shows dosage/frequency/drill-down.
- Model stats calculations outside composables so they can be unit-tested.
- Make AI opt-in explicit, default off, and explain that enabling it sends journal context to the configured Gemini API.
- Put settings rows behind one accessible row per control: parent row handles toggle/click behavior, icons that are decorative use `contentDescription = null`, and chart canvases expose meaningful semantics.

Security note: the existing app stores Gemini API key/model in DataStore. The implementation below keeps that storage to avoid expanding scope and dependency surface. A separate follow-up can migrate the key to encrypted storage by adding `androidx.security:security-crypto` and a migration path from the current DataStore key.

## File structure

### Remove More Charts

- Modify `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt`: remove `MoreChartsRoute`, its import, and `navigateToMoreCharts`.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt`: remove the More Charts icon/action parameter.
- Delete `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsScreen.kt`.
- Delete `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsViewModel.kt`.
- Keep `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/ExperienceStatsHelper.kt` only if still referenced by tests or other code; otherwise delete it and its test.
- Update or delete `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/charts/ExperienceStatsHelperTest.kt` depending on whether `ExperienceStatsHelper.kt` remains.

### Consolidate substance dosage stats

- Create `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelper.kt`: pure calculations for bucketed dose data, unknown-dose diagnostics, mixed-unit diagnostics, and summary metrics.
- Create `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelperTest.kt`: JVM tests for the helper.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionViewModel.kt`: replace private dosage/stat calculations with the helper and expose a single `SubstanceDosageStatsModel`.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionScreen.kt`: restructure into summary cards, dosage chart card, warning/unknown-dose cards, tolerance, and history.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/DosageBarChart.kt`: add chart semantics and any small formatting helpers needed by the new detail layout.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceFrequencySection.kt`: keep as a compact component or fold into the new summary cards.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionScreenPreviewProvider.kt`: add preview data that exercises mixed units, unknown doses, non-empty chart buckets, and history.
- Delete `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatScreen.kt`.
- Delete `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatViewModel.kt`.
- Delete or migrate `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelper.kt`.
- Delete or migrate `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelperTest.kt`.

### Improve top-level Stats substance rows

- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsViewModel.kt`: add last-used and clearer dose/status fields to `StatItem`; keep logic pure enough to unit-test if extracted.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt`: replace dense rows with card/list rows that show substance name, ingestion/session counts, dose status, route summary, and last used.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsPreviewProvider.kt`: include rows with known dose, mixed/unknown dose, multiple routes, and old/recent last-used dates.

### Add default-off AI assistant setting and submenu

- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt`: add `AI_ASSISTANT_ENABLED`, `aiAssistantEnabledFlow`, and `saveAiAssistantEnabled`.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsViewModel.kt`: expose `aiAssistantEnabledFlow` and `saveAiAssistantEnabled`.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt`: replace inline API key/model fields with a default-off switch and a navigation row to a submenu.
- Create `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/AiAssistantSettingsScreen.kt`: submenu with API key, model name, explanatory privacy text, and back navigation.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt`: add `AiAssistantSettingsRoute`.
- Modify `app/src/main/java/foo/pilz/freaklog/ui/tabs/journal/experience/ExperienceScreen.kt`: show the SmartToy action only when AI assistant is enabled.
- Modify `app/src/main/java/foo/pilz/freaklog/data/ai/AiChatbotRepository.kt`: return an explicit `AiChatSessionResult.Disabled` before checking the API key.
- Modify `app/src/main/java/foo/pilz/freaklog/data/ai/AiChatViewModel.kt`: surface disabled, missing-key, failed, and ready session states distinctly.
- Add tests under `app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/` or `app/src/test/java/foo/pilz/freaklog/data/ai/` for the new preference and repository guard.

## Subagent strategy

Use `superpowers:subagent-driven-development` during execution. Dispatch fresh, focused subagents after baseline verification in dependency order:

| Subagent | Owns | Must not touch |
|---|---|---|
| `substance-dosage-redesign` | Helper/model tests, ViewModel wiring, substance detail UI, migration of behavior from the old dosage helper | Settings and AI files |
| `stats-navigation-removal` | Remove More Charts and delete standalone Dosage stats routes/screens after dosage behavior is migrated | AI settings files and substance dosage helper logic |
| `stats-summary-polish` | Top-level Stats row model/UI/preview polish after route deletion compiles | Navigation route deletion |
| `ai-settings-gating` | Default-off preference, settings submenu, experience button/repository guard | Stats files |
| `verification-review` | Review diffs, run targeted/full checks, identify conflicts | Direct code edits unless explicitly assigned |

Phase 1 can run `substance-dosage-redesign` and `ai-settings-gating` in parallel because they touch independent files. Phase 2 runs `stats-navigation-removal` after the dosage behavior has moved out of `ui/tabs/stats/dosage/`. Phase 3 runs `stats-summary-polish` after the Stats graph and screen compile. Phase 4 runs `verification-review`.

Each subagent returns: files changed, tests run, failing/passing status, and any conflicts with another task. The lead session integrates after every subagent, runs the relevant targeted Gradle command, and only then dispatches dependent work.

## Tasks

### Task 1: Baseline verification and dispatch setup

**Files:**
- Read: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionScreen.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt`

- [ ] **Step 1: Confirm a clean starting point**

Run:

```bash
git --no-pager status --short
```

Expected: no output, or only unrelated user changes that are documented before starting.

- [ ] **Step 2: Run current targeted stats tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If it fails before edits, record the failing tests and do not treat them as caused by this work.

- [ ] **Step 3: Run current targeted AI/settings tests if any exist**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.ai.*" --tests "foo.pilz.freaklog.ui.tabs.settings.*" --no-daemon
```

Expected: either `BUILD SUCCESSFUL` or Gradle reporting no matching tests. If Gradle fails because no tests match, continue and add the tests in later tasks.

- [ ] **Step 4: Dispatch implementation subagents**

Use `superpowers:subagent-driven-development` and dispatch these prompts after the baseline:

```text
Subagent substance-dosage-redesign:
Create a pure helper/model for substance dosage stats, migrate existing dosage-helper behavior into it, update SubstanceCompanionViewModel and SubstanceCompanionScreen to use the consolidated model, and add JVM tests. Do not edit AI/settings files. Return files changed and commands run.

Subagent ai-settings-gating:
Add a default-off AI assistant preference, move Gemini API key/model into a Settings submenu, gate the experience-screen AI button and AiChatbotRepository session creation, and add targeted tests. Do not edit Stats files. Return files changed and commands run.
```

After both Phase 1 subagents finish and their changes are integrated, dispatch:

```text
Subagent stats-navigation-removal:
Remove MoreChartsRoute and the standalone DosageStatRoute from the Stats graph, delete the unreachable screens/viewmodels/tests after confirming dosage behavior has migrated to SubstanceDosageStatsHelper, and update StatsScreen signatures/previews so the app compiles. Do not edit AI/settings files or substance dosage helper logic. Return files changed and commands run.
```

After `stats-navigation-removal` compiles and its targeted tests pass, dispatch:

```text
Subagent stats-summary-polish:
Improve the top-level Stats substance rows with clearer card summaries after stats-navigation-removal compiles. Do not edit settings, AI, or deleted route files. Return files changed and commands run.
```

- [ ] **Step 5: Commit only if the baseline notes are useful**

Do not commit anything in this task unless a baseline test failure needs to be documented in the repository. If a documentation-only baseline commit is needed, use:

```bash
git add docs/superpowers/plans/2026-05-08-stats-ai-settings-cleanup.md
git commit -m "docs: plan stats and ai settings cleanup

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Remove More Charts navigation and dead code

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt`
- Delete: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsScreen.kt`
- Delete: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsViewModel.kt`
- Delete if unused: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/ExperienceStatsHelper.kt`
- Delete if helper deleted: `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/charts/ExperienceStatsHelperTest.kt`

- [ ] **Step 1: Write the failing reference check**

Run:

```bash
rg "MoreCharts|More charts|navigateToMoreCharts" app/src/main/java app/src/test/java
```

Expected before implementation: matches in `statsGraph.kt`, `StatsScreen.kt`, `MoreChartsScreen.kt`, and `MoreChartsViewModel.kt`.

- [ ] **Step 2: Remove More Charts from `statsGraph.kt`**

Change `statsGraph.kt` so the Stats root only passes substance and tolerance navigation:

```kotlin
import foo.pilz.freaklog.ui.tabs.stats.StatsScreen
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.SubstanceCompanionScreen
import foo.pilz.freaklog.ui.tabs.stats.tolerance.ToleranceChartScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.statsGraph(navController: NavHostController) {
    navigation<StatsTopLevelRoute>(
        startDestination = StatsScreenRoute,
    ) {
        composableWithTransitions<StatsScreenRoute> {
            StatsScreen(
                navigateToSubstanceCompanion = { substanceName, consumerName ->
                    navController.navigate(
                        SubstanceCompanionRoute(
                            substanceName = substanceName,
                            consumerName = consumerName
                        )
                    )
                },
                navigateToToleranceChart = {
                    navController.navigate(ToleranceChartRoute)
                }
            )
        }
        composableWithTransitions<SubstanceCompanionRoute> {
            SubstanceCompanionScreen()
        }
        composableWithTransitions<ToleranceChartRoute> {
            ToleranceChartScreen()
        }
    }
}

@Serializable
object StatsScreenRoute

@Serializable
data class SubstanceCompanionRoute(val substanceName: String, val consumerName: String?)

@Serializable
object ToleranceChartRoute
```

- [ ] **Step 3: Remove More Charts parameters/actions from `StatsScreen.kt`**

Update the public composable signatures:

```kotlin
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    navigateToSubstanceCompanion: (substanceName: String, consumerName: String?) -> Unit,
    navigateToToleranceChart: () -> Unit = {},
) {
    StatsScreen(
        navigateToSubstanceCompanion = navigateToSubstanceCompanion,
        navigateToToleranceChart = navigateToToleranceChart,
        onTapOption = viewModel::onTapOption,
        statsModel = viewModel.statsModelFlow.collectAsState().value,
        onChangeConsumerName = viewModel::onChangeConsumer,
        consumerNamesSorted = viewModel.sortedConsumerNamesFlow.collectAsState().value,
    )
}
```

Update the stateless overload:

```kotlin
fun StatsScreen(
    navigateToSubstanceCompanion: (substanceName: String, consumerName: String?) -> Unit,
    navigateToToleranceChart: () -> Unit = {},
    onTapOption: (option: TimePickerOption) -> Unit,
    statsModel: StatsModel,
    onChangeConsumerName: (String?) -> Unit,
    consumerNamesSorted: List<String>,
)
```

Remove the `Icons.Outlined.Insights` import and the `IconButton` whose content description is `"More charts"`.

- [ ] **Step 4: Delete More Charts files**

Delete:

```text
app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsScreen.kt
app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts/MoreChartsViewModel.kt
```

Then check whether the helper is now unused:

```bash
rg "ExperienceStatsHelper|SubstanceFraction" app/src/main/java app/src/test/java
```

If only `ExperienceStatsHelper.kt` and `ExperienceStatsHelperTest.kt` match, delete both files too.

- [ ] **Step 5: Verify references are gone**

Run:

```bash
rg "MoreCharts|More charts|navigateToMoreCharts" app/src/main/java app/src/test/java
```

Expected: no matches.

- [ ] **Step 6: Run compile check for Stats**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/charts \
  app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/charts
git commit -m "refactor: remove redundant stats chart pages

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 3: Consolidate dosage calculations into a pure substance helper

**Files:**
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelper.kt`
- Create: `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelperTest.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/DosageBarChart.kt`
- Read: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelper.kt`

- [ ] **Step 1: Write failing helper tests**

Create `SubstanceDosageStatsHelperTest.kt` with these tests:

```kotlin
package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SubstanceDosageStatsHelperTest {
    private val now = Instant.parse("2026-04-29T12:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun ingestion(
        id: Int,
        time: String,
        dose: Double?,
        units: String? = "mg",
        experienceId: Int = id,
        route: AdministrationRoute = AdministrationRoute.ORAL,
    ) = Ingestion(
        id = id,
        substanceName = "MDMA",
        time = Instant.parse(time),
        administrationRoute = route,
        dose = dose,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = units,
        experienceId = experienceId,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    @Test
    fun `build returns buckets summary unknown count and mixed units for selected range`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0, "mg", experienceId = 1),
                ingestion(2, "2026-04-29T09:00:00Z", null, "mg", experienceId = 1),
                ingestion(3, "2026-04-20T08:00:00Z", 50.0, "ug", experienceId = 2),
                ingestion(4, "2026-01-01T08:00:00Z", 200.0, "mg", experienceId = 3),
            ),
            range = DosageTimeRange.DAYS_30,
            now = now,
            zone = zone,
        )

        assertThat(model.buckets.size).isEqualTo(30)
        assertThat(model.summary.totalSessions).isEqualTo(2)
        assertThat(model.summary.totalKnownDose).isEqualTo(null)
        assertThat(model.summary.averageKnownDosePerSession).isEqualTo(null)
        assertThat(model.summary.unknownDoseCount).isEqualTo(1)
        assertThat(model.summary.unitsUsed).containsExactly("mg", "ug")
        assertThat(model.hasMixedUnits).isTrue()
    }

    @Test
    fun `average dose per session ignores unknown doses and counts distinct experiences`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0, experienceId = 1),
                ingestion(2, "2026-04-29T09:00:00Z", 50.0, experienceId = 1),
                ingestion(3, "2026-04-28T08:00:00Z", null, experienceId = 2),
            ),
            range = DosageTimeRange.DAYS_30,
            now = now,
            zone = zone,
        )

        assertThat(model.summary.totalSessions).isEqualTo(2)
        assertThat(model.summary.averageKnownDosePerSession).isEqualTo(75.0)
        assertThat(model.summary.unknownDoseCount).isEqualTo(1)
    }

    @Test
    fun `all range creates at least one bucket and includes old data`() {
        val model = SubstanceDosageStatsHelper.build(
            ingestions = listOf(ingestion(1, "2024-04-29T08:00:00Z", 100.0)),
            range = DosageTimeRange.ALL,
            now = now,
            zone = zone,
        )

        assertThat(model.buckets.isNotEmpty()).isTrue()
        assertThat(model.summary.totalKnownDose).isEqualTo(100.0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.substancecompanion.SubstanceDosageStatsHelperTest" --no-daemon
```

Expected: fail because `SubstanceDosageStatsHelper` and `SubstanceDosageStatsModel` do not exist.

- [ ] **Step 3: Implement the helper model**

Create `SubstanceDosageStatsHelper.kt`:

```kotlin
package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SubstanceDosageStatsModel(
    val buckets: List<DosageBucket>,
    val summary: SubstanceDosageSummary,
    val hasMixedUnits: Boolean,
)

data class SubstanceDosageSummary(
    val totalSessions: Int,
    val totalKnownDose: Double?,
    val averageKnownDosePerSession: Double?,
    val peakKnownDose: Double?,
    val unknownDoseCount: Int,
    val unitsUsed: List<String>,
    val longestGapDays: Int?,
    val currentStreakWeeks: Int,
)

object SubstanceDosageStatsHelper {
    fun build(
        ingestions: List<Ingestion>,
        range: DosageTimeRange,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SubstanceDosageStatsModel {
        val rangeStart = when (range) {
            DosageTimeRange.ALL -> ingestions.minOfOrNull { it.time } ?: now
            else -> now.atZone(zone).minus(range.period ?: Period.ZERO).toInstant()
        }
        val visible = ingestions
            .filter { it.time >= rangeStart && it.time < now }
            .sortedBy { it.time }
        val buckets = buildBuckets(visible, range, rangeStart, now, zone)
        val unitsUsed = visible.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct()
        val sessions = visible.map { it.experienceId }.toSet()
        val singleUnit = unitsUsed.singleOrNull()
        val knownDoses = visible
            .filter { singleUnit != null && it.units == singleUnit }
            .mapNotNull { it.dose }
        val totalKnownDose = singleUnit?.let { knownDoses.sum() }
        val summary = SubstanceDosageSummary(
            totalSessions = sessions.size,
            totalKnownDose = totalKnownDose,
            averageKnownDosePerSession = totalKnownDose?.let {
                if (sessions.isEmpty()) 0.0 else it / sessions.size
            },
            peakKnownDose = singleUnit?.let { knownDoses.maxOrNull() ?: 0.0 },
            unknownDoseCount = visible.count { it.dose == null },
            unitsUsed = unitsUsed,
            longestGapDays = longestGapDays(visible, zone),
            currentStreakWeeks = currentStreakWeeks(visible, now),
        )
        return SubstanceDosageStatsModel(
            buckets = buckets,
            summary = summary,
            hasMixedUnits = unitsUsed.size > 1,
        )
    }

    private fun buildBuckets(
        ingestions: List<Ingestion>,
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
    ): List<DosageBucket> {
        val config = bucketConfig(range, start, end, zone)
        val bucketCount = bucketCount(range, start, end, zone, config.step)
        val labelFormat = DateTimeFormatter.ofPattern(config.labelPattern)
        val fullDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val unitsUsed = ingestions.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct()
        val singleUnit = unitsUsed.singleOrNull()
        val buckets = ArrayDeque<DosageBucket>()
        var bucketEnd = end.atZone(zone)
        repeat(bucketCount) {
            val bucketStart = bucketEnd.minus(config.step)
            val inBucket = ingestions.filter {
                val instant = it.time
                instant >= bucketStart.toInstant() && instant < bucketEnd.toInstant()
            }
            buckets.addFirst(
                DosageBucket(
                    label = labelFormat.format(bucketStart),
                    fullDateText = fullDateFormat.format(bucketStart),
                    totalDose = if (singleUnit == null) 0.0 else {
                        inBucket.filter { it.units == singleUnit }.sumOf { it.dose ?: 0.0 }
                    },
                    sessionCount = inBucket.map { it.experienceId }.toSet().size,
                    unit = singleUnit.orEmpty(),
                )
            )
            bucketEnd = bucketStart
        }
        return buckets.toList()
    }

    private data class BucketConfig(val step: Period, val labelPattern: String)

    private fun bucketConfig(
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
    ): BucketConfig = when (range) {
        DosageTimeRange.DAYS_30 -> BucketConfig(Period.ofDays(1), "dd")
        DosageTimeRange.WEEKS_26 -> BucketConfig(Period.ofWeeks(1), "dd.MM")
        DosageTimeRange.MONTHS_12 -> BucketConfig(Period.ofMonths(1), "MMM")
        DosageTimeRange.ALL -> {
            val days = ChronoUnit.DAYS.between(start.atZone(zone), end.atZone(zone)).coerceAtLeast(1)
            val months = ChronoUnit.MONTHS.between(start.atZone(zone), end.atZone(zone))
            when {
                days <= 60 -> BucketConfig(Period.ofDays(1), "dd.MM")
                months <= 18 -> BucketConfig(Period.ofWeeks(1), "dd.MM")
                else -> BucketConfig(Period.ofMonths(1), "MMM yy")
            }
        }
    }

    private fun bucketCount(
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
        step: Period,
    ): Int = when (range) {
        DosageTimeRange.DAYS_30 -> 30
        DosageTimeRange.WEEKS_26 -> 26
        DosageTimeRange.MONTHS_12 -> 12
        DosageTimeRange.ALL -> {
            var count = 0
            var cursor = end.atZone(zone)
            while (cursor.toInstant().isAfter(start) && count < 200) {
                cursor = cursor.minus(step)
                count++
            }
            count.coerceAtLeast(1)
        }
    }

    private fun longestGapDays(ingestions: List<Ingestion>, zone: ZoneId): Int? {
        val sessionTimes = ingestions
            .groupBy { it.experienceId }
            .values
            .map { group -> group.minOf { it.time } }
            .sorted()
        if (sessionTimes.size < 2) return null
        return sessionTimes.zipWithNext { previous, next ->
            ChronoUnit.DAYS.between(previous.atZone(zone), next.atZone(zone)).toInt()
        }.maxOrNull()
    }

    private fun currentStreakWeeks(ingestions: List<Ingestion>, now: Instant): Int {
        val currentEpochMs = now.toEpochMilli()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val sessionWeekIndices = ingestions
            .map { it.time.toEpochMilli() }
            .filter { it <= currentEpochMs }
            .map { ((currentEpochMs - it) / weekMs).toInt() }
            .toHashSet()
        var streak = 0
        for (weekIndex in 0 until 104) {
            if (weekIndex in sessionWeekIndices) streak++ else break
        }
        return streak
    }
}
```

- [ ] **Step 4: Run helper tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.substancecompanion.SubstanceDosageStatsHelperTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelper.kt \
  app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceDosageStatsHelperTest.kt
git commit -m "test: cover consolidated substance dosage stats

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 4: Wire consolidated dosage stats into the substance detail ViewModel

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionViewModel.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt`
- Delete: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatScreen.kt`
- Delete: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatViewModel.kt`
- Delete: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelper.kt`
- Delete: `app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelperTest.kt`

- [ ] **Step 1: Verify current standalone dosage references fail the target check**

Run:

```bash
rg "DosageStatRoute|DosageStatScreen|DosageStatViewModel|DosageStatHelper|navigateToDosageStat" app/src/main/java app/src/test/java
```

Expected before implementation: matches in stats graph, dosage package, and substance detail wiring.

- [ ] **Step 2: Replace dosage flows in `SubstanceCompanionViewModel.kt`**

Keep the public time-range/metric toggles, but replace `dosageChartDataFlow`, `chartSummaryFlow`, `frequencyFlow`, and `hasMixedUnitsFlow` with one model:

```kotlin
val dosageStatsFlow: StateFlow<SubstanceDosageStatsModel> =
    combine(allIngestionsFlow, selectedTimeRange, currentTimeFlow) { ingestions, timeRange, currentTime ->
        SubstanceDosageStatsHelper.build(
            ingestions = ingestions.map { it.ingestion },
            range = timeRange,
            now = currentTime,
            zone = ZoneId.systemDefault(),
        )
    }.stateIn(
        initialValue = SubstanceDosageStatsModel(
            buckets = emptyList(),
            summary = SubstanceDosageSummary(
                totalSessions = 0,
                totalKnownDose = null,
                averageKnownDosePerSession = null,
                peakKnownDose = null,
                unknownDoseCount = 0,
                unitsUsed = emptyList(),
                longestGapDays = null,
                currentStreakWeeks = 0,
            ),
            hasMixedUnits = false,
        ),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )
```

If `frequencyFlow` remains useful as its own UI section, keep it. If the redesigned screen uses only `dosageStatsFlow.summary`, remove `frequencyFlow` and `SubstanceFrequency`.

- [ ] **Step 3: Simplify Stats graph substance route**

Remove `navigateToDosageStat` from `SubstanceCompanionScreen` call:

```kotlin
composableWithTransitions<SubstanceCompanionRoute> {
    SubstanceCompanionScreen()
}
```

Delete `DosageStatRoute` from the bottom of `statsGraph.kt`.

- [ ] **Step 4: Delete standalone dosage package files**

Delete:

```text
app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatScreen.kt
app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatViewModel.kt
app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelper.kt
app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/dosage/DosageStatHelperTest.kt
```

- [ ] **Step 5: Verify references are gone**

Run:

```bash
rg "DosageStatRoute|DosageStatScreen|DosageStatViewModel|DosageStatHelper|navigateToDosageStat" app/src/main/java app/src/test/java
```

Expected: no matches.

- [ ] **Step 6: Run stats tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/statsGraph.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/dosage \
  app/src/test/java/foo/pilz/freaklog/ui/tabs/stats/dosage
git commit -m "refactor: consolidate dosage stats into substance detail

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 5: Redesign the substance detail stats UI

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/DosageBarChart.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion/SubstanceCompanionScreenPreviewProvider.kt`

- [ ] **Step 1: Write a UI reference check for removed clutter**

Run:

```bash
rg "Dosage stats|Heads-up: ingestions for this substance use multiple unit strings|Average|Trend line" app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion
```

Expected before implementation: matches in `SubstanceCompanionScreen.kt`.

- [ ] **Step 2: Change `SubstanceCompanionScreen` parameters**

Replace separate dosage-related parameters with one model:

```kotlin
fun SubstanceCompanionScreen(
    substanceCompanion: SubstanceCompanion,
    ingestionBursts: List<IngestionsBurst>,
    tolerance: Tolerance?,
    crossTolerances: List<String>,
    consumerName: String? = null,
    dosageStats: SubstanceDosageStatsModel = SubstanceDosageStatsModel(
        buckets = emptyList(),
        summary = SubstanceDosageSummary(
            totalSessions = 0,
        totalKnownDose = null,
        averageKnownDosePerSession = null,
        peakKnownDose = null,
            unknownDoseCount = 0,
            unitsUsed = emptyList(),
            longestGapDays = null,
            currentStreakWeeks = 0,
        ),
        hasMixedUnits = false,
    ),
    selectedTimeRange: DosageTimeRange = DosageTimeRange.WEEKS_26,
    onTimeRangeSelected: (DosageTimeRange) -> Unit = {},
    showAverage: Boolean = false,
    onToggleShowAverage: (Boolean) -> Unit = {},
    showTrendLine: Boolean = false,
    onToggleShowTrendLine: (Boolean) -> Unit = {},
    selectedMetric: DosageMetric = DosageMetric.TOTAL_DOSE,
    onMetricSelected: (DosageMetric) -> Unit = {},
    doseThresholds: DoseThresholds? = null,
)
```

Remove the top-bar `TextButton` labeled `"Dosage stats"`.

- [ ] **Step 3: Add compact summary cards**

Add this composable in `SubstanceCompanionScreen.kt`:

```kotlin
@Composable
private fun SubstanceSummaryCards(summary: SubstanceDosageSummary, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCard(
            label = "Sessions",
            value = summary.totalSessions.toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Known total",
            value = summary.totalKnownDose?.takeIf { it > 0 }?.let {
                "${formatSiValue(it)} $unit".trim()
            } ?: "Mixed/unknown",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Avg/session",
            value = summary.averageKnownDosePerSession?.takeIf { it > 0 }?.let {
                "${formatSiValue(it)} $unit".trim()
            } ?: "Mixed/unknown",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 4: Add explicit diagnostics cards**

Replace warning text with cards that do not claim mixed units are safe to sum:

```kotlin
@Composable
private fun DosageDiagnostics(stats: SubstanceDosageStatsModel) {
    if (stats.hasMixedUnits) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Mixed units: ${stats.summary.unitsUsed.joinToString(", ")}. Dose totals are shown as logged and should not be compared as one unit.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (stats.summary.unknownDoseCount > 0) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${stats.summary.unknownDoseCount} ingestion${if (stats.summary.unknownDoseCount == 1) "" else "s"} in this range have no dose. Known-dose totals exclude them.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 5: Use a single chart card**

Add `DosageChartCard` in `SubstanceCompanionScreen.kt` before `IngestionRowOnSubstanceCompanionScreen`:

```kotlin
@Composable
private fun DosageChartCard(
    substanceCompanion: SubstanceCompanion,
    dosageStats: SubstanceDosageStatsModel,
    selectedTimeRange: DosageTimeRange,
    onTimeRangeSelected: (DosageTimeRange) -> Unit,
    showAverage: Boolean,
    onToggleShowAverage: (Boolean) -> Unit,
    showTrendLine: Boolean,
    onToggleShowTrendLine: (Boolean) -> Unit,
    selectedMetric: DosageMetric,
    onMetricSelected: (DosageMetric) -> Unit,
    doseThresholds: DoseThresholds?,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dosage over time", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DosageTimeRange.values().forEach { range ->
                    FilterChip(
                        selected = selectedTimeRange == range,
                        onClick = { onTimeRangeSelected(range) },
                        label = { Text(range.displayText) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DosageMetric.values().forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { onMetricSelected(metric) },
                        label = { Text(metric.displayText) },
                    )
                }
            }
            DosageBarChart(
                buckets = dosageStats.buckets,
                barColor = substanceCompanion.color.getComposeColor(isSystemInDarkTheme()),
                showAverage = showAverage,
                showTrendLine = showTrendLine,
                metric = if (dosageStats.hasMixedUnits && selectedMetric != DosageMetric.SESSION_COUNT) {
                    DosageMetric.SESSION_COUNT
                } else {
                    selectedMetric
                },
                doseThresholds = if (dosageStats.hasMixedUnits) null else doseThresholds,
                height = 200.dp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Average", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showAverage, onCheckedChange = onToggleShowAverage)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Trend line", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showTrendLine, onCheckedChange = onToggleShowTrendLine)
            }
        }
    }
}
```

Then inside the `LazyColumn`, order items as:

```kotlin
item {
    val unit = dosageStats.summary.unitsUsed.firstOrNull().orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SubstanceSummaryCards(summary = dosageStats.summary, unit = unit)
        DosageDiagnostics(stats = dosageStats)
        DosageChartCard(
            substanceCompanion = substanceCompanion,
            dosageStats = dosageStats,
            selectedTimeRange = selectedTimeRange,
            onTimeRangeSelected = onTimeRangeSelected,
            showAverage = showAverage,
            onToggleShowAverage = onToggleShowAverage,
            showTrendLine = showTrendLine,
            onToggleShowTrendLine = onToggleShowTrendLine,
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected,
            doseThresholds = doseThresholds,
        )
    }
}
```

Keep tolerance and history below this chart block.

- [ ] **Step 6: Add chart semantics**

In `DosageBarChart.kt`, import semantics and add a content description:

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

Apply to the chart `Box`:

```kotlin
val chartDescription = remember(buckets, metric) {
    val nonEmpty = buckets.count { it.totalDose > 0 || it.sessionCount > 0 }
    "${metric.displayText} chart with ${buckets.size} buckets and $nonEmpty non-empty buckets"
}

Box(
    modifier = modifier
        .fillMaxWidth()
        .height(height)
        .padding(horizontal = 4.dp)
        .semantics { contentDescription = chartDescription }
) {
    // Keep the existing Canvas drawing block here.
}
```

- [ ] **Step 7: Update preview data**

Add preview buckets and stats in `SubstanceCompanionScreenPreviewProvider.kt` so the preview shows:

```kotlin
val previewStats = SubstanceDosageStatsModel(
    buckets = listOf(
        DosageBucket("W1", "01 Apr 2026", 120.0, 1, "mg"),
        DosageBucket("W2", "08 Apr 2026", 0.0, 0, "mg"),
        DosageBucket("W3", "15 Apr 2026", 180.0, 2, "mg"),
    ),
    summary = SubstanceDosageSummary(
        totalSessions = 3,
        totalKnownDose = 300.0,
        averageKnownDosePerSession = 100.0,
        peakKnownDose = 180.0,
        unknownDoseCount = 1,
        unitsUsed = listOf("mg"),
        longestGapDays = 14,
        currentStreakWeeks = 1,
    ),
    hasMixedUnits = false,
)
```

- [ ] **Step 8: Run targeted tests and compile**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/substancecompanion
git commit -m "feat: simplify substance dosage stats view

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 6: Improve top-level Stats substance rows

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsPreviewProvider.kt`

- [ ] **Step 1: Add fields to `StatItem`**

In `StatsViewModel.kt`, extend `StatItem`:

```kotlin
data class StatItem(
    val substanceName: String,
    val color: AdaptiveColor,
    val experienceCount: Int,
    val ingestionCount: Int,
    val routeCounts: List<RouteCount>,
    val totalDose: TotalDose?,
    val lastUsed: Instant?,
    val unknownDoseCount: Int,
    val unitNames: List<String>,
)
```

- [ ] **Step 2: Populate the new fields**

When building `StatItem`, add:

```kotlin
val ingestions = groupedIngestions.map { it.ingestion }
StatItem(
    substanceName = name,
    color = oneCompanion.color,
    experienceCount = experienceCounts,
    ingestionCount = groupedIngestions.size,
    routeCounts = getRouteCounts(ingestions),
    totalDose = getTotalDose(groupedIngestions),
    lastUsed = ingestions.maxOfOrNull { it.time },
    unknownDoseCount = ingestions.count { it.dose == null && it.customUnitId == null },
    unitNames = ingestions.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct(),
)
```

- [ ] **Step 3: Extract a card row composable**

In `StatsScreen.kt`, add imports:

```kotlin
import java.time.ZoneId
import java.time.format.DateTimeFormatter
```

Then add:

```kotlin
@Composable
private fun SubstanceStatCard(
    stat: StatItem,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = stat.color.getComposeColor(isDarkTheme),
                modifier = Modifier.size(width = 12.dp, height = 56.dp),
            ) {}
            Column(modifier = Modifier.weight(1f)) {
                Text(stat.substanceName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${stat.ingestionCount} ingestion${if (stat.ingestionCount == 1) "" else "s"} · " +
                        "${stat.experienceCount} experience${if (stat.experienceCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val routes = stat.routeCounts.joinToString(" · ") {
                    "${it.administrationRoute.displayText.lowercase()} ${it.count}x"
                }
                if (routes.isNotBlank()) {
                    Text(
                        text = routes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                stat.lastUsed?.let { lastUsed ->
                    val formatted = DateTimeFormatter.ofPattern("dd MMM yyyy")
                        .format(lastUsed.atZone(ZoneId.systemDefault()))
                    Text(
                        text = "Last used $formatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stat.totalDose?.let(::formatTotalDose) ?: "Dose varies",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stat.unknownDoseCount > 0) {
                    Text(
                        text = "${stat.unknownDoseCount} unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTotalDose(totalDose: TotalDose): String {
    val value = totalDose.dose.toReadableString()
    val uncertainty = totalDose.estimatedDoseStandardDeviation?.let { "±${it.toReadableString()}" }.orEmpty()
    val estimatePrefix = if (totalDose.isEstimate && totalDose.estimatedDoseStandardDeviation == null) "~" else ""
    return "$estimatePrefix$value$uncertainty ${totalDose.units}"
}
```

- [ ] **Step 4: Replace the old row body**

Inside `LazyColumn`, replace the manual `Row` and right-column text with:

```kotlin
items(statsModel.statItems, key = { it.substanceName }) { subStat ->
    SubstanceStatCard(
        stat = subStat,
        isDarkTheme = isDarkTheme,
        onClick = {
            performHaptic(HapticType.SELECTION)
            navigateToSubstanceCompanion(subStat.substanceName, statsModel.consumerName)
        }
    )
}
```

- [ ] **Step 5: Update preview provider**

Ensure `StatsPreviewProvider.kt` constructs every `StatItem` with the new fields:

```kotlin
lastUsed = Instant.parse("2026-04-29T12:00:00Z"),
unknownDoseCount = 1,
unitNames = listOf("mg"),
```

- [ ] **Step 6: Run targeted stats tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsViewModel.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsScreen.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/stats/StatsPreviewProvider.kt
git commit -m "feat: improve stats substance summaries

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 7: Add default-off AI assistant preference

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferencesAiTest.kt`

- [ ] **Step 1: Write failing preference tests**

Create `UserPreferencesAiTest.kt`:

```kotlin
package foo.pilz.freaklog.ui.tabs.settings.combinations

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserPreferencesAiTest {
    @Test
    fun `ai assistant is disabled by default and can be enabled`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.cacheDir, "ai-prefs-${System.nanoTime()}.preferences_pb") },
        )
        val preferences = UserPreferences(dataStore)

        preferences.aiAssistantEnabledFlow.test {
            assertThat(awaitItem()).isEqualTo(false)
            preferences.saveAiAssistantEnabled(true)
            assertThat(awaitItem()).isEqualTo(true)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferencesAiTest" --no-daemon
```

Expected: fail because `aiAssistantEnabledFlow` and `saveAiAssistantEnabled` do not exist.

- [ ] **Step 3: Add DataStore key and accessors**

In `UserPreferences.kt`, add the key:

```kotlin
val AI_ASSISTANT_ENABLED = booleanPreferencesKey("ai_assistant_enabled")
```

Add the flow and saver near the existing AI API key/model accessors:

```kotlin
val aiAssistantEnabledFlow: Flow<Boolean> = dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.AI_ASSISTANT_ENABLED] ?: false
    }

suspend fun saveAiAssistantEnabled(value: Boolean) {
    dataStore.edit { preferences ->
        preferences[PreferencesKeys.AI_ASSISTANT_ENABLED] = value
    }
}
```

- [ ] **Step 4: Expose through SettingsViewModel**

In `SettingsViewModel.kt`, add:

```kotlin
val aiAssistantEnabledFlow = userPreferences.aiAssistantEnabledFlow.stateIn(
    initialValue = false,
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000)
)

fun saveAiAssistantEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiAssistantEnabled(value)
}
```

- [ ] **Step 5: Run preference test**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferencesAiTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferences.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsViewModel.kt \
  app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/combinations/UserPreferencesAiTest.kt
git commit -m "feat: add ai assistant opt-in preference

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 8: Move AI provider settings into a submenu

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt`
- Create: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/AiAssistantSettingsScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt`

- [ ] **Step 1: Verify inline AI fields exist before the change**

Run:

```bash
rg "Gemini API Key|Gemini Model Name|AI chatbot" app/src/main/java/foo/pilz/freaklog/ui/tabs/settings app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt
```

Expected before implementation: matches in `SettingsScreen.kt`.

- [ ] **Step 2: Update SettingsScreen parameters**

Add `navigateToAiAssistantSettings`, `aiAssistantEnabled`, and `saveAiAssistantEnabled` to both overloads. Remove `aiApiKey`, `saveAiApiKey`, `aiModelName`, and `saveAiModelName` from the main Settings screen overloads.

Use this new card:

```kotlin
CardWithTitle(title = "AI assistant", innerPaddingHorizontal = 0.dp) {
    SettingsSwitchRow(
        text = "Enable AI assistant",
        description = "Off by default. Requires a Gemini API key and sends assistant prompts to the configured provider.",
        checked = aiAssistantEnabled,
        onCheckedChange = {
            performHaptic(HapticType.TOGGLE)
            saveAiAssistantEnabled(it)
        }
    )
    HorizontalDivider()
    SettingsButton(
        imageVector = Icons.Outlined.SmartToy,
        text = "AI provider settings"
    ) {
        performHaptic(HapticType.CLICK)
        navigateToAiAssistantSettings()
    }
}
```

- [ ] **Step 3: Create AI submenu screen**

Create `AiAssistantSettingsScreen.kt`:

```kotlin
package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.ai.AiChatbotRepository
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    AiAssistantSettingsScreen(
        navigateBack = navigateBack,
        aiApiKey = viewModel.aiApiKeyFlow.collectAsState().value,
        saveAiApiKey = viewModel::saveAiApiKey,
        aiModelName = viewModel.aiModelNameFlow.collectAsState().value,
        saveAiModelName = viewModel::saveAiModelName,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    aiApiKey: String,
    saveAiApiKey: (String) -> Unit,
    aiModelName: String,
    saveAiModelName: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI assistant") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "The assistant uses your Gemini API key to send relevant journal context to Google's Gemini API when you open or message the assistant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = saveAiApiKey,
                label = { Text("Gemini API key") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = aiModelName.ifBlank { AiChatbotRepository.DEFAULT_MODEL_NAME },
                onValueChange = saveAiModelName,
                label = { Text("Gemini model name") },
                supportingText = {
                    Text("Recommended: ${AiChatbotRepository.DEFAULT_MODEL_NAME}. Settings apply to new chats.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Add settings route**

In `settingsGraph.kt`, import the new screen and wire navigation:

```kotlin
import foo.pilz.freaklog.ui.tabs.settings.AiAssistantSettingsScreen
```

Pass from root Settings:

```kotlin
navigateToAiAssistantSettings = {
    navController.navigate(AiAssistantSettingsRoute)
},
```

Add route:

```kotlin
composableWithTransitions<AiAssistantSettingsRoute> {
    AiAssistantSettingsScreen(navigateBack = navController::popBackStack)
}
```

Add serializable object:

```kotlin
@Serializable
object AiAssistantSettingsRoute
```

- [ ] **Step 5: Verify inline fields moved**

Run:

```bash
rg "Gemini API Key|Gemini Model Name|AI chatbot" app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt
```

Expected: no matches in `SettingsScreen.kt`. The API/model labels should exist only in `AiAssistantSettingsScreen.kt`.

- [ ] **Step 6: Run settings compile check**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/SettingsScreen.kt \
  app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/AiAssistantSettingsScreen.kt \
  app/src/main/java/foo/pilz/freaklog/ui/main/navigation/graphs/settingsGraph.kt
git commit -m "feat: move ai provider settings into submenu

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 9: Gate AI assistant entry and repository creation

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/journal/experience/ExperienceScreen.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/data/ai/AiChatbotRepository.kt`
- Modify: `app/src/main/java/foo/pilz/freaklog/data/ai/AiChatViewModel.kt`
- Create: `app/src/test/java/foo/pilz/freaklog/data/ai/AiChatbotRepositoryPreferenceTest.kt`

- [ ] **Step 1: Write failing repository guard test**

Create `AiChatbotRepositoryPreferenceTest.kt`:

```kotlin
package foo.pilz.freaklog.data.ai

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AiChatbotRepositoryPreferenceTest {
    @Test
    fun `createChatSession returns disabled when assistant is disabled even with api key`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.cacheDir, "ai-repo-${System.nanoTime()}.preferences_pb") },
        )
        val preferences = UserPreferences(dataStore)
        preferences.saveAiApiKey("fake-key")
        preferences.saveAiModelName(AiChatbotRepository.DEFAULT_MODEL_NAME)
        preferences.saveAiAssistantEnabled(false)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repository = AiChatbotRepository(db.experienceDao(), preferences)
            assertThat(repository.createChatSession(experienceId = null))
                .isEqualTo(AiChatSessionResult.Disabled)
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.ai.AiChatbotRepositoryPreferenceTest" --no-daemon
```

Expected: fail because `AiChatSessionResult` does not exist and the disabled preference is not checked.

- [ ] **Step 3: Gate repository session creation with an explicit result**

In `AiChatbotRepository.kt`, add a top-level result type above `AiChatbotRepository`:

```kotlin
sealed interface AiChatSessionResult {
    data class Ready(val session: GeminiChatSession, val modelName: String) : AiChatSessionResult
    data object Disabled : AiChatSessionResult
    data object MissingApiKey : AiChatSessionResult
    data class Failed(val message: String) : AiChatSessionResult
}
```

Replace `ReadySession` and the nullable return with the explicit result:

```kotlin
suspend fun createChatSession(experienceId: Int?): AiChatSessionResult {
    val isEnabled = userPreferences.aiAssistantEnabledFlow.firstOrNull() ?: false
    if (!isEnabled) {
        Log.i(TAG, "AI assistant is disabled")
        return AiChatSessionResult.Disabled
    }
    val apiKey = userPreferences.aiApiKeyFlow.firstOrNull().orEmpty()
    val configuredName = userPreferences.aiModelNameFlow.firstOrNull().orEmpty()
    val modelName = configuredName.ifBlank { DEFAULT_MODEL_NAME }

    if (apiKey.isBlank()) {
        Log.w(TAG, "API key is empty - cannot create chat session")
        return AiChatSessionResult.MissingApiKey
    }

    return try {
        val systemInstruction = buildSystemInstructionJson(experienceId)
        val tools = buildToolsJson()
        val session = GeminiChatSession(apiKey, modelName, systemInstruction, tools)
        AiChatSessionResult.Ready(session, modelName)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialise chat session: ${e.message}", e)
        AiChatSessionResult.Failed(e.message ?: e::class.java.simpleName)
    }
}
```

- [ ] **Step 4: Gate the experience top bar**

In the Hilt wrapper `ExperienceScreen` composable, collect the preference from the existing ViewModel if available. If `ExperienceViewModel` does not already inject `UserPreferences`, add it and expose:

```kotlin
val aiAssistantEnabledFlow = userPreferences.aiAssistantEnabledFlow.stateIn(
    initialValue = false,
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
)
```

Pass `isAiAssistantEnabled` into the stateless screen and top bar:

```kotlin
val isAiAssistantEnabled = viewModel.aiAssistantEnabledFlow.collectAsState().value

if (showAiChat && isAiAssistantEnabled) {
    AiChatBottomSheet(
        experienceId = experience?.id ?: 0,
        onDismiss = { showAiChat = false }
    )
}
```

In `ExperienceTopBar`, wrap the icon:

```kotlin
if (isAiAssistantEnabled) {
    IconButton(onClick = openAiChat) {
        Icon(
            Icons.Outlined.SmartToy,
            contentDescription = "AI assistant"
        )
    }
}
```

- [ ] **Step 5: Handle disabled, missing-key, failed, and ready states in AI chat UI**

In `AiChatUiState`, add:

```kotlin
val isAssistantDisabled: Boolean = false,
```

In `AiChatViewModel.startNewChat`, replace the nullable `ready` branch with:

```kotlin
when (val result = repository.createChatSession(experienceId)) {
    AiChatSessionResult.Disabled -> {
        _uiState.update {
            it.copy(
                isContextLoading = false,
                isAssistantDisabled = true,
                isApiKeyMissing = false,
                modelName = null,
                items = listOf(
                    ChatItem.Message(
                        text = "The AI assistant is disabled. Enable it in Settings > AI assistant.",
                        isUser = false,
                    )
                ),
            )
        }
        return@launch
    }
    AiChatSessionResult.MissingApiKey -> {
        _uiState.update {
            it.copy(
                isContextLoading = false,
                isAssistantDisabled = false,
                isApiKeyMissing = true,
                modelName = null,
            )
        }
        return@launch
    }
    is AiChatSessionResult.Failed -> {
        _uiState.update {
            it.copy(
                isContextLoading = false,
                isAssistantDisabled = false,
                isApiKeyMissing = false,
                statusMessage = null,
                items = listOf(ChatItem.Message(text = "Error: ${result.message}", isUser = false, isError = true)),
            )
        }
        return@launch
    }
    is AiChatSessionResult.Ready -> {
        chatSession = result.session
        val initial = if (showWelcome) {
            listOf(
                ChatItem.Message(
                    text = "Hi! I'm your in-app journal assistant. I can search your past " +
                        "experiences, summarise patterns, and answer harm-reduction questions about " +
                        "your current session. Try a suggestion below or ask me anything.",
                    isUser = false
                )
            )
        } else emptyList()
        _uiState.update {
            it.copy(
                isContextLoading = false,
                isAssistantDisabled = false,
                isApiKeyMissing = false,
                modelName = result.modelName,
                items = initial,
            )
        }
    }
}
```

- [ ] **Step 6: Run AI guard test**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.ai.AiChatbotRepositoryPreferenceTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run misc/data AI tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.ai.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

Run:

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/journal/experience/ExperienceScreen.kt \
  app/src/main/java/foo/pilz/freaklog/data/ai/AiChatbotRepository.kt \
  app/src/main/java/foo/pilz/freaklog/data/ai/AiChatViewModel.kt \
  app/src/test/java/foo/pilz/freaklog/data/ai/AiChatbotRepositoryPreferenceTest.kt
git commit -m "feat: gate ai assistant behind opt-in setting

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 10: Integration cleanup, accessibility pass, and verification

**Files:**
- Review all files changed in Tasks 2-9.
- Modify only files with compile failures, duplicate logic, or accessibility issues introduced by this plan.

- [ ] **Step 1: Search for removed routes and labels**

Run:

```bash
rg "MoreCharts|More charts|DosageStatRoute|DosageStatScreen|navigateToDosageStat|Gemini API Key|Gemini Model Name" app/src/main/java app/src/test/java
```

Expected: no More Charts or DosageStat matches. Gemini labels should appear only in `AiAssistantSettingsScreen.kt`, using the new capitalization if changed.

- [ ] **Step 2: Search for AI assistant ungated entry points**

Run:

```bash
rg "SmartToy|AiChatBottomSheet|showAiChat|createChatSession" app/src/main/java/foo/pilz/freaklog
```

Expected:

- `SmartToy` appears in Settings submenu/top-level AI settings and in `ExperienceScreen.kt` behind `isAiAssistantEnabled`.
- `AiChatBottomSheet` appears only in `ExperienceScreen.kt`.
- `createChatSession` checks `aiAssistantEnabledFlow` before API key/model.

- [ ] **Step 3: Run targeted stats tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.stats.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run targeted settings and AI tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.*" --tests "foo.pilz.freaklog.data.ai.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the full unit suite**

Run:

```bash
./gradlew testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run Detekt**

Run:

```bash
./gradlew detekt --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Build debug APK**

Run:

```bash
./gradlew assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Request code review subagent**

Dispatch a `code-review` agent with:

```text
Review the current branch diff for the stats/AI settings cleanup. Focus only on real bugs, missed gating, stale navigation routes, privacy regressions, failing edge cases in mixed/unknown dose stats, and accessibility regressions in custom chart/settings controls. Do not comment on style-only issues.
```

- [ ] **Step 9: Fix review findings and rerun affected checks**

For each valid review finding, edit the smallest relevant file and rerun the narrowest failed check. If review finds no valid issues, rerun:

```bash
./gradlew testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Final commit**

If Step 9 changed files, commit them:

```bash
git add app/src/main/java app/src/test/java
git commit -m "fix: address stats and ai cleanup review

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

If Step 9 did not change files, do not create an empty commit.

## Notes for execution

- Prefer one task per commit so route deletion, stats redesign, and AI settings can be reviewed independently.
- Do not rewrite or reformat unrelated settings sections.
- Do not add new dependencies unless the user explicitly expands scope to encrypted API-key storage or a new charting library.
- Keep the app package as `foo.pilz.freaklog`.
- Use `collectAsStateWithLifecycle()` only if it is already available in dependencies; otherwise keep the existing `collectAsState()` pattern and do not add a dependency for this cleanup.
- If deleting `ExperienceStatsHelper.kt` removes useful pure tests but no production code uses the helper, prefer deletion over keeping dead code.
- If a subagent edits a file also edited by another subagent, stop and manually integrate the conflict before dispatching more work in that area.

## Self-review checklist

- Spec coverage: More Charts page removal is covered by Tasks 2 and 10; dosage/substance stats improvement is covered by Tasks 3-6 and 10; default-off AI toggle and submenu are covered by Tasks 7-9; subagent usage is covered by Task 1 and Task 10.
- Placeholder scan: the plan avoids deferred implementation markers and names exact files, commands, expected outcomes, and code shapes.
- Type consistency: `SubstanceDosageStatsModel`, `SubstanceDosageSummary`, `DosageTimeRange`, `DosageBucket`, `StatItem`, `AI_ASSISTANT_ENABLED`, and `AiAssistantSettingsRoute` are introduced before later tasks consume them.
- Scope check: encrypted API-key storage and third-party chart/settings libraries are explicitly out of this implementation plan to keep the change focused.
