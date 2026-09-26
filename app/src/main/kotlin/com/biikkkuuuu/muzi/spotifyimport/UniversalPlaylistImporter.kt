package com.biikkkuuuu.muzi.spotifyimport

import android.content.Context
import com.biikkkuuuu.muzi.R
import com.biikkkuuuu.muzi.db.MusicDatabase
import com.biikkkuuuu.muzi.db.entities.PlaylistEntity
import com.biikkkuuuu.muzi.db.entities.PlaylistSongMap
import com.biikkkuuuu.muzi.models.toMediaMetadata
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaylistImportResult {
    data class Success(val localPlaylistId: String, val title: String, val trackCount: Int) : PlaylistImportResult
    data class Error(val message: String) : PlaylistImportResult
}

data class PlaylistImportProgress(
    val title: String,
    val current: Int,
    val total: Int,
    val message: String,
)

@Singleton
class UniversalPlaylistImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val spotifyImportRepository: SpotifyImportRepository,
) {
    suspend fun importPlaylist(
        url: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return@withContext PlaylistImportResult.Error(context.getString(R.string.invalid_playlist_url))
        }

        // Check if YouTube / YouTube Music playlist
        val ytListId = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.get(1)
        if (ytListId != null) {
            return@withContext importYouTubePlaylist(ytListId, onProgress)
        }

        // Check if Spotify playlist
        val isSpotify = trimmed.contains("spotify.com") || trimmed.startsWith("spotify:")
        if (isSpotify) {
            return@withContext importSpotifyPlaylist(trimmed, onProgress)
        }

        PlaylistImportResult.Error(context.getString(R.string.invalid_playlist_url))
    }

    private suspend fun importYouTubePlaylist(
        playlistId: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportResult {
        onProgress(PlaylistImportProgress("YouTube Music", 0, 0, context.getString(R.string.loading)))

        val allSongs = mutableListOf<SongItem>()
        var title = "Imported Playlist"
        var thumbnailUrl: String? = null

        if (playlistId.startsWith("RD")) {
            val songs = YouTube.queue(playlistId = playlistId).getOrElse { error ->
                if (error is CancellationException) throw error
                return PlaylistImportResult.Error(error.message ?: "Failed to fetch YouTube Mix")
            }
            allSongs.addAll(songs)
            title = "YouTube Mix"
            thumbnailUrl = allSongs.firstOrNull()?.thumbnail
        } else {
            val page = YouTube.playlist(playlistId).getOrElse { error ->
                if (error is CancellationException) throw error
                return PlaylistImportResult.Error(error.message ?: "Failed to fetch YouTube playlist")
            }
            title = page.playlist.title.ifBlank { "Imported Playlist" }
            thumbnailUrl = page.playlist.thumbnail?.ifBlank { null }
            allSongs.addAll(page.songs)

            var continuation = page.songsContinuation
            val seenContinuations = mutableSetOf<String>()

            while (continuation != null && seenContinuations.add(continuation)) {
                onProgress(PlaylistImportProgress(title, allSongs.size, allSongs.size, "Loading more tracks..."))
                val contPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                allSongs.addAll(contPage.songs)
                continuation = contPage.continuation
            }
            thumbnailUrl = thumbnailUrl ?: allSongs.firstOrNull()?.thumbnail
        }

        val localPlaylistId = "YT_PLAYLIST_$playlistId"

        database.withTransaction {
            val now = LocalDateTime.now()
            val existing = getPlaylistById(localPlaylistId)
            val entity = existing?.playlist?.copy(
                name = title,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl,
                isEditable = true,
            ) ?: PlaylistEntity(
                id = localPlaylistId,
                name = title,
                bookmarkedAt = now,
                lastUpdateTime = now,
                thumbnailUrl = thumbnailUrl,
                isEditable = true,
            )

            if (existing == null) {
                insert(entity)
            } else {
                update(entity)
            }

            clearPlaylist(localPlaylistId)

            allSongs.forEachIndexed { index, songItem ->
                onProgress(PlaylistImportProgress(title, index + 1, allSongs.size, "Saving ${songItem.title}"))
                val metadata = songItem.toMediaMetadata()
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = localPlaylistId,
                        songId = metadata.id,
                        position = index,
                        setVideoId = metadata.setVideoId,
                    )
                )
            }
            update(entity.copy(lastUpdateTime = now))
        }

        return PlaylistImportResult.Success(
            localPlaylistId = localPlaylistId,
            title = title,
            trackCount = allSongs.size,
        )
    }

    private suspend fun importSpotifyPlaylist(
        url: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportResult {
        onProgress(PlaylistImportProgress("Spotify", 0, 0, context.getString(R.string.loading)))

        val source = runCatching {
            spotifyImportRepository.addPlaylistByUrl(url)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return PlaylistImportResult.Error(error.message ?: "Failed to resolve Spotify playlist")
        }

        val summary = spotifyImportRepository.importSources(listOf(source)) { p ->
            onProgress(
                PlaylistImportProgress(
                    title = p.sourceTitle,
                    current = p.matchedTracks,
                    total = p.totalTracks,
                    message = "${p.matchedTracks} / ${p.totalTracks} tracks matched (${p.percent}%)",
                )
            )
        }

        val resultSummary = summary.sources.firstOrNull()
        return PlaylistImportResult.Success(
            localPlaylistId = source.localPlaylistId,
            title = source.title,
            trackCount = resultSummary?.importedTracks ?: 0,
        )
    }
}
