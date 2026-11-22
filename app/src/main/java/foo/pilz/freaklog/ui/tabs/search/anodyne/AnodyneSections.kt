package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import foo.pilz.freaklog.data.anodyne.AnodyneInstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneLink
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.EffectGroup
import foo.pilz.freaklog.data.anodyne.model.Reagent
import foo.pilz.freaklog.data.anodyne.model.Schedule
import foo.pilz.freaklog.ui.tabs.search.substance.SectionWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun AnodyneCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  // Same card as the bundled sections so both sources read as one page.
  SectionWithTitle(title = title) {
    Column(
        modifier =
            modifier.padding(start = horizontalPadding, end = horizontalPadding, bottom = 12.dp)
    ) {
      content()
    }
  }
}

/** Offers to load the AnodyneWiki page; says why when the last attempt found nothing. */
@Composable
fun AnodyneLoadCard(isLoading: Boolean, problem: String?, onLoad: () -> Unit) {
  SectionWithTitle(title = "AnodyneWiki") {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = problem ?: "Load dosages, effects, reagents, legal status and pharmacology.",
          style = MaterialTheme.typography.bodySmall,
          color =
              problem?.let { MaterialTheme.colorScheme.error }
                  ?: MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      } else {
        Button(onClick = onLoad) { Text(if (problem != null) "Retry" else "Load") }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnodyneOverviewSection(substance: AnodyneSubstance) {
  AnodyneCard("Overview") {
    substance.structureSvg?.let { svg ->
      MoleculeStructureView(svg = svg, substanceName = substance.title)
      Spacer(Modifier.height(8.dp))
    }

    FormulaAndMassRow(substance.formula, substance.molarMass)

    if (substance.classes.isNotEmpty()) {
      Text(
          text = "Classes",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier.padding(top = 4.dp),
      ) {
        substance.classes.forEach { tag ->
          SuggestionChip(
              onClick = {},
              label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
              colors =
                  SuggestionChipDefaults.suggestionChipColors(
                      containerColor = MaterialTheme.colorScheme.surfaceVariant,
                  ),
              border = null,
          )
        }
      }
      Spacer(Modifier.height(8.dp))
    }

    val otherNames = (substance.abbreviations + substance.aliases + substance.slang).distinct()
    if (otherNames.isNotEmpty()) {
      Text(
          text = "Also known as",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = otherNames.joinToString(", "),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

@Composable
private fun FormulaAndMassRow(formula: String?, molarMass: String?) {
  if (formula.isNullOrBlank() && molarMass.isNullOrBlank()) return
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    formula?.let {
      Column {
        Text(
            text = "Formula",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
    molarMass?.let { mass ->
      Column {
        Text(
            text = "Molar Mass",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = mass,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
  Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnodyneEffectsSection(effectGroups: List<EffectGroup>) {
  if (effectGroups.isEmpty()) return
  AnodyneCard("Subjective Effects") {
    effectGroups.forEach { group ->
      Text(
          text = group.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 4.dp),
      )
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
      ) {
        group.effects.forEach { effect ->
          SuggestionChip(
              onClick = {},
              label = { Text(effect, style = MaterialTheme.typography.labelSmall) },
              colors =
                  SuggestionChipDefaults.suggestionChipColors(
                      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  ),
              border = null,
          )
        }
      }
    }
  }
}

@Composable
fun AnodyneDurationSection(substance: AnodyneSubstance) {
  if (substance.durationOfAction == null && substance.halfLife == null) return
  AnodyneCard("Duration & Elimination") {
    substance.durationOfAction?.let {
      Text(
          text = "Duration of Action",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(6.dp))
    }
    substance.halfLife?.let {
      Text(
          text = "Biological Half-Life",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
fun AnodyneInteractionsSection(interactions: String?) {
  if (interactions.isNullOrBlank()) return
  AnodyneCard("Literature Interactions") {
    Text(interactions, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
fun AnodyneToxicitySection(reportedFatalDose: String?, treatment: String?) {
  if (reportedFatalDose.isNullOrBlank() && treatment.isNullOrBlank()) return
  AnodyneCard("Toxicity & Harm Reduction") {
    reportedFatalDose?.let {
      Text(
          text = "Reported Fatal Dose",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(8.dp))
    }
    treatment?.let {
      Text(
          text = "Treatment & Overdose Management",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
fun AnodyneHealthWarningsSection(healthEffects: String?, drugWarnings: String?) {
  if (healthEffects.isNullOrBlank() && drugWarnings.isNullOrBlank()) return
  AnodyneCard("Health Effects & Warnings") {
    drugWarnings?.let {
      Text(
          text = "Drug Warnings",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(8.dp))
    }
    healthEffects?.let {
      Text(
          text = "Health Effects",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
fun AnodyneReagentsSection(reagents: List<Reagent>) {
  if (reagents.isEmpty()) return
  AnodyneCard("Reagent Tests") {
    reagents.forEach { reagent ->
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
            text = reagent.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          reagent.colors.forEach { colorStr ->
            val color = parseReagentColor(colorStr)
            Box(
                modifier =
                    Modifier.size(16.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            CircleShape,
                        ),
            )
          }
        }
      }
    }
  }
}

private fun parseReagentColor(str: String): Color {
  val clean = str.trim().removePrefix("#")
  return when (clean.lowercase()) {
    "red" -> Color(0xFFE53935)
    "yellow" -> Color(0xFFFFD600)
    "blue" -> Color(0xFF1E88E5)
    "purple" -> Color(0xFF8E24AA)
    "black" -> Color(0xFF212121)
    "brown" -> Color(0xFF6D4C41)
    "orange" -> Color(0xFFFB8C00)
    "green" -> Color(0xFF43A047)
    "clear",
    "white" -> Color(0xFFFAFAFA)
    else -> runCatching { Color("#$clean".toColorInt()) }.getOrDefault(Color.Gray)
  }
}

@Composable
fun AnodyneLegalSection(scheduling: List<Schedule>) {
  if (scheduling.isEmpty()) return
  AnodyneCard("Legal Status") {
    scheduling.forEach { s ->
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(s.country, style = MaterialTheme.typography.bodyMedium)
        Text(
            s.schedule,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
fun AnodynePharmacologySection(mechanism: String?, metabolism: String?) {
  if (mechanism.isNullOrBlank() && metabolism.isNullOrBlank()) return
  AnodyneCard("Pharmacology") {
    mechanism?.let {
      Text(
          text = "Mechanism of Action",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(8.dp))
    }
    metabolism?.let {
      Text(
          text = "Metabolism",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
fun AnodyneSourcesSection(
    substanceName: String,
    references: List<AnodyneLink>,
) {
  val uriHandler = LocalUriHandler.current
  val wikiUrl = AnodyneInstance.substancePageUrl(substanceName)

  AnodyneCard("Sources") {
    OutlinedButton(
        onClick = { uriHandler.openUri(wikiUrl) },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(
          Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Spacer(Modifier.width(8.dp))
      Text("View on AnodyneWiki")
    }

    if (references.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Text(
          text = "References",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      references.forEach { link ->
        Text(
            text = link.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { uriHandler.openUri(link.url) }
                    .padding(vertical = 2.dp),
        )
      }
    }
  }
}
