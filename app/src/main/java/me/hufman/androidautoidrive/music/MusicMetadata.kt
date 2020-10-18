package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import me.hufman.androidautoidrive.dumpToString

private const val TAG = "MusicMetadata"
open class MusicMetadata(val mediaId: String? = null,
                         val queueId: Long? = null,
                         val playable: Boolean = false,
                         val browseable: Boolean = false,
                         val duration: Long? = null,
                         open val coverArt: Bitmap? = null,
                         val coverArtUri: String? = null,
                         val icon: Bitmap? = null,
                         val artist: String? = null,
                         val album: String? = null,
                         val title: String? = null,
                         val subtitle: String? = null,
                         val trackNumber: Long? = null,
                         val trackCount: Long? = null,
                         val extras: Bundle? = null
                    ) {
	companion object {
		var lastLoggedMetadata: MediaMetadataCompat? = null
		fun fromMediaMetadata(metadata: MediaMetadataCompat, playbackState: PlaybackStateCompat? = null): MusicMetadata {
			if (lastLoggedMetadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) != metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)) {
				Log.i(TAG, "Parsing MediaMetadata ${metadata.bundle.dumpToString()}")
				Log.i(TAG, "Playback state: queueId:${playbackState?.activeQueueItemId}")
				lastLoggedMetadata = metadata
			}
			return MusicMetadata(
					mediaId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
					queueId = playbackState?.activeQueueItemId,
					duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
					coverArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART) ?:
							metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART),
					coverArtUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ART_URI) ?:
							metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI),
					icon = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON),
					artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST) ?:
							metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
					album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
					title = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) ?:
							metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE),
					trackNumber = metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER),
					trackCount = metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS)
			)
		}

		fun fromMediaItem(mediaItem: MediaBrowserCompat.MediaItem): MusicMetadata {
			val desc = mediaItem.description
			Log.i(TAG, "Parsing mediaitem ${desc.title} with extras ${desc.extras?.dumpToString()}")
			return MusicMetadata(
					mediaId = mediaItem.mediaId,
					browseable = mediaItem.isBrowsable,
					playable = mediaItem.isPlayable,
					title = desc.title?.toString(),
					subtitle = desc.subtitle?.toString(),
					artist = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST) ?:
					         desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
					album = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
					extras = desc.extras
			)
		}

		fun fromQueueItem(queueItem: MediaSessionCompat.QueueItem): MusicMetadata {
			val id = queueItem.queueId
			val desc = queueItem.description
			return MusicMetadata(queueId = id,
					icon = desc.iconBitmap,
					title = desc.title.toString(),
					subtitle = desc.subtitle?.toString(),
					artist = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST) ?:
							desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
					album = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
					extras = desc.extras
			)
		}

		fun copy(other: MusicMetadata, mediaId: String? = null, queueId: Long? = null, playable: Boolean? = null, browseable: Boolean? = null, duration: Long? = null, coverArt: Bitmap? = null,
		         coverArtUri: String? = null, icon: Bitmap? = null, artist: String? = null, album: String? = null, title: String? = null, subtitle: String? = null, trackCount: Long? = null,
		         trackNumber: Long? = null): MusicMetadata {
			return MusicMetadata(mediaId = mediaId ?: other.mediaId, queueId = queueId ?: other.queueId, playable = playable ?: other.playable, browseable = browseable ?: other.browseable, duration = duration ?: other.duration, coverArt = coverArt ?: other.coverArt, coverArtUri = coverArtUri ?: other.coverArtUri, icon = icon ?: other.icon, artist = artist ?: other.artist, album = album ?: other.album, title = title ?: other.title, subtitle = subtitle ?: other.subtitle, trackCount = trackCount ?: other.trackCount, trackNumber = trackNumber ?: other.trackNumber)
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MusicMetadata

		if (mediaId != other.mediaId) return false
		if (queueId != other.queueId) return false
		if (playable != other.playable) return false
		if (browseable != other.browseable) return false
		if (duration != other.duration) return false
		if (artist != other.artist) return false
		if (album != other.album) return false
		if (title != other.title) return false
		if (trackNumber != other.trackNumber) return false
		if (trackCount != other.trackCount) return false
		if (coverArtUri != other.coverArtUri) return false
		if ((coverArt == null) != (other.coverArt == null)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = mediaId?.hashCode() ?: 0
		result = 31 * result + (queueId?.hashCode() ?: 0)
		result = 31 * result + playable.hashCode()
		result = 31 * result + browseable.hashCode()
		result = 31 * result + (duration?.hashCode() ?: 0)
		result = 31 * result + (artist?.hashCode() ?: 0)
		result = 31 * result + (album?.hashCode() ?: 0)
		result = 31 * result + (title?.hashCode() ?: 0)
		result = 31 * result + (trackNumber?.hashCode() ?: 0)
		result = 31 * result + (trackCount?.hashCode() ?: 0)
		return result
	}

	override fun toString(): String {
		return title ?: mediaId ?: ""
	}
}