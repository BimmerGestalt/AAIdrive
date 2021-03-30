package me.hufman.androidautoidrive.music.spotify

import kotlinx.serialization.Serializable

@Serializable
data class LikedSongsState(val hashCode: String, val playlistUri: String, val playlistId: String, var queueCoverArtUri: String?)