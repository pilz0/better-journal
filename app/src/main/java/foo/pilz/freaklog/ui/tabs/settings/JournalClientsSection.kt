package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dagger.hilt.android.EntryPointAccessors
import foo.pilz.freaklog.provider.JournalClientAccess
import foo.pilz.freaklog.provider.JournalProvider

@Composable
internal fun JournalClientsSection() {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val access = remember(context) { JournalClientAccess(context.applicationContext) }
    var clients by remember { mutableStateOf(access.clients()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { clients = access.clients() }
    Text("Journal access for other apps")
    Text("Apps must request access first. Allowing an app lets it read your journal, including notes, in the background.")
    if (clients.isEmpty()) Text("No apps have requested journal access.")
    clients.forEach { client ->
        Text(client.packageName)
        Text("Signing identity: ${client.certificate.take(16)}…")
        TextButton(onClick = {
            val auth = EntryPointAccessors.fromApplication(
                context.applicationContext,
                JournalProvider.ProviderEntryPoint::class.java
            ).biometricAuthManager()
            if (!auth.shouldLock.value) {
                access.setApproved(client, !client.approved)
                clients = access.clients()
            }
        }) { Text(if (client.approved) "Revoke journal access" else "Allow journal access") }
    }
}
