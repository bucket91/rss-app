package com.example.service

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing an individual item extracted from an RSS or Atom feed.
 */
data class ParsedFeedItem(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String? = null,
    val pubDateLong: Long = System.currentTimeMillis(),
    val guid: String? = null,
    val thumbnailUrl: String? = null,
    val flair: String? = null,
    val audioUrl: String? = null
)

/**
 * Data class representing the high-level metadata and contents of an RSS/Atom feed.
 */
data class ParsedFeedResult(
    val title: String,
    val description: String,
    val link: String,
    val items: List<ParsedFeedItem>
)

/**
 * A dedicated service module designed to fetch and parse external RSS and Atom feeds.
 * Strictly extracts title, description, link, pubDate, and other available metadata.
 */
class FeedFetchService(private val client: OkHttpClient = OkHttpClient()) {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm zzz", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss zzz", Locale.US)
    )

    /**
     * Parses a date string into millisecond representation of Epoch.
     */
    fun parsePubDate(dateStr: String?): Long {
        if (dateStr == null) return System.currentTimeMillis()
        val trimmedDate = dateStr.trim()
        for (format in dateFormats) {
            try {
                val date = format.parse(trimmedDate)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return System.currentTimeMillis()
    }

    /**
     * Fetches feed XML content from the specified URL and delegates to the parser.
     */
    suspend fun fetchAndParseFeed(feedUrl: String): ParsedFeedResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code} for URL: $feedUrl")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                return@withContext parseXml(bodyString, feedUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse feed: $feedUrl", e)
            null
        }
    }

    /**
     * Parses RSS or Atom XML input string into structured Feed models.
     */
    fun parseXml(xml: String, feedUrl: String): ParsedFeedResult? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            
            // Feed-level metadata
            var feedTitle = ""
            var feedDescription = ""
            var feedLink = feedUrl
            
            val items = mutableListOf<ParsedFeedItem>()

            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
            var currentPubDate = ""
            var currentGuid = ""
            var currentThumbnailUrl: String? = null
            var currentFlair: String? = null
            var currentAudioUrl: String? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            insideItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentDescription = ""
                            currentPubDate = ""
                            currentGuid = ""
                            currentThumbnailUrl = null
                            currentFlair = null
                            currentAudioUrl = null
                        } else if (insideItem) {
                            val namespace = parser.namespace ?: ""
                            val isMediaTag = namespace.contains("yahoo.com", ignoreCase = true) || namespace.contains("mrss", ignoreCase = true)
                            
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    currentTitle = safeNextText(parser).trim()
                                }
                                tagName.equals("link", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val type = parser.getAttributeValue(null, "type")
                                    if (href != null) {
                                        if (rel == "enclosure" && type != null && type.startsWith("audio/", ignoreCase = true)) {
                                            currentAudioUrl = href.trim()
                                        } else {
                                            currentLink = href.trim()
                                        }
                                    } else {
                                        currentLink = safeNextText(parser).trim()
                                    }
                                }
                                tagName.equals("description", ignoreCase = true) || tagName.equals("summary", ignoreCase = true) || (tagName.equals("content", ignoreCase = true) && !isMediaTag) -> {
                                    currentDescription = safeNextText(parser).trim()
                                }
                                tagName.equals("pubDate", ignoreCase = true) || tagName.equals("published", ignoreCase = true) || tagName.equals("updated", ignoreCase = true) -> {
                                    currentPubDate = safeNextText(parser).trim()
                                }
                                tagName.equals("guid", ignoreCase = true) || tagName.equals("id", ignoreCase = true) -> {
                                    currentGuid = safeNextText(parser).trim()
                                }
                                tagName.equals("category", ignoreCase = true) -> {
                                    val term = parser.getAttributeValue(null, "term")
                                    if (term != null) {
                                        currentFlair = term.trim()
                                    } else {
                                        currentFlair = safeNextText(parser).trim()
                                    }
                                }
                                tagName.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type")
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null) {
                                        if (type != null && type.startsWith("image/", ignoreCase = true)) {
                                            currentThumbnailUrl = url
                                        } else if (type != null && type.startsWith("audio/", ignoreCase = true)) {
                                            currentAudioUrl = url
                                        } else if (url.contains(".mp3") || url.contains(".wav") || url.contains(".m4a")) {
                                            currentAudioUrl = url
                                        }
                                    }
                                }
                                tagName.equals("content", ignoreCase = true) || tagName.endsWith("content", ignoreCase = true) || tagName.endsWith("thumbnail", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    val type = parser.getAttributeValue(null, "type")
                                    if (url != null) {
                                        if (type != null && type.startsWith("audio/", ignoreCase = true)) {
                                            currentAudioUrl = url
                                        } else if (type != null && type.startsWith("image/", ignoreCase = true)) {
                                            currentThumbnailUrl = url
                                        } else {
                                            currentThumbnailUrl = url
                                        }
                                    }
                                }
                            }
                        } else {
                            // Extract feed/channel level info
                            when {
                                tagName.equals("title", ignoreCase = true) && feedTitle.isEmpty() -> {
                                    feedTitle = safeNextText(parser).trim()
                                }
                                (tagName.equals("description", ignoreCase = true) || tagName.equals("subtitle", ignoreCase = true)) && feedDescription.isEmpty() -> {
                                    feedDescription = safeNextText(parser).trim()
                                }
                                tagName.equals("link", ignoreCase = true) && feedLink == feedUrl -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null) {
                                        feedLink = href.trim()
                                    } else {
                                        val urlText = safeNextText(parser).trim()
                                        if (urlText.isNotEmpty()) {
                                            feedLink = urlText
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            insideItem = false
                            val finalGuid = if (currentGuid.isNotEmpty()) currentGuid else currentLink
                            
                            if (currentTitle.isNotEmpty() && currentLink.isNotEmpty()) {
                                if (currentThumbnailUrl == null && currentDescription.isNotEmpty()) {
                                    currentThumbnailUrl = extractImageFromHtml(currentDescription)
                                }
                                val cleanDesc = stripHtml(currentDescription)
                                val pubDateMs = parsePubDate(currentPubDate)

                                items.add(
                                    ParsedFeedItem(
                                        guid = finalGuid,
                                        title = currentTitle,
                                        link = currentLink,
                                        description = cleanDesc,
                                        pubDate = currentPubDate,
                                        pubDateLong = pubDateMs,
                                        thumbnailUrl = currentThumbnailUrl,
                                        flair = currentFlair,
                                        audioUrl = currentAudioUrl
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Defaults if feed metadata wasn't fully extracted
            val finalTitle = feedTitle.ifEmpty { "Feed Title" }
            val finalDesc = feedDescription.ifEmpty { "RSS/Atom syndicated feed" }
            
            return ParsedFeedResult(
                title = finalTitle,
                description = finalDesc,
                link = feedLink,
                items = items
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseXml: ${e.message}", e)
            return null
        }
    }

    private fun safeNextText(parser: XmlPullParser): String {
        val result = java.lang.StringBuilder()
        try {
            var eventType = parser.next()
            var depth = 1
            while (depth > 0 && eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    depth++
                } else if (eventType == XmlPullParser.END_TAG) {
                    depth--
                } else if (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) {
                    result.append(parser.text)
                }
                if (depth > 0) {
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error safeNextText: ${e.message}")
        }
        return result.toString()
    }

    private fun extractImageFromHtml(html: String): String? {
        try {
            val imgIndex = html.indexOf("<img", ignoreCase = true)
            if (imgIndex != -1) {
                val srcIndex = html.indexOf("src=\"", imgIndex, ignoreCase = true)
                if (srcIndex != -1) {
                    val start = srcIndex + 5
                    val end = html.indexOf("\"", start)
                    if (end != -1) {
                        val url = html.substring(start, end)
                        if (url.startsWith("http")) return url
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun stripHtml(html: String): String {
        return try {
            val clean = html.replace("<br\\s*/?>".toRegex(), " ")
                .replace("</p>".toRegex(), " ")
                .replace("<[^>]*>".toRegex(), "")
                .replace("&amp;".toRegex(), "&")
                .replace("&lt;".toRegex(), "<")
                .replace("&gt;".toRegex(), ">")
                .replace("&quot;".toRegex(), "\"")
                .replace("&apos;".toRegex(), "'")
                .replace("\\s+".toRegex(), " ")
                .trim()
            if (clean.length > 500) clean.take(500) + "..." else clean
        } catch (e: Exception) {
            html
        }
    }

    companion object {
        private const val TAG = "FeedFetchService"
    }
}
