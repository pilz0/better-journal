package foo.pilz.freaklog.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JournalClientAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val access = JournalClientAccess(context)
    private val clientUid = 22345
    private val clientName = "example.journal.helper"

    private fun install(signature: String) {
        val signing = ReflectionHelpers.callConstructor(SigningInfo::class.java)
        Shadows.shadowOf(signing).setSignatures(arrayOf(Signature(signature)))
        val info = PackageInfo().apply {
            packageName = clientName
            applicationInfo = ApplicationInfo().apply {
                packageName = clientName
                uid = clientUid
            }
            signingInfo = signing
        }
        Shadows.shadowOf(context.packageManager).installPackage(info)
    }

    @Before
    fun setUp() {
        context.getSharedPreferences("journal_clients", Context.MODE_PRIVATE).edit().clear().commit()
        install("1234")
        ShadowBinder.setCallingUid(clientUid)
    }

    @Test
    fun externalReadersNeedOwnerApprovalAndRevocationIsImmediate() {
        assertThrows(SecurityException::class.java) { access.enforce(clientName) }
        val client = access.clients().single()
        assertFalse(client.approved)
        access.setApproved(client, true)
        access.enforce(clientName)
        access.setApproved(client, false)
        assertThrows(SecurityException::class.java) { access.enforce(clientName) }
    }

    @Test
    fun changedSigningIdentityCannotReuseApproval() {
        assertThrows(SecurityException::class.java) { access.enforce(clientName) }
        access.setApproved(access.clients().single(), true)
        install("5678")
        assertThrows(SecurityException::class.java) { access.enforce(clientName) }
    }

    @Test
    fun spoofedPackageAndMissingCallerCannotRequestAccess() {
        assertThrows(SecurityException::class.java) { access.enforce("trusted.someone.else") }
        assertThrows(SecurityException::class.java) { access.enforce(null) }
        assertTrue(access.clients().isEmpty())
    }

    @Test
    fun appCanReadItsOwnProvider() {
        ShadowBinder.setCallingUid(context.applicationInfo.uid)
        access.enforce(null)
        assertTrue(access.clients().isEmpty())
    }
}
