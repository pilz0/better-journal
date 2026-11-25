package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.ui.utils.stateInVm
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddSubstanceGroupViewModel
@Inject
constructor(
    private val groupRepo: SubstanceGroupRepository,
) : ViewModel() {

  private val _idFlow = MutableStateFlow<Int?>(null)
  val idFlow = _idFlow.asStateFlow()

  val nameFlow = MutableStateFlow("")

  private val persistLock = Mutex()
  private var draftDeleted = false
  private var isDeleting = false
  private val initJob: Job

  val itemsFlow =
      _idFlow
          .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else groupRepo.getWithItemsFlow(id).map { it?.sortedItems.orEmpty() }
          }
          .stateInVm(viewModelScope, emptyList<SubstanceGroupItem>())

  init {
    initJob = viewModelScope.launch {
      _idFlow.value = groupRepo.create(SubstanceGroup(name = ""))
      if (!draftDeleted && nameFlow.value.isNotBlank()) {
        persist()
      }
    }
  }

  fun onNameChange(value: String) {
    nameFlow.value = value
    persist()
  }

  private fun persist() {
    viewModelScope.launch {
      persistLock.withLock {
        if (draftDeleted) return@withLock
        val id = _idFlow.value ?: return@withLock
        groupRepo.update(SubstanceGroup(id = id, name = nameFlow.value.trim()))
      }
    }
  }

  fun removeItem(item: SubstanceGroupItem) {
    viewModelScope.launch { groupRepo.deleteItem(item) }
  }

  fun deleteDraft(onDone: () -> Unit = {}) {
    if (isDeleting) return
    draftDeleted = true
    isDeleting = true
    viewModelScope.launch {
      try {
        initJob.join()
        persistLock.withLock {
          val id = _idFlow.value ?: return@withLock
          groupRepo.delete(SubstanceGroup(id = id, name = nameFlow.value.trim()))
        }
      } finally {
        withContext(Dispatchers.Main) { onDone() }
      }
    }
  }
}
