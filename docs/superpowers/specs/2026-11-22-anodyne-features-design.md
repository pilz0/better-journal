# AnodyneWiki Features Integration Design Spec

**Date:** 2026-11-22  
**Target:** FreakLog Android (`foo.pilz.freaklog`)  
**Parity Reference:** FreakLog-iOS (`https://woof.rip/FreakLog/FreakLog-iOS`)

---

## 1. Overview & Goals

FreakLog-iOS introduced an integration with AnodyneWiki (a public-domain harm-reduction and pharmacology meta-wiki at `https://anodyne.wiki`). This specification ports these features to FreakLog Android, bringing full parity:

- Configurable AnodyneWiki instance URL (defaulting to `https://anodyne.wiki`, supporting mirrors/self-hosted instances).
- Disk caching with automatic TTL management and cache deletion tools.
- Dedicated AnodyneWiki settings under Settings.
- Substance search integration with a dedicated `AnodyneSearchScreen`.
- Standalone `AnodyneSubstanceScreen` with 1-tap conversion to custom substances (prefilling dose tiers, routes, and categories).
- Seamless enrichment of standard `SubstanceScreen` and `CustomSubstanceScreen` (dose ladder with source toggle, user timings duration fallback, 2D molecular structure diagram, formula, molar mass, subjective effects, experience report links, literature interactions, toxicity, reagents, legal status, pharmacology, and sources).
- Anodyne user notes screens (`AnodyneUserNotesScreen` and `AnodyneUserNoteScreen`).
- Native Compose `Canvas`-based 2D chemical structure SVG rendering adapting to Material3 light and dark themes.
- Comprehensive unit test suite covering network/parsing, caching, fallback resolution, viewmodels, and SVG path parsing.

---

## 2. Architecture & Data Layer

### 2.1 Configuration & Preferences (`UserPreferences`)

- Key `ANODYNE_BASE_URL` (`String`): Configured instance address (default `"https://anodyne.wiki"`). Validates `http`/`https` scheme, normalizes trailing slashes, and accepts raw hostnames (e.g. `wiki.example.org` becomes `https://wiki.example.org`). Changing this value purges cached substance/index files.
- Key `ANODYNE_AUTO_LOAD_ENABLED` (`Boolean`, default `false`): Controls whether substance pages automatically fetch Anodyne data or wait for the user to tap "More from AnodyneWiki".
- Preserves backwards compatibility with existing AI assistant tools (`AiAnodyneTools`), ensuring both the AI assistant and the UI use the same configured base URL and client logic.

### 2.2 Data Models (`foo.pilz.freaklog.data.anodyne.model`)

1. **`AnodyneSubstance`**:
   - `title`: Canonical substance name.
   - `abbreviations`, `aliases`, `slang`, `classes`: List of strings.
   - `dosing`: List of `AnodyneDosing(method, light, common, strong, heavy, extreme)` with `totalEntries` and route mapping to `AdministrationRoute`.
   - `durationOfAction`, `halfLife`: Duration and elimination strings.
   - `structureSvg`, `formula`, `molarMass`: Chemical structure SVG and formatted subscripted formula (e.g. `C₁₁H₁₅NO₂`).
   - `interactions`, `mechanismOfAction`, `metabolism`, `healthEffects`, `drugWarnings`, `reportedFatalDose`, `treatment`: Pharmacology and harm-reduction texts.
   - `subjectiveEffects`: List of `EffectGroup(name, effects)`.
   - `scheduling`: List of `Schedule(country, schedule)`.
   - `reagents`: List of `Reagent(name, colors)`.
   - `erowidReports`, `redditReports`, `references`: List of `AnodyneLink(title, url)`.
2. **`AnodyneIndexEntry`**:
   - `title`: Substance title.
   - `abbreviations`: Short names/aliases.
   - `classes`: Categorization list.
3. **`AnodyneUserNote`**:
   - `user`: Username.
   - `substanceName`: Subject substance.
   - `routes`: List of `UserNoteRoute(name, dosage, onset, comeup, duration)`.
   - `classes`, `forms`, `sources`, `indications`, `effects`, `afterEffects`, `tolerance`, `odor`, `notes`: Details parsed from userpage TriedSubstances.

### 2.3 Repository & Disk Caching (`AnodyneRepository`)

- Caches files in `context.cacheDir.resolve("anodyne")`:
  - `substance_<encoded_name>.json`: 7-day TTL.
  - `index.json`: 24-hour TTL.
  - `user_notes.json`: 24-hour TTL.
- Methods:
  - `lookup(name, otherNames): Result<AnodyneSubstance?>`: Checks disk cache first; if expired/missing, calls API `/api/substance/<name>`. If not found, checks index aliases and loads matching title.
  - `getIndexEntries(): Result<List<AnodyneIndexEntry>>`: Checks disk cache; queries `/api/index/substance`.
  - `getUserNotes(names): Result<List<AnodyneUserNote>>`: Checks disk cache; queries user index and userpages.
  - `getCacheSizeBytes(): Long` and `clearCache()`: Utility methods for cache size management.

### 2.4 Fallback Store (`AnodyneFallbackStore`)

- Singleton in-memory cache initialized from disk cached substances and user notes.
- Methods:
  - `getDose(substanceName, route): RoaDose?`: Returns dose from Anodyne dose tiers when PsychonautWiki/custom data is missing.
  - `getDuration(substanceName, route): RoaDuration?`: Combines user note timings (onset, comeup, total) or substance `durationOfAction` as fallback.
  - `getRoutesWithData(substanceName): List<AdministrationRoute>`
  - `getRoutesWithTimings(substanceName): List<AdministrationRoute>`

---

## 3. UI & Navigation Layer

### 3.1 Settings Screen (`AnodyneSettingsScreen`)

- Route: `AnodyneSettingsRoute` under `settingsGraph`.
- Entry in main `SettingsScreen`: "AnodyneWiki" row under Data/Integrations.
- UI elements:
  - Switch: "Load Automatically" (with subtitle explaining privacy/data behavior).
  - OutlinedTextField: "Instance URL" with validation, error text if invalid, and "Reset to anodyne.wiki" button if customized.
  - OutlinedCard / Button: "Delete Downloaded Data" showing current cache size in MB/KB.
  - Navigation rows: "Browse AnodyneWiki" and external browser link.

### 3.2 Search Integration & `AnodyneSearchScreen`

- Route: `AnodyneSearchRoute(query)` under `searchGraph`.
- In `SearchScreen`: Bottom row "Search AnodyneWiki" with book icon leading to `AnodyneSearchScreen`.
- `AnodyneSearchScreen`:
  - Search bar with live filtering over `title`, `abbreviations`, and `classes`.
  - Displays matching entries with title, abbreviations, and class chips.
  - Clicking an entry navigates to `AnodyneSubstanceRoute(title)`.

### 3.3 Standalone `AnodyneSubstanceScreen`

- Route: `AnodyneSubstanceRoute(title)`.
- If substance already exists locally (PsychonautWiki or CustomSubstance), shows "Open in FreakLog" action banner.
- If not present locally, top app bar has an "Add" button opening `AddCustomSubstanceFromAnodyneSheet`:
  - Pre-fills name, units, and custom ROA dose entries from Anodyne dose tiers, and categories from matched classes.
- Content sections:
  - Overview: Common name, canonical title, class chips, 2D structure SVG, formula, molar mass, alternative names.
  - Effects: Subjective effects groups.
  - Experience Reports: User notes count, Erowid count, Reddit count.
  - Logged Doses: `AnodyneDoseLadder` showing light, common, strong, heavy, extreme tiers with log counts and DBI-IGS disclaimer.
  - Duration: Route timings and duration of action / half-life.
  - Interactions: Literature notes.
  - Toxicity: Fatal doses and overdose treatment.
  - Health Warnings: Health effects and drug warnings.
  - Reagent Tests: Reagent list with color progression swatches.
  - Legal Status: Scheduling by country.
  - Pharmacology: Mechanism of action and metabolism.
  - Sources: Link to AnodyneWiki page, references list.

### 3.4 Combined `SubstanceScreen` & `CustomSubstanceScreen` Integration

- If not auto-loaded and not in cache, shows an "AnodyneWiki" card with button "More from AnodyneWiki".
- When loaded:
  - Overview includes 2D molecular structure diagram, formula, molar mass.
  - Dose section includes a segmented button (PsychonautWiki vs AnodyneWiki) when both exist.
  - Duration section fills in missing routes using `AnodyneFallbackStore`.
  - Topic sections (effects, reports, literature interactions, toxicity fatal doses/treatment, health warnings, reagents, legal status, pharmacology, sources) seamlessly appear in topic order.

### 3.5 User Notes & Links Screens

- `AnodyneUserNotesRoute(substanceName)`: List of all user notes for the substance.
- `AnodyneUserNoteDetailRoute(userId, substanceName)`: Detailed viewer for an individual user's experience report.
- `AnodyneLinksRoute(title, links)`: Displays lists of Erowid/Reddit experience reports and references.

### 3.6 2D Chemical Structure SVG Canvas (`MoleculeStructureView`)

- Parses CDK-generated SVG (`<path d="...">`, `<line>`, `<rect>`, `<polyline>`, `<polygon>`, `<circle>`, `<ellipse>`).
- Re-colors black strokes/fills to `MaterialTheme.colorScheme.onSurface` so structures render clearly in both dark and light themes, while preserving colored heteroatoms (O=red, N=blue, S=yellow, Cl/F/Br=halogens).

---

## 4. Testing Strategy

1. **`AnodyneSubstanceParserTest`**: Verifies robust JSON parsing of complex substance payloads, dose tiers, reagents, schedules, report links, and subscript formatting.
2. **`AnodyneTimingParserTest`**: Tests extraction of durations, time ranges (e.g. `"3 – 6 hours"`, `"10-15 mins"`), and combined user note timings.
3. **`AnodyneDiskCacheTest`**: Tests cache write/read, TTL expiration (7-day substance, 24-hour index), and clearing.
4. **`AnodyneFallbackStoreTest`**: Verifies fallback dose and duration resolution when PsychonautWiki data is absent.
5. **`MoleculeSvgParserTest`**: Verifies parsing of SVG path commands (`M`, `L`, `H`, `V`, `C`, `S`, `Z`, absolute and relative).
6. **`AnodyneSettingsViewModelTest`**: Tests URL validation, normalization, and cache clear triggers.

---

## 5. Security & Privacy Invariants

- Anodyne queries only send public substance names or index queries.
- Default behavior preserves user privacy: "Load Automatically" is opt-in, only fetching when explicitly navigated or tapped unless enabled in settings.
- All network calls use secure HTTP clients without third-party proprietary SDKs.
- Custom instance URLs are strictly validated to prevent malformed or invalid protocols.
