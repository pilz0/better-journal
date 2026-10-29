package foo.pilz.freaklog.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import androidx.core.content.edit
import java.security.MessageDigest

/** Owner grants are pinned to both the installed package and its signing certificates. */
class JournalClientAccess(private val context: Context) {
    private val prefs = context.getSharedPreferences("journal_clients", Context.MODE_PRIVATE)

    data class Client(val packageName: String, val certificate: String, val approved: Boolean) {
        val key: String get() = "$packageName|$certificate"
    }

    @Suppress("DEPRECATION")
    private fun certificate(packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners?.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.sorted()?.joinToString(",")
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun enforce(packageName: String?) {
        if (Binder.getCallingUid() == context.applicationInfo.uid) return
        val (name, cert) = callerIdentity(packageName)
        val key = "$name|$cert"
        if (key in prefs.getStringSet("approved", emptySet()).orEmpty()) return
        synchronized(JournalClientAccess::class.java) {
            val pending = prefs.getStringSet("pending", emptySet()).orEmpty()
            if (pending.size < 32) prefs.edit { putStringSet("pending", pending + key) }
        }
        throw SecurityException("Journal access requires approval in Freaklog Settings > Privacy")
    }

    private fun callerIdentity(packageName: String?): Pair<String, String> {
        val names = context.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (packageName == null || packageName !in names) throw SecurityException("Invalid caller identity")
        val cert = certificate(packageName) ?: throw SecurityException("Caller signing identity is unavailable")
        return packageName to cert
    }

    fun clients(): List<Client> {
        val approved = prefs.getStringSet("approved", emptySet()).orEmpty()
        return (approved + prefs.getStringSet("pending", emptySet()).orEmpty()).mapNotNull { key ->
            val parts = key.split('|', limit = 2)
            if (parts.size != 2) null else Client(parts[0], parts[1], key in approved)
        }.sortedBy { it.packageName }
    }

    fun setApproved(client: Client, approved: Boolean) {
        synchronized(JournalClientAccess::class.java) {
            val grants = prefs.getStringSet("approved", emptySet()).orEmpty() - client.key
            val pending = prefs.getStringSet("pending", emptySet()).orEmpty() - client.key
            // A signing change requires a new, independently reviewed grant.
            check(!approved || certificate(client.packageName) == client.certificate) { "Client identity changed" }
            prefs.edit(commit = true) {
                putStringSet("approved", if (approved) grants + client.key else grants)
                putStringSet("pending", pending)
            }
        }
    }
}
