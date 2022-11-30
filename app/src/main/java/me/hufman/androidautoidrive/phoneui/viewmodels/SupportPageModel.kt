package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.R
import java.text.SimpleDateFormat
import java.util.*

class SupportPageModel: ViewModel() {
	private val _buildInfo = MutableLiveData<Context.() -> String> {
		val commitTime = if (BuildConfig.COMMIT_TIME == 0L) {
			""
		} else {
			SimpleDateFormat.getDateTimeInstance().format(Date(BuildConfig.COMMIT_TIME * 1000))
		}
		getString(R.string.txt_build_info, BuildConfig.VERSION_NAME, commitTime)
	}
	val buildInfo: LiveData<Context.() -> String> = _buildInfo
}