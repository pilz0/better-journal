package foo.pilz.freaklog.ui.tabs.search.substance

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute

/** A stable colour per route, used to tell routes apart in lists. */
val AdministrationRoute.color: AdaptiveColor
    get() = AdaptiveColor.entries.filter { it.isPreferred }.let { it[ordinal % it.size] }

@Composable
fun RouteColorBar(administrationRoute: AdministrationRoute) {
    val isDarkTheme = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = administrationRoute.color.getComposeColor(isDarkTheme),
        modifier = Modifier.size(width = 4.dp, height = 16.dp)
    ) {}
}
