# FreakQuery Shell Improvements & Documentation Specification

## Overview
FreakQuery is the built-in domain-specific query engine in Freaklog that allows users to query, analyze, and template their local substance journal data (for webhooks, personal analytics, and command-line style queries).

This project accomplishes three connected goals:
1. **Improve the FreakQuery Shell in the Android app**:
   - History navigation (up/down arrow recall).
   - Clipboard copy actions for queries and results.
   - Dual-mode evaluation supporting both bare query tags (e.g. `today|sum_dose`) and full template strings containing arbitrary text and `{{tags}}` (e.g. `Took {{last|substance}} {{last|since}} ago`).
   - Direct documentation action in the TopAppBar.
2. **Comprehensive Guide in `freaklog-website`**:
   - Create `freakquery.html` with detailed syntax explanations, reference tables, real-world recipes, and webhook integration examples.
   - Integrate links in `index.html` and `privacy.html`.
3. **App Integration & Links**:
   - Link to `https://freaklog.as214958.net/freakquery.html` from the FreakQuery Shell top bar and from `WebhookEditorScreen.kt`.

---

## Architecture & Changes

### 1. Android App: FreakQuery Shell Enhancements
- **`foo.pilz.freaklog.ui.Constants.kt`**:
  - Add `const val FREAKQUERY_GUIDE_URL = "https://freaklog.as214958.net/freakquery.html"`.

- **`foo.pilz.freaklog.ui.tabs.settings.freakquery.FreakQueryShellViewModel`**:
  - State:
    - `sentQueries: List<String>` tracking all run queries for navigation.
    - `historyCursor: Int` representing current position in history when navigating.
  - History Navigation:
    - `navigateHistoryUp()`: populates the input field with the previous query in history.
    - `navigateHistoryDown()`: advances forward in history, restoring empty input or the draft when reaching the latest position.
  - Evaluation Logic:
    - If input contains `{{` and `}}` and is not solely a wrapped single tag, evaluate with `freakQueryRepository.render(input)`.
    - Otherwise, evaluate with `freakQueryRepository.query(input)`.
    - Special shell commands like `.clear`, `.help`, `version` are preserved.

- **`foo.pilz.freaklog.ui.tabs.settings.freakquery.FreakQueryShellScreen`**:
  - TopAppBar:
    - Clear history button (`Icons.Outlined.ClearAll`).
    - Help / Documentation button (`Icons.AutoMirrored.Outlined.HelpOutline` or `Icons.Outlined.HelpOutline`) opening `FREAKQUERY_GUIDE_URL` via custom tabs / browser Intent.
  - Entry Card:
    - Display query (`fq> ...`) and output.
    - Copy query icon button.
    - Copy output icon button.
    - Click-to-reuse query in input.
  - Input Area:
    - History Previous / Next buttons (Up / Down) to quickly cycle history without requiring a hardware keyboard.
    - Play / Run button.
    - Send action on IME keyboard.

- **`foo.pilz.freaklog.ui.tabs.settings.webhooks.WebhookEditorScreen`**:
  - Under the "Enable FreakQuery tags" switch, add a clickable link or button: "View FreakQuery syntax guide" that opens `FREAKQUERY_GUIDE_URL`.

### 2. Website: `../freaklog-website`
- **`freakquery.html`**:
  - Dark-mode responsive styling matching `index.html`.
  - Sections:
    - **Header & Navigation**: Links back to Home, Privacy, and GitHub.
    - **Overview**: What FreakQuery is, offline privacy guarantee, and usage modes (Shell vs Webhooks).
    - **Tag Structure**: `[filters] | [group] | [selector] | [metrics] | [formats]`.
    - **Filters & Selectors Reference Table**: `today`, `week`, `month`, `year`, `substance=<name>`, `route=<route>`, `last`, `first`, `binges`, `streaks`.
    - **Metrics & Aggregations Table**: `count`, `dose`, `substance`, `since`, `sum_dose`, `top_substances`, `top_routes`, `sequence`, `ratio=route`.
    - **Formatting Options**: `json`, `compact`, `sep=<separator>`, `labels`, `parens`.
    - **Webhook Templates Guide**: Examples with Discord markdown and conditional blocks.
    - **Interactive Shell Cheat Sheet**: Common queries to try directly in Freaklog.
- **`index.html` & `privacy.html`**:
  - Add navigation links to `FreakQuery Guide` in headers and footers.
  - Highlight FreakQuery in the feature grid.

---

## Verification Plan

### Automated Tests
1. Run `./gradlew testDebugUnitTest --tests "foo.pilz.freaklog.ui.tabs.settings.freakquery.*" --no-daemon` to verify ViewModel history navigation and template vs tag evaluation.
2. Run `./gradlew detekt --no-daemon` to ensure code style conformance.
3. Run `./gradlew :app:lintDebug --no-daemon` to verify Android Lint.

### Manual / Browser Verification
1. Validate `freakquery.html` markup, styling, and navigation links across `../freaklog-website`.
2. Verify git status and clean branch state in both repositories.
