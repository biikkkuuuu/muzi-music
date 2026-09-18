package com.biikkkuuuu.muzi.di

import com.biikkkuuuu.muzi.spotifyimport.UniversalPlaylistImporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaylistImporterEntryPoint {
    fun playlistImporter(): UniversalPlaylistImporter
}
