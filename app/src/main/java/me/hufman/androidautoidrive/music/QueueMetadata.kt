package me.hufman.androidautoidrive.music

import android.graphics.Bitmap

private const val TAG = "QueueMetadata"
data class QueueMetadata(val title: String? = null,
                         val subtitle: String? = null,
                         val songs: List<MusicMetadata>? = null,
						 val coverArt: Bitmap? = null
                         ) {
}