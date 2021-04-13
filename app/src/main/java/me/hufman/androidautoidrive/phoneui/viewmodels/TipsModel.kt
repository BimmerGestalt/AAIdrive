package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.hufman.androidautoidrive.CarCapabilitiesSummarized
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R

data class Tip(val text: Context.() -> String, val drawable: Context.() -> Drawable?, val condition: CarCapabilitiesSummarized.() -> Boolean)

class TipsModel: ViewModel() {
	var mode = ""
	val carInformation = CarInformation()

	val MUSIC_TIPS = listOf(
		Tip({getString(R.string.tip_audioplayer_bookmark)}, {null}) { isId4 },
		Tip({getString(R.string.tip_audioplayer_nowplaying_bookmark)}, {null}) {true},
		Tip({getString(R.string.tip_music_hide)}, {null}) {true},
	)
	val NOTIFICATIONS_TIPS = listOf(
		Tip({getString(R.string.tip_popup_suppress_id5)}, {null}) { isId5 },
		Tip({getString(R.string.tip_notification_bookmark)}, {null}) {true},
		Tip({getString(R.string.tip_notification_bookmark_details)}, {null}) {true},
		Tip({getString(R.string.tip_notification_quickaccess)}, {null}) {true},
	)
	val ALL_TIPS = MUSIC_TIPS + NOTIFICATIONS_TIPS

	private val _hasCarConnected = MutableLiveData<Boolean>()
	val hasCarConnnected: LiveData<Boolean> = _hasCarConnected

	val currentTips: MutableList<Tip> = ArrayList()

	init {
		update()
	}

	fun update() {
		_hasCarConnected.value = carInformation.capabilities.isNotEmpty()
		val summarized = CarCapabilitiesSummarized(carInformation)
		currentTips.clear()
		val sourceTips = when(mode) {
			"notifications" -> NOTIFICATIONS_TIPS
			"music" -> MUSIC_TIPS
			else -> ALL_TIPS
		}
		currentTips.addAll(sourceTips.filter { summarized.run(it.condition) })
	}
}