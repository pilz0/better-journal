/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.reminders

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asks the user for the [Manifest.permission.POST_NOTIFICATIONS] permission on the first
 * entry to the Reminders screen on API 33+ within a process. We deliberately ask only
 * once per process and respect [Activity.shouldShowRequestPermissionRationale] so that
 * users who have already declined aren't re-prompted on every navigation.
 */
@Composable
fun PostNotificationsPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* result is reflected by the system; nothing to do here */ },
    )
    LaunchedEffect(Unit) {
        if (RequestState.alreadyAskedThisProcess) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        // If the OS reports the user already denied this permission with "don't ask again",
        // showing the system prompt becomes a no-op and just trains the user to ignore it.
        // Skip the request and let the in-screen banner / settings deep-link guide them.
        val activity = context.findActivity()
        if (activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) &&
            RequestState.hasEverAsked
        ) {
            return@LaunchedEffect
        }
        RequestState.alreadyAskedThisProcess = true
        RequestState.hasEverAsked = true
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private object RequestState {
    /** True once the user has been prompted at least once in this process. */
    var alreadyAskedThisProcess: Boolean = false

    /**
     * True once the user has been prompted at least once in the current app session
     * (process lifetime). Not persisted: a fresh process will retry once on first entry,
     * which matches typical first-launch behaviour.
     */
    var hasEverAsked: Boolean = false
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
