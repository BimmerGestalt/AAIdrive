package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.FragmentManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_music.*
import kotlinx.android.synthetic.main.activity_music.btnGrantSessions
import kotlinx.android.synthetic.main.activity_music.listMusicApps
import kotlinx.android.synthetic.main.activity_music.paneGrantSessions
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController

class MusicActivity : AppCompatActivity() {

	val handler = Handler()

	val displayedApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread = AppDiscoveryThread(this) { appDiscovery ->
		handler.post {
			displayedApps.clear()
			displayedApps.addAll(appDiscovery.combinedApps)
			listMusicApps.adapter?.notifyDataSetChanged() // redraw the app list
		}
	}
	val appSettings by lazy { MutableAppSettings(this, handler) }
	val hiddenApps by lazy { ListSetting(appSettings, AppSettings.KEYS.HIDDEN_MUSIC_APPS) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_music)

		swAudioContext.setOnCheckedChangeListener { buttonView, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.AUDIO_FORCE_CONTEXT, isChecked.toString())
		}
		swSpotifyLayout.setOnCheckedChangeListener { button, isChecked ->
			AppSettings.saveSetting(this, AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT, isChecked.toString())
		}
		btnGrantSessions.setOnClickListener {
			promptNotificationPermission()
		}

		// build list of discovered music apps
		appDiscoveryThread.start()

		listMusicApps.setHasFixedSize(true)
		listMusicApps.layoutManager = LinearLayoutManager(this)
		listMusicApps.adapter = MusicAppListAdapter(this, handler, supportFragmentManager, displayedApps, appDiscoveryThread, hiddenApps)

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
					listMusicApps.adapter?.notifyItemChanged(view.adapterPosition)
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
		val showAdvancedSettings = AppSettings[AppSettings.KEYS.SHOW_ADVANCED_SETTINGS].toBoolean()
		swAudioContext.visible = showAdvancedSettings || BuildConfig.MANUAL_AUDIO_CONTEXT
		swAudioContext.isChecked = AppSettings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT].toBoolean()
		swSpotifyLayout.visible = showAdvancedSettings
		swSpotifyLayout.isChecked = AppSettings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT].toBoolean()
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

class MusicAppListAdapter(val context: Context, val handler: Handler, val supportFragmentManager: FragmentManager, val contents: ArrayList<MusicAppInfo>, val appDiscoveryThread: AppDiscoveryThread, val hiddenApps: Set<String>): RecyclerView.Adapter<MusicAppListAdapter.ViewHolder>() {

	// animations for the music session
	val animationLoopCallback = object: Animatable2.AnimationCallback() {
		override fun onAnimationEnd(drawable: Drawable?) {
			handler.post { (drawable as AnimatedVectorDrawable).start() }
		}
	}
	val equalizerStatic = context.getDrawable(R.drawable.ic_equalizer_black_24dp)
	val equalizerAnimated = (context.getDrawable(R.drawable.ic_dancing_equalizer) as AnimatedVectorDrawable).apply {
		this.registerAnimationCallback(animationLoopCallback)
	}
	val grayscaleColorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply { setSaturation(0.0f) }
	)

	// high level handling to link a row View to a specific MusicAppInfo
	inner class ViewHolder(val view: View): RecyclerView.ViewHolder(view), View.OnClickListener {
		var appInfo: MusicAppInfo? = null

		val imgMusicAppIcon = view.findViewById<ImageView>(R.id.imgMusicAppIcon)
		val txtMusicAppName = view.findViewById<TextView>(R.id.txtMusicAppName)
		val imgNowPlaying = view.findViewById<ImageView>(R.id.imgNowPlaying)
		val imgControllable = view.findViewById<ImageView>(R.id.imgControllable)
		val imgConnectable = view.findViewById<ImageView>(R.id.imgConnectable)
		val imgBrowseable = view.findViewById<ImageView>(R.id.imgBrowseable)
		val imgSearchable = view.findViewById<ImageView>(R.id.imgSearchable)
		val imgBlock = view.findViewById<ImageView>(R.id.imgBlock)
		val txtMusicAppFeatures = view.findViewById<TextView>(R.id.txtMusicAppFeatures)
		val paneMusicAppFeatures = view.findViewById<LinearLayout>(R.id.paneMusicAppFeatures)
		val txtMusicAppNotes = view.findViewById<TextView>(R.id.txtMusicAppNotes)

		init {
			view.setOnClickListener(this)
		}

		fun bind(appInfo: MusicAppInfo?) {
			this.appInfo = appInfo
			if (appInfo == null) {
				txtMusicAppName.text = "Error"
			} else {
				val icon = appInfo.icon
				icon.mutate()

				if (hiddenApps.contains(appInfo.packageName)) {
					imgMusicAppIcon.alpha = 0.4f
					icon.colorFilter = grayscaleColorFilter
					txtMusicAppName.setTextColor(txtMusicAppName.textColors.withAlpha(128))
					txtMusicAppName.setTypeface(null, Typeface.ITALIC)
				} else {
					imgMusicAppIcon.alpha = 1.0f
					icon.colorFilter = null
					txtMusicAppName.setTextColor(txtMusicAppName.textColors.withAlpha(255))
					txtMusicAppName.setTypeface(null, Typeface.NORMAL)
				}
				imgMusicAppIcon.setImageDrawable(icon)
				txtMusicAppName.text = appInfo.name

				if (appInfo.packageName == appDiscoveryThread.discovery?.musicSessions?.getPlayingApp()?.packageName) {
					imgNowPlaying.setImageDrawable(equalizerAnimated)
					equalizerAnimated.start()
					imgNowPlaying.visibility = View.VISIBLE
				} else {
					imgNowPlaying.setImageDrawable(equalizerStatic)
					imgNowPlaying.visibility = View.GONE
				}
				imgControllable.visible = appInfo.controllable && !appInfo.connectable
				imgConnectable.visible = appInfo.connectable
				imgBrowseable.visible = appInfo.browseable
				imgSearchable.visible = appInfo.searchable || appInfo.playsearchable
				imgBlock.visible = !(appInfo.controllable || appInfo.connectable)
				val features = listOfNotNull(
						if (appInfo.controllable && !appInfo.connectable) context.getString(R.string.musicAppControllable) else null,
						if (appInfo.connectable) context.getString(R.string.musicAppConnectable) else null,
						if (appInfo.browseable) context.getString(R.string.musicAppBrowseable) else null,
						if (appInfo.searchable || appInfo.playsearchable) context.getString(R.string.musicAppSearchable) else null,
						if (appInfo.controllable || appInfo.connectable) null else context.getString(R.string.musicAppUnavailable),
						if (hiddenApps.contains(appInfo.packageName)) context.getString(R.string.musicAppHidden) else null
				).joinToString(", ")
				txtMusicAppFeatures.text = features
				paneMusicAppFeatures.setOnClickListener {
					val txtFeatures = txtMusicAppFeatures
					txtFeatures.visible = !txtFeatures.visible
				}

				// show app-specific notes
				if (appInfo.packageName == "com.spotify.music" && appInfo.probed && !appInfo.connectable) {
					if (SpotifyAppController.hasSupport(context)) {
						// prompt the user to click again to show the Spotify auth dialog
						txtMusicAppNotes.text = context.getString(R.string.musicAppNotes_unauthorizedSpotify)
						txtMusicAppNotes.visible = false
						txtMusicAppNotes.setOnClickListener(null)
					} else {
						// show a note to downgrade Spotify
						txtMusicAppNotes.text = context.getString(R.string.musicAppNotes_oldSpotify)
						txtMusicAppNotes.visible = true
						txtMusicAppNotes.setOnClickListener {
							SpotifyDowngradeDialog().show(supportFragmentManager, "notes")
						}
					}
					txtMusicAppNotes.visible = true
				} else {
					txtMusicAppNotes.visible = false
					txtMusicAppNotes.setOnClickListener(null)
				}
			}
		}

		override fun onClick(item: View?) {
			if (this.appInfo != null) {
				UIState.selectedMusicApp = appInfo
				val intent = Intent(context, MusicPlayerActivity::class.java)
				context.startActivity(intent)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val layout = LayoutInflater.from(context).inflate(R.layout.musicapp_listitem, parent,false)
		return ViewHolder(layout)
	}

	override fun getItemCount(): Int {
		return contents.size
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val appInfo = contents.getOrNull(position)
		holder.bind(appInfo)
	}
}