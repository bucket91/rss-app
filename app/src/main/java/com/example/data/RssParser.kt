package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

object RssParser {
    private const val TAG = "RssParser"
    private val client = OkHttpClient()

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm zzz", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss zzz", Locale.US)
    )

    fun parsePubDate(dateStr: String?): Long {
        if (dateStr == null) return System.currentTimeMillis()
        val trimmedDate = dateStr.trim()
        for (format in dateFormats) {
            try {
                val date = format.parse(trimmedDate)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Keep trying next formats
            }
        }
        return System.currentTimeMillis()
    }

    suspend fun fetchAndParseFeed(feedUrl: String, category: String): List<RssItemEntity> {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch feed: ${response.code} for URL: $feedUrl")
                    return emptyList()
                }

                val xmlBody = response.body?.string() ?: return emptyList()
                parseXml(xmlBody, feedUrl, category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching/parsing: ${e.message}", e)
            emptyList()
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
            Log.e(TAG, "Error in safeNextText: ${e.message}")
        }
        return result.toString()
    }

    private fun parseXml(xml: String, feedUrl: String, category: String): List<RssItemEntity> {
        val items = mutableListOf<RssItemEntity>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
            var currentPubDate = ""
            var currentGuid = ""
            var currentThumbnailUrl: String? = null
            var currentFlair: String? = null
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
                        } else if (insideItem) {
                            val namespace = parser.namespace ?: ""
                            val isMediaTag = namespace.contains("yahoo.com", ignoreCase = true) || namespace.contains("mrss", ignoreCase = true)
                            
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    currentTitle = safeNextText(parser).trim()
                                }
                                tagName.equals("link", ignoreCase = true) -> {
                                    // Sometimes link is stored as an attribute in Atom (href)
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null) {
                                        currentLink = href.trim()
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
                                        val cleanTerm = term.trim()
                                        if (!cleanTerm.equals("bangladesh", ignoreCase = true) &&
                                            !cleanTerm.equals("dhaka", ignoreCase = true) &&
                                            !cleanTerm.equals("BangladeshMedia", ignoreCase = true)) {
                                            currentFlair = cleanTerm
                                        }
                                    } else {
                                        val catText = safeNextText(parser).trim()
                                        if (catText.isNotEmpty() && !catText.equals(category, ignoreCase = true)) {
                                            currentFlair = catText
                                        }
                                    }
                                }
                                tagName.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type")
                                    if (type != null && type.startsWith("image/", ignoreCase = true)) {
                                        val url = parser.getAttributeValue(null, "url")
                                        if (url != null) {
                                            currentThumbnailUrl = url
                                        }
                                    }
                                }
                                tagName.equals("content", ignoreCase = true) || tagMatchesMediaContent(tagName) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null) {
                                        currentThumbnailUrl = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true) || tagName.equals("entry", ignoreCase = true)) {
                            insideItem = false
                            
                            // Guid fallback to link if not present
                            val finalGuid = if (currentGuid.isNotEmpty()) currentGuid else currentLink
                            
                            // If title or link is missing, we don't save it
                            if (currentTitle.isNotEmpty() && currentLink.isNotEmpty()) {
                                // Extract high-quality image from description if we don't have one and summary has <img src="...">
                                if (currentThumbnailUrl == null && currentDescription.isNotEmpty()) {
                                    currentThumbnailUrl = extractImageFromHtml(currentDescription)
                                }

                                // Strip HTML tags from description to make it preview nicely
                                val cleanDesc = stripHtml(currentDescription)

                                val pubDateMs = parsePubDate(currentPubDate)

                                items.add(
                                    RssItemEntity(
                                        guid = finalGuid,
                                        feedUrl = feedUrl,
                                        title = currentTitle,
                                        link = currentLink,
                                        description = cleanDesc,
                                        pubDate = currentPubDate,
                                        pubDateLong = pubDateMs,
                                        category = category,
                                        thumbnailUrl = currentThumbnailUrl,
                                        isSaved = false,
                                        flair = currentFlair
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XML: ${e.message}", e)
        }
        return items
    }

    private fun tagMatchesMediaContent(tagName: String): Boolean {
        // media:content might have namespace prefix or just be content
        return tagName.endsWith("content", ignoreCase = true) || tagName.endsWith("thumbnail", ignoreCase = true)
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
                        if (url.startsWith("http")) {
                            return url
                        }
                    }
                }
                val srcImgIndexSingle = html.indexOf("src='", imgIndex, ignoreCase = true)
                if (srcImgIndexSingle != -1) {
                    val start = srcImgIndexSingle + 5
                    val end = html.indexOf("'", start)
                    if (end != -1) {
                        val url = html.substring(start, end)
                        if (url.startsWith("http")) {
                            return url
                        }
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
            // Very simple HTML tag stripping that replaces common tags like <p>, <br> with spaces, and removes others
            val withBorders = html.replace("<br\\s*/?>".toRegex(), " ")
                .replace("</p>".toRegex(), " ")
                .replace("<[^>]*>".toRegex(), "")
                .replace("&amp;".toRegex(), "&")
                .replace("&lt;".toRegex(), "<")
                .replace("&gt;".toRegex(), ">")
                .replace("&quot;".toRegex(), "\"")
                .replace("&apos;".toRegex(), "'")
                .replace("\\s+".toRegex(), " ")
                .trim()
            if (withBorders.length > 500) {
                withBorders.take(500) + "..."
            } else {
                withBorders
            }
        } catch (e: Exception) {
            html
        }
    }
}
