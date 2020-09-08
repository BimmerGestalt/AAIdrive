package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import com.spotify.protocol.types.ImageUri
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class SpotifyMusicMetadata(
		val spotifyController: SpotifyAppController,
		val s_mediaId: String? = null,
		val s_queueId: Long? = null,
		val s_coverArtUri: String? = null,
		val s_artist: String? = null,
		val s_album: String? = null,
		val s_title: String? = null
		): MusicMetadata(mediaId = s_mediaId, queueId = s_queueId, coverArtUri = s_coverArtUri, artist = s_artist, album = s_album, title = s_title) {

  	override val coverArt: Bitmap?
      get() = spotifyController.getCoverArt(ImageUri(coverArtUri))
}