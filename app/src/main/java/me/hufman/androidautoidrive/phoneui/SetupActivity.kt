package me.hufman.androidautoidrive.phoneui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_setup.*
import me.hufman.androidautoidrive.*
import java.text.SimpleDateFormat
import java.util.*

class SetupActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_setup)

		val buildTime = SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIME))
		txtBuildInfo.text = getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, buildTime)

		val advancedSetting = BooleanLiveSetting(this, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
		advancedSetting.observe(this, androidx.lifecycle.Observer {
			swAdvancedSettings.isChecked = it
			paneAdvancedInfo.visible = it
		})
		swAdvancedSettings.setOnCheckedChangeListener { _, isChecked ->
			advancedSetting.setValue(isChecked)
		}
	}
}