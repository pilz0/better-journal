package foo.pilz.freaklog.ui.tabs.settings.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** LAN access is optional: hosted providers and on-device servers work without this grant. */
@Composable
internal fun AiLocalNetworkAccess() {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    var granted by remember { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    if (!granted) {
        Text("To use a server on your local network, allow local network access. LAN servers also require HTTPS.")
        TextButton(onClick = {
            if (denied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                )
            } else {
                launcher.launch(permission)
            }
        }) {
            Text(if (denied) "Open app permissions" else "Allow local network access")
        }
    }
}
