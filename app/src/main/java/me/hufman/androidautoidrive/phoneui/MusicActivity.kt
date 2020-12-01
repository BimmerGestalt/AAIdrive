package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.android.synthetic.main.activity_music.*
import kotlinx.android.synthetic.main.activity_music.btnGrantSessions
import kotlinx.android.synthetic.main.activity_music.listMusicApps
import kotlinx.android.synthetic.main.activity_music.paneGrantSessions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.adapters.MusicAppListAdapter

class MusicActivity : AppCompatActivity() {

	val handler = Handler()

	val displayedApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread = MusicAppDiscoveryThread(this) { appDiscovery ->
		handler.post {
			displayedApps.clear()
			displayedApps.addAll(appDiscovery.allApps)
			listMusicApps.adapter?.notifyDataSetChanged() // redraw the app list
		}
	}
	val appSettings by lazy { MutableAppSettingsReceiver(this) }
	val hiddenApps by lazy { ListSetting(appSettings, AppSettings.KEYS.HIDDEN_MUSIC_APPS) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_music)

		swAudioContext.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT] = isChecked.toString()
		}
		swSpotifyLayout.setOnCheckedChangeListener { button, isChecked ->
			appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT] = isChecked.toString()
		}
		btnGrantSessions.setOnClickListener {
			promptNotificationPermission()
		}

		// build list of discovered music apps
		appDiscoveryThread.start()

		listMusicApps.setHasFixedSize(true)
		listMusicApps.layoutManager = LinearLayoutManager(this)
		listMusicApps.adapter = MusicAppListAdapter(this, handler, supportFragmentManager, displayedApps, appDiscoveryThread)

		listMusicAppsRefresh.setOnRefreshListener {
			appDiscoveryThread.forceDiscovery()
			handler.postDelayed({
				listMusicAppsRefresh.isRefreshing = false
			}, 2000)
		}

		val swipeCallback = object: ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
			override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder): Boolean {
				return false
			}

			override fun onSwiped(view: RecyclerView.ViewHolder, direction: Int) {
				val musicAppInfo = (view as? MusicAppListAdapter.ViewHolder)?.appInfo
				if (musicAppInfo != null) {
					val previous = hiddenApps.contains(musicAppInfo.packageName)
					if (previous) {
						hiddenApps.remove(musicAppInfo.packageName)
					} else {
						hiddenApps.add(musicAppInfo.packageName)
					}
				}
			}
		}
		ItemTouchHelper(swipeCallback).attachToRecyclerView(listMusicApps)
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	override fun onDestroy() {
		super.onDestroy()
		appDiscoveryThread.stopDiscovery()
	}

	fun redraw() {
		val showAdvancedSettings = appSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		swAudioContext.visible = showAdvancedSettings || BuildConfig.MANUAL_AUDIO_CONTEXT
		swAudioContext.isChecked = appSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		swSpotifyLayout.visible = showAdvancedSettings
		swSpotifyLayout.isChecked = appSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT].toBoolean()
		paneGrantSessions.visibility = if (hasNotificationPermission()) View.GONE else View.VISIBLE
		appDiscoveryThread.discovery()
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasNotificationPermission(): Boolean {
		return UIState.notificationListenerConnected && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
	}
}