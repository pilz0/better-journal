package foo.pilz.freaklog.ui.tabs.settings.substancegroups

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.substanceshare.GroupImportResult
import foo.pilz.freaklog.data.substanceshare.ImportFailure
import foo.pilz.freaklog.data.substanceshare.SharedGroup
import foo.pilz.freaklog.data.substanceshare.parseAndImportGroup
import foo.pilz.freaklog.data.substanceshare.resolveGroupCollisionKeepBoth
import foo.pilz.freaklog.data.substanceshare.resolveGroupCollisionReplace
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubstanceGroupsViewModel @Inject constructor(
    private val groupRepo: SubstanceGroupRepository,
    private val customSubstanceRepo: CustomSubstanceRepository,
    private val experienceRepo: ExperienceRepository,
) : ViewModel() {

    private val _searchTextFlow = MutableStateFlow("")
    val searchTextFlow = _searchTextFlow.asStateFlow()

    private val _pendingCollision = MutableStateFlow<SharedGroup?>(null)
    val pendingCollision = _pendingCollision.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private val allGroupsFlow = groupRepo.getGroupsWithItemsFlow()

    val filteredGroupsFlow = combine(allGroupsFlow, _searchTextFlow) { list, query ->
        if (query.isBlank()) list
        else list.filter { group ->
            group.group.name.contains(query, ignoreCase = true)
                    || group.items.any { it.substanceName.contains(query, ignoreCase = true) }
        }
    }.stateInVm(viewModelScope, emptyList())

    fun onSearch(text: String) {
        viewModelScope.launch { _searchTextFlow.emit(text) }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val result =
                parseAndImportGroup(context, uri, groupRepo, customSubstanceRepo, experienceRepo)) {
                is GroupImportResult.Imported -> _snackbar.value = "Imported ${result.name}"
                is GroupImportResult.CollisionPending -> _pendingCollision.value = result.payload
                is GroupImportResult.Failed -> _snackbar.value = when (result.reason) {
                    ImportFailure.OpenStream -> "Could not open file"
                    ImportFailure.Parse -> "Invalid group file"
                    ImportFailure.UnsupportedFormat -> "Unsupported file format"
                }
            }
        }
    }

    fun resolveReplace() {
        val payload = _pendingCollision.value ?: return
        viewModelScope.launch {
            resolveGroupCollisionReplace(payload, groupRepo, customSubstanceRepo, experienceRepo)
            _snackbar.value = "Imported ${payload.name}"
            _pendingCollision.value = null
        }
    }

    fun resolveKeepBoth() {
        val payload = _pendingCollision.value ?: return
        viewModelScope.launch {
            val existingNames = allGroupsFlow.firstOrNull().orEmpty().map { it.group.name }.toSet()
            val newName = resolveGroupCollisionKeepBoth(
                payload,
                existingNames,
                groupRepo,
                customSubstanceRepo,
                experienceRepo
            )
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
