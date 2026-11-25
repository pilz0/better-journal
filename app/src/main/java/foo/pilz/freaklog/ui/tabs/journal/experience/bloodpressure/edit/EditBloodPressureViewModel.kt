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
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toPreservedString
import foo.pilz.freaklog.ui.utils.getInstant
import foo.pilz.freaklog.ui.utils.getLocalDateTime
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class EditBloodPressureViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    state: SavedStateHandle,
) : ViewModel() {
  private val recordId = state.toRoute<EditBloodPressureRoute>().recordId

  var systolic by mutableStateOf("")
  var diastolic by mutableStateOf("")
  var localDateTimeFlow = MutableStateFlow(LocalDateTime.now())

  val fieldErrors
    get() = bloodPressureFieldErrors(systolic, diastolic)

  val isInputValid
    get() = isBloodPressureInputValid(systolic, diastolic)

  private var metadata: Metadata? = null
  private var isBusy = false

  init {
    viewModelScope.launch {
      try {
        val client = HealthConnectClient.getOrCreate(context)
        val record = client.readRecord(BloodPressureRecord::class, recordId).record
        metadata = record.metadata
        systolic = record.systolic.inMillimetersOfMercury.toPreservedString()
        diastolic = record.diastolic.inMillimetersOfMercury.toPreservedString()
        localDateTimeFlow.emit(record.time.getLocalDateTime())
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        Toast.makeText(
                context,
                "Couldn't load blood pressure measurement: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG,
            )
            .show()
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

  fun onDoneTap(onDone: () -> Unit = {}) {
    if (!isInputValid || isBusy) return

    val metadata = metadata ?: return
    val systolicValue = systolic.trim().replace(',', '.').toDoubleOrNull() ?: return
    val diastolicValue = diastolic.trim().replace(',', '.').toDoubleOrNull() ?: return
    isBusy = true

    val record =
        BloodPressureRecord(
            time = localDateTimeFlow.value.getInstant(),
            zoneOffset = null,
            metadata = metadata,
            systolic = Pressure.millimetersOfMercury(systolicValue),
            diastolic = Pressure.millimetersOfMercury(diastolicValue),
        )
    viewModelScope.launch {
      try {
        val client = HealthConnectClient.getOrCreate(context)
        client.updateRecords(listOf(record))
        withContext(Dispatchers.Main) { onDone() }
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        Toast.makeText(
                context,
                "Couldn't update blood pressure measurement: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG,
            )
            .show()
      } finally {
        isBusy = false
      }
    }
  }

  fun delete(onDone: () -> Unit = {}) {
    if (isBusy) return
    isBusy = true
    viewModelScope.launch {
      try {
        val client = HealthConnectClient.getOrCreate(context)
        client.deleteRecords(BloodPressureRecord::class, listOf(recordId), emptyList())
        withContext(Dispatchers.Main) { onDone() }
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        Toast.makeText(
                context,
                "Couldn't delete blood pressure measurement: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG,
            )
            .show()
      } finally {
        isBusy = false
      }
    }
  }
}
