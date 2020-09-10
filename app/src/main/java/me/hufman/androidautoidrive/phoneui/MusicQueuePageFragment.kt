package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.music_queuepage.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

class MusicQueuePageFragment: Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	companion object {
		fun newInstance(): MusicQueuePageFragment {
			val fragment = MusicQueuePageFragment()
			fragment.arguments = Bundle().apply {

			}
			return fragment
		}
	}

	lateinit var musicController: MusicController
	lateinit var placeholderCoverArt: Bitmap

	var loaderJob: Job? = null
	val contents = ArrayList<MusicMetadata>()
	var currentQueueMetadata: QueueMetadata? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_queuepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProviders.of(requireActivity()).get(MusicActivityModel::class.java)
		val musicController = viewModel.musicController ?: return
		this.musicController = musicController
		placeholderCoverArt = viewModel.icons[MusicNowPlayingFragment.PLACEHOLDER_ID]!!

		listQueue.setHasFixedSize(true)
		listQueue.layoutManager = LinearLayoutManager(this.context)
		listQueue.adapter = QueueAdapter(this.context!!, contents) { mediaEntry ->
			if(mediaEntry != null) {
				musicController.playQueue(mediaEntry)
				(activity as MusicPlayerActivity).showNowPlaying()
			}
		}

		listQueueRefresh.setOnRefreshListener {
			redrawQueueUI()
			Handler(this.context?.mainLooper).postDelayed({
				listQueueRefresh.isRefreshing = false
			}, 1000)
		}

		txtQueueEmpty.text = getString(R.string.MUSIC_BROWSE_LOADING)

		musicController.listener = Runnable {
			if(currentQueueMetadata?.title != musicController.getQueue()?.title || currentQueueMetadata?.songs?.size != musicController.getQueue()?.songs?.size) {
				redrawQueueUI()
			}

			(listQueue.adapter as QueueAdapter).notifyDataSetChanged()
		}
	}

	fun redrawQueueUI() {
		if(loaderJob != null) {
			loaderJob?.cancel()
		}

		loaderJob = launch {
			currentQueueMetadata = musicController.getQueue()
			val result = currentQueueMetadata?.songs
			this@MusicQueuePageFragment.contents.clear()
			result?.forEach {
				this@MusicQueuePageFragment.contents.add(it)
			}

			if (isVisible) {
				if (contents.isEmpty()) {
					txtQueueEmpty.text = getString(R.string.MUSIC_BROWSE_EMPTY)
				} else {
					txtQueueEmpty.text = ""
				}

				listQueue.removeAllViews()
				listQueue.adapter?.notifyDataSetChanged()
			}

			val coverArtImage = currentQueueMetadata?.coverArt
			if(coverArtImage != null) {
				playlistCoverArt.setImageBitmap(coverArtImage)
			}
			else {
				playlistCoverArt.setImageBitmap(placeholderCoverArt)
			}

			playlistName.setText(currentQueueMetadata?.title)
		}
	}

	inner class QueueAdapter(val context: Context, val contents: ArrayList<MusicMetadata>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<QueueAdapter.ViewHolder>() {
		inner class ViewHolder(val view: View, val checkmarkImageView: ImageView, val coverArtImageView: ImageView, val songTextView: TextView, val artistTextView: TextView): RecyclerView.ViewHolder(view), View.OnClickListener {
			init {
				view.setOnClickListener(this)
			}

			override fun onClick(v: View?) {
				val mediaEntry = contents.getOrNull(adapterPosition)
				clickListener(mediaEntry)
			}
		}

		override fun getItemCount(): Int {
			return contents.size
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val layout = LayoutInflater.from(context).inflate(R.layout.music_queue_listitem, parent, false)
			return ViewHolder(layout, layout.findViewById(R.id.checkmarkImage), layout.findViewById(R.id.coverArtImage), layout.findViewById(R.id.songText), layout.findViewById(R.id.artistText))
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = contents.getOrNull(position) ?: return

			if(item.queueId == musicController.getMetadata()?.queueId) {
				holder.checkmarkImageView.setImageDrawable(resources.getDrawable(R.drawable.ic_equalizer_black_24dp, null))
			} else {
				holder.checkmarkImageView.setImageDrawable(null)
			}
			holder.coverArtImageView.setImageBitmap(item.coverArt)
			holder.songTextView.setText(item.title)
			holder.artistTextView.setText(item.artist)
		}
	}
}