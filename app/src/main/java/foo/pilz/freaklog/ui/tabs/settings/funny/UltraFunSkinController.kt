package foo.pilz.freaklog.ui.tabs.settings.funny

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Payload of one ultra fun mode disguise (Teams, Outlook, Excel). */
interface UltraFunSkin

/**
 * Holds the one disguise shown over the app. Showing a skin replaces the current one and stops its
 * updates, so skins never stack and two sources never fight over the screen.
 */
@Singleton
class UltraFunSkinController @Inject constructor() {
  private val _state = MutableStateFlow<UltraFunSkin?>(null)
  val state: StateFlow<UltraFunSkin?> = _state.asStateFlow()
  private var job: Job? = null

  /** Shows the skin and keeps it updated from [payloads] (collected in [scope]) until dismissed. */
  fun show(scope: CoroutineScope, payloads: Flow<UltraFunSkin>) {
    job?.cancel()
    job = scope.launch { payloads.collect { _state.value = it } }
  }

  fun dismiss() {
    job?.cancel()
    job = null
    _state.value = null
  }

  inline fun <reified T : UltraFunSkin> dismissIfShowing() {
    if (state.value is T) dismiss()
  }
}

@HiltViewModel
class UltraFunSkinHostViewModel @Inject constructor(val controller: UltraFunSkinController) :
    ViewModel()
