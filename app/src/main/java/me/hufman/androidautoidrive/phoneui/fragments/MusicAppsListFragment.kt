package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.fragment_music_applist.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.StoredSet
import me.hufman.androidautoidrive.MutableAppSettingsReceiver
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.phoneui.MusicAppDiscoveryThread
import me.hufman.androidautoidrive.phoneui.adapters.MusicAppListAdapter
import me.hufman.androidautoidrive.phoneui.adapters.ObservableListCallback
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicAppsViewModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels
import me.hufman.androidautoidrive.phoneui.ViewHelpers.findParent
import me.hufman.androidautoidrive.phoneui.ViewHelpers.scrollTop
import kotlin.math.max

class MusicAppsListFragment: Fragment() {
	val handler = Handler()

	val appsViewModel by activityViewModels<MusicAppsViewModel> { MusicAppsViewModel.Factory(requireActivity().applicationContext) }
	private val appsChangedCallback = ObservableListCallback<MusicAppInfo> {
		val listView = view?.findViewById<RecyclerView>(R.id.listMusicApps)
		if (listView != null && listView.adapter == null) {
			listView.adapter = MusicAppListAdapter(requireActivity(), handler, requireActivity().supportFragmentManager, appsViewModel.allApps, appsViewModel.musicAppDiscoveryThread.discovery!!.musicSessions)
		}
		listView?.adapter?.notifyDataSetChanged() // redraw the app list
	}
	val appSettings by lazy { MutableAppSettingsReceiver(requireContext()) }
	val hiddenApps by lazy { StoredSet(appSettings, AppSettings.KEYS.HIDDEN_MUSIC_APPS) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_music_applist, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		listMusicApps.setHasFixedSize(true)
		listMusicApps.layoutManager = LinearLayoutManager(requireActivity())
		view.post {
			appsChangedCallback.onChanged(null)
		}

		appsViewModel.validApps.addOnListChangedCallback(appsChangedCallback)

		listMusicAppsRefresh.setOnRefreshListener {
			appsViewModel.musicAppDiscoveryThread.forceDiscovery()
			handler.postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listMusicAppsRefresh)?.isRefreshing = false
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

		// set the actual scrollview size
		view.post {
			setHeightInScrollview()
		}
	}

	override fun onResume() {
		super.onResume()

		// build list of discovered music apps
		appsViewModel.musicAppDiscoveryThread.discovery()
	}

	private fun setHeightInScrollview() {
		// set height based on parent scrollview size
		val view = this.view ?: return
		val scrollView = view.findParent { it is ScrollView } as? ScrollView ?: return
		val scrollHeight = scrollView.height
		val height = max(400, scrollHeight - view.scrollTop)

		val layoutParams = view.layoutParams
		layoutParams.height = height
		view.layoutParams = layoutParams
		view.requestLayout()
	}

	override fun onDestroy() {
		super.onDestroy()
		appsViewModel.validApps.removeOnListChangedCallback(appsChangedCallback)
	}
}