package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.anodyne.model.AnodyneTier
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass

@Composable
fun AnodyneDoseLadder(
    dosing: AnodyneDosing,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
      shape = RoundedCornerShape(12.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = dosing.method,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (dosing.totalEntries > 0) {
          Text(
              text = "${dosing.totalEntries} logs",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(12.dp))

      // Same colours as the PsychonautWiki dose classes on the same screen.
      val dark = isSystemInDarkTheme()
      dosing.light?.let { TierRow("Light", it, DoseClass.LIGHT.getComposeColor(dark)) }
      dosing.common?.let { TierRow("Common", it, DoseClass.COMMON.getComposeColor(dark)) }
      dosing.strong?.let { TierRow("Strong", it, DoseClass.STRONG.getComposeColor(dark)) }
      dosing.heavy?.let { TierRow("Heavy", it, DoseClass.HEAVY.getComposeColor(dark)) }
      dosing.extreme?.let { TierRow("Extreme", it, Color(0xFF9C27B0)) }

      Spacer(Modifier.height(8.dp))
      Text(
          text = "Crowdsourced doses from AnodyneWiki (DBI-IGS). Use with caution.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
      )
    }
  }
}

@Composable
private fun TierRow(
    label: String,
    tier: AnodyneTier,
    indicatorColor: Color,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.width(6.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(indicatorColor),
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.width(72.dp),
    )
    Spacer(Modifier.width(8.dp))

    val rangeText =
        when {
          tier.lower > 0 && tier.upper > 0 && tier.lower != tier.upper ->
              "${formatDose(tier.lower)} – ${formatDose(tier.upper)} ${tier.unit}"
          tier.lower > 0 -> "≥ ${formatDose(tier.lower)} ${tier.unit}"
          tier.upper > 0 -> "≤ ${formatDose(tier.upper)} ${tier.unit}"
          else -> "–"
        }

    Text(
        text = rangeText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )

    if (tier.entries > 0) {
      Text(
          text = "(${tier.entries})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun formatDose(num: Double): String {
  return if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
}
