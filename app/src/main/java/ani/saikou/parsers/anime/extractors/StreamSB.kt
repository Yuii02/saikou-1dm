package ani.saikou.parsers.anime.extractors

import ani.saikou.client
import ani.saikou.findBetween
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class StreamSB(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val videos = mutableListOf<Video>()
        val hex = bytesToHex((server.embed.url.let { it.findBetween("/e/", ".html") ?: it.split("/e/")[1] }.encodeToByteArray()))
        val jsonLink =
            "${getHost()}/7361696b6f757c7c${hex}7c7c7361696b6f757c7c73747265616d7362/7361696b6f757c7c363136653639366436343663363136653639366436343663376337633631366536393664363436633631366536393664363436633763376336313665363936643634366336313665363936643634366337633763373337343732363536313664373336327c7c7361696b6f757c7c73747265616d7362"
        val json = client.get(jsonLink, mapOf("watchsb" to "streamsb")).parsed<Response>()
        if (json.statusCode == 200) {
            videos.add(Video(null, true, json.streamData!!.file))
        }
        return VideoContainer(videos)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    @Serializable
    private data class Response(
        @SerialName("stream_data")
        val streamData: StreamData? = null,
        @SerialName("status_code")
        val statusCode: Int? = null
    )

    @Serializable
    private data class StreamData(
        @SerialName("file") val file: String
    )

    companion object {
        private var host: String? = null
        private suspend fun getHost(): String? {
            host = host
                ?: client.get("https://raw.githubusercontent.com/saikou-app/mal-id-filler-list/main/sb.txt").text
            return host
        }
    }
}