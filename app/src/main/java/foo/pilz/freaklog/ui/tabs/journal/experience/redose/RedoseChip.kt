/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.journal.experience.redose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class RedoseChipState(
    val show: Boolean = true,
    val params: RedoseParameters = RedoseParameters.Default
)

@HiltViewModel
class RedoseChipViewModel @Inject constructor(
    userPreferences: UserPreferences
) : ViewModel() {

    val stateFlow = combine(
        userPreferences.isRedoseShownFlow,
        userPreferences.redoseOnsetFractionFlow,
        userPreferences.redoseComeupFractionFlow,
        userPreferences.redosePeakFractionFlow
    ) { show, onset, comeup, peak ->
        RedoseChipState(
            show = show,
            params = RedoseParameters.sanitize(onset, comeup, peak)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RedoseChipState()
    )
}

/**
 * Subscribe to the redose-chip preferences once per parent screen so
 * large lists don't create N independent collectors. The returned
 * [State] can then be passed to each [RedoseChip] cheaply.
 */
@Composable
fun rememberRedoseChipState(
    viewModel: RedoseChipViewModel = hiltViewModel()
): State<RedoseChipState> = viewModel.stateFlow.collectAsState()

private val REDOSE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Renders a compact "next redose" chip next to an ingestion row. Returns
 * silently if the feature is disabled in settings or the substance has
 * insufficient duration data to compute a meaningful time.
 *
 * Callers should hoist the [state] once per screen via
 * [rememberRedoseChipState] to avoid N preference subscriptions in a list.
 */
@Composable
fun RedoseChip(
    ingestionTime: Instant,
    roaDuration: RoaDuration?,
    state: RedoseChipState,
    modifier: Modifier = Modifier,
) {
    if (!state.show) return
    val redoseAt = remember(ingestionTime, roaDuration, state.params) {
        computeRedoseTime(ingestionTime, roaDuration, state.params)
    } ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Replay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(
            text = "Redose ≥ ${REDOSE_TIME_FORMATTER.format(redoseAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
