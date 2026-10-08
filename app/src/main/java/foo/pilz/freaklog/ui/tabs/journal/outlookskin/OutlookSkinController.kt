package foo.pilz.freaklog.ui.tabs.journal.outlookskin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OutlookSkinPayload(
    val rows: List<OutlookMailRow>,
)

@Singleton
class OutlookSkinController @Inject constructor() {
    private val _state = MutableStateFlow<OutlookSkinPayload?>(null)
    val state: StateFlow<OutlookSkinPayload?> = _state.asStateFlow()

    fun show(payload: OutlookSkinPayload) {
        _state.value = payload
    }

    fun dismiss() {
        _state.value = null
    }
}

@HiltViewModel
class OutlookSkinHostViewModel @Inject constructor(
    val controller: OutlookSkinController
) : ViewModel()
