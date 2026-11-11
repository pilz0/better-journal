package foo.pilz.freaklog.data.ai

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import foo.pilz.freaklog.data.health.HealthSharingPreferences
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiHealthToolsTest {
    private val allowed = MutableStateFlow(false)
    private val locked = MutableStateFlow(false)
    private val preferences = mockk<HealthSharingPreferences> { every { aiVitals } returns allowed }
    private val auth = mockk<BiometricAuthManager> { every { shouldLock } returns locked }
    private val tools = AiHealthTools(mockk<Context>(), preferences, auth)
    private val controller = mockk<PermissionController>()
    private val client = mockk<HealthConnectClient> { every { permissionController } returns controller }

    @Test fun disabledAndLockedSharingRejectEvenDirectToolCalls() = runTest {
        assertEquals("error", tools.execute(AiHealthTools.TOOL_NAME, emptyMap())!!.getString("status"))
        allowed.value = true
        locked.value = true
        assertEquals("error", tools.execute(AiHealthTools.TOOL_NAME, emptyMap())!!.getString("status"))
        assertNull(tools.execute("unrelated_tool", emptyMap()))
    }

    @Test fun noPermissionsNeverQueriesRecords() = runTest {
        coEvery { controller.getGrantedPermissions() } returns emptySet()
        val response = tools.summarize(client, Instant.now(), 1)
        assertEquals("error", response.getString("status"))
        coVerify(exactly = 0) { client.aggregate(any()) }
    }

    @Test fun partialPermissionsAndMissingDataAreExplicitAndWindowIsBounded() = runTest {
        coEvery { controller.getGrantedPermissions() } returns setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
        coEvery { client.aggregate(any()) } returns AggregationResult(emptyMap(), emptyMap(), emptySet())
        val now = Instant.parse("2026-11-11T12:00:00Z")
        val response = tools.summarize(client, now, 500)
        assertEquals("success", response.getString("status"))
        assertEquals(now.minus(Duration.ofDays(7)).toString(), response.getString("start"))
        assertTrue(response.isNull("heart_rate_bpm_mean"))
        assertTrue(response.isNull("sleep_minutes"))
        assertTrue(response.isNull("systolic_mmhg_mean"))
        assertEquals(2, response.getJSONArray("missing_permissions").length())
        assertTrue(response.getString("interpretation").contains("Null means missing"))
        coVerify(exactly = 1) { client.aggregate(any()) }
    }

    @Test fun permissionRevocationWhileReadingSuppressesResult() = runTest {
        coEvery { controller.getGrantedPermissions() } returnsMany listOf(AiHealthTools.vitalsPermissions, emptySet())
        coEvery { client.aggregate(any()) } returns AggregationResult(emptyMap(), emptyMap(), emptySet())
        assertEquals("error", tools.summarize(client, Instant.now(), 1).getString("status"))
    }

    @Test fun dispatcherOnlyAdvertisesVitalsAfterSeparateOptIn() = runTest {
        val substances = mockk<AiSubstanceTools> { every { specs } returns emptyList() }
        val anodyne = mockk<foo.pilz.freaklog.data.ai.anodyne.AiAnodyneTools> {
            coEvery { isEnabled() } returns false
        }
        val dispatcher = AiTools(mockk(), substances, anodyne, auth, tools)
        assertFalse(dispatcher.specs().any { it.name == AiHealthTools.TOOL_NAME })
        allowed.value = true
        assertTrue(dispatcher.specs().any { it.name == AiHealthTools.TOOL_NAME })
    }
}
