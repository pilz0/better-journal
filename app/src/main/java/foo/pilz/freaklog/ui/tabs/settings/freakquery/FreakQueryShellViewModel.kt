package foo.pilz.freaklog.ui.tabs.settings.freakquery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndm4.freakquery.FreakQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FreakQueryShellEntry(
    val query: String,
    val output: String,
)

data class FreakQueryShellState(
    val input: String = "",
    val isRunning: Boolean = false,
    val logCount: Int = 0,
    val history: List<FreakQueryShellEntry> = emptyList(),
)

@HiltViewModel
class FreakQueryShellViewModel @Inject constructor(
    private val freakQueryRepository: FreakQueryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FreakQueryShellState())
    val state: StateFlow<FreakQueryShellState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val count = freakQueryRepository.getLogs().size
            _state.update { it.copy(logCount = count) }
        }
    }

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
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

    private fun runQuery(query: String, clearInput: Boolean) {
        if (query == ".clear") {
            _state.update { it.copy(input = if (clearInput) "" else it.input, history = emptyList()) }
            return
        }

        if (query == ".help") {
            _state.update {
                it.copy(
                    input = if (clearInput) "" else it.input,
                    history = it.history + FreakQueryShellEntry(
                        query = query,
                        output = "Examples: count, last, last|dose, month|count, ratio=route, top_substances, sequence"
                    )
                )
            }
            return
        }

        // Synchronously mark running so concurrent runCurrentInput/runExample
        // calls see the guard before the coroutine has a chance to start.
        _state.update { it.copy(isRunning = true, input = if (clearInput) "" else it.input) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = runCatching {
                    if (query == "version") {
                        FreakQuery.VERSION
                    } else {
                        freakQueryRepository.query(query)
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
