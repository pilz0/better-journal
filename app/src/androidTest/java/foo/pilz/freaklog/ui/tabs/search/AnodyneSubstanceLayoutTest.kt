package foo.pilz.freaklog.ui.tabs.search

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.ui.tabs.journal.experience.TimelineDisplayOption
import foo.pilz.freaklog.ui.tabs.search.custom.CustomSubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.substance.SubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.substance.SubstanceWithCategoriesPreviewProvider
import foo.pilz.freaklog.ui.theme.JournalTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the AnodyneWiki additions laid out like the rest of the substance pages. */
@RunWith(AndroidJUnit4::class)
class AnodyneSubstanceLayoutTest {
  @get:Rule val compose = createComposeRule()

  private val structure =
      """<svg viewBox="0 0 10 10"><line x1="0" y1="0" x2="10" y2="10" stroke="#000"/></svg>"""

  @Test
  fun anodyneSectionsLineUpWithBundledSections() {
    val bundled = SubstanceWithCategoriesPreviewProvider().values.first()
    compose.setContent {
      JournalTheme {
        SubstanceScreen(
            timelineDisplayOption = TimelineDisplayOption.Loading,
            ingestionTime = LocalDateTime.now(),
            onChangeIngestionTime = {},
            navigateToDosageExplanationScreen = {},
            navigateToSaferHallucinogensScreen = {},
            navigateToSaferStimulantsScreen = {},
            navigateToVolumetricDosingScreen = {},
            navigateToExplainTimeline = {},
            navigateToCategoryScreen = {},
            substanceWithCategories = bundled,
            customUnits = emptyList(),
            anodyneSubstance =
                AnodyneSubstance("Example", durationOfAction = "8 hours", structureSvg = structure),
        )
      }
    }

    compose.onNodeWithContentDescription("Structure of ${bundled.substance.name}").assertExists()
    // Bounds are clipped to the viewport, so measure each title while it is scrolled into view.
    val bundledLeft =
        compose.onAllNodesWithText("Dosage").onFirst().performScrollTo().getBoundsInRoot().left
    val anodyneLeft =
        compose.onNodeWithText("Duration & Elimination").performScrollTo().getBoundsInRoot().left
    // Same card inset + title padding as the bundled sections (they used to run edge to edge).
    assertEquals(bundledLeft, anodyneLeft)
  }

  /** Regression: the structure lived in the summary card, which most custom substances lack. */
  @Test
  fun customSubstanceShowsStructureWithoutSummary() {
    compose.setContent {
      JournalTheme {
        CustomSubstanceScreen(
            substance =
                CustomSubstanceWithEverything(
                    substance = CustomSubstance(name = "Mystery", units = "mg", description = "")
                ),
            categoriesByName = emptyMap(),
            ingestionTime = LocalDateTime.now(),
            onChangeIngestionTime = {},
            timelineDisplayOption = TimelineDisplayOption.Loading,
            anodyneSubstance = AnodyneSubstance("Mystery", structureSvg = structure),
            navigateBack = {},
            navigateToEdit = {},
            onShare = {},
        )
      }
    }

    compose.onNodeWithContentDescription("Structure of Mystery").assertExists()
  }

  @Test
  fun missingPageIsExplainedWithARetry() {
    compose.setContent {
      JournalTheme {
        CustomSubstanceScreen(
            substance =
                CustomSubstanceWithEverything(
                    substance = CustomSubstance(name = "Mystery", units = "mg", description = "")
                ),
            categoriesByName = emptyMap(),
            ingestionTime = LocalDateTime.now(),
            onChangeIngestionTime = {},
            timelineDisplayOption = TimelineDisplayOption.Loading,
            anodyneProblem = "AnodyneWiki has no page for this substance.",
            navigateBack = {},
            navigateToEdit = {},
            onShare = {},
        )
      }
    }

    compose.onNodeWithText("AnodyneWiki has no page for this substance.").assertExists()
    compose.onNodeWithText("Retry").assertExists()
  }
}
