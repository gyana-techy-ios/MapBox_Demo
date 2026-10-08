package com.raahi.app

import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.voice.InstructionLanguage
import com.raahi.app.feature.offline.OfflineRegionMapper
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionLanguageTest {

    @Test
    fun supportedLanguageIsPassedThrough() {
        assertEquals("de", InstructionLanguage.apiTagFor(Locale.GERMAN))
        assertEquals("en", InstructionLanguage.apiTagFor(Locale.ENGLISH))
    }

    @Test
    fun hindiAndOdiaFallBackToEnglish() {
        assertEquals("en", InstructionLanguage.apiTagFor(Locale.forLanguageTag("hi")))
        assertEquals("en", InstructionLanguage.apiTagFor(Locale.forLanguageTag("or")))
    }

    @Test
    fun speechLocaleMatchesInstructionLanguage() {
        assertEquals("en", InstructionLanguage.speechLocaleFor(Locale.forLanguageTag("hi")).language)
        assertEquals("IN", InstructionLanguage.speechLocaleFor(Locale.forLanguageTag("hi")).country)
        assertEquals("de", InstructionLanguage.speechLocaleFor(Locale.GERMAN).language)
    }
}

class OfflineLiveSizeTest {

    private val region = OfflineRegion(
        id = "r1",
        name = "Area",
        bounds = GeoBounds(south = 20.0, west = 85.0, north = 20.1, east = 85.1),
        minZoom = 10,
        maxZoom = 14,
        status = OfflineRegionStatus.Downloading,
        progress = 0.2f,
        sizeBytes = 0L,
        downloadedAt = null,
        isOutdated = false,
    )

    @Test
    fun downloadingRegionReportsLiveBytes() {
        val active = DownloadProgress("r1", OfflineRegionStatus.Downloading, 0.5f, 4_096L)
        assertEquals(4_096L, OfflineRegionMapper.toItem(region, active).region.sizeBytes)
    }

    @Test
    fun idleRegionKeepsStoredBytes() {
        val stored = region.copy(status = OfflineRegionStatus.Completed, sizeBytes = 9_000L)
        assertEquals(9_000L, OfflineRegionMapper.toItem(stored, null).region.sizeBytes)
    }
}
