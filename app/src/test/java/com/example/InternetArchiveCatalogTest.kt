package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.InternetArchiveCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InternetArchiveCatalogTest {
    @Test
    fun bundledCatalogHasAllUniqueCandidatesAndDoesNotInventRatings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val movies = InternetArchiveCatalog(context).load()
        assertTrue("Filtered candidate library should stay substantial", movies.size in 2200..2300)
        assertEquals(movies.size, movies.map { it.archiveIdentifier }.distinct().size)
        assertFalse(movies.any { it.archiveIdentifier.isBlank() })
        assertFalse(movies.any { it.rating >= 0.0 })
        assertFalse(movies.any { it.streamUrl.isNotBlank() })
        assertNotNull(movies.firstOrNull { it.archiveIdentifier == "HorrorHotel" })
        assertFalse(movies.any { (it.title + " " + it.synopsis).contains("porn", ignoreCase = true) })
    }
}
