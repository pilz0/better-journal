/*
 * Adapted from PsychonautWiki Journal (GPL v3).
 */

package foo.pilz.freaklog.provider

import android.content.Context
import android.net.Uri

/**
 * Contract that defines ContentProvider URIs, paths, and column names so the
 * Psychonaut Network Helper can read ingestion data.
 */
object JournalContract {
    const val PERMISSION_READ = "foo.pilz.freaklog.permission.READ_JOURNAL"

    private const val AUTHORITY_SUFFIX = ".provider"

    const val PATH_INGESTIONS = "ingestions"
    const val PATH_INGESTIONS_PUBLIC = "ingestions/public"
    const val PATH_EXPERIENCES = "experiences"
    const val PATH_INGESTION_CHANGES = "ingestion_changes"
    const val PATH_INGESTION_CHANGES_META = "ingestion_changes/meta"

    const val QUERY_SINCE = "since"
    const val QUERY_LIMIT = "limit"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun ingestionsUri(context: Context): Uri = base(context, PATH_INGESTIONS)
    fun ingestionsPublicUri(context: Context): Uri = base(context, PATH_INGESTIONS_PUBLIC)
    fun experiencesUri(context: Context): Uri = base(context, PATH_EXPERIENCES)
    fun ingestionChangesUri(context: Context): Uri = base(context, PATH_INGESTION_CHANGES)

    private fun base(context: Context, path: String): Uri =
        Uri.parse("content://${authority(context)}/$path")

    object Ingestions {
        const val ID = "_id"
        const val SUBSTANCE_NAME = "substance_name"
        const val TIME_EPOCH_S = "time_epoch_s"
        const val END_TIME_EPOCH_S = "end_time"
        const val CREATION_DATE_EPOCH_S = "creation_date"
        const val ADMINISTRATION_ROUTE = "administration_route"
        const val DOSE = "dose"
        const val IS_DOSE_ESTIMATE = "is_dose_estimate"
        const val ESTIMATED_DOSE_SD = "estimated_dose_sd"
        const val UNITS = "units"
        const val EXPERIENCE_ID = "experience_id"
        const val CUSTOM_UNIT_ID = "custom_unit_id"
        const val NOTES = "notes"
        const val STOMACH_FULLNESS = "stomach_fullness"
        const val CONSUMER_NAME = "consumer_name"
        const val CATEGORY = "category"
    }

    object Changes {
        const val SEQ = "seq"
        const val INGESTION_ID = "ingestion_id"
        const val OP = "op"
        const val OP_INSERT = "INSERT"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }

    object ChangesMeta {
        const val MIN_SEQ = "min_seq"
        const val MAX_SEQ = "max_seq"
        const val ROW_COUNT = "row_count"
    }

    object Experiences {
        const val ID = "_id"
        const val TITLE = "title"
        const val TEXT = "text"
        const val CREATION_DATE_EPOCH_S = "creation_date_epoch_s"
        const val SORT_DATE_EPOCH_S = "sort_date_epoch_s"
        const val IS_FAVORITE = "is_favorite"
    }
}
