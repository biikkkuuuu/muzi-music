package com.biikkkuuuu.muzi.spotifyimport

import com.biikkkuuuu.muzi.spotify.models.SpotifyImage
import com.biikkkuuuu.muzi.spotify.models.SpotifyPlaylist
import com.biikkkuuuu.muzi.spotify.models.SpotifyPlaylistOwner
import com.biikkkuuuu.muzi.spotify.models.SpotifyPlaylistTracksRef
import com.biikkkuuuu.muzi.spotify.models.SpotifySimpleAlbum
import com.biikkkuuuu.muzi.spotify.models.SpotifySimpleArtist
import com.biikkkuuuu.muzi.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object SpotifyPublicPlaylistScraper {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private fun JsonElement?.asObj(): JsonObject? = if (this is JsonObject) this else null
    private fun JsonElement?.asArr(): JsonArray? = if (this is JsonArray) this else null
    private fun JsonElement?.asStr(): String? = if (this is JsonPrimitive && this !is JsonNull) this.content else null
    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key)?.asObj()
    private fun JsonObject?.arr(key: String): JsonArray? = this?.get(key)?.asArr()
    private fun JsonObject?.str(key: String): String? = this?.get(key)?.asStr()

    data class PublicPlaylistResult(
        val playlist: SpotifyPlaylist,
        val tracks: List<SpotifyTrack>,
    )

    suspend fun fetchPlaylist(playlistId: String): Result<PublicPlaylistResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
                val url = URL(embedUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Spotify embed responded with HTTP $responseCode")
                }

                val html = conn.inputStream.bufferedReader().use { it.readText() }

                val scriptToken = "__NEXT_DATA__"
                val tokenIdx = html.indexOf(scriptToken)
                if (tokenIdx == -1) {
                    throw IllegalStateException("Could not parse Spotify embed page (__NEXT_DATA__ missing)")
                }

                val scriptStart = html.indexOf('>', tokenIdx)
                val scriptEnd = html.indexOf("</script>", tokenIdx)
                if (scriptStart == -1 || scriptEnd == -1) {
                    throw IllegalStateException("Malformed script tag in Spotify embed page")
                }

                val jsonText = html.substring(scriptStart + 1, scriptEnd).trim()
                val rootJson = json.parseToJsonElement(jsonText).asObj()
                    ?: throw IllegalStateException("Invalid root JSON in embed")

                val pageProps = rootJson.obj("props").obj("pageProps")
                    ?: throw IllegalStateException("pageProps not found in Spotify data")

                val status = pageProps.str("status")
                if (status == "404") {
                    throw IllegalArgumentException("Spotify playlist not found or is private")
                }

                val entity = pageProps.obj("state").obj("data").obj("entity")
                    ?: throw IllegalStateException("Entity not found in Spotify embed data")

                val playlistName = entity.str("name")
                    ?: entity.str("title")
                    ?: "Spotify Playlist"

                val coverSources = entity.obj("coverArt").arr("sources")
                    ?: entity.obj("visualIdentity").arr("image")
                val coverUrl = coverSources?.firstOrNull()?.asObj()?.str("url")

                val rawTracks = entity.arr("trackList") ?: emptyList()
                val parsedTracks = mutableListOf<SpotifyTrack>()

                for (item in rawTracks) {
                    val trackObj = item.asObj() ?: continue
                    val title = trackObj.str("title") ?: continue
                    if (title.isBlank()) continue

                    val subtitle = trackObj.str("subtitle").orEmpty()
                    val durationMs = trackObj.str("duration")?.toIntOrNull() ?: 0
                    val uri = trackObj.str("uri").orEmpty()
                    val id = uri.substringAfterLast(":", "")

                    val artists = subtitle.split(",")
                        .map { it.replace('\u00A0', ' ').trim() }
                        .filter { it.isNotEmpty() }
                        .map { SpotifySimpleArtist(id = null, name = it) }

                    val previewUrl = trackObj.obj("audioPreview").str("url")

                    parsedTracks.add(
                        SpotifyTrack(
                            id = id,
                            name = title.replace('\u00A0', ' ').trim(),
                            artists = if (artists.isNotEmpty()) artists else listOf(SpotifySimpleArtist(name = "Unknown Artist")),
                            album = SpotifySimpleAlbum(
                                name = playlistName,
                                images = if (coverUrl != null) listOf(SpotifyImage(url = coverUrl)) else emptyList()
                            ),
                            durationMs = durationMs,
                            previewUrl = previewUrl,
                            uri = uri.ifEmpty { null }
                        )
                    )
                }

                val playlist = SpotifyPlaylist(
                    id = playlistId,
                    name = playlistName,
                    description = entity.str("subtitle"),
                    images = if (coverUrl != null) listOf(SpotifyImage(url = coverUrl)) else emptyList(),
                    owner = SpotifyPlaylistOwner(
                        id = "",
                        displayName = "Spotify",
                    ),
                    tracks = SpotifyPlaylistTracksRef(total = parsedTracks.size),
                )

                PublicPlaylistResult(
                    playlist = playlist,
                    tracks = parsedTracks,
                )
            }
        }
}
