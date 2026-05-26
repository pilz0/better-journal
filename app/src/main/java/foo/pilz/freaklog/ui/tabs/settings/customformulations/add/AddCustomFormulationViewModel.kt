package foo.pilz.freaklog.ui.tabs.settings.customformulations.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomFormulationRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import foo.pilz.freaklog.data.room.experiences.entities.FormulationDurationOverrides
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddCustomFormulationViewModel @Inject constructor(
    private val repository: CustomFormulationRepository
) : ViewModel() {

    fun saveFormulation(
        substanceName: String,
        baseRoa: AdministrationRoute,
        name: String,
        onsetMin: Int?,
        onsetMax: Int?,
        comeupMin: Int?,
        comeupMax: Int?,
        peakMin: Int?,
        peakMax: Int?,
        offsetMin: Int?,
        offsetMax: Int?,
        totalMin: Int?,
        totalMax: Int?,
        afterglowMin: Int?,
        afterglowMax: Int?
    ) {
        viewModelScope.launch {
            val overrides = FormulationDurationOverrides(
                onsetMinMinutes = onsetMin,
                onsetMaxMinutes = onsetMax,
                comeupMinMinutes = comeupMin,
                comeupMaxMinutes = comeupMax,
                peakMinMinutes = peakMin,
                peakMaxMinutes = peakMax,
                offsetMinMinutes = offsetMin,
                offsetMaxMinutes = offsetMax,
                totalMinMinutes = totalMin,
                totalMaxMinutes = totalMax,
                afterglowMinMinutes = afterglowMin,
                afterglowMaxMinutes = afterglowMax
            )
            val formulation = CustomFormulation(
                substanceName = substanceName,
                baseRoa = baseRoa,
                name = name,
                durationOverrides = overrides
            )
            repository.insert(formulation)
        }
    }
}
