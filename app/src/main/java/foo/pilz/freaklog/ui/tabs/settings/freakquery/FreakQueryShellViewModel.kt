package foo.pilz.freaklog.ui.tabs.settings.freakquery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndm4.freakquery.FreakQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FreakQueryShellEntry(
    val query: String,
    val output: String,
    val id: String = java.util.UUID.randomUUID().toString(),
)

data class FreakQueryShellState(
    val input: String = "",
    val isRunning: Boolean = false,
    val logCount: Int = 0,
    val history: List<FreakQueryShellEntry> = emptyList(),
    val canNavigateHistoryUp: Boolean = false,
    val canNavigateHistoryDown: Boolean = false,
)

@HiltViewModel
class FreakQueryShellViewModel @Inject constructor(
    private val freakQueryRepository: FreakQueryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FreakQueryShellState())
    val state: StateFlow<FreakQueryShellState> = _state.asStateFlow()

    private val sentQueries = mutableListOf<String>()
    private var historyCursor: Int? = null
    private var draftInput: String = ""

    init {
        viewModelScope.launch {
            val count = freakQueryRepository.getLogs().size
            _state.update { it.copy(logCount = count) }
        }
    }

    fun updateInput(value: String) {
        historyCursor = null
        draftInput = value
        val (canUp, canDown) = calculateNavState(null, sentQueries.size)
        _state.update {
            it.copy(
                input = value,
                canNavigateHistoryUp = canUp,
                canNavigateHistoryDown = canDown
            )
        }
    }

    fun navigateHistoryUp() {
        if (sentQueries.isEmpty()) return
        val currentCursor = historyCursor
        val newCursor = if (currentCursor == null) {
            draftInput = state.value.input
            sentQueries.lastIndex
        } else {
            (currentCursor - 1).coerceAtLeast(0)
        }
        historyCursor = newCursor
        val (canUp, canDown) = calculateNavState(newCursor, sentQueries.size)
        _state.update {
            it.copy(
                input = sentQueries[newCursor],
                canNavigateHistoryUp = canUp,
                canNavigateHistoryDown = canDown
            )
        }
    }

    fun navigateHistoryDown() {
        val currentCursor = historyCursor ?: return
        if (currentCursor < sentQueries.lastIndex) {
            val newCursor = currentCursor + 1
            historyCursor = newCursor
            val (canUp, canDown) = calculateNavState(newCursor, sentQueries.size)
            _state.update {
                it.copy(
                    input = sentQueries[newCursor],
                    canNavigateHistoryUp = canUp,
                    canNavigateHistoryDown = canDown
                )
            }
        } else {
            historyCursor = null
            val (canUp, canDown) = calculateNavState(null, sentQueries.size)
            _state.update {
                it.copy(
                    input = draftInput,
                    canNavigateHistoryUp = canUp,
                    canNavigateHistoryDown = canDown
                )
            }
        }
    }

    fun runCurrentInput() {
        val query = state.value.input.trim()
        if (query.isEmpty() || state.value.isRunning) return
        runQuery(query, clearInput = true)
    }

    fun runExample(query: String) {
        if (state.value.isRunning) return
        runQuery(query, clearInput = false)
    }

    fun clear() {
        _state.update { it.copy(history = emptyList()) }
    }

    private fun calculateNavState(cursor: Int?, sentSize: Int): Pair<Boolean, Boolean> {
        val canUp = if (sentSize == 0) false else (cursor == null || cursor > 0)
        val canDown = cursor != null
        return canUp to canDown
    }

    private fun runQuery(query: String, clearInput: Boolean) {
        sentQueries.add(query)
        historyCursor = null
        draftInput = ""
        val (canUp, canDown) = calculateNavState(null, sentQueries.size)

        if (query == ".clear") {
            _state.update {
                it.copy(
                    input = if (clearInput) "" else it.input,
                    history = emptyList(),
                    canNavigateHistoryUp = canUp,
                    canNavigateHistoryDown = canDown
                )
            }
            return
        }

        if (query == ".help") {
            _state.update {
                it.copy(
                    input = if (clearInput) "" else it.input,
                    history = it.history + FreakQueryShellEntry(
                        query = query,
                        output = "Examples: count, last, last|dose, month|count, ratio=route, top_substances, sequence"
                    ),
                    canNavigateHistoryUp = canUp,
                    canNavigateHistoryDown = canDown
                )
            }
            return
        }

        // Synchronously mark running so concurrent runCurrentInput/runExample
        // calls see the guard before the coroutine has a chance to start.
        _state.update {
            it.copy(
                isRunning = true,
                input = if (clearInput) "" else it.input,
                canNavigateHistoryUp = canUp,
                canNavigateHistoryDown = canDown
            )
        }

        viewModelScope.launch {
            try {
                val output = runCatching {
                    when {
                        query == "version" -> FreakQuery.VERSION
                        query.contains("{{") && query.contains("}}") -> freakQueryRepository.render(query)
                        else -> freakQueryRepository.query(query)
                    }
                }.getOrElse { throwable ->
                    throwable.message ?: throwable::class.java.simpleName
                }

                _state.update {
                    it.copy(
                        history = it.history + FreakQueryShellEntry(query, output.ifBlank { "(empty)" })
                    )
                }
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }
}
