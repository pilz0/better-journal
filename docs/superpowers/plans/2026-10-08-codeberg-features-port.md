# Codeberg Feature Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port all major features from the Codeberg fork (`gay.cybercrime.journal`) into Freaklog (`foo.pilz.freaklog`), keeping all existing Freaklog features intact and adding unit tests for each new subsystem.

**Architecture:** Incrementally integrate standalone utilities first (math expression parser, volumetric input, sharing), then Room database extensions with proper schema version migrations (categories, intake limits, custom substances, encryption), then background services (timeline notification, backup worker), and finally hardware/system APIs (Health Connect, NFC) and UI skins/easter eggs.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Room SQLite, Kotlinx Serialization, Coroutines & Flow, WorkManager, AndroidX Health Connect, Android NFC / HCE.

---

### Task 1: Dose Math Expressions & Volumetric Utilities
- Create `DoseExpression.kt` with recursive-descent math parser (`evaluateNumericExpression`).
- Create `VolumetricInput.kt` with volume/concentration parser and formatters.
- Integrate into dose viewmodels and validation logic.
- Unit tests: `DoseExpressionTest.kt`, `VolumetricInputTest.kt`.

### Task 2: Ingestion Category (Medical vs Recreational Tagging)
- Add `IngestionCategory` enum (`MEDICINAL`, `RECREATIONAL`).
- Update `Ingestion`, `CustomUnit`, `SubstanceCompanion` entities.
- Room migration and Dao updates.
- UI category pickers in dose selection and ingestion editing screens.
- Unit tests for category resolution and migration.

### Task 3: Intake Limits & Real-Time Harm-Reduction Banners
- Add `IntakeLimit` entity, DAO, and repository.
- Implement `IntakeLimitLogic` (sliding window calculations, unit conversions) and `IntakeLimitChecker`.
- Add `IntakeLimitBanner` to dose logging and `IntakeLimitsScreen` in Settings.
- Unit tests for intake limit evaluation and time window math.

### Task 4: Granular Export Filtering, Encryption & Automated Backups
- Add `ExportFilter` to selectively filter exports.
- Implement `ExportEncryption` (AES-GCM / PBKDF2).
- Implement `BackupWorker` and `BackupPasswordStore`.
- Update Export/Import settings screens with encryption and filtering options.
- Unit tests for export encryption, decryption, and filtering.

### Task 5: Active Experience Live Notification Tracker
- Implement `TimelineNotificationService`, notification channel, and `PhaseCalculator`.
- Implement `TimelineBitmapRenderer` for live graph in status bar notification.
- Register service in `AndroidManifest.xml` and wire start/stop lifecycle.
- Unit tests for phase calculation and timeline notification model.

### Task 6: Experience Summary Card Image Sharing
- Implement `ExperienceShareRenderer` (off-screen Compose view to PNG bitmap).
- Create `ShareableExperienceContent` composable card.
- Wire into experience options menu.

### Task 7: Comprehensive Custom Substance Schema & File Sharing
- Add `CustomRoa`, `CustomRoaDose`, `CustomRoaDuration`, `CustomInteraction`, `CustomCrossTolerance` entities and DAOs.
- Add `SharedSubstance` serialization models, exporter, and importer with collision handling.
- Expand custom substance creation and editing screens.
- Unit tests for substance serialization and import/collision flows.

### Task 8: Health Connect & Vitals Overlays
- Implement Health Connect permissions and query wrappers (`healthConnect.kt`).
- Query heart rate and sleep samples.
- Render heart rate curve overlay in timeline.
- Add blood pressure and pulse tracking screens.

### Task 9: NFC Transfer & Keychain
- Implement `NfcCrypto`, `NdefHostApduService`, `NfcTransferController`.
- Implement `NFCActivity`, popup dialogs, and Keychain management screen.
- Register NFC intents in `AndroidManifest.xml`.

### Task 10: UI Extras: Excel Stats Skin & Condition AST Achievement Engine
- Implement `ExcelStatsSkin` and skin switcher.
- Implement `ConditionLexer`, `ConditionParser`, `ConditionAst`, `ConditionEvaluator`, and `AchievementEngine`.
- Unit tests for AST parsing and achievement evaluation.
