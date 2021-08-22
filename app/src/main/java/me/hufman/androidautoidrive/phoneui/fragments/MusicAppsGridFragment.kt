package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.NestedGridView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
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
		val listMusicApps = view.findViewById<NestedGridView>(R.id.listMusicApps)
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

		listMusicApps.adapter = DataBoundArrayAdapter(requireContext(), R.layout.musicapp_griditem, appsViewModel.validApps, null)
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