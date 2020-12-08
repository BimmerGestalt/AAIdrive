package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import kotlinx.android.synthetic.main.activity_setup.*
import me.hufman.androidautoidrive.*
import java.text.SimpleDateFormat
import java.util.*

class SetupActivity : AppCompatActivity() {
	companion object {
		const val REDRAW_DEBOUNCE = 100
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.SetupActivity.REDRAW"
		fun redraw(context: Context) {
			context.sendBroadcast(Intent(INTENT_REDRAW))
		}
	}

	val appSettings by lazy { MutableAppSettingsReceiver(this) }

	val redrawListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			redraw()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_setup)

		val buildTime = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIME))
		txtBuildInfo.text = getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, buildTime)

		swAdvancedSettings.setOnClickListener {
			appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS] = swAdvancedSettings.isChecked.toString()
			redraw()
		}

		this.registerReceiver(redrawListener, IntentFilter(INTENT_REDRAW))
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	override fun onDestroy() {
		super.onDestroy()
		this.unregisterReceiver(redrawListener)
	}

	fun redraw() {
		if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
			return
		}

		// only redraw on the main thread
		if (!Looper.getMainLooper().isCurrentThread) {
			sendBroadcast(Intent(INTENT_REDRAW))
			return
		}

		// toggle the advanced details
		val showAdvancedSettings = appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		swAdvancedSettings.isChecked = showAdvancedSettings
		paneAdvancedInfo.visible = showAdvancedSettings
	}
}