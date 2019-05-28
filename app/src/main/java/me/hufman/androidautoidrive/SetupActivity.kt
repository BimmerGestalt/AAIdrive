package me.hufman.androidautoidrive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.android.synthetic.main.activity_setup.*
import me.hufman.idriveconnectionkit.android.SecurityService

class SetupActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_setup)

		btnInstallBmwClassic.setOnClickListener { installBMWClassic() }
		btnInstallMissingBMWClassic.setOnClickListener { installBMWClassic() }
		btnInstallMiniClassic.setOnClickListener { installMiniClassic() }
		btnInstallMissingMiniClassic.setOnClickListener { installMiniClassic() }
	}

	override fun onResume() {
		super.onResume()

		redraw()
	}

	fun isConnectedSecurityConnected(): Boolean {
		return SecurityService.isConnected()
	}
	fun isConnectedNewInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			!it.contains("Classic")
		}
	}

	fun isBMWConnectedInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("BMW")
		}
	}
	fun isBMWConnectedClassicInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("BMW") &&
			it.contains("Classic")
		}
	}
	fun isBMWConnectedNewInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("BMW") &&
			!it.contains("Classic")
		}
	}

	fun isMiniConnectedInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("Mini")
		}
	}
	fun isMiniConnectedClassicInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("Mini") &&
			it.contains("Classic")
		}
	}
	fun isMiniConnectedNewInstalled(): Boolean {
		return SecurityService.installedSecurityServices.any {
			it.startsWith("Mini") &&
			!it.contains("Classic")
		}
	}

	fun redraw() {
		paneConnectedBMWNewInstalled.visible = isConnectedNewInstalled() && !isConnectedSecurityConnected() && isBMWConnectedNewInstalled()
		paneConnectedMiniNewInstalled.visible = isConnectedNewInstalled() && !isConnectedSecurityConnected() && !isBMWConnectedNewInstalled() && isMiniConnectedNewInstalled()
		paneBMWMissing.visible = !(isConnectedNewInstalled() && !isConnectedSecurityConnected()) && !isBMWConnectedInstalled()
		paneMiniMissing.visible = !(isConnectedNewInstalled() && !isConnectedSecurityConnected()) && !isMiniConnectedInstalled()
		paneBMWReady.visible = isConnectedSecurityConnected() && isBMWConnectedInstalled()
		paneMiniReady.visible = isConnectedSecurityConnected() && isMiniConnectedInstalled()
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