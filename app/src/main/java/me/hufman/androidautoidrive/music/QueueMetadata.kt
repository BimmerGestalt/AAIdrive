package me.hufman.androidautoidrive.music

private const val TAG = "QueueMetadata"
data class QueueMetadata(val title: String? = null,
                         val songs: List<MusicMetadata>? = null
                         ) {
	override fun toString(): String {
		return title ?: ""
	}
}