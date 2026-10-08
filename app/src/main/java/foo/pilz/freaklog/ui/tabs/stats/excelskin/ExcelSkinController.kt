package foo.pilz.freaklog.ui.tabs.stats.excelskin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.ui.tabs.stats.StatItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ExcelSkinPayload(
    val statItems: List<StatItem>,
    val fileName: String,
)

@Singleton
class ExcelSkinController @Inject constructor() {
    private val _state = MutableStateFlow<ExcelSkinPayload?>(null)
    val state: StateFlow<ExcelSkinPayload?> = _state.asStateFlow()

    fun show(payload: ExcelSkinPayload) {
        _state.value = payload
    }

    fun dismiss() {
        _state.value = null
    }
}

@HiltViewModel
class ExcelSkinHostViewModel @Inject constructor(
    val controller: ExcelSkinController
) : ViewModel()
