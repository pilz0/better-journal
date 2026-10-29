package foo.pilz.freaklog

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSecurityPolicyTest {
    @Test
    fun permitsHttpOnlyForExplicitLoopbackHosts() {
        val policy = NetworkSecurityPolicy.getInstance()
        for (host in listOf("localhost", "127.0.0.1", "::1", "[::1]")) {
            assertTrue(host, policy.isCleartextTrafficPermitted(host))
        }
        for (host in listOf("192.168.1.5", "10.0.0.2", "example.com", "localhost.example.com")) {
            assertFalse(host, policy.isCleartextTrafficPermitted(host))
        }
    }
}
