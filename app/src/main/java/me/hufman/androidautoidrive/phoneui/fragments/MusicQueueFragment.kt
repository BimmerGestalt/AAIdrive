package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.collections.ArrayList
import kotlinx.android.synthetic.main.music_queuepage.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.phoneui.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity

class MusicQueueFragment: Fragment() {
	lateinit var musicController: MusicController
	lateinit var placeholderCoverArt: Bitmap

	private val contents = ArrayList<MusicMetadata>()
	private var currentQueueMetadata: QueueMetadata? = null

	val handler = Handler()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_queuepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		val musicController = viewModel.musicController ?: return
		this.musicController = musicController
		placeholderCoverArt = viewModel.icons[MusicNowPlayingFragment.PLACEHOLDER_ID]!!

		listQueue.setHasFixedSize(true)
		listQueue.layoutManager = LinearLayoutManager(this.context)
		listQueue.adapter = QueueAdapter(this.context!!, contents) { mediaEntry ->
			if (mediaEntry != null) {
				musicController.playQueue(mediaEntry)
				(activity as MusicPlayerActivity).showNowPlaying()
			}
		}
		redrawQueueUI()
		listQueueRefresh.setOnRefreshListener {
			redrawQueueUI()
			Handler(this.context?.mainLooper).postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listQueueRefresh)?.isRefreshing = false
			}, 1000)
		}

		txtQueueEmpty.text = getString(R.string.MUSIC_QUEUE_EMPTY)
	}

	override fun onResume() {
		super.onResume()

		musicController.listener = Runnable {
			if(currentQueueMetadata?.title != musicController.getQueue()?.title || currentQueueMetadata?.songs?.size != musicController.getQueue()?.songs?.size) {
				redrawQueueUI()
			}

			activity?.findViewById<RecyclerView>(R.id.listQueue)?.adapter?.notifyDataSetChanged()
		}
		redrawQueueUI()
	}

	private fun redrawQueueUI() {
		currentQueueMetadata = musicController.getQueue()
		val songs = currentQueueMetadata?.songs
		contents.clear()
		songs?.forEach {
			contents.add(it)
		}

		if (isResumed) {
			if (contents.isEmpty()) {
				txtQueueEmpty.text = getString(R.string.MUSIC_BROWSE_EMPTY)
			} else {
				txtQueueEmpty.text = ""
			}

			listQueue.removeAllViews()
			listQueue.adapter?.notifyDataSetChanged()

			val coverArtImage = currentQueueMetadata?.coverArt
			if (coverArtImage != null) {
				queueCoverArt.setImageBitmap(coverArtImage)
			}
			else {
				queueCoverArt.setImageBitmap(placeholderCoverArt)
			}

			queueTitle.text = currentQueueMetadata?.title
			queueSubtitle.text = currentQueueMetadata?.subtitle
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

		private val animationLoopCallback = object: Animatable2.AnimationCallback() {
			override fun onAnimationEnd(drawable: Drawable?) {
				handler.post { (drawable as? AnimatedVectorDrawable)?.start() }
			}
		}
		private val equalizerAnimated = (resources.getDrawable(R.drawable.ic_dancing_equalizer, null) as AnimatedVectorDrawable).apply {
			this.registerAnimationCallback(animationLoopCallback)
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

			if (item.queueId == musicController.getMetadata()?.queueId && !musicController.getPlaybackPosition().playbackPaused) {
				holder.checkmarkImageView.setImageDrawable(equalizerAnimated)
				equalizerAnimated.start()
			} else {
				holder.checkmarkImageView.setImageDrawable(null)
			}
			holder.coverArtImageView.setImageBitmap(item.coverArt)
			holder.songTextView.text = item.title
			holder.artistTextView.text = item.artist
		}
	}
}