package foo.pilz.freaklog.data.experienceshare

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import foo.pilz.freaklog.ui.theme.JournalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

private const val SHARE_WIDTH_PX = 1440

suspend fun shareExperienceImage(
    activity: ComponentActivity,
    sanitizedTitle: String,
    shareText: String,
    content: @Composable () -> Unit,
) {
    val bitmap = renderComposableToBitmap(activity, SHARE_WIDTH_PX, content)
    val uri = try {
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "shared_experiences").apply { mkdirs() }
            val file = File(dir, "$sanitizedTitle-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        }
    } finally {
        bitmap.recycle()
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (shareText.isNotBlank()) {
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    activity.startActivity(chooser)
}

suspend fun renderComposableToBitmap(
    activity: ComponentActivity,
    widthPx: Int,
    content: @Composable () -> Unit,
): Bitmap = withContext(Dispatchers.Main) {
    val decor = activity.findViewById<ViewGroup>(android.R.id.content)
    val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        alpha = 0f
        setContent {
            JournalTheme {
                content()
            }
        }
    }
    decor.addView(
        composeView,
        ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    )

    try {
        awaitNextDraw(composeView)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        val bitmap =
            createBitmap(composeView.measuredWidth, composeView.measuredHeight.coerceAtLeast(1))
        composeView.draw(Canvas(bitmap))
        bitmap
    } finally {
        decor.removeView(composeView)
    }
}

private suspend fun awaitNextDraw(view: View) = suspendCancellableCoroutine<Unit> { cont ->
    val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            view.viewTreeObserver.removeOnPreDrawListener(this)
            if (cont.isActive) cont.resume(Unit)
            return true
        }
    }
    view.viewTreeObserver.addOnPreDrawListener(listener)
    cont.invokeOnCancellation {
        view.viewTreeObserver.removeOnPreDrawListener(listener)
    }
}
