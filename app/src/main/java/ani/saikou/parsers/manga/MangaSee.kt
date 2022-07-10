package ani.saikou.parsers.manga

import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.findBetween
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.sortByTitle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MangaSee : MangaParser() {

    override val name = "MangaSee"
    override val saveName = "manga_see"
    override val hostUrl = host

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {

        val json = client.get("$hostUrl/manga/$mangaLink").document.select("script")
            .lastOrNull()?.toString()?.findBetween("vm.Chapters = ", ";") ?: return listOf()

        return Mapper.parse<List<MangaResponse>>(json).reversed().map {
            val chap = it.chapter
            val num = "${
                if (chap.startsWith("0") || chap.startsWith("1")) "" else  "S" + chap[0] + " : "
            }${
                chap.drop(1).dropLast(1).toInt()
            }${
                if (chap.endsWith("0")) "" else (".${chap[chap.length - 1]}")
            }"
            val link = hostUrl + "/read-online/$mangaLink" + chapterURLEncode(chap)
            MangaChapter(num, link, it.chapterName)
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val res = client.get(chapterLink).document.select("script").lastOrNull()
        val str = res?.toString() ?: return listOf()
        val server = str.findBetween("vm.CurPathName = ", ";")?.trim('"') ?: return listOf()
        var slug = str.findBetween("vm.IndexName = ", ";")?.trim('"') ?: return listOf()
        val json = Mapper.parse<ChapterResponse>(
            str.findBetween("vm.CurChapter = ", ";") ?: return listOf()
        )
        slug += json.directory.let { if (it.isEmpty()) "" else "/$it" }
        val chap = chapterImage(json.chapter)

        return (1..json.page.toInt()).map {
            val link = "https://$server/manga/$slug/$chap-${"000$it".takeLast(3)}.png"
            MangaImage(link)
        }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val list = getSearchData().toMutableList()
        list.sortByTitle(query)
        return list
    }

    companion object {
        private const val host = "https://mangasee123.com"
        private var response: List<ShowResponse>? = null
        suspend fun getSearchData(): List<ShowResponse> {
            response = if (response != null) response ?: listOf()
            else {
                val json = client.get("$host/search/").document.select("script")
                    .last().toString().findBetween("vm.Directory = ", "\n")!!.replace(";", "")
                Mapper.parse<List<SearchResponse>>(json).map {
                    ShowResponse(
                        it.s, it.i, "https://cover.nep.li/cover/${it.i}.jpg"
                    )
                }
            }
            return response ?: listOf()
        }
    }

    private fun chapterURLEncode(e: String): String {
        var index = ""
        val t = e.substring(0, 1).toInt()
        if (1 != t) {
            index = "-index-$t"
        }
        val dgt = when {
            e.toInt() < 100100 -> 4
            e.toInt() < 101000 -> 3
            e.toInt() < 110000 -> 2
            else               -> 1
        }
        val n = e.substring(dgt, e.length - 1)
        var suffix = ""
        val path = e.substring(e.length - 1).toInt()
        if (0 != path) {
            suffix = ".$path"
        }
        return "-chapter-$n$suffix$index.html"
    }

    private val chapterImageRegex = Regex("""^0+""")

    private fun chapterImage(e: String, cleanString: Boolean = false): String {
        val a = e.substring(1, e.length - 1).let { if (cleanString) it.replace(chapterImageRegex, "") else it }
        val b = e.substring(e.length - 1).toInt()
        return when {
            (b == 0 && a.isNotEmpty()) -> a
            (b == 0 && a.isEmpty())    -> "0"
            else                       -> "$a.$b"
        }
    }

    @Serializable
    private data class MangaResponse(
        @SerialName("Chapter") val chapter: String,
        @SerialName("ChapterName") val chapterName: String?
    )

    @Serializable
    private data class ChapterResponse(
        @SerialName("Chapter") val chapter: String,
        @SerialName("Page") val page: String,
        @SerialName("Directory") val directory: String
    )

    @Serializable
    private data class SearchResponse(
        val s: String,
        val i: String
    )
}