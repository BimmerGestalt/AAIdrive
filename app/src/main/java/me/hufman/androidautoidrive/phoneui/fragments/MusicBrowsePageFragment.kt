package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.*
import kotlin.coroutines.CoroutineContext

class MusicBrowsePageFragment: Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	companion object {
		const val ARG_MEDIA_ID = "me.hufman.androidautoidrive.BROWSE_MEDIA_ID"

		fun newInstance(mediaEntry: MusicMetadata?): MusicBrowsePageFragment {
			val fragment = MusicBrowsePageFragment()
			fragment.arguments = Bundle().apply {
				putString(ARG_MEDIA_ID, mediaEntry?.mediaId)
			}
			return fragment
		}
	}

	var loaderJob: Job? = null

	val viewModel by activityViewModels<MusicActivityModel> { MusicActivityModel.Factory(requireContext().applicationContext, UIState.selectedMusicApp) }
	val iconsModel by activityViewModels<MusicActivityIconsModel> { MusicActivityIconsModel.Factory(requireActivity()) }
	private lateinit var musicController: MusicController
	private lateinit var _iconsModel: MusicActivityIconsModel

	val contents = ArrayList<MusicPlayerItem>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_browsepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		musicController = viewModel.musicController
		_iconsModel = iconsModel

		val listBrowse = view.findViewById<RecyclerView>(R.id.listBrowse)
		val listBrowseRefresh = view.findViewById<SwipeRefreshLayout>(R.id.listBrowseRefresh)

		// redraw to catch any updated coverart
		viewModel.redrawListener.observe(viewLifecycleOwner, {
			listBrowse.adapter?.notifyDataSetChanged()
		})

		listBrowse.setHasFixedSize(true)
		listBrowse.layoutManager = LinearLayoutManager(this.context)
		listBrowse.adapter = DataBoundListAdapter(contents, R.layout.music_browse_listitem, (activity as MusicPlayerActivity).musicPlayerController)

		val mediaId = arguments?.getString(ARG_MEDIA_ID)
		listBrowseRefresh.setOnRefreshListener {
			browseDirectory(mediaId)
			Handler(this.requireContext().mainLooper).postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listBrowseRefresh)?.isRefreshing = false
			}, 1000)
		}
		browseDirectory(mediaId)
	}

	private fun browseDirectory(mediaId: String?) {
		view?.findViewById<TextView>(R.id.txtEmpty)?.text = getString(R.string.MUSIC_BROWSE_LOADING)

		if (loaderJob != null) {
			loaderJob?.cancel()
		}

		loaderJob = launch {
			val result = musicController.browseAsync(MusicMetadata(mediaId = mediaId))
			val contents = result.await()
			this@MusicBrowsePageFragment.contents.clear()
			this@MusicBrowsePageFragment.contents.addAll(contents.map {
				MusicPlayerBrowseItem(iconsModel, it)
			})
			redraw()
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		if (isResumed) {
			val txtEmpty = view?.findViewById<TextView>(R.id.txtEmpty)
			txtEmpty?.text = if (contents.isEmpty()) {
				getString(R.string.MUSIC_BROWSE_EMPTY)
			} else {
				""
			}

			val listBrowse = view?.findViewById<RecyclerView>(R.id.listBrowse)
			listBrowse?.removeAllViews()
			listBrowse?.adapter?.notifyDataSetChanged()
		}
	}
}