package ani.saikou.parsers.manga

import ani.saikou.anilist.Anilist
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.DecimalFormat

class AllAnime : MangaParser() {
    override val name = "AllAnime"
    override val saveName = "all_anime_manga"
    override val hostUrl = "https://allanime.site"

    private val idRegex = Regex("${hostUrl}/manga/(\\w+)")
    private val epNumRegex = Regex("/[sd]ub/(\\d+)")

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val responseArray = mutableListOf<MangaChapter>()
        tryWithSuspend {
            val showId = idRegex.find(mangaLink)?.groupValues?.get(1)
            if (showId != null) {
                val episodeInfos = getEpisodeInfos(showId)
                val format = DecimalFormat("#####.#####")
                episodeInfos?.sortedBy { it.episodeIdNum }?.forEach { epInfo ->
                    val link = """${hostUrl}/manga/$showId/chapters/sub/${epInfo.episodeIdNum}"""
                    val epNum = format.format(epInfo.episodeIdNum).toString()
                    responseArray.add(MangaChapter(epNum, link, epInfo.notes))
                }

            }
        }
        return responseArray
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val images = mutableListOf<MangaImage>()
        val showId = idRegex.find(chapterLink)?.groupValues?.get(1)
        val episodeNum = epNumRegex.find(chapterLink)?.groupValues?.get(1)
        if (showId != null && episodeNum != null) {
            tryWithSuspend {
                val variables = """{"mangaId":"$showId","translationType":"sub","chapterString":"$episodeNum","limit":1000000}"""
                val chapterPages =
                    graphqlQuery(
                        variables,
                        "fd2226907c2435bdfc0d03a9c46ef354b75ba42ec0599acb6b3346ef9c1e162d"
                    )?.data?.chapterPages?.edges
                // For future reference: If pictureUrlHead is null then the link provided is a relative link of the "apivtwo" variety, but it doesn't seem to contain useful images
                val chapter = chapterPages?.filter { !it.pictureUrlHead.isNullOrEmpty() }?.get(0)
                chapter?.pictureUrls?.sortedBy { it.num }?.forEach { images.add(MangaImage("""${chapter.pictureUrlHead}${it.url}""")) }
            }
        }
        return images
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val responseArray = arrayListOf<ShowResponse>()
        tryWithSuspend {
            val variables =
                """{"search":{"isManga":true,"allowAdult":${Anilist.adult},"query":"$query"},"translationType":"sub"}"""
            val edges =
                graphqlQuery(variables, "d35cd021d782eb55310250ea818269622b4b94742c25f8af778562317966ac88")?.data?.mangas?.edges
            if (!edges.isNullOrEmpty()) {
                for (show in edges) {
                    val link = """${hostUrl}/manga/${show.id}"""
                    val otherNames = mutableListOf<String>()
                    show.englishName?.let { otherNames.add(it) }
                    show.nativeName?.let { otherNames.add(it) }
                    show.altNames?.forEach { otherNames.add(it) }
                    responseArray.add(
                        ShowResponse(show.name, link, show.thumbnail, otherNames, show.availableChapters.sub)
                    )
                }
            }
        }
        return responseArray
    }

    private suspend fun graphqlQuery(variables: String, persistHash: String): Query? {
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$persistHash"}}"""
        val graphqlUrl = ("$hostUrl/graphql").toHttpUrl().newBuilder().addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions).build()
        val headers = mutableMapOf<String, String>()
        headers["Host"] = "allanime.site"
        return tryWithSuspend {
            client.get(graphqlUrl.toString(), headers).parsed()
        }
    }

    private suspend fun getEpisodeInfos(showId: String): List<EpisodeInfo>? {
        val variables = """{"_id": "$showId"}"""
        val manga = graphqlQuery(variables, "f60064134ecbaf89350a8aae1441dbffc86cf561a193a3bb8db4bb5a9989b9ad")?.data?.manga
        if (manga != null) {
            val epCount = manga.availableChapters.sub
            val epVariables = """{"showId":"manga@$showId","episodeNumStart":0,"episodeNumEnd":${epCount.toFloat()}}"""
            return graphqlQuery(
                epVariables,
                "73d998d209d6d8de325db91ed8f65716dce2a1c5f4df7d304d952fa3f223c9e8"
            )?.data?.episodeInfos
        }
        return null
    }

    @Serializable
    private data class Query(
        @SerialName("data") var data: Data?
    ) {
        @Serializable
        data class Data(
            @SerialName("manga") val manga: Manga?,
            @SerialName("mangas") val mangas: MangasConnection?,
            @SerialName("episodeInfos") val episodeInfos: List<EpisodeInfo>?,
            @SerialName("chapterPages") val chapterPages: ChapterConnection?,
        )

        @Serializable
        data class MangasConnection(
            @SerialName("edges") val edges: List<Manga>
        )

        @Serializable
        data class Manga(
            @SerialName("_id") val id: String,
            @SerialName("name") val name: String,
            @SerialName("description") val description: String?,
            @SerialName("englishName") val englishName: String?,
            @SerialName("nativeName") val nativeName: String?,
            @SerialName("thumbnail") val thumbnail: String,
            @SerialName("availableChapters") val availableChapters: AvailableChapters,
            @SerialName("altNames") val altNames: List<String>?
        )

        @Serializable
        data class AvailableChapters(
            @SerialName("sub") val sub: Int,
            @SerialName("raw") val raw: Int
        )

        @Serializable
        data class ChapterConnection(
            @SerialName("edges") val edges: List<Chapter>
        ) {
            @Serializable
            data class Chapter(
                @SerialName("pictureUrls") val pictureUrls: List<PictureUrl>,
                @SerialName("pictureUrlHead") val pictureUrlHead: String?
            )

            @Serializable
            data class PictureUrl(
                @SerialName("num") val num: Int,
                @SerialName("url") val url: String

                )
        }
    }

    @Serializable
    private data class EpisodeInfo(
        // Episode "numbers" can have decimal values, hence float
        @SerialName("episodeIdNum") val episodeIdNum: Float,
        @SerialName("notes") val notes: String?,
        @SerialName("thumbnails") val thumbnails: List<String>?,
    )

}
