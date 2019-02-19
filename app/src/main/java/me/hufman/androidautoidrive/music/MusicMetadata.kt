package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.util.Log

private const val TAG = "MusicMetadata"
data class MusicMetadata(val mediaId: String?,
                    val duration: Long?,
                    val coverArt: Bitmap? = null,
                    val icon: Bitmap? = null,
                    val artist: String?,
                    val album: String?,
                    val title: String?,
                    val trackNumber: Long? = null,
                    val trackCount: Long? = null
                    ) {
	companion object {
		fun fromMediaMetadata(metadata: MediaMetadataCompat): MusicMetadata {
			Log.i(TAG, "Parsing MediaMetadata ${metadata.bundle}")
			return MusicMetadata(mediaId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
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
	}
}