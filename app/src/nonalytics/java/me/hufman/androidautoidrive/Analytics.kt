package me.hufman.androidautoidrive

import android.content.Context
import me.hufman.androidautoidrive.music.MusicAppInfo

object Analytics : AnalyticsProvider {
    override fun init(context: Context) {
    }

    override fun reportMusicAppProbe(appInfo: MusicAppInfo) {
    }

    override fun reportCarProbeFailure(port: Int, message: String?, throwable: Throwable?) {
    }

    override fun reportCarProbeDiscovered(port: Int?, vehicleType: String?, hmiType: String?) {
    }

    override fun reportCarCapabilities(capabilities: Map<String, String?>) {
    }
}
