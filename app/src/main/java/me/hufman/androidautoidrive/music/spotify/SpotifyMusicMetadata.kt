package me.hufman.androidautoidrive.music.spotify

import android.graphics.Bitmap
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.ListItem
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class SpotifyMusicMetadata(
		val spotifyController: SpotifyAppController,
		mediaId: String? = null,
		queueId: Long? = null,
		coverArtUri: String? = null,
		artist: String? = null,
		album: String? = null,
		title: String? = null,
		subtitle: String? = null,
		playable: Boolean = false,
		browseable: Boolean = false
		): MusicMetadata(mediaId = mediaId, queueId = queueId, coverArtUri = coverArtUri, artist = artist, album = album, title = title, subtitle = subtitle, playable = playable, browseable = browseable) {

	companion object {
		fun fromMusicMetadata(spotifyController: SpotifyAppController, musicMetadata: MusicMetadata): SpotifyMusicMetadata {
			return SpotifyMusicMetadata(spotifyController, mediaId = musicMetadata.mediaId, queueId = musicMetadata.queueId, coverArtUri = musicMetadata.coverArtUri, artist = musicMetadata.artist, album = musicMetadata.album, title = musicMetadata.title)
		}

		fun createSpotifyMusicMetadataList(spotifyAppController: SpotifyAppController, musicMetadataList: List<MusicMetadata>): List<SpotifyMusicMetadata> {
			return musicMetadataList.map { fromMusicMetadata(spotifyAppController, it) }
		}

		fun fromBrowseItem(spotifyController: SpotifyAppController, listItem: ListItem): SpotifyMusicMetadata {
			// some imageUris that aren't valid Spotify imageUris occasionally come up and can't be used in the ImagesApi call
			val coverArtUri = if (listItem.imageUri == null || listItem.imageUri.raw?.contains("android") == true) null else listItem.imageUri.raw

			return SpotifyMusicMetadata(spotifyController, mediaId = listItem.uri, queueId = listItem.uri.hashCode().toLong(), title = listItem.title, subtitle = listItem.subtitle, playable = listItem.playable, browseable = listItem.hasChildren, coverArtUri = coverArtUri)
		}

		fun fromSpotifyQueueListItem(spotifyController: SpotifyAppController, listItem: ListItem): SpotifyMusicMetadata {
			// some imageUris that aren't valid Spotify imageUris occasionally come up and can't be used in the ImagesApi call
			val coverArtUri = if (listItem.imageUri == null || listItem.imageUri.raw?.contains("android") == true) null else listItem.imageUri.raw

			return SpotifyMusicMetadata(spotifyController, mediaId = listItem.uri, queueId = listItem.uri.hashCode().toLong(),
					title = listItem.title, artist = listItem.subtitle, subtitle = listItem.subtitle,
					playable = listItem.playable, browseable = listItem.hasChildren,
					coverArtUri = coverArtUri)
		}
	}

  	override val coverArt: Bitmap?
      get() = if (coverArtUri != null) spotifyController.getCoverArt(ImageUri(coverArtUri)) else null
}