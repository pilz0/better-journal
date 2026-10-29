package foo.pilz.freaklog.ui.tabs.journal.experience.recommendations

import android.content.Context
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import java.io.IOException

/** Intercept every Markdown/HTML image before Coil can fetch it or follow a redirect. */
internal fun assistantImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(
            Interceptor { chain ->
                ErrorResult(null, chain.request, IOException("Images are disabled in assistant replies"))
            }
        )
    }
    .build()
