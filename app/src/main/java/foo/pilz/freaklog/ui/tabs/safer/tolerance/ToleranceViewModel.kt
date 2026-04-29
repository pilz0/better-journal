package foo.pilz.freaklog.ui.tabs.safer.tolerance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ToleranceViewModel @Inject constructor(
    private val toleranceCalculator: ToleranceCalculator,
    private val substanceRepo: SubstanceRepository,
    private val experienceRepo: ExperienceRepository,
) : ViewModel() {

    private val _recentTolerances = MutableStateFlow<List<ToleranceEstimate>>(emptyList())
    val recentTolerances: StateFlow<List<ToleranceEstimate>> = _recentTolerances

    private val _searchedTolerance = MutableStateFlow<ToleranceEstimate?>(null)
    val searchedTolerance: StateFlow<ToleranceEstimate?> = _searchedTolerance

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    var searchText by mutableStateOf("")
        private set

    val searchSuggestions: List<String> get() {
        if (searchText.isBlank()) return emptyList()
        val lower = searchText.lowercase()
        return substanceRepo.getAllSubstances()
            .map { it.name }
            .filter { it.lowercase().contains(lower) }
            .take(5)
    }

    init {
        viewModelScope.launch {
            val now = Instant.now()
            val since = now.minus(90, ChronoUnit.DAYS)
            val recentIngestions = experienceRepo.getLatestIngestionOfEverySubstanceSinceDate(since)
            val substanceNames = recentIngestions.map { it.substanceName }.distinct()

            val estimates = substanceNames.mapNotNull { name ->
                toleranceCalculator.calculate(name)
            }.filter { estimate ->
                val lastUse = estimate.lastIngestionTime ?: return@filter false
                val daysSinceUse = Duration.between(lastUse, now).toDays()
                estimate.toleranceLevel > 0f || daysSinceUse <= estimate.zeroDays.toLong() + 7
            }.sortedByDescending { it.toleranceLevel }

            _recentTolerances.value = estimates
            _isLoading.value = false
        }
    }

    fun onSearchTextChange(text: String) {
        searchText = text
        _searchedTolerance.value = null
    }

    fun onSelectSubstance(substanceName: String) {
        searchText = substanceName
        viewModelScope.launch {
            _searchedTolerance.value = toleranceCalculator.calculate(substanceName)
        }
    }
}
