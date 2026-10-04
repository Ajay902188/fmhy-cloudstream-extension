package com.ajay902188.fmhy

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class FMHYProvider : MainAPI() {
    override var mainUrl = "https://fmhy.net"
    override var name = "FMHY"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override var lang = "en"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Others,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/videopiracyguide" to "Streaming Sites",
    )

    companion object {
        /** File extensions that indicate a direct media link. */
        private val MEDIA_EXTENSIONS = listOf(".m3u8", ".mp4", ".webm", ".mpd", ".mkv")

        /** Domains we know are not actual streaming sources. */
        private val EXCLUDED_DOMAINS = listOf(
            "discord.gg", "discord.com", "t.me", "telegram.me",
            "reddit.com", "github.com", "greasyfork.org",
            "rentry.co", "rentry.org", "fmhy.net",
            "base64decode.org",
        )
    }

    // ----------------------------- Main page -----------------------------

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val entries = parseEntries(doc)
        return newHomePageResponse(
            list = HomePageList(request.name, entries),
            hasNext = false
        )
    }

    // ----------------------------- Search --------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/videopiracyguide").document
        return parseEntries(doc).filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    // ----------------------------- Load ----------------------------------

    override suspend fun load(url: String): LoadResponse {
        // The URL is the site homepage itself; title is encoded in the fragment.
        val parts = url.split("#", limit = 2)
        val siteUrl = parts[0]
        val title = if (parts.size > 1) {
            parts[1].replace("-", " ")
        } else {
            siteUrl.substringAfter("://").substringBefore("/")
        }

        return newMovieLoadResponse(
            name = title,
            url = url,
            dataUrl = siteUrl,
            type = TvType.Others,
        ) {
            this.plot =
                "Streaming site indexed from the FMHY video directory.\n\nURL: $siteUrl"
        }
    }

    // ----------------------------- Links ---------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val url = data.split("#", limit = 2)[0]

        // If it's a direct media link, return it immediately.
        if (MEDIA_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
            callback(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = url,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                )
            )
            return true
        }

        // Try Cloudstream's built-in extractors for known hosts.
        val extracted = loadExtractor(url, mainUrl, subtitleCallback, callback)
        if (extracted) return true

        // Fallback: try to scrape the page for embedded media links.
        return try {
            val doc = app.get(url).document
            var found = false

            // Look for video/source tags
            doc.select("video source, video").forEach { el ->
                val src = el.attr("src").ifBlank { el.attr("data-src") }
                if (src.isNotBlank()) {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = src,
                            referer = url,
                            quality = Qualities.Unknown.value,
                        )
                    )
                    found = true
                }
            }

            // Look for iframe embeds and run extractors on them
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                if (src.isNotBlank() && !EXCLUDED_DOMAINS.any { src.contains(it) }) {
                    val absoluteSrc = if (src.startsWith("http")) src else "https:$src"
                    if (loadExtractor(absoluteSrc, url, subtitleCallback, callback)) {
                        found = true
                    }
                }
            }

            // Look for direct media links in anchor tags
            doc.select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (MEDIA_EXTENSIONS.any { href.contains(it, ignoreCase = true) }) {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "${name} - ${a.text().ifBlank { "Direct" }}",
                            url = href,
                            referer = url,
                            quality = Qualities.Unknown.value,
                        )
                    )
                    found = true
                }
            }

            found
        } catch (_: Exception) {
            false
        }
    }

    // ----------------------------- Helpers --------------------------------

    /**
     * Parses list-item entries from the FMHY video page.
     * Each `<li>` typically contains one or more `<a>` links with a description like
     * "SiteName - Movies / TV / Anime / ...".
     */
    private fun parseEntries(root: org.jsoup.nodes.Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()

        root.select("li").forEach { li ->
            parseSiteEntry(li, results, seen)
        }

        return results
    }

    private fun parseSiteEntry(
        li: Element,
        results: MutableList<SearchResponse>,
        seen: MutableSet<String>,
    ) {
        val links = li.select("a[href]")
        if (links.isEmpty()) return

        // The text content of the <li> is something like:
        // "SiteName - Movies / TV / Anime / Auto-Next"
        val fullText = li.text().trim()

        for (link in links) {
            val href = link.attr("href")
            if (href.isBlank()) continue
            if (EXCLUDED_DOMAINS.any { href.contains(it) }) continue
            if (!href.startsWith("http")) continue
            if (href in seen) continue
            seen.add(href)

            val siteName = link.text().trim()
            if (siteName.isBlank() || siteName.length < 2) continue

            // Build a meaningful title: "SiteName" + any category tags from the line
            val categories = extractCategories(fullText)
            val title = if (categories.isNotBlank()) {
                "$siteName - $categories"
            } else {
                siteName
            }

            results.add(
                newMovieSearchResponse(
                    name = title,
                    url = "$href#${siteName.replace(" ", "-")}",
                    type = TvType.Others,
                )
            )
        }
    }

    /**
     * Extracts category tags like "Movies / TV / Anime" from an FMHY line.
     */
    private fun extractCategories(text: String): String {
        val dashIdx = text.indexOf(" - ")
        if (dashIdx == -1) return ""
        return text.substring(dashIdx + 3).trim()
    }
}
