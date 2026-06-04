package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RssRepository(private val db: AppDatabase) {
    private val feedDao = db.rssFeedDao()
    private val itemDao = db.rssItemDao()

    val feeds: Flow<List<RssFeedEntity>> = feedDao.getAllFeedsFlow()
    val allItems: Flow<List<RssItemEntity>> = itemDao.getAllItemsFlow()
    val savedItems: Flow<List<RssItemEntity>> = itemDao.getSavedItemsFlow()

    companion object {
        private const val TAG = "RssRepository"

        val PRE_POPULATED_FEEDS = listOf(
            RssFeedEntity(
                url = "https://www.thedailystar.net/rss/index.xml",
                title = "The Daily Star",
                category = "News",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.dhakatribune.com/feed",
                title = "Dhaka Tribune",
                category = "News",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://en.prothomalo.com/feed",
                title = "Prothom Alo (English)",
                category = "News",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.tbsnews.net/rss.xml",
                title = "The Business Standard",
                category = "Business",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.reddit.com/r/bangladesh/.rss",
                title = "r/bangladesh",
                category = "Reddit",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.reddit.com/r/dhaka/.rss",
                title = "r/dhaka",
                category = "Reddit",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.reddit.com/r/BangladeshMedia/.rss",
                title = "r/BangladeshMedia",
                category = "Reddit",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCthidp7_fIIn6_pPLaY3sBg",
                title = "Somoy TV",
                category = "YouTube",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCp6ZfV9Mh6f7gS8_T-a8AQA",
                title = "Jamuna TV",
                category = "YouTube",
                isPredefined = true
            ),
            RssFeedEntity(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCvclA86D9P7VnU8m0S_Vj9g",
                title = "Khalid Farhan",
                category = "YouTube",
                isPredefined = true
            )
        )
    }

    suspend fun checkAndPrepopulateFeeds() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Ensuring all predefined BD RSS feeds are configured...")
            feedDao.deletePredefinedFeeds()
            feedDao.insertFeeds(PRE_POPULATED_FEEDS)
            
            // Purge orphaned items that do not belong to active feeds to remove old/stale articles of removed feeds
            val activeUrls = feedDao.getAllFeedsDirect().map { it.url }
            if (activeUrls.isNotEmpty()) {
                itemDao.deleteUnsavedItemsNotMatchingFeeds(activeUrls)
            }
        }
    }

    fun getItemsByCategory(category: String): Flow<List<RssItemEntity>> {
        return if (category == "All" || category.isEmpty()) {
            itemDao.getAllItemsFlow()
        } else {
            itemDao.getItemsByCategoryFlow(category)
        }
    }

    suspend fun addFeed(url: String, title: String, category: String) {
        withContext(Dispatchers.IO) {
            val cleanUrl = url.trim()
            val newFeed = RssFeedEntity(
                url = cleanUrl,
                title = title.trim().ifEmpty { "User Feed" },
                category = category.trim().ifEmpty { "General" },
                isPredefined = false
            )
            feedDao.insertFeed(newFeed)
            // Immediately fetch newly added feed
            refreshFeed(cleanUrl, newFeed.category)
        }
    }

    suspend fun deleteFeed(url: String) {
        withContext(Dispatchers.IO) {
            feedDao.deleteFeed(url)
            itemDao.deleteUnsavedItemsByFeed(url)
        }
    }

    suspend fun refreshFeed(feedUrl: String, category: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Refreshing feed: $feedUrl ($category)")
                val items = RssParser.fetchAndParseFeed(feedUrl, category)
                if (items.isNotEmpty()) {
                    // Update database
                    // For offline capabilities, we do insert which ignores on conflicts (GUIDs already present remain unchanged)
                    itemDao.insertItems(items)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh feed: $feedUrl", e)
            }
        }
    }

    suspend fun refreshAllFeeds() {
        withContext(Dispatchers.IO) {
            val activeFeeds = feedDao.getAllFeedsDirect()
            coroutineScope {
                activeFeeds.map { feed ->
                    async {
                        refreshFeed(feed.url, feed.category)
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun toggleSaveArticle(guid: String, isNowSaved: Boolean) {
        withContext(Dispatchers.IO) {
            itemDao.updateSavedStatus(guid, isNowSaved)
        }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            itemDao.clearAllUnsavedItems()
        }
    }
}
