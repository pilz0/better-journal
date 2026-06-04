package foo.pilz.freaklog.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderQueryParamsTest {

    @Test
    fun since_defaultsToZero_whenNullOrInvalidOrNegative() {
        assertEquals(0, ProviderQueryParams.parseSince(null))
        assertEquals(0, ProviderQueryParams.parseSince("abc"))
        assertEquals(0, ProviderQueryParams.parseSince("-5"))
    }

    @Test
    fun since_parsesNonNegative() {
        assertEquals(0, ProviderQueryParams.parseSince("0"))
        assertEquals(42, ProviderQueryParams.parseSince("42"))
    }

    @Test
    fun sinceLong_defaultsToZero_whenNullOrInvalidOrNegative() {
        assertEquals(0L, ProviderQueryParams.parseSinceLong(null))
        assertEquals(0L, ProviderQueryParams.parseSinceLong("abc"))
        assertEquals(0L, ProviderQueryParams.parseSinceLong("-5"))
    }

    @Test
    fun sinceLong_parsesNonNegative() {
        assertEquals(0L, ProviderQueryParams.parseSinceLong("0"))
        assertEquals(42L, ProviderQueryParams.parseSinceLong("42"))
    }

    @Test
    fun limit_unlimitedWhenNullInvalidOrNonPositive() {
        assertEquals(ProviderQueryParams.UNLIMITED, ProviderQueryParams.parseLimit(null))
        assertEquals(ProviderQueryParams.UNLIMITED, ProviderQueryParams.parseLimit("abc"))
        assertEquals(ProviderQueryParams.UNLIMITED, ProviderQueryParams.parseLimit("0"))
        assertEquals(ProviderQueryParams.UNLIMITED, ProviderQueryParams.parseLimit("-3"))
    }

    @Test
    fun limit_parsesPositive() {
        assertEquals(100, ProviderQueryParams.parseLimit("100"))
    }
}
