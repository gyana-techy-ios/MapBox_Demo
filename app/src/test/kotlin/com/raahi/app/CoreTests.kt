package com.raahi.app

import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.config.TokenStatus
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.HttpErrorMapper
import com.raahi.app.core.result.errorOrNull
import com.raahi.app.core.result.getOrNull
import com.raahi.app.core.result.map
import com.raahi.app.data.remote.Polyline6
import com.raahi.app.data.remote.TokenRedactor
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Polyline6Test {

    @Test
    fun decodesKnownVector() {
        val points = Polyline6.decode("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-6)
        assertEquals(-120.2, points[0].lng, 1e-6)
        assertEquals(40.7, points[1].lat, 1e-6)
        assertEquals(-120.95, points[1].lng, 1e-6)
        assertEquals(43.252, points[2].lat, 1e-6)
        assertEquals(-126.453, points[2].lng, 1e-6)
    }

    @Test
    fun decodesFixtureGeometry() {
        val points = Polyline6.decode("womqu@oymgrCg|J_vJ_pR_pR_pR_pR")
        assertEquals(4, points.size)
        assertEquals(28.6139, points[0].lat, 1e-6)
        assertEquals(77.2090, points[0].lng, 1e-6)
        assertEquals(28.6400, points[3].lat, 1e-6)
        assertEquals(77.2350, points[3].lng, 1e-6)
    }

    @Test
    fun emptyStringDecodesToEmptyList() {
        assertTrue(Polyline6.decode("").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedInputThrows() {
        Polyline6.decode("_izlhA~")
    }
}

class HttpErrorMapperTest {

    @Test
    fun mapsStatusCodes() {
        assertEquals(AppError.Unauthorized, HttpErrorMapper.fromHttp(401))
        assertEquals(AppError.Unauthorized, HttpErrorMapper.fromHttp(403))
        assertEquals(AppError.RateLimited, HttpErrorMapper.fromHttp(429))
        assertEquals(AppError.NotFound, HttpErrorMapper.fromHttp(404))
        assertEquals(AppError.Server(503), HttpErrorMapper.fromHttp(503))
    }

    @Test
    fun noRouteApiCodesWinOverStatus() {
        assertEquals(AppError.NoRoute, HttpErrorMapper.fromHttp(404, "NoRoute"))
        assertEquals(AppError.NoRoute, HttpErrorMapper.fromHttp(422, "NoSegment"))
        assertEquals(AppError.NoRoute, HttpErrorMapper.fromApiCode("NoRoute"))
        assertNull(HttpErrorMapper.fromApiCode("Ok"))
    }

    @Test
    fun mapsThrowables() {
        assertEquals(AppError.Timeout, HttpErrorMapper.fromThrowable(SocketTimeoutException("x")))
        assertEquals(AppError.NoInternet, HttpErrorMapper.fromThrowable(UnknownHostException("x")))
        assertEquals(AppError.NoInternet, HttpErrorMapper.fromThrowable(IOException("connection reset")))
        assertEquals(AppError.Timeout, HttpErrorMapper.fromThrowable(IOException("Read timeout")))
        assertTrue(HttpErrorMapper.fromThrowable(IllegalStateException("boom")) is AppError.Unknown)
    }

    @Test
    fun cancellationIsRethrown() {
        try {
            HttpErrorMapper.fromThrowable(CancellationException("cancelled"))
            fail("expected CancellationException")
        } catch (_: CancellationException) {
        }
    }
}

class AppResultTest {
    @Test
    fun mapTransformsSuccessOnly() {
        val success: AppResult<Int> = AppResult.Success(2)
        assertEquals(4, success.map { it * 2 }.getOrNull())
        val failure: AppResult<Int> = AppResult.Failure(AppError.Timeout)
        assertEquals(AppError.Timeout, failure.map { it * 2 }.errorOrNull())
    }
}

class MapboxConfigTest {

    @Test
    fun validPublicToken() {
        val config = MapboxConfig("pk.fake-test-token")
        assertEquals(TokenStatus.Valid, config.status)
        assertTrue(config.isMapAvailable)
        assertEquals("pk.fake-test-token", config.accessToken)
    }

    @Test
    fun missingTokenVariants() {
        assertEquals(TokenStatus.Missing, MapboxConfig(null).status)
        assertEquals(TokenStatus.Missing, MapboxConfig("").status)
        assertEquals(TokenStatus.Missing, MapboxConfig("   ").status)
        assertFalse(MapboxConfig(null).isMapAvailable)
        assertNull(MapboxConfig(null).accessToken)
    }

    @Test
    fun secretTokenIsRejected() {
        val config = MapboxConfig("sk.secret")
        assertEquals(TokenStatus.SecretTokenRejected, config.status)
        assertFalse(config.isMapAvailable)
        assertNull(config.accessToken)
    }

    @Test
    fun malformedTokens() {
        assertEquals(TokenStatus.Malformed, MapboxConfig("abc123").status)
        assertEquals(TokenStatus.Malformed, MapboxConfig("pk.").status)
        assertEquals(TokenStatus.Malformed, MapboxConfig("tk.abc").status)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(TokenStatus.Valid, MapboxConfig("  pk.abc \n").status)
    }
}

class TokenRedactorTest {
    @Test
    fun redactsAccessTokenQueryParam() {
        val redacted = TokenRedactor.redact("--> GET https://api.mapbox.com/x?q=a&access_token=pk.secret123&limit=3")
        assertFalse(redacted.contains("pk.secret123"))
        assertTrue(redacted.contains("access_token=REDACTED&limit=3"))
    }
}
