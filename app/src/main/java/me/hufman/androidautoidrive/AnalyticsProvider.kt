package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.music.MusicAppInfo

interface AnalyticsProvider {
    fun init(context: Context)
    fun reportMusicAppProbe(appInfo: MusicAppInfo)
    fun reportCarProbeFailure(port: Int, message: String?, throwable: Throwable?)
    fun reportCarProbeDiscovered(port: Int?, vehicleType: String?, hmiType: String?)
    fun reportCarCapabilities(capabilities: Map<String, String?>)
}
