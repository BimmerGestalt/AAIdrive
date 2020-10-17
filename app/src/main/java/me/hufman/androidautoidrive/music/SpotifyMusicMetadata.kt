package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import com.spotify.protocol.types.ImageUri
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class SpotifyMusicMetadata(
		val spotifyController: SpotifyAppController,
		mediaId: String? = null,
		queueId: Long? = null,
		coverArtUri: String? = null,
		artist: String? = null,
		album: String? = null,
		title: String? = null
		): MusicMetadata(mediaId = mediaId, queueId = queueId, coverArtUri = coverArtUri, artist = artist, album = album, title = title) {

	companion object {
		fun fromMusicMetadata(spotifyController: SpotifyAppController, musicMetadata: MusicMetadata): SpotifyMusicMetadata {
			return SpotifyMusicMetadata(spotifyController, mediaId = musicMetadata.mediaId, queueId = musicMetadata.queueId, coverArtUri = musicMetadata.coverArtUri, artist = musicMetadata.artist, album = musicMetadata.album, title = musicMetadata.title)
		}

		fun createSpotifyMusicMetadataList(spotifyAppController: SpotifyAppController, musicMetadataList: List<MusicMetadata>): List<SpotifyMusicMetadata> {
			return musicMetadataList.map { fromMusicMetadata(spotifyAppController, it) }
		}
	}

  	override val coverArt: Bitmap?
      get() = spotifyController.getCoverArt(ImageUri(coverArtUri))
}