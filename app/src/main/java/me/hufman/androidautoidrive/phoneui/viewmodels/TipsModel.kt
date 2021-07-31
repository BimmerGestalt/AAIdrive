package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.CarCapabilitiesSummarized
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R

open class Tip(val text: Context.() -> String, val drawable: Context.() -> Drawable?)

abstract class TipsModel: ViewModel() {
	val currentTips: MutableList<Tip> = ArrayList()
	var mode = ""
	abstract fun update()
}

class CapabilitiesTip(text: Context.() -> String, drawable: Context.() -> Drawable?, val condition: CarCapabilitiesSummarized.() -> Boolean): Tip(text, drawable)

class CapabilitiesTipsModel(overrideCarInformation: CarInformation? = null): TipsModel() {
	class Factory() : ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			return CapabilitiesTipsModel().apply {
				update()
			} as T
		}
	}

	val carInformation = overrideCarInformation ?: CarInformation()

	val MUSIC_TIPS = listOf(
		CapabilitiesTip({getString(R.string.tip_audioplayer_bookmark)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_audioplayer_entrybutton_mini_id4)}) { isId4 },
		CapabilitiesTip({getString(R.string.tip_bluetooth_music)}, {ContextCompat.getDrawable(this, R.drawable.tip_music_bluetooth_mini_id5)})  { isId4 },
		CapabilitiesTip({getString(R.string.tip_audioplayer_nowplaying_bookmark)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_music_queuebutton_bmw_id5)}) { isBmw },
		CapabilitiesTip({getString(R.string.tip_audioplayer_nowplaying_bookmark)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_music_queuebutton_mini_id5)}) { isMini },
		CapabilitiesTip({getString(R.string.tip_music_hide)}, {ContextCompat.getDrawable(this, R.drawable.pic_phone_music_swipe)}) {true},
		CapabilitiesTip({getString(R.string.tip_batterymode_music)}, {ContextCompat.getDrawable(this, R.drawable.tip_batterymode_music)}) { true }
	)
	val NOTIFICATIONS_TIPS = listOf(
		CapabilitiesTip({getString(R.string.tip_popup_suppress_id5)}, {ContextCompat.getDrawable(this, R.drawable.tip_input_emoji_bmw)}) { isId5 && isBmw },
		CapabilitiesTip({getString(R.string.tip_popup_suppress_id5)}, {ContextCompat.getDrawable(this, R.drawable.tip_input_emoji_mini)}) { isId5 && isMini },
		CapabilitiesTip({getString(R.string.tip_notification_bookmark)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_entrybutton_mini_id4)}) {isId4},
		CapabilitiesTip({getString(R.string.tip_notification_bookmark_ambutton)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_ambutton_bmw_id5)}) {isId5 && isBmw},
		CapabilitiesTip({getString(R.string.tip_notification_bookmark_ambutton)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_ambutton_mini_id5)}) {isId5 && isMini},
		CapabilitiesTip({getString(R.string.tip_notification_bookmark_details)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_list_bmw_id5)}) {isBmw},
		CapabilitiesTip({getString(R.string.tip_notification_bookmark_details)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_list_mini_id4)}) {isMini && isId4},
		CapabilitiesTip({getString(R.string.tip_notification_bookmark_details)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_list_mini_id5)}) {isMini && isId5},
		CapabilitiesTip({getString(R.string.tip_notification_quickaccess)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_read_bmw_id5)}) {isBmw},
		CapabilitiesTip({getString(R.string.tip_notification_quickaccess)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_popup_mini_id4)}) {isMini && isId4},
		CapabilitiesTip({getString(R.string.tip_notification_quickaccess)}, {ContextCompat.getDrawable(this, R.drawable.tip_bookmark_news_read_mini_id5)}) {isMini && isId5},
		CapabilitiesTip({getString(R.string.tip_input_emoji)}, {ContextCompat.getDrawable(this, R.drawable.tip_input_emoji_bmw)}) { isBmw },
		CapabilitiesTip({getString(R.string.tip_input_emoji)}, {ContextCompat.getDrawable(this, R.drawable.tip_input_emoji_mini)}) { isMini },
	)


	init {
		update()
	}

	override fun update() {
		val summarized = CarCapabilitiesSummarized(carInformation)
		currentTips.clear()
		val sourceTips = when(mode) {
			"notifications" -> NOTIFICATIONS_TIPS
			"music" -> MUSIC_TIPS
			else -> emptyList()
		}
		currentTips.addAll(sourceTips.filter { summarized.run(it.condition) })
	}
}