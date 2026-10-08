package foo.pilz.freaklog.data.substances.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DefaultSubstanceColors @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    private val colorsByName: Map<String, Int> by lazy {
        val json = JSONObject(
            appContext.assets.open("colorMap.json").bufferedReader().use { it.readText() }
        )
        json.keys().asSequence()
            .associateWith { android.graphics.Color.parseColor(json.getString(it)) }
    }

    fun colorFor(substanceName: String): Int? = colorsByName[substanceName]

    fun colorOrRandom(substanceName: String): Int =
        colorsByName[substanceName] ?: (0xFF000000.toInt() or Random.nextInt(0x1000000))
}
