package me.hufman.androidautoidrive.music

import android.support.v4.media.session.PlaybackStateCompat

enum class MusicAction(val flag: Long) {
	PLAY(PlaybackStateCompat.ACTION_PLAY),
	PAUSE(PlaybackStateCompat.ACTION_PAUSE),
	REWIND(PlaybackStateCompat.ACTION_REWIND),
	FAST_FORWARD(PlaybackStateCompat.ACTION_FAST_FORWARD),
	PLAY_FROM_SEARCH(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH),
	SEEK_TO(PlaybackStateCompat.ACTION_SEEK_TO),
	SET_SHUFFLE_MODE(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE),
	SKIP_TO_NEXT(PlaybackStateCompat.ACTION_SKIP_TO_NEXT),
	SKIP_TO_PREVIOUS(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS),
	SKIP_TO_QUEUE_ITEM(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM),
	SET_REPEAT_MODE(PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
}
