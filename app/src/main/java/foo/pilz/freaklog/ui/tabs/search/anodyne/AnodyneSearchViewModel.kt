package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AnodyneSearchState {
    data object Loading : AnodyneSearchState
    data class Success(val entries: List<AnodyneIndexEntry>) : AnodyneSearchState
    data class Error(val message: String) : AnodyneSearchState
}

@HiltViewModel
class AnodyneSearchViewModel @Inject constructor(
    private val repository: AnodyneRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val initialQuery: String = savedStateHandle.get<String>("query") ?: ""
    private val _searchQuery = MutableStateFlow(initialQuery)
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val queryFlow: StateFlow<String> = _searchQuery.asStateFlow()

    private val allEntriesFlow = MutableStateFlow<List<AnodyneIndexEntry>>(emptyList())
    private val isLoadingFlow = MutableStateFlow(true)
    private val errorMessageFlow = MutableStateFlow<String?>(null)

    val filteredEntries: StateFlow<List<AnodyneIndexEntry>> = combine(
        allEntriesFlow,
        _searchQuery
    ) { allEntries, query ->
        filterAndRank(allEntries, query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<AnodyneSearchState> = combine(
        filteredEntries,
        isLoadingFlow,
        errorMessageFlow
    ) { filtered, isLoading, error ->
        if (isLoading && filtered.isEmpty() && error == null) {
            AnodyneSearchState.Loading
        } else if (error != null && filtered.isEmpty()) {
            AnodyneSearchState.Error(error)
        } else {
            AnodyneSearchState.Success(filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnodyneSearchState.Loading)

    init {
        loadIndex()
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onQueryChange(newQuery: String) {
        onSearchQueryChange(newQuery)
    }

    fun refresh() {
        loadIndex(forceRefresh = true)
    }

    private fun loadIndex(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingFlow.value = true
            errorMessageFlow.value = null
            val result = repository.getIndexEntries()
            result.fold(
                onSuccess = { entries ->
                    allEntriesFlow.value = entries
                },
                onFailure = { error ->
                    errorMessageFlow.value = error.message ?: "Failed to load index"
                }
            )
            isLoadingFlow.value = false
        }
    }

    internal fun filterAndRank(entries: List<AnodyneIndexEntry>, query: String): List<AnodyneIndexEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return entries.sortedBy { it.title.lowercase() }
        }

        return entries.mapNotNull { entry ->
            val titleLower = entry.title.lowercase()
            val abbrevLowers = entry.abbreviations.map { it.lowercase() }
            val classLowers = entry.classes.map { it.lowercase() }

            val score = when {
                titleLower == q -> 0
                abbrevLowers.any { it == q } -> 1
                titleLower.startsWith(q) -> 2
                abbrevLowers.any { it.startsWith(q) } -> 3
                titleLower.contains(q) -> 4
                abbrevLowers.any { it.contains(q) } -> 5
                classLowers.any { it.contains(q) } -> 6
                else -> -1
            }

            if (score >= 0) score to entry else null
        }
            .sortedWith(compareBy({ it.first }, { it.second.title.lowercase() }))
            .map { it.second }
    }
}
