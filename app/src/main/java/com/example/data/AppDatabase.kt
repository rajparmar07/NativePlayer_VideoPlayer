package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val urlOrPath: String,
    val duration: Long = 0,
    val subtitleUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_downloads")
data class VideoDownload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val localFilePath: String,
    val downloadStatus: String, // "PENDING", "DOWNLOADING", "COMPLETED", "FAILED"
    val progress: Float = 0f,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val subtitleUrl: String? = null,
    val localSubtitlePath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface VideoPlayerDao {
    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // Playlist Items
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItem): Long

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deletePlaylistItem(id: Long)

    // Downloads
    @Query("SELECT * FROM video_downloads ORDER BY addedAt DESC")
    fun getAllDownloads(): Flow<List<VideoDownload>>

    @Query("SELECT * FROM video_downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): VideoDownload?

    @Query("SELECT * FROM video_downloads WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getDownloadByUrl(sourceUrl: String): VideoDownload?

    @Query("SELECT * FROM video_downloads WHERE localFilePath = :localFilePath LIMIT 1")
    suspend fun getDownloadByFilePath(localFilePath: String): VideoDownload?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: VideoDownload): Long

    @Update
    suspend fun updateDownload(download: VideoDownload)

    @Query("DELETE FROM video_downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)
}

@Database(entities = [Playlist::class, PlaylistItem::class, VideoDownload::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoPlayerDao(): VideoPlayerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "video_player.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
