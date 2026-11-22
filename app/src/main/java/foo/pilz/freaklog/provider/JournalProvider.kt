package foo.pilz.freaklog.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import kotlinx.coroutines.flow.first

class JournalProvider : ContentProvider() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface ProviderEntryPoint {
    fun experienceDao(): ExperienceDao

    fun biometricAuthManager(): foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager

    fun userPreferences(): foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
  }

  private val code =
      object {
        val ingestions = 1
        val ingestionsPublic = 2
        val experiences = 3
        val ingestionById = 4
        val ingestionPublicById = 5
        val ingestionChanges = 6
        val ingestionChangesMeta = 7
      }
  private lateinit var matcher: UriMatcher

  private val entryPoint: ProviderEntryPoint by lazy {
    EntryPointAccessors.fromApplication(
        context!!.applicationContext,
        ProviderEntryPoint::class.java,
    )
  }

  private val dao: ExperienceDao by lazy { entryPoint.experienceDao() }

  /**
   * While app lock is on, approved readers get nothing either. This reads the saved setting: the
   * in-memory unlock state only describes this app's own UI, and the provider also serves callers
   * while the app is not running.
   */
  private fun enforceNotLocked() {
    if (android.os.Binder.getCallingUid() == context!!.applicationInfo.uid) return
    val locked =
        kotlinx.coroutines.runBlocking { entryPoint.userPreferences().isLockEnabledFlow.first() }
    if (locked) {
      throw SecurityException("The journal is locked. Turn off app lock in Freaklog to share it.")
    }
  }

  override fun onCreate(): Boolean {
    val authority = JournalContract.authority(context!!)
    matcher =
        UriMatcher(UriMatcher.NO_MATCH).apply {
          addURI(authority, JournalContract.PATH_INGESTIONS, code.ingestions)
          addURI(authority, JournalContract.PATH_INGESTIONS_PUBLIC, code.ingestionsPublic)
          addURI(authority, JournalContract.PATH_EXPERIENCES, code.experiences)
          addURI(authority, "${JournalContract.PATH_INGESTIONS}/#", code.ingestionById)
          addURI(authority, "${JournalContract.PATH_INGESTIONS_PUBLIC}/#", code.ingestionPublicById)
          addURI(authority, JournalContract.PATH_INGESTION_CHANGES, code.ingestionChanges)
          addURI(authority, JournalContract.PATH_INGESTION_CHANGES_META, code.ingestionChangesMeta)
        }
    return true
  }

  override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
  ): Cursor {
    JournalClientAccess(context!!.applicationContext).enforce(callingPackage)
    enforceNotLocked()
    val since = ProviderQueryParams.parseSince(uri.getQueryParameter(JournalContract.QUERY_SINCE))
    val limit = ProviderQueryParams.parseLimit(uri.getQueryParameter(JournalContract.QUERY_LIMIT))
    val cursor =
        when (matcher.match(uri)) {
          code.ingestions -> dao.providerIngestions(since, limit)
          code.ingestionsPublic -> dao.providerIngestionsPublic(since, limit)
          code.experiences -> dao.providerExperiences(limit)
          code.ingestionById -> dao.providerIngestionById(ContentUris.parseId(uri).toInt())
          code.ingestionPublicById ->
              dao.providerIngestionPublicById(ContentUris.parseId(uri).toInt())
          code.ingestionChanges -> {
            val sinceLong =
                ProviderQueryParams.parseSinceLong(
                    uri.getQueryParameter(JournalContract.QUERY_SINCE)
                )
            dao.providerIngestionChanges(sinceLong, limit)
          }
          code.ingestionChangesMeta -> dao.providerIngestionChangesMeta()
          else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    cursor.setNotificationUri(context!!.contentResolver, uri)
    return cursor
  }

  override fun getType(uri: Uri): String =
      when (matcher.match(uri)) {
        code.ingestions,
        code.ingestionsPublic -> "vnd.android.cursor.dir/vnd.${context!!.packageName}.ingestion"
        code.ingestionById,
        code.ingestionPublicById -> "vnd.android.cursor.item/vnd.${context!!.packageName}.ingestion"
        code.experiences -> "vnd.android.cursor.dir/vnd.${context!!.packageName}.experience"
        code.ingestionChanges,
        code.ingestionChangesMeta ->
            "vnd.android.cursor.dir/vnd.${context!!.packageName}.ingestion_change"
        else -> throw IllegalArgumentException("Unknown URI: $uri")
      }

  override fun insert(uri: Uri, values: ContentValues?): Uri =
      throw UnsupportedOperationException("Read-only provider")

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
  ): Int = throw UnsupportedOperationException("Read-only provider")

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
      throw UnsupportedOperationException("Read-only provider")
}
