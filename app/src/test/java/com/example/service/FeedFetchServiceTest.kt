package com.example.service

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedFetchServiceTest {

    private val service = FeedFetchService()

    @Test
    fun testParsePubDate_validFormats() {
        // RSS Format
        val rssDate = "Sun, 01 Jun 2026 12:00:00 GMT"
        val rssTime = service.parsePubDate(rssDate)
        assertTrue(rssTime > 0)

        // Atom Format (ISO 8601)
        val atomDate = "2026-06-01T12:00:00Z"
        val atomTime = service.parsePubDate(atomDate)
        assertTrue(atomTime > 0)
    }

    @Test
    fun testParseXml_validRssFeed() {
        val rssXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0">
                <channel>
                    <title>Tech News Bangladesh</title>
                    <description>Latest tech findings from Bangladesh</description>
                    <link>https://technews.bd</link>
                    <item>
                        <title>New AI Innovation in Dhaka</title>
                        <link>https://technews.bd/story1</link>
                        <description><![CDATA[<p>Researchers in Dhaka have built a state-of-the-art AI model.</p>]]></description>
                        <pubDate>Mon, 01 Jun 2026 18:00:00 +0600</pubDate>
                        <guid>https://technews.bd/story1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val result = service.parseXml(rssXml, "https://technews.bd/rss")
        assertNotNull(result)
        assertEquals("Tech News Bangladesh", result?.title)
        assertEquals("Latest tech findings from Bangladesh", result?.description)
        assertEquals("https://technews.bd", result?.link)
        
        val items = result?.items ?: emptyList()
        assertEquals(1, items.size)
        val firstItem = items[0]
        assertEquals("New AI Innovation in Dhaka", firstItem.title)
        assertEquals("Researchers in Dhaka have built a state-of-the-art AI model.", firstItem.description)
        assertEquals("https://technews.bd/story1", firstItem.link)
        assertEquals("Mon, 01 Jun 2026 18:00:00 +0600", firstItem.pubDate)
        assertEquals("https://technews.bd/story1", firstItem.guid)
    }

    @Test
    fun testParseXml_validAtomFeed() {
        val atomXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Bangladesh Business Feed</title>
                <subtitle>Economic and startup updates</subtitle>
                <link href="https://biznews.bd" />
                <entry>
                    <title>Dhaka Stock Exchange Rebounds</title>
                    <link href="https://biznews.bd/stocks/123" />
                    <summary>Optimistic local investments triggered a positive trajectory today.</summary>
                    <published>2026-06-02T10:00:00Z</published>
                    <id>urn:uuid:12345</id>
                </entry>
            </feed>
        """.trimIndent()

        val result = service.parseXml(atomXml, "https://biznews.bd/feed")
        assertNotNull(result)
        assertEquals("Bangladesh Business Feed", result?.title)
        assertEquals("Economic and startup updates", result?.description)
        assertEquals("https://biznews.bd", result?.link)

        val items = result?.items ?: emptyList()
        assertEquals(1, items.size)
        val firstItem = items[0]
        assertEquals("Dhaka Stock Exchange Rebounds", firstItem.title)
        assertEquals("Optimistic local investments triggered a positive trajectory today.", firstItem.description)
        assertEquals("https://biznews.bd/stocks/123", firstItem.link)
        assertEquals("2026-06-02T10:00:00Z", firstItem.pubDate)
        assertEquals("urn:uuid:12345", firstItem.guid)
    }

    @Test
    fun testParseXml_invalidXmlFallback() {
        val badXml = "Not valid xml content indeed"
        val result = service.parseXml(badXml, "https://invalid.url")
        assertNull(result)
    }
}
