package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.utils.getTimeDifferenceText

@Composable
fun IntakeLimitBannerContent(
    statuses: List<IntakeLimitStatus>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        statuses.forEach { status ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "${status.limit.maxDescription()} per ${status.limit.windowDescription()}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = status.currentLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status.pendingValue > 0.0) {
                    Text(
                        text = status.projectedLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.severityColor()
                    )
                }
                status.resetInfo()?.let { reset ->
                    Text(
                        text = reset,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (status.ignoredIngestionCount > 0) {
                    val count = status.ignoredIngestionCount
                    Text(
                        text = "$count earlier ${if (count == 1) "ingestion" else "ingestions"} in other units not counted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IntakeLimitStatus.severityColor(): Color = when {
    exceedsLimit -> InteractionType.DANGEROUS.color
    crossesWarning -> InteractionType.UNSAFE.color
    else -> MaterialTheme.colorScheme.primary
}

fun IntakeLimitStatus.resetInfo(): String? {
    val label = when {
        exceedsLimit -> "under the limit"
        crossesWarning -> "under ${limit.warningPercent}%"
        projectedValue > 0.0 -> "to zero"
        else -> return null
    }
    val instant = when {
        exceedsLimit -> instantUnderLimit()
        crossesWarning -> instantUnderWarning()
        else -> instantFullReset()
    } ?: return null
    return "Back $label in ${getTimeDifferenceText(now, instant)}"
}

@Composable
fun IntakeLimitWarningDialog(
    statuses: List<IntakeLimitStatus>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val anyExceed = statuses.any { it.exceedsLimit }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = if (anyExceed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (anyExceed) "Intake limit exceeded" else "Approaching intake limit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                statuses.forEach { status ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "${status.limit.substanceName}: ${status.warningLine()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        status.resetInfo()?.let { reset ->
                            Text(
                                text = reset,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Log anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Go back") }
        }
    )
}
