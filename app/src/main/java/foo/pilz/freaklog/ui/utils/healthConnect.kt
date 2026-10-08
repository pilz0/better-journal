package foo.pilz.freaklog.ui.utils

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient


fun isHealthConnectAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
