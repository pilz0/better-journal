package foo.pilz.freaklog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Permission rationale contains no private journal data and is safe to open while locked. */
class HealthPermissionsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface {
          Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Health Connect data use", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Freaklog reads heart rate, sleep and blood pressure to show them alongside logged doses when " +
                    "you enable Vitals on timeline. History access is used for older timeline entries."
            )
            Text("You can write blood-pressure readings to Health Connect from an experience.")
            Text(
                "Medication export is optional. Only selected substances and your own logged doses in the last " +
                    "30 days are exported as self-reported records. Preview them in Settings → Health Connect & " +
                    "Google Health, then refresh manually."
            )
            Text(
                "Apps you authorize in Health Connect, including Google Health, can read these records. Enable " +
                    "Google Health Coach and its personalization separately if you want it to use them."
            )
            Text(
                "Freaklog's AI assistant can receive a summary of vitals only after you separately enable Share " +
                    "vitals with Freaklog AI. These summaries are sent to your configured AI provider."
            )
            Text(
                "Revoke permissions in Health Connect at any time. Remove medication export in Freaklog deletes " +
                    "only its dedicated medication source; it cannot retract copies held by other apps or AI " +
                    "conversations. Local files and clipboard copies are shared only when you choose those actions."
            )
            TextButton(onClick = ::finish) { Text("Close") }
          }
        }
      }
    }
  }
}
