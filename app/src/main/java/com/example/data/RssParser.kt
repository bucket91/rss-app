package com.example.data

import com.example.service.FeedFetchService

/**
 * Helper object keeping compatibility with RssRepository, delegating core feed extraction
 * logic to the modern FeedFetchService.
 */
object RssParser {
    private val service = FeedFetchService()

    fun parsePubDate(dateStr: String?): Long {
        return service.parsePubDate(dateStr)
    }

    suspend fun fetchAndParseFeed(feedUrl: String, category: String): List<RssItemEntity> {
        val result = service.fetchAndParseFeed(feedUrl) ?: return emptyList()
        return result.items.map { item ->
            RssItemEntity(
                guid = item.guid ?: item.link,
                feedUrl = feedUrl,
                title = item.title,
                link = item.link,
                description = item.description,
                pubDate = item.pubDate ?: "",
                pubDateLong = item.pubDateLong,
                category = category,
                isSaved = false,
                thumbnailUrl = item.thumbnailUrl,
                flair = item.flair
            )
        }
    }
}
