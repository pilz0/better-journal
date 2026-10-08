package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.EditIntakeLimitRoute
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

enum class WindowMode { DAY, WEEK, MONTH, CUSTOM }

enum class CustomWindowUnit(val seconds: Long, val label: String) {
    HOURS(3_600, "Hours"),
    DAYS(86_400, "Days"),
    WEEKS(604_800, "Weeks"),
}

data class RoaDoseInfo(
    val route: AdministrationRoute,
    val roaDose: RoaDose,
)

@HiltViewModel
class EditIntakeLimitViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    private val route = state.toRoute<EditIntakeLimitRoute>()
    val isEditing: Boolean = route.limitId >= 0

    var substanceName by mutableStateOf(route.substanceName)
        private set

    var limitType by mutableStateOf(IntakeLimitType.DOSE)
    var maxDoseText by mutableStateOf("")
    var unit by mutableStateOf("mg")
    var maxCountText by mutableStateOf("")
    var warningPercentText by mutableStateOf(IntakeLimit.DEFAULT_WARNING_PERCENT.toString())
    var isEnabled by mutableStateOf(true)

    var discoveredUnits by mutableStateOf(STANDARD_DOSE_UNITS)
        private set
    var doseInfos by mutableStateOf<List<RoaDoseInfo>>(emptyList())
        private set
    private var userEditedUnit = false

    fun onUnitChange(newUnit: String) {
        unit = newUnit
        userEditedUnit = true
    }

    var windowMode by mutableStateOf(WindowMode.DAY)
    var customWindowValueText by mutableStateOf("1")
    var customWindowUnit by mutableStateOf(CustomWindowUnit.DAYS)

    private var existingId = 0
    private var creationDate: Instant = Instant.now()

    init {
        viewModelScope.launch {
            if (isEditing) load()
            discoverFromSubstance()
        }
    }

    private suspend fun discoverFromSubstance() {
        val infos = roaDoseInfosForSubstance(substanceName)
        doseInfos = infos
        val substanceUnits = infos.map { it.roaDose.units }
        discoveredUnits = suggestedDoseUnits(substanceUnits)
        if (!isEditing && !userEditedUnit) {
            unit = defaultDoseUnit(substanceUnits)
        }
    }

    private suspend fun roaDoseInfosForSubstance(name: String): List<RoaDoseInfo> {
        substanceRepo.getSubstance(name)?.let { substance ->
            val infos = substance.roas.mapNotNull { roa ->
                roa.roaDose?.let { RoaDoseInfo(roa.route, it) }
            }
            if (infos.isNotEmpty()) return infos
        }
        // Custom substances carry no dose ranges yet.
        return emptyList()
    }

    private suspend fun load() {
        val limit = experienceRepo.getIntakeLimit(route.limitId) ?: return
        existingId = limit.id
        creationDate = limit.creationDate
        substanceName = limit.substanceName
        limitType = limit.limitType
        maxDoseText = limit.maxDose?.toReadableString() ?: ""
        unit = limit.unit ?: "mg"
        maxCountText = limit.maxCount?.toString() ?: ""
        warningPercentText = limit.warningPercent.toString()
        isEnabled = limit.isEnabled
        applyWindowSeconds(limit.windowSeconds)
    }

    private fun applyWindowSeconds(seconds: Long) {
        when (seconds) {
            86_400L -> windowMode = WindowMode.DAY
            604_800L -> windowMode = WindowMode.WEEK
            2_592_000L -> windowMode = WindowMode.MONTH
            else -> {
                windowMode = WindowMode.CUSTOM
                val (value, wUnit) = when {
                    seconds % 604_800L == 0L -> seconds / 604_800L to CustomWindowUnit.WEEKS
                    seconds % 86_400L == 0L -> seconds / 86_400L to CustomWindowUnit.DAYS
                    else -> (seconds / 3_600L).coerceAtLeast(1) to CustomWindowUnit.HOURS
                }
                customWindowValueText = value.toString()
                customWindowUnit = wUnit
            }
        }
    }

    val windowSeconds: Long
        get() = when (windowMode) {
            WindowMode.DAY -> 86_400L
            WindowMode.WEEK -> 604_800L
            WindowMode.MONTH -> 2_592_000L
            WindowMode.CUSTOM -> (customWindowValueText.toLongOrNull()
                ?: 0L) * customWindowUnit.seconds
        }

    private val maxDose: Double? get() = maxDoseText.toDoubleOrNull()
    private val maxCount: Int? get() = maxCountText.toIntOrNull()
    private val warningPercent: Int
        get() = warningPercentText.toIntOrNull()?.coerceIn(1, 100)
            ?: IntakeLimit.DEFAULT_WARNING_PERCENT

    val isValid: Boolean
        get() {
            if (windowSeconds <= 0L) return false
            return when (limitType) {
                IntakeLimitType.DOSE -> (maxDose ?: 0.0) > 0.0 && unit.isNotBlank()
                IntakeLimitType.COUNT -> (maxCount ?: 0) > 0
            }
        }

    fun saveAndDismiss(dismiss: () -> Unit) {
        if (!isValid) return
        viewModelScope.launch {
            val limit = IntakeLimit(
                id = existingId,
                substanceName = substanceName,
                creationDate = creationDate,
                limitType = limitType,
                maxDose = if (limitType == IntakeLimitType.DOSE) maxDose else null,
                unit = if (limitType == IntakeLimitType.DOSE) unit.trim() else null,
                maxCount = if (limitType == IntakeLimitType.COUNT) maxCount else null,
                windowSeconds = windowSeconds,
                warningPercent = warningPercent,
                isEnabled = isEnabled,
            )
            if (isEditing) experienceRepo.update(limit) else experienceRepo.insert(limit)
            withContext(Dispatchers.Main) { dismiss() }
        }
    }

    fun deleteAndDismiss(dismiss: () -> Unit) {
        viewModelScope.launch {
            experienceRepo.getIntakeLimit(existingId)?.let { experienceRepo.delete(it) }
            withContext(Dispatchers.Main) { dismiss() }
        }
    }
}
