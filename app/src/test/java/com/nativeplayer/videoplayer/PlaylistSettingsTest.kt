package com.nativeplayer.videoplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nativeplayer.videoplayer.data.AppDatabase
import com.nativeplayer.videoplayer.data.Playlist
import com.nativeplayer.videoplayer.data.VideoRepository
import com.nativeplayer.videoplayer.viewmodel.PlaylistThumbnailPattern
import com.nativeplayer.videoplayer.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistSettingsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: VideoRepository
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before each test to ensure a clean state
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VideoRepository(database.videoPlayerDao())
        viewModel = VideoPlayerViewModel(repository, context)
    }

    @org.junit.After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPlaylistThumbnailPatternDefaultAndPersistence() {
        // Default is FIRST_VIDEO
        assertEquals(PlaylistThumbnailPattern.FIRST_VIDEO, viewModel.playlistThumbnailPattern.value)

        // Set to LAST_VIDEO
        viewModel.setPlaylistThumbnailPattern(PlaylistThumbnailPattern.LAST_VIDEO)
        assertEquals(PlaylistThumbnailPattern.LAST_VIDEO, viewModel.playlistThumbnailPattern.value)

        // Re-instantiate ViewModel to verify preference persistence
        val newViewModel = VideoPlayerViewModel(repository, context)
        assertEquals(PlaylistThumbnailPattern.LAST_VIDEO, newViewModel.playlistThumbnailPattern.value)

        // Switch back to FIRST_VIDEO
        newViewModel.setPlaylistThumbnailPattern(PlaylistThumbnailPattern.FIRST_VIDEO)
        assertEquals(PlaylistThumbnailPattern.FIRST_VIDEO, newViewModel.playlistThumbnailPattern.value)
    }

    @Test
    fun testBookmarksPlaylistAutoSeeding() = runBlocking {
        repository.ensureDefaultBookmarksPlaylist()

        val bookmarks = repository.getBookmarksPlaylist()
        assertNotNull(bookmarks)
        assertEquals("Bookmarks", bookmarks?.title)
        assertEquals("Your bookmarked and favorite videos", bookmarks?.description)
        assertTrue(bookmarks?.isSystem == true)

        // Calling ensureDefaultBookmarksPlaylist again shouldn't duplicate it
        repository.ensureDefaultBookmarksPlaylist()
        val allPlaylists = repository.playlists.first()
        val bookmarksCount = allPlaylists.count { it.title == "Bookmarks" || it.isSystem }
        assertEquals(1, bookmarksCount)
    }

    @Test
    fun testSystemPlaylistCannotBeDeleted() = runBlocking {
        repository.ensureDefaultBookmarksPlaylist()
        val bookmarks = repository.getBookmarksPlaylist()
        assertNotNull(bookmarks)

        // Attempt to delete system playlist
        repository.deletePlaylist(bookmarks!!.id)

        // Verify it is still present in database
        val afterAttempt = repository.getBookmarksPlaylist()
        assertNotNull(afterAttempt)
        assertEquals(bookmarks.id, afterAttempt?.id)
    }

    @Test
    fun testCustomPlaylistCreationAndDeletion() = runBlocking {
        val id = repository.createPlaylist("Favorites", "My personal favorites")
        assertTrue(id > 0)

        val all = repository.playlists.first()
        assertTrue(all.any { it.id == id && it.title == "Favorites" && !it.isSystem })

        // Delete custom playlist
        repository.deletePlaylist(id)
        val afterDelete = repository.playlists.first()
        assertFalse(afterDelete.any { it.id == id })
    }

    @Test
    fun testCustomThumbnailPathUpdate() = runBlocking {
        val id = repository.createPlaylist("Action Movies", "Cool action movies")
        val testImagePath = "/data/user/0/com.nativeplayer.videoplayer/files/playlist_covers/cover_test.jpg"

        repository.updatePlaylistThumbnail(id, testImagePath)

        val updatedList = repository.playlists.first()
        val targetPlaylist = updatedList.find { it.id == id }
        assertNotNull(targetPlaylist)
        assertEquals(testImagePath, targetPlaylist?.customThumbnailPath)

        // Reset thumbnail
        repository.updatePlaylistThumbnail(id, null)
        val resetList = repository.playlists.first()
        val resetPlaylist = resetList.find { it.id == id }
        assertNotNull(resetPlaylist)
        assertNull(resetPlaylist?.customThumbnailPath)
    }

    @Test
    fun testSelectedPlaylistState() {
        assertNull(viewModel.selectedPlaylist.value)

        val dummy = Playlist(id = 101, title = "Test Playlist", description = "Sample")
        viewModel.setSelectedPlaylist(dummy)
        assertEquals(dummy, viewModel.selectedPlaylist.value)

        viewModel.setSelectedPlaylist(null)
        assertNull(viewModel.selectedPlaylist.value)
    }

    @Test
    fun testAddVideosToBookmarksAndPreventDuplicates() = runBlocking {
        repository.ensureDefaultBookmarksPlaylist()
        val bookmarks = repository.getBookmarksPlaylist()
        assertNotNull(bookmarks)

        val videos = listOf(
            com.nativeplayer.videoplayer.data.VideoModel(id = "1", title = "Movie 1", urlOrPath = "/path/to/movie1.mp4"),
            com.nativeplayer.videoplayer.data.VideoModel(id = "2", title = "Movie 2", urlOrPath = "/path/to/movie2.mp4")
        )

        // First addition: adds 2
        val addedFirst = repository.addVideosToBookmarks(videos)
        assertEquals(2, addedFirst)

        val itemsAfterFirst = repository.getItemsForPlaylist(bookmarks!!.id).first()
        assertEquals(2, itemsAfterFirst.size)

        // Second addition with same videos: should prevent duplicates and add 0
        val addedSecond = repository.addVideosToBookmarks(videos)
        assertEquals(0, addedSecond)

        val itemsAfterSecond = repository.getItemsForPlaylist(bookmarks.id).first()
        assertEquals(2, itemsAfterSecond.size)

        // isVideoBookmarked checks
        assertTrue(repository.isVideoBookmarked("/path/to/movie1.mp4"))
        assertTrue(repository.isVideoBookmarked("/path/to/movie2.mp4"))
        assertFalse(repository.isVideoBookmarked("/path/to/movie3.mp4"))

        // Remove one video
        val removeList = listOf(com.nativeplayer.videoplayer.data.VideoModel(id = "1", title = "Movie 1", urlOrPath = "/path/to/movie1.mp4"))
        repository.removeVideosFromBookmarks(removeList)

        assertFalse(repository.isVideoBookmarked("/path/to/movie1.mp4"))
        assertTrue(repository.isVideoBookmarked("/path/to/movie2.mp4"))
        val itemsAfterRemove = repository.getItemsForPlaylist(bookmarks.id).first()
        assertEquals(1, itemsAfterRemove.size)
        assertEquals("Movie 2", itemsAfterRemove[0].title)
    }
}
