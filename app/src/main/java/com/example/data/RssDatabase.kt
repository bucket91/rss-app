package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rss_feeds")
data class RssFeedEntity(
    @PrimaryKey val url: String,
    val title: String,
    val category: String,
    val isPredefined: Boolean = false
)

@Entity(tableName = "rss_items")
data class RssItemEntity(
    @PrimaryKey val guid: String,
    val feedUrl: String,
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val pubDateLong: Long,
    val category: String,
    val isSaved: Boolean = false,
    val thumbnailUrl: String? = null,
    val flair: String? = null,
    val audioUrl: String? = null,
    val localAudioPath: String? = null
)

@Dao
interface RssFeedDao {
    @Query("SELECT * FROM rss_feeds")
    fun getAllFeedsFlow(): Flow<List<RssFeedEntity>>

    @Query("SELECT * FROM rss_feeds")
    suspend fun getAllFeedsDirect(): List<RssFeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeeds(feeds: List<RssFeedEntity>)

    @Query("DELETE FROM rss_feeds WHERE isPredefined = 1")
    suspend fun deletePredefinedFeeds()

    @Query("DELETE FROM rss_feeds WHERE url = :url")
    suspend fun deleteFeed(url: String)
}

@Dao
interface RssItemDao {
    @Query("SELECT * FROM rss_items ORDER BY pubDateLong DESC, guid DESC")
    fun getAllItemsFlow(): Flow<List<RssItemEntity>>

    @Query("SELECT * FROM rss_items WHERE category = :category ORDER BY pubDateLong DESC, guid DESC")
    fun getItemsByCategoryFlow(category: String): Flow<List<RssItemEntity>>

    @Query("SELECT * FROM rss_items WHERE isSaved = 1 ORDER BY pubDateLong DESC")
    fun getSavedItemsFlow(): Flow<List<RssItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<RssItemEntity>)

    @Query("UPDATE rss_items SET isSaved = :isSaved WHERE guid = :guid")
    suspend fun updateSavedStatus(guid: String, isSaved: Boolean)

    @Query("DELETE FROM rss_items WHERE feedUrl = :feedUrl AND isSaved = 0")
    suspend fun deleteUnsavedItemsByFeed(feedUrl: String)

    @Query("DELETE FROM rss_items WHERE feedUrl NOT IN (:activeUrls) AND isSaved = 0")
    suspend fun deleteUnsavedItemsNotMatchingFeeds(activeUrls: List<String>)

    @Query("DELETE FROM rss_items WHERE isSaved = 0")
    suspend fun clearAllUnsavedItems()

    @Query("SELECT * FROM rss_items WHERE guid = :guid LIMIT 1")
    suspend fun getItemByGuid(guid: String): RssItemEntity?

    @Query("UPDATE rss_items SET localAudioPath = :localAudioPath WHERE guid = :guid")
    suspend fun updateLocalAudioPath(guid: String, localAudioPath: String?)
}

@Database(entities = [RssFeedEntity::class, RssItemEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rssFeedDao(): RssFeedDao
    abstract fun rssItemDao(): RssItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rss_reader_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
