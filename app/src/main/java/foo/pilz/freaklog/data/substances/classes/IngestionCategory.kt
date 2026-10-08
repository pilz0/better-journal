package foo.pilz.freaklog.data.substances.classes

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class IngestionCategory {
    MEDICINAL {
        override val displayText = "Medicinal"
    },
    RECREATIONAL {
        override val displayText = "Recreational"
    };

    abstract val displayText: String

    companion object {
        val DEFAULT_INGESTION_CATEGORY = RECREATIONAL
    }
}
