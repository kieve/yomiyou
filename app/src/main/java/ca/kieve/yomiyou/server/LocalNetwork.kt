package ca.kieve.yomiyou.server

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Android 17 blocks the local network for apps that target it until the user allows it; the server is on the LAN. */
private const val LOCAL_NETWORK_API = 37

fun hasLocalNetwork(context: Context): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_API ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Whether the server can be reached; [ask] asks again, or once refused opens the app's settings. */
class LocalNetwork(val granted: Boolean, val ask: () -> Unit)

/** Asks for local network access once, where it's needed and not granted. */
@Composable
fun rememberLocalNetwork(): LocalNetwork {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocalNetwork(context)) }
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        refused = !it
    }
    LifecycleResumeEffect(Unit) {
        granted = hasLocalNetwork(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
    return LocalNetwork(granted) {
        if (refused) {
            val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}
