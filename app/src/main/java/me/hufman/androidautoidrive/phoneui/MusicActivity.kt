package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_music.*
import kotlinx.android.synthetic.main.activity_music.btnGrantSessions
import kotlinx.android.synthetic.main.activity_music.listMusicApps
import kotlinx.android.synthetic.main.activity_music.paneGrantSessions
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BuildConfig
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class MusicActivity : AppCompatActivity() {

	val handler = Handler()

	val displayedApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread = AppDiscoveryThread(this) { apps ->
		handler.post {
			displayedApps.clear()
			displayedApps.addAll(apps)
			listMusicApps.invalidateViews() // redraw the app list
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_music)

		swAudioContext.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.AUDIO_FORCE_CONTEXT, isChecked.toString())
		}
		btnGrantSessions.setOnClickListener {
			promptNotificationPermission()
		}

		// build list of discovered music apps
		appDiscoveryThread.start()

		listMusicApps.setOnItemClickListener { adapterView, view, i, l ->
			val appInfo = adapterView.adapter.getItem(i) as? MusicAppInfo
			if (appInfo != null) {
				UIState.selectedMusicApp = appInfo
				val intent = Intent(this, MusicPlayerActivity::class.java)
				startActivity(intent)
			}
		}
		listMusicApps.adapter = object: ArrayAdapter<MusicAppInfo>(this, R.layout.musicapp_listitem, displayedApps) {
			val animationLoopCallback = object: Animatable2.AnimationCallback() {
				override fun onAnimationEnd(drawable: Drawable?) {
					handler.post { (drawable as AnimatedVectorDrawable).start() }
				}
			}
			val equalizerStatic = resources.getDrawable(R.drawable.ic_equalizer_black_24dp, null)
			val equalizerAnimated = (resources.getDrawable(R.drawable.ic_dancing_equalizer, null) as AnimatedVectorDrawable).apply {
				this.registerAnimationCallback(animationLoopCallback)
			}

			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView ?: layoutInflater.inflate(R.layout.musicapp_listitem, parent,false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<TextView>(R.id.txtMusicAppName).setText(appInfo.name)

					if (appInfo.packageName == appDiscoveryThread.discovery?.musicSessions?.getPlayingApp()?.packageName) {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerAnimated)
						equalizerAnimated.start()
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.VISIBLE
					} else {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerStatic)
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.GONE
					}
					layout.findViewById<ImageView>(R.id.imgControllable).visibility = if (appInfo.controllable && !appInfo.connectable) View.VISIBLE else View.GONE
					layout.findViewById<ImageView>(R.id.imgConnectable).visibility = if (appInfo.connectable) View.VISIBLE else View.GONE
					layout.findViewById<ImageView>(R.id.imgBrowseable).visibility = if (appInfo.browseable) View.VISIBLE else View.GONE
					layout.findViewById<ImageView>(R.id.imgSearchable).visibility = if (appInfo.searchable || appInfo.playsearchable) View.VISIBLE else View.GONE
					layout.findViewById<ImageView>(R.id.imgBlock).visibility = if (appInfo.controllable || appInfo.connectable) View.GONE else View.VISIBLE
					val features = listOfNotNull(
							if (appInfo.controllable && !appInfo.connectable) getString(R.string.musicAppControllable) else null,
							if (appInfo.connectable) getString(R.string.musicAppConnectable) else null,
							if (appInfo.browseable) getString(R.string.musicAppBrowseable) else null,
							if (appInfo.searchable || appInfo.playsearchable) getString(R.string.musicAppSearchable) else null,
							if (appInfo.controllable || appInfo.connectable) null else getString(R.string.musicAppUnavailable)
					).joinToString(", ")
					layout.findViewById<TextView>(R.id.txtMusicAppFeatures).text = features
					layout.findViewById<LinearLayout>(R.id.paneMusicAppFeatures).setOnClickListener {
						val txtFeatures = layout.findViewById<TextView>(R.id.txtMusicAppFeatures)
						txtFeatures.visibility = if (txtFeatures.visibility == View.VISIBLE) View.GONE else View.VISIBLE
					}

					// show app-specific notes
					val notes = layout.findViewById<TextView>(R.id.txtMusicAppNotes)
					if (appInfo.packageName == "com.spotify.music" && appInfo.probed && !appInfo.connectable) {
						notes.text = if (SpotifyAppController.hasSupport(context)) {
							getString(R.string.musicAppNotes_unauthorizedSpotify)
						} else {
							getString(R.string.musicAppNotes_oldSpotify)
						}
						notes.visibility = View.VISIBLE
						notes.setOnClickListener {
							SpotifyDowngradeDialog().show(supportFragmentManager, "notes")
						}
					} else {
						notes.visibility = View.GONE
						notes.setOnClickListener(null)
					}

					layout
				} else {
					layout.findViewById<TextView>(R.id.txtMusicAppName).setText("Error")
					layout
				}
			}
		}

		listMusicAppsRefresh.setOnRefreshListener {
			appDiscoveryThread.forceDiscovery()
			handler.postDelayed({
				listMusicAppsRefresh.isRefreshing = false
			}, 2000)
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		swAudioContext.visible = BuildConfig.MANUAL_AUDIO_CONTEXT
		swAudioContext.isChecked = AppSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		paneGrantSessions.visibility = if (hasNotificationPermission()) View.GONE else View.VISIBLE
	}

	fun promptNotificationPermission() {
		startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
	}

	fun hasNotificationPermission(): Boolean {
		return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
	}
}