package foo.pilz.freaklog.ui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val STATE_TIMEOUT_MS = 5_000L

fun <T> Flow<T>.stateInVm(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS), initialValue)
