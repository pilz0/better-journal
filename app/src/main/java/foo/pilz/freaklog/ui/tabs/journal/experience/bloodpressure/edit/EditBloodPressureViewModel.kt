package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.edit

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Pressure
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.ui.main.navigation.graphs.EditBloodPressureRoute
import foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.bloodPressureFieldErrors
import foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.isBloodPressureInputValid
import foo.pilz.freaklog.ui.utils.getInstant
import foo.pilz.freaklog.ui.utils.getLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EditBloodPressureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    state: SavedStateHandle
) : ViewModel() {
    private val recordId = state.toRoute<EditBloodPressureRoute>().recordId

    var systolic by mutableStateOf("")
    var diastolic by mutableStateOf("")
    var localDateTimeFlow = MutableStateFlow(LocalDateTime.now())

    val fieldErrors get() = bloodPressureFieldErrors(systolic, diastolic)
    val isInputValid get() = isBloodPressureInputValid(systolic, diastolic)

    private var metadata: Metadata? = null

    init {
        viewModelScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val record = client.readRecord(BloodPressureRecord::class, recordId).record
                metadata = record.metadata
                systolic = record.systolic.inMillimetersOfMercury.toString()
                diastolic = record.diastolic.inMillimetersOfMercury.toString()
                localDateTimeFlow.emit(record.time.getLocalDateTime())
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Couldn't load blood pressure measurement: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun onChangeTime(newLocalDateTime: LocalDateTime) {
        viewModelScope.launch {
            localDateTimeFlow.emit(newLocalDateTime)
        }
    }

    fun onChangeSystolic(newSystolic: String) {
        systolic = newSystolic
    }

    fun onChangeDiastolic(newDiastolic: String) {
        diastolic = newDiastolic
    }

    fun onDoneTap() {
        if (!isInputValid) return

        val metadata = metadata ?: return
        val systolic = systolic.toDoubleOrNull() ?: return
        val diastolic = diastolic.toDoubleOrNull() ?: return

        val record = BloodPressureRecord(
            time = localDateTimeFlow.value.getInstant(),
            zoneOffset = null,
            metadata = metadata,
            systolic = Pressure.millimetersOfMercury(systolic),
            diastolic = Pressure.millimetersOfMercury(diastolic),
        )
        viewModelScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                client.updateRecords(listOf(record))
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Couldn't update blood pressure measurement: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                client.deleteRecords(BloodPressureRecord::class, listOf(recordId), emptyList())
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Couldn't delete blood pressure measurement: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
