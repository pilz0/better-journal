package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.add

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Pressure
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.bloodPressureFieldErrors
import foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.isBloodPressureInputValid
import foo.pilz.freaklog.ui.utils.getInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class AddBloodPressureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var systolic by mutableStateOf("")
    var diastolic by mutableStateOf("")
    var localDateTimeFlow = MutableStateFlow(LocalDateTime.now())

    val fieldErrors get() = bloodPressureFieldErrors(systolic, diastolic)
    val isInputValid get() = isBloodPressureInputValid(systolic, diastolic)

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
        val systolic = systolic.toDoubleOrNull() ?: return
        val diastolic = diastolic.toDoubleOrNull() ?: return
        val record = BloodPressureRecord(
            time = localDateTimeFlow.value.getInstant(),
            zoneOffset = null,
            metadata = Metadata.manualEntry(),
            systolic = Pressure.millimetersOfMercury(systolic),
            diastolic = Pressure.millimetersOfMercury(diastolic),
        )
        viewModelScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                client.insertRecords(listOf(record))
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Couldn't save blood pressure measurement: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}