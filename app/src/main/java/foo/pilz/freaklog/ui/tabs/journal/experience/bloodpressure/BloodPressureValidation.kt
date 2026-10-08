package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure

// Health Connect panics outside of these ranges
const val MIN_SYSTOLIC = 20
const val MAX_SYSTOLIC = 200
const val MIN_DIASTOLIC = 10
const val MAX_DIASTOLIC = 180

data class BloodPressureFieldErrors(val systolic: String?, val diastolic: String?)

fun bloodPressureFieldErrors(systolic: String, diastolic: String): BloodPressureFieldErrors {
    val sys = systolic.toDoubleOrNull()
    val dia = diastolic.toDoubleOrNull()
    val systolicError = when {
        systolic.isBlank() -> null
        sys == null -> "Enter a number"
        sys !in MIN_SYSTOLIC.toDouble()..MAX_SYSTOLIC.toDouble() ->
            "Must be between $MIN_SYSTOLIC and $MAX_SYSTOLIC mmHg"
        dia != null && sys <= dia -> "Must be above diastolic"
        else -> null
    }
    val diastolicError = when {
        diastolic.isBlank() -> null
        dia == null -> "Enter a number"
        dia !in MIN_DIASTOLIC.toDouble()..MAX_DIASTOLIC.toDouble() ->
            "Must be between $MIN_DIASTOLIC and $MAX_DIASTOLIC mmHg"
        else -> null
    }
    return BloodPressureFieldErrors(systolicError, diastolicError)
}

fun isBloodPressureInputValid(systolic: String, diastolic: String): Boolean {
    val sys = systolic.toDoubleOrNull() ?: return false
    val dia = diastolic.toDoubleOrNull() ?: return false
    return sys in MIN_SYSTOLIC.toDouble()..MAX_SYSTOLIC.toDouble() &&
        dia in MIN_DIASTOLIC.toDouble()..MAX_DIASTOLIC.toDouble() &&
        sys > dia
}
