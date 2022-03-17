package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.databinding.MusicQueuePageBinding
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicPlayerQueueItem
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class MusicQueueFragment: Fragment() {
	lateinit var musicController: MusicController

	private val contents = ArrayList<MusicPlayerQueueItem>()
	private var currentQueueMetadata: QueueMetadata? = null

	val viewModel by activityViewModels<MusicActivityModel> { MusicActivityModel.Factory(requireContext().applicationContext, UIState.selectedMusicApp) }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val binding = MusicQueuePageBinding.inflate(layoutInflater)
		binding.lifecycleOwner = this
		binding.viewModel = viewModel
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		this.musicController = viewModel.musicController

		val listQueue = view.findViewById<RecyclerView>(R.id.listQueue)
		val listQueueRefresh = view.findViewById<SwipeRefreshLayout>(R.id.listQueueRefresh)

		// redraw to catch any updated coverart
		viewModel.redrawListener.observe(viewLifecycleOwner, Observer {
			listQueue.adapter?.notifyDataSetChanged()
		})

		viewModel.queueMetadata.observe(viewLifecycleOwner, Observer { metadata ->
			redraw(metadata)
		})

		listQueue.setHasFixedSize(true)
		listQueue.layoutManager = LinearLayoutManager(this.context)
		listQueue.adapter = DataBoundListAdapter(contents, R.layout.music_queue_listitem, (activity as MusicPlayerActivity).musicPlayerController)

		listQueueRefresh.setOnRefreshListener {
			Handler(this.requireContext().mainLooper).postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listQueueRefresh)?.isRefreshing = false
			}, 1000)
		}
	}

	fun redraw(metadata: QueueMetadata?) {
		// The MusicMetadata objects may have their coverart filled in later
		// so we don't need to clear the list and readd them just to get new coverart
		// so we can avoid work by not doing this cover if the list is the same
		if (currentQueueMetadata?.title != metadata?.title || currentQueueMetadata?.songs?.size != metadata?.songs?.size) {
			contents.clear()
			contents.addAll(metadata?.songs?.map {
				MusicPlayerQueueItem(viewModel, it)
			} ?: emptyList())

			val listQueue = view?.findViewById<RecyclerView>(R.id.listQueue)
			listQueue?.adapter?.notifyDataSetChanged()
			currentQueueMetadata = metadata
		}
	}
}