package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_music_applist.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.ListSetting
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread
import me.hufman.androidautoidrive.phoneui.adapters.MusicAppListAdapter

class MusicAppsListFragment: Fragment() {
	val handler = Handler()

	val displayedApps = ArrayList<MusicAppInfo>()
	val appDiscoveryThread by lazy {
		MusicAppDiscoveryThread(requireContext()) { appDiscovery ->
			handler.post {
				displayedApps.clear()
				displayedApps.addAll(appDiscovery.allApps)
				listMusicApps.adapter?.notifyDataSetChanged() // redraw the app list
			}
		}
	}
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }
	val hiddenApps by lazy { ListSetting(appSettings, AppSettings.KEYS.HIDDEN_MUSIC_APPS) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_applist, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		// build list of discovered music apps
		appDiscoveryThread.start()

		listMusicApps.setHasFixedSize(true)
		listMusicApps.layoutManager = LinearLayoutManager(requireActivity())
		listMusicApps.adapter = MusicAppListAdapter(requireActivity(), handler, requireActivity().supportFragmentManager, displayedApps, appDiscoveryThread)

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
		appDiscoveryThread.discovery()
	}

	override fun onPause() {
		super.onPause()
		appDiscoveryThread.stopDiscovery()
	}
}