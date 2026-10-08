package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TeamsSkinHostViewModel @Inject constructor(
    val controller: TeamsSkinController
) : ViewModel()
