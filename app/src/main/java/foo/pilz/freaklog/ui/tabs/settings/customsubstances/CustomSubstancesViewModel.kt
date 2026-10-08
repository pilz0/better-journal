package foo.pilz.freaklog.ui.tabs.settings.customsubstances

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substanceshare.ImportFailure
import foo.pilz.freaklog.data.substanceshare.ImportResult
import foo.pilz.freaklog.data.substanceshare.SharedSubstance
import foo.pilz.freaklog.data.substanceshare.parseAndImport
import foo.pilz.freaklog.data.substanceshare.resolveCollisionKeepBoth
import foo.pilz.freaklog.data.substanceshare.resolveCollisionReplace
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomSubstancesViewModel @Inject constructor(
    private val customRepo: CustomSubstanceRepository,
    private val experienceRepo: ExperienceRepository,
) : ViewModel() {

    private val _searchTextFlow = MutableStateFlow("")
    val searchTextFlow = _searchTextFlow.asStateFlow()

    private val _pendingCollision = MutableStateFlow<SharedSubstance?>(null)
    val pendingCollision = _pendingCollision.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private val allCustomSubstancesFlow = experienceRepo.getCustomSubstancesFlow()

    val filteredCustomSubstancesFlow =
        combine(allCustomSubstancesFlow, _searchTextFlow) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) }
        }.stateInVm(viewModelScope, emptyList())

    fun onSearch(text: String) {
        viewModelScope.launch { _searchTextFlow.emit(text) }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val result = parseAndImport(context, uri, customRepo)) {
                is ImportResult.Imported -> _snackbar.value = "Imported ${result.name}"
                is ImportResult.CollisionPending -> _pendingCollision.value = result.payload
                is ImportResult.Failed -> _snackbar.value = when (result.reason) {
                    ImportFailure.OpenStream -> "Could not open file"
                    ImportFailure.Parse -> "Invalid substance file"
                    ImportFailure.UnsupportedFormat -> "Unsupported file format"
                }
            }
        }
    }

    fun resolveReplace() {
        val payload = _pendingCollision.value ?: return
        viewModelScope.launch {
            resolveCollisionReplace(payload, customRepo)
            _snackbar.value = "Imported ${payload.name}"
            _pendingCollision.value = null
        }
    }

    fun resolveKeepBoth() {
        val payload = _pendingCollision.value ?: return
        viewModelScope.launch {
            val existingNames =
                allCustomSubstancesFlow.firstOrNull().orEmpty().map { it.name }.toSet()
            val newName = resolveCollisionKeepBoth(payload, existingNames, customRepo)
            _snackbar.value = "Imported $newName"
            _pendingCollision.value = null
        }
    }

    fun resolveCancel() {
        _pendingCollision.value = null
        _snackbar.value = "Import cancelled"
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }
}
