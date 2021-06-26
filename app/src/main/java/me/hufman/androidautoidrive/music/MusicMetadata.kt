package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import me.hufman.androidautoidrive.utils.dumpToString
import java.lang.ref.WeakReference

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
		var lastLoggedMetadata: WeakReference<MediaMetadataCompat>? = null
		fun fromMediaMetadata(metadata: MediaMetadataCompat, playbackState: PlaybackStateCompat? = null): MusicMetadata {
			val lastLoggedMetadata = lastLoggedMetadata?.get()
			val changed = lastLoggedMetadata?.mediaId != metadata.mediaId ||
					lastLoggedMetadata?.title != metadata.title ||
					lastLoggedMetadata?.displayTitle != metadata.displayTitle ||
					lastLoggedMetadata?.displaySubtitle != metadata.displaySubtitle
			if (changed) {
				Log.i(TAG, "Parsing MediaMetadata ${metadata.bundle.dumpToString()}")
				Log.i(TAG, "Playback state: queueId:${playbackState?.activeQueueItemId}")
				this.lastLoggedMetadata = WeakReference(metadata)
			}
			// some apps only set DISPLAY_TITLE and DISPLAY_SUBTITLE
			// so use those for artist/title fields, because that matches the car widget display order
			// prefer the artist field if the DISPLAY_TITLE is the same as the COLLECTION
			var artist: String? = null
			var album: String? = null
			var title: String? = null
			if (metadata.compilation != null && metadata.compilation == metadata.displayTitle &&
					metadata.artist != metadata.displaySubtitle) {
				// radio station is used as the Display Title, try not to prefer the Display fields
				// Except ignore when artist == displaySubtitle scenario from SomaFM
				artist = metadata.artist ?:
						metadata.albumArtist ?:
						metadata.displayTitle
				title = metadata.title ?:
						metadata.displaySubtitle ?:
						metadata.displayTitle
			} else {
				artist = metadata.displayTitle ?:
						metadata.artist ?:
						metadata.albumArtist
				title = metadata.displaySubtitle ?:
						metadata.displayTitle ?:
						metadata.title
			}
			album = metadata.album

			return MusicMetadata(
					mediaId = metadata.mediaId,
					queueId = playbackState?.activeQueueItemId,
					duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
					coverArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART) ?:
							metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) ?:
							metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON),
					coverArtUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ART_URI) ?:
							metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI) ?:
							metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI),
					icon = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON),
					artist = artist,
					album = album,
					title = title,
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

// Helper functions for convenient access to common metadata fields
val MediaMetadataCompat.mediaId: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
val MediaMetadataCompat.artist: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
val MediaMetadataCompat.albumArtist: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
val MediaMetadataCompat.album: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
val MediaMetadataCompat.compilation: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_COMPILATION)
val MediaMetadataCompat.title: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_TITLE)
val MediaMetadataCompat.displayTitle: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
val MediaMetadataCompat.displaySubtitle: String?
	get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)