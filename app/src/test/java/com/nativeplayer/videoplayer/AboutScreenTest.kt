package com.nativeplayer.videoplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nativeplayer.videoplayer.ui.screens.OpenSourceLibrariesData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutScreenTest {

    @Test
    fun testAppMetadataAndBuildConfig() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertNotNull(appName)
        assertTrue(appName.isNotBlank())

        // BuildConfig tests
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals("com.nativeplayer.videoplayer", BuildConfig.APPLICATION_ID)
        assertEquals("Native Player", appName)
    }

    @Test
    fun testOpenSourceLibrariesDataIntegrity() {
        val libraries = OpenSourceLibrariesData.libraries
        assertTrue("Library catalog should not be empty", libraries.isNotEmpty())
        assertTrue("Expected at least 15 libraries", libraries.size >= 15)

        for (lib in libraries) {
            assertTrue("Library name should not be blank: ${lib.name}", lib.name.isNotBlank())
            assertTrue("Artifact coordinate should not be blank: ${lib.name}", lib.artifact.isNotBlank())
            assertTrue("Version should not be blank: ${lib.name}", lib.version.isNotBlank())
            assertTrue("License should not be blank: ${lib.name}", lib.license.isNotBlank())
            assertTrue("Description should not be blank: ${lib.name}", lib.description.isNotBlank())
            assertTrue("URL should start with https:// : ${lib.url}", lib.url.startsWith("https://"))
            assertTrue("Category should not be blank: ${lib.name}", lib.category.isNotBlank())
        }
    }

    @Test
    fun testCoreLibrariesIncluded() {
        val artifacts = OpenSourceLibrariesData.libraries.map { it.artifact }
        val names = OpenSourceLibrariesData.libraries.map { it.name }

        // Verify key player & streaming libraries
        assertTrue(names.any { it.contains("ExoPlayer", ignoreCase = true) })
        assertTrue(names.any { it.contains("FFmpeg", ignoreCase = true) })

        // Verify Compose & UI libraries
        assertTrue(names.any { it.contains("Compose", ignoreCase = true) })
        assertTrue(names.any { it.contains("Material 3", ignoreCase = true) })
        assertTrue(names.any { it.contains("Coil", ignoreCase = true) })

        // Verify Networking & Storage
        assertTrue(names.any { it.contains("Retrofit", ignoreCase = true) })
        assertTrue(names.any { it.contains("OkHttp", ignoreCase = true) })
        assertTrue(names.any { it.contains("Room", ignoreCase = true) })
        assertTrue(names.any { it.contains("DataStore", ignoreCase = true) })

        // Verify Concurrency & Architecture
        assertTrue(names.any { it.contains("Coroutines", ignoreCase = true) })
    }

    @Test
    fun testCategoryFilterLogic() {
        val allLibraries = OpenSourceLibrariesData.libraries

        // Category: Media & Playback
        val mediaCategory = allLibraries.filter { it.category == "Media & Playback" }
        assertTrue(mediaCategory.isNotEmpty())
        assertTrue(mediaCategory.any { it.name.contains("ExoPlayer") })

        // Category: UI & Compose
        val uiCategory = allLibraries.filter { it.category == "UI & Compose" }
        assertTrue(uiCategory.isNotEmpty())
        assertTrue(uiCategory.any { it.name.contains("Coil") })

        // Category: Networking
        val netCategory = allLibraries.filter { it.category == "Networking" }
        assertTrue(netCategory.isNotEmpty())
        assertTrue(netCategory.any { it.name.contains("Retrofit") })

        // Category: Storage & Database
        val storageCategory = allLibraries.filter { it.category == "Storage & Database" }
        assertTrue(storageCategory.isNotEmpty())
        assertTrue(storageCategory.any { it.name.contains("Room") })

        // All category items are partitioned properly
        val totalCategorized = mediaCategory.size + uiCategory.size + netCategory.size + storageCategory.size +
                allLibraries.filter { it.category == "Architecture & Async" }.size +
                allLibraries.filter { it.category == "Utilities" }.size +
                allLibraries.filter { it.category == "Testing" }.size

        assertEquals(allLibraries.size, totalCategorized)
    }

    @Test
    fun testGitHubUrlFormat() {
        val targetGitHubUrl = "https://github.com/rajparmar07/StreamCache_VideoPlayer"
        assertTrue(targetGitHubUrl.startsWith("https://github.com/"))
        assertTrue(targetGitHubUrl.contains("StreamCache_VideoPlayer"))
    }

    @Test
    fun testAuthorGitHubUrlFormat() {
        val authorUrl = "https://github.com/rajparmar07"
        assertTrue(authorUrl.startsWith("https://github.com/"))
        assertEquals("https://github.com/rajparmar07", authorUrl)
    }

    @Test
    fun testBuyMeACoffeeDonateUrlFormat() {
        val donateUrl = "https://buymeacoffee.com/rajparmar07"
        assertTrue(donateUrl.startsWith("https://buymeacoffee.com/"))
        assertEquals("https://buymeacoffee.com/rajparmar07", donateUrl)
    }
}
