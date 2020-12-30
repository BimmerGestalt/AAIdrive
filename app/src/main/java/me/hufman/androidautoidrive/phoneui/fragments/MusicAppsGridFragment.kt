package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_music_appgrid.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.NestedGridView
import me.hufman.androidautoidrive.phoneui.UIState

class MusicAppsGridFragment: Fragment() {
	val handler = Handler()
	val displayedMusicApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread by lazy {
		MusicAppDiscoveryThread(requireActivity().applicationContext) { appDiscovery ->
			handler.post {
				displayedMusicApps.clear()
				displayedMusicApps.addAll(appDiscovery.validApps)
				view?.findViewById<NestedGridView>(R.id.listMusicApps)?.invalidateViews() // redraw the app list
			}
		}.apply { start() }
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_appgrid, container, false)
	}
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		// build list of discovered music apps
		appDiscoveryThread.discovery()

		listMusicApps.setOnItemClickListener { _, _, _, _ ->
			findNavController().navigate(R.id.nav_music)
		}

		listMusicApps.adapter = object : ArrayAdapter<MusicAppInfo>(requireContext(), R.layout.musicapp_listitem, displayedMusicApps) {
			val animationLoopCallback = object : Animatable2.AnimationCallback() {
				override fun onAnimationEnd(drawable: Drawable?) {
					handler.post { (drawable as? AnimatedVectorDrawable)?.start() }
				}
			}
			val equalizerStatic = ResourcesCompat.getDrawable(resources, R.drawable.ic_equalizer_black_24dp, null)
			val equalizerAnimated = (ResourcesCompat.getDrawable(resources, R.drawable.ic_dancing_equalizer, null) as AnimatedVectorDrawable).apply {
				this.registerAnimationCallback(animationLoopCallback)
			}

			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val appInfo = getItem(position)
				val layout = convertView
						?: layoutInflater.inflate(R.layout.musicapp_griditem, parent, false)
				return if (appInfo != null) {
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).setImageDrawable(appInfo.icon)
					layout.findViewById<ImageView>(R.id.imgMusicAppIcon).contentDescription = appInfo.name

					if (appInfo.packageName == appDiscoveryThread.discovery?.musicSessions?.getPlayingApp()?.packageName) {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerAnimated)
						equalizerAnimated.start()
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.VISIBLE
					} else {
						layout.findViewById<ImageView>(R.id.imgNowPlaying).setImageDrawable(equalizerStatic)
						layout.findViewById<ImageView>(R.id.imgNowPlaying).visibility = View.GONE
					}
					layout
				} else {
					layout.findViewById<TextView>(R.id.txtMusicAppName).text = getText(R.string.lbl_error)
					layout
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		// update the music apps list, including any music sessions
		appDiscoveryThread.discovery()
	}

	override fun onDestroy() {
		super.onDestroy()
		appDiscoveryThread.stopDiscovery()
	}
}