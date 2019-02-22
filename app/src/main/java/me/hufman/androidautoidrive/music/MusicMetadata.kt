package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

private const val TAG = "MusicMetadata"
data class MusicMetadata(val mediaId: String? = null,
                        val queueId: Long? = null,
                    val duration: Long? = null,
                    val coverArt: Bitmap? = null,
                    val icon: Bitmap? = null,
                    val artist: String? = null,
                    val album: String? = null,
                    val title: String? = null,
                    val trackNumber: Long? = null,
                    val trackCount: Long? = null
                    ) {
	companion object {
		fun fromMediaMetadata(metadata: MediaMetadataCompat, playbackState: PlaybackStateCompat? = null): MusicMetadata {
			//Log.i(TAG, "Parsing MediaMetadata ${metadata.bundle}")
			return MusicMetadata(
					mediaId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
					queueId = playbackState?.activeQueueItemId,
					duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
					coverArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) ?:
							metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART),
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

		fun fromQueueItem(queueItem: MediaSessionCompat.QueueItem): MusicMetadata {
			val id = queueItem.queueId
			val desc = queueItem.description
			return MusicMetadata(queueId = id,
					icon = desc.iconBitmap,
					title = desc.title.toString(),
					artist = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST) ?:
							desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST),
					album = desc.extras?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
			)
		}
	}
}