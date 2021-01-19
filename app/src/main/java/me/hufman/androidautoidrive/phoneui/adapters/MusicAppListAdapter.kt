package me.hufman.androidautoidrive.phoneui.adapters

import android.app.Activity
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicSessions
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.*
import me.hufman.androidautoidrive.phoneui.controllers.PermissionsController
import me.hufman.androidautoidrive.phoneui.fragments.SpotifyDowngradeDialog
import me.hufman.androidautoidrive.phoneui.visible

class MusicAppListAdapter(val activity: Activity, val handler: Handler, val supportFragmentManager: FragmentManager, val contents: ArrayList<MusicAppInfo>, val musicSessions: MusicSessions): RecyclerView.Adapter<MusicAppListAdapter.ViewHolder>() {

	// permissions controller for opening the app permissions screen
	val permissionsController = PermissionsController(activity)

	// animations for the music session
	val animationLoopCallback = object: Animatable2.AnimationCallback() {
		override fun onAnimationEnd(drawable: Drawable?) {
			handler.post { (drawable as? AnimatedVectorDrawable)?.start() }
		}
	}
	val equalizerStatic = ContextCompat.getDrawable(activity, R.drawable.ic_equalizer_black_24dp)
	val equalizerAnimated = (ContextCompat.getDrawable(activity, R.drawable.ic_dancing_equalizer) as AnimatedVectorDrawable).apply {
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
		val imgSettings = view.findViewById<ImageView>(R.id.imgSettings)
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
				txtMusicAppName.text = view.context.getText(R.string.lbl_error)
			} else {
				val icon = appInfo.icon
				icon.mutate()

				if (appInfo.hidden) {
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

				if (appInfo.packageName == musicSessions.getPlayingApp()?.packageName) {
					imgNowPlaying.setImageDrawable(equalizerAnimated)
					equalizerAnimated.start()
					imgNowPlaying.visibility = View.VISIBLE
				} else {
					imgNowPlaying.setImageDrawable(equalizerStatic)
					imgNowPlaying.visibility = View.GONE
				}

				imgSettings.setOnClickListener {
					permissionsController.openApplicationPermissions(appInfo.packageName)
				}

				imgControllable.visible = appInfo.controllable && !appInfo.connectable
				imgConnectable.visible = appInfo.connectable
				imgBrowseable.visible = appInfo.browseable
				imgSearchable.visible = appInfo.searchable || appInfo.playsearchable
				imgBlock.visible = !(appInfo.controllable || appInfo.connectable)
				val features = listOfNotNull(
						if (appInfo.controllable && !appInfo.connectable) activity.getString(R.string.musicAppControllable) else null,
						if (appInfo.connectable) activity.getString(R.string.musicAppConnectable) else null,
						if (appInfo.browseable) activity.getString(R.string.musicAppBrowseable) else null,
						if (appInfo.searchable || appInfo.playsearchable) activity.getString(R.string.musicAppSearchable) else null,
						if (appInfo.controllable || appInfo.connectable) null else activity.getString(R.string.musicAppUnavailable),
						if (appInfo.hidden) activity.getString(R.string.musicAppHidden) else null
				).joinToString(", ")
				txtMusicAppFeatures.text = features
				paneMusicAppFeatures.setOnClickListener {
					val txtFeatures = txtMusicAppFeatures
					txtFeatures.visible = !txtFeatures.visible
				}

				// show app-specific notes
				if (appInfo.packageName == "com.spotify.music" && appInfo.probed && !appInfo.connectable) {
					if (!SpotifyAppController.hasSupport(activity)) {
						// show a note to downgrade Spotify, since API support isn't compiled
						txtMusicAppNotes.text = activity.getString(R.string.musicAppNotes_oldSpotify)
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
				val intent = Intent(activity, MusicPlayerActivity::class.java)
				activity.startActivity(intent)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val layout = LayoutInflater.from(activity).inflate(R.layout.musicapp_listitem, parent,false)
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