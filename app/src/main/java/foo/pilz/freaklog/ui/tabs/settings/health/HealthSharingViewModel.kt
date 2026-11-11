package foo.pilz.freaklog.ui.tabs.settings.health

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.health.HealthSharingPreferences
import foo.pilz.freaklog.data.health.MedicationExport
import foo.pilz.freaklog.data.health.MedicationHealthConnect
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.ui.tabs.settings.FileSystemConnection
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MedicationChoice(val name: String, val doses: Int)
data class HealthSharingState(
    val choices: List<MedicationChoice> = emptyList(),
    val selected: Set<String> = emptySet(),
    val aiVitals: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class HealthSharingViewModel @Inject constructor(
    private val dao: ExperienceDao,
    private val preferences: HealthSharingPreferences,
    private val medical: MedicationHealthConnect,
    private val files: FileSystemConnection,
    private val auth: BiometricAuthManager,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val preview = MutableStateFlow<MedicationExport?>(null)

    val state = combine(
        dao.getSortedIngestionsWithSubstanceCompanionsFlow(),
        preferences.medications,
        preferences.aiVitals,
    ) { rows, selected, ai ->
        preview.value = null
        val now = Instant.now()
        val counts = rows.filter {
            it.ingestion.consumerName == null &&
                it.ingestion.time >= now.minus(MedicationExport.WINDOW) && it.ingestion.time <= now
        }.groupingBy { it.ingestion.substanceName }.eachCount()
        HealthSharingState(
            choices = (counts.keys + selected).sorted().map { MedicationChoice(it, counts[it] ?: 0) },
            selected = selected,
            aiVitals = ai,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthSharingState())

    fun medicalAvailable() = medical.isAvailable()

    fun select(name: String, checked: Boolean) = action {
        val selected = preferences.medications.first()
        preferences.selectMedications(if (checked) selected + name else selected - name)
        preview.value = null
    }

    fun allowAi(allow: Boolean) = action { preferences.allowAiVitals(allow) }

    fun preparePreview() = action {
        preview.value = MedicationExport.create(
            dao.getSortedIngestionsWithSubstanceCompanionsFlow().first(),
            preferences.medications.first(),
            Instant.now(),
        )
    }

    fun sync() = action {
        val report = preview.value ?: return@action
        val count = medical.sync(report)
        message.value = "Shared $count logged doses with Health Connect."
    }

    fun removeExport() = action {
        medical.removeExport()
        message.value = "Removed Freaklog's medication export from Health Connect."
    }

    fun exportFile(uri: Uri) = action {
        val report = preview.value ?: return@action
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            check(!auth.shouldLock.value) { "Journal locked" }
            files.saveTextInUri(uri, report.bundle())
        }
        message.value = "Saved medication records."
    }

    /** Called immediately before handing a preview to the clipboard. */
    fun summary(): String? =
        if (auth.shouldLock.value) null else preview.value?.summary()

    private fun action(block: suspend () -> Unit) {
        if (busy.value || auth.shouldLock.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                if (!auth.shouldLock.value) block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Platform errors may include resource contents; never log or display them.
                message.value = "Sharing failed. Check Health Connect permissions and try again. A partial export may need refreshing."
            } finally {
                busy.value = false
            }
        }
    }
}
