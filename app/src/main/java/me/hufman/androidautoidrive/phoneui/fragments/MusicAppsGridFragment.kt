package me.hufman.androidautoidrive.phoneui.fragments

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
import me.hufman.androidautoidrive.phoneui.NestedGridView
import me.hufman.androidautoidrive.phoneui.adapters.ObservableListCallback
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicAppsViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels
import java.lang.IllegalStateException

class MusicAppsGridFragment: Fragment() {
	val handler = Handler()
	val appsViewModel by activityViewModels<MusicAppsViewModel> { MusicAppsViewModel.Factory(requireActivity().applicationContext) }
	private val appsChangedCallback = ObservableListCallback<MusicAppInfo> {
		view?.findViewById<NestedGridView>(R.id.listMusicApps)?.invalidateViews() // redraw the app list
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_appgrid, container, false)
	}
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		listMusicApps.setOnItemClickListener { _, _, _, _ ->
			try {
				findNavController().navigate(R.id.nav_music)
			} catch (e: IllegalStateException) {
				// this is rare and unusual, but swallow the exception to not crash the app
			}
		}

		view.post {
			appsChangedCallback.onChanged(null)
		}
		appsViewModel.validApps.addOnListChangedCallback(appsChangedCallback)

		listMusicApps.adapter = object : ArrayAdapter<MusicAppInfo>(requireContext(), R.layout.musicapp_listitem, appsViewModel.validApps) {
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

					if (appInfo.packageName == appsViewModel.musicAppDiscoveryThread.discovery?.musicSessions?.getPlayingApp()?.packageName) {
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
		appsViewModel.musicAppDiscoveryThread.discovery()
	}

	override fun onDestroy() {
		super.onDestroy()
		appsViewModel.validApps.removeOnListChangedCallback(appsChangedCallback)
	}
}