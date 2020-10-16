package me.hufman.androidautoidrive.carapp.music

abstract class MusicImageIDs {
	abstract val COVERART_SMALL: Int
	abstract val COVERART_LARGE: Int
	abstract val ARTIST: Int
	abstract val ALBUM: Int
	abstract val SONG: Int
	abstract val SEARCH: Int
	abstract val BROWSE: Int
	abstract val ACTIONS: Int
	abstract val SKIP_BACK: Int
	abstract val SKIP_NEXT: Int
	abstract val SHUFFLE_OFF: Int
	abstract val SHUFFLE_ON: Int
	abstract val CHECKMARK: Int
	abstract val REPEAT_OFF: Int
	abstract val REPEAT_ALL_ON: Int
	abstract val REPEAT_ONE_ON: Int
}

object MusicImageIDsMultimedia: MusicImageIDs() {
	override val COVERART_SMALL = 146
	override val COVERART_LARGE = 147
	override val ARTIST = 150
	override val ALBUM = 148
	override val SONG = 152
	override val SEARCH = 154
	override val BROWSE = 155
	override val ACTIONS = 157
	override val SKIP_BACK = 159
	override val SKIP_NEXT = 160
	override val SHUFFLE_OFF = 158
	override val SHUFFLE_ON = 151
	override val CHECKMARK = 149
	override val REPEAT_OFF = 0
	override val REPEAT_ALL_ON = 0
	override val REPEAT_ONE_ON = 0
}
object MusicImageIDsSpotify: MusicImageIDs() {
	override val COVERART_SMALL = 147
	override val COVERART_LARGE = 148
	override val ARTIST = 151
	override val ALBUM = 149
	override val SONG = 153
	override val SEARCH = 155
	override val BROWSE = 156
	override val ACTIONS = 158
	override val SKIP_BACK = 160
	override val SKIP_NEXT = 161
	override val SHUFFLE_OFF = 159
	override val SHUFFLE_ON = 152
	override val CHECKMARK = 150
	override val REPEAT_OFF = 1015
	override val REPEAT_ALL_ON = 1007
	override val REPEAT_ONE_ON = 1008
}