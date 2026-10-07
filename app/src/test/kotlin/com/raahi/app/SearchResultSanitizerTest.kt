package com.raahi.app

import com.raahi.app.data.remote.SearchResultSanitizer
import com.raahi.app.domain.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultSanitizerTest {
    private val centroid = GeoPoint.INDIA_CENTER
    private val bhubaneswar = GeoPoint(20.2961, 85.8245)

    @Test
    fun exactCentroidIsPlaceholder() = assertTrue(SearchResultSanitizer.isPlaceholder(centroid, bhubaneswar))

    @Test
    fun withinTwoHundredMetersIsPlaceholder() =
        assertTrue(SearchResultSanitizer.isPlaceholder(GeoPoint(centroid.lat + 0.001, centroid.lng), null))

    @Test
    fun beyondTwoHundredMetersIsKept() =
        assertFalse(SearchResultSanitizer.isPlaceholder(GeoPoint(centroid.lat + 0.003, centroid.lng), bhubaneswar))

    @Test
    fun originNearCentroidExemptsPlaceholderRule() =
        assertFalse(SearchResultSanitizer.isPlaceholder(centroid, GeoPoint(centroid.lat + 0.02, centroid.lng)))

    @Test
    fun ordinaryPlaceIsKept() = assertFalse(SearchResultSanitizer.isPlaceholder(bhubaneswar, bhubaneswar))
}
