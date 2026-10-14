# FreakQuery Shell Improvements & Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the FreakQuery shell in the Android app (history navigation, clipboard copy, dual tag/template evaluation, guide link), author a comprehensive web guide in `../freaklog-website`, and link it to the app.

**Architecture:** 
1. Android app: Update `Constants.kt` with `FREAKQUERY_GUIDE_URL`, extend `FreakQueryShellViewModel` with query history cycling and automatic template rendering, update `FreakQueryShellScreen` with history controls, copy actions, and a top bar docs link using `LocalUriHandler` and `LocalClipboardManager`. Add a docs link in `WebhookEditorScreen`.
2. Website: Add `freakquery.html` to `../freaklog-website` styled to match the dark theme, complete with reference tables, syntax explanations, webhook examples, and cheat sheets. Link it from `index.html` and `privacy.html`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Coroutines, HTML5, CSS3, JUnit 5 / mockk.

---

### Task 1: Add Documentation URL Constant

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/Constants.kt`

- [ ] **Step 1: Add `FREAKQUERY_GUIDE_URL` to `Constants.kt`**

```kotlin
const val FREAKQUERY_GUIDE_URL = "https://freaklog.as214958.net/freakquery.html"
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/Constants.kt
git commit -m "feat(freakquery): add FREAKQUERY_GUIDE_URL constant"
```

---

### Task 2: Enhance FreakQueryShellViewModel with History Navigation & Template Evaluation

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellViewModel.kt`
- Create: `app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellViewModelTest.kt`

- [ ] **Step 1: Write unit tests for history navigation and template execution**

Create `app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellViewModelTest.kt`:

```kotlin
package foo.pilz.freaklog.ui.tabs.settings.freakquery

import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreakQueryShellViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<FreakQueryRepository>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getLogs() } returns listOf(mapOf("id" to 1L))
        coEvery { repository.query(any(), any()) } returns "query-result"
        coEvery { repository.render(any(), any()) } returns "rendered-template"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `runCurrentInput executes bare tag using query`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        viewModel.updateInput("today|sum_dose")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        coVerify { repository.query("today|sum_dose") }
        assertEquals("query-result", viewModel.state.value.history.last().output)
    }

    @Test
    fun `runCurrentInput executes template with curly braces using render`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        viewModel.updateInput("Dose: {{today|sum_dose}} mg")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        coVerify { repository.render("Dose: {{today|sum_dose}} mg") }
        assertEquals("rendered-template", viewModel.state.value.history.last().output)
    }

    @Test
    fun `history navigation cycles up and down through sent queries`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        viewModel.updateInput("query 1")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        viewModel.updateInput("query 2")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        viewModel.updateInput("draft")

        // Navigate up to query 2
        viewModel.navigateHistoryUp()
        assertEquals("query 2", viewModel.state.value.input)

        // Navigate up to query 1
        viewModel.navigateHistoryUp()
        assertEquals("query 1", viewModel.state.value.input)

        // Navigate down to query 2
        viewModel.navigateHistoryDown()
        assertEquals("query 2", viewModel.state.value.input)

        // Navigate down to draft
        viewModel.navigateHistoryDown()
        assertEquals("draft", viewModel.state.value.input)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.freakquery.FreakQueryShellViewModelTest" --no-daemon`
Expected: Compilation failure or missing methods (`navigateHistoryUp`, `navigateHistoryDown`).

- [ ] **Step 3: Update `FreakQueryShellViewModel.kt`**

Update `FreakQueryShellState` and `FreakQueryShellViewModel`:
- Add `canNavigateHistoryUp: Boolean` and `canNavigateHistoryDown: Boolean` to `FreakQueryShellState`.
- Maintain `sentQueries: List<String>`, `historyCursor: Int?`, and `draftInput: String`.
- Implement `navigateHistoryUp()` and `navigateHistoryDown()`.
- In `runQuery`, route `query.contains("{{") && query.contains("}}")` to `freakQueryRepository.render(query)`.
- Record successful query execution in `sentQueries`.

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.freakquery.FreakQueryShellViewModelTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellViewModel.kt app/src/test/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellViewModelTest.kt
git commit -m "feat(freakquery): add history navigation and template evaluation to FreakQueryShellViewModel"
```

---

### Task 3: Enhance FreakQueryShellScreen UI

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellScreen.kt`

- [ ] **Step 1: Implement UI features in `FreakQueryShellScreen.kt`**
  - In `TopAppBar`: Add documentation action button using `Icons.Outlined.HelpOutline` which invokes `uriHandler.openUri(FREAKQUERY_GUIDE_URL)`.
  - In `ShellEntry`:
    - Add copy query and copy output buttons with `Icons.Outlined.ContentCopy` using `LocalClipboardManager`.
    - Show brief toast feedback or haptic click on copy.
    - Keep click-on-card to populate input.
  - In Input Row:
    - Add History Up (`Icons.Outlined.ArrowUpward`) and History Down (`Icons.Outlined.ArrowDownward`) icon buttons enabled when `state.canNavigateHistoryUp` / `state.canNavigateHistoryDown`.
    - Support Run on keyboard `ImeAction.Send`.

- [ ] **Step 2: Build & test UI compilation**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run Detekt on modified files**

Run: `./gradlew detekt --no-daemon`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/freakquery/FreakQueryShellScreen.kt
git commit -m "feat(freakquery): add copy controls, history cycling, and documentation link to FreakQueryShellScreen"
```

---

### Task 4: Link FreakQuery Documentation in WebhookEditorScreen

**Files:**
- Modify: `app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/webhooks/WebhookEditorScreen.kt`

- [ ] **Step 1: Add guide link to `WebhookEditorScreen.kt`**
  - In the FreakQuery section of `WebhookEditorScreenContent`, add an action or text button "Syntax guide" linking to `FREAKQUERY_GUIDE_URL` via `LocalUriHandler`.

- [ ] **Step 2: Verify compilation and tests**

Run: `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.data.webhook.*" --no-daemon`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/foo/pilz/freaklog/ui/tabs/settings/webhooks/WebhookEditorScreen.kt
git commit -m "feat(webhook): link FreakQuery syntax guide in WebhookEditorScreen"
```

---

### Task 5: Create FreakQuery Documentation in `freaklog-website`

**Files:**
- Create: `../freaklog-website/freakquery.html`
- Modify: `../freaklog-website/index.html`
- Modify: `../freaklog-website/privacy.html`

- [ ] **Step 1: Create `../freaklog-website/freakquery.html`**
  - Design with matching theme (`--bg: #0b1020; --brand: #8aa4ff;` responsive container).
  - Include:
    - Title & Eyebrow: "FreakQuery Documentation & Guide".
    - Interactive overview: offline query DSL for logs and webhooks.
    - Pipeline stages: Filter -> Group -> Selector -> Metric -> Format.
    - Reference tables for:
      - Filters: `today`, `week`, `month`, `year`, `substance=...`, `route=...`, `limit=N`.
      - Groups: `binges`, `streaks`.
      - Selectors: `last`, `first`, `random`, `largest`, `longest`.
      - Metrics: `count`, `dose`, `substance`, `since`, `sum_dose`, `top_substances`, `top_routes`, `sites`, `substance_totals`, `timeline`, `sequence`, `ratio=route`.
      - Formatting: `json`, `compact`, `sep=...`, `labels`, `parens`.
    - Webhook templating guide (`{{tag}}` placeholders, Discord markdown formatting).
    - Practical examples / Shell recipes table with one-click copyable snippets.

- [ ] **Step 2: Update `../freaklog-website/index.html` and `privacy.html`**
  - Add "FreakQuery Guide" link to navigation header and footer on both pages.
  - In `index.html` under "Feature highlights", add a link to the FreakQuery documentation under the automation & tools card.

- [ ] **Step 3: Check website HTML syntax and commit in `../freaklog-website`**

Run:
```bash
git -C ../freaklog-website status
git -C ../freaklog-website add freakquery.html index.html privacy.html
git -C ../freaklog-website commit -m "docs: add comprehensive FreakQuery syntax and usage guide"
```

---

### Task 6: Final Verification & Testing

**Files:**
- All touched files in both repositories

- [ ] **Step 1: Run complete test suite**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: ALL TESTS PASS

- [ ] **Step 2: Run Detekt and Android Lint**

Run:
```bash
./gradlew detekt --no-daemon
./gradlew :app:lintDebug --no-daemon
```
Expected: ALL CHECKS PASS

- [ ] **Step 3: Review git status in both repos**

Run:
```bash
git status
git -C ../freaklog-website status
```
Expected: Clean working directories on both repositories.
