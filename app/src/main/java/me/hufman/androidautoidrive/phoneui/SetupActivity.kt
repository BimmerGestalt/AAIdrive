package me.hufman.androidautoidrive.phoneui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.android.synthetic.main.activity_setup.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.assistant.AssistantControllerAndroid
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import java.text.SimpleDateFormat
import java.util.*

class SetupActivity : AppCompatActivity() {
	companion object {
		const val INTENT_REDRAW = "me.hufman.androidautoidrive.SetupActivity.REDRAW"
		fun redraw(context: Context) {
			context.sendBroadcast(Intent(INTENT_REDRAW))
		}
	}

	val securityAccess = SecurityAccess.getInstance(this)

	val bclStatusListener = BclStatusListener()

	val redrawListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			redraw()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_setup)

		btnInstallBmwClassic.setOnClickListener { installBMWClassic() }
		btnInstallMissingBMWClassic.setOnClickListener { installBMWClassic() }
		btnInstallMiniClassic.setOnClickListener { installMiniClassic() }
		btnInstallMissingMiniClassic.setOnClickListener { installMiniClassic() }

		swAdvancedSettings.setOnClickListener {
			AppSettings.saveSetting(this, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS, swAdvancedSettings.isChecked.toString())
			redraw()
		}

		this.registerReceiver(redrawListener, IntentFilter(INTENT_REDRAW))
	}

	override fun onResume() {
		super.onResume()

		redraw()

		bclStatusListener.subscribe(this)
	}

	override fun onPause() {
		super.onPause()

		bclStatusListener.unsubscribe(this)
	}

	override fun onDestroy() {
		super.onDestroy()
		this.unregisterReceiver(redrawListener)
	}

	fun isConnectedSecurityConnected(): Boolean {
		return securityAccess.isConnected()
	}
	fun isConnectedNewInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			!it.name.contains("Classic")
		}
	}

	fun isBMWConnectedInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("BMW")
		}
	}
	fun isBMWConnectedClassicInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("BMW") &&
			it.name.contains("Classic")
		}
	}
	fun isBMWConnectedNewInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("BMW") &&
			!it.name.contains("Classic")
		}
	}

	fun isMiniConnectedInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("Mini")
		}
	}
	fun isMiniConnectedClassicInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("Mini") &&
			it.name.contains("Classic")
		}
	}
	fun isMiniConnectedNewInstalled(): Boolean {
		return securityAccess.installedSecurityServices.any {
			it.name.startsWith("Mini") &&
			!it.name.contains("Classic")
		}
	}

	fun redraw() {
		paneConnectedBMWNewInstalled.visible = isConnectedNewInstalled() && !isConnectedSecurityConnected() && isBMWConnectedNewInstalled()
		paneConnectedMiniNewInstalled.visible = isConnectedNewInstalled() && !isConnectedSecurityConnected() && !isBMWConnectedNewInstalled() && isMiniConnectedNewInstalled()
		paneBMWMissing.visible = !(isConnectedNewInstalled() && !isConnectedSecurityConnected()) && !isBMWConnectedInstalled()
		paneMiniMissing.visible = !(isConnectedNewInstalled() && !isConnectedSecurityConnected()) && !isMiniConnectedInstalled()
		paneBMWReady.visible = isConnectedSecurityConnected() && isBMWConnectedInstalled()
		paneMiniReady.visible = isConnectedSecurityConnected() && isMiniConnectedInstalled()

		val buildTime = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIME))
		txtBuildInfo.text = getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, buildTime)

		val showAdvancedSettings = AppSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		swAdvancedSettings.isChecked = showAdvancedSettings

		txtBclReport.text = bclStatusListener.toString()
		paneBclReport.visible = showAdvancedSettings && bclStatusListener.state != "UNKNOWN"

		val carCapabilities = synchronized(DebugStatus.carCapabilities) {
			DebugStatus.carCapabilities.map {
				"${it.key}: ${it.value}"
			}.sorted().joinToString("\n")
		}
		txtCarCapabilities.text = carCapabilities
		paneCarCapabilities.visible = showAdvancedSettings && carCapabilities.isNotEmpty()
	}

	fun installBMWClassic() {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.bmwgroup.connected.bmw.usa")
		}
		startActivity(intent)
	}

	fun installMiniClassic() {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.bmwgroup.connected.mini.usa")
		}
		startActivity(intent)
	}
}

var View.visible: Boolean
	get() { return this.visibility == VISIBLE }
	set(value) {this.visibility = if (value) VISIBLE else GONE}