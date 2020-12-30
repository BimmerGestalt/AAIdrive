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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.collections.ArrayList
import kotlinx.android.synthetic.main.music_queuepage.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
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
		this.musicController = viewModel.musicController

		val iconsModel = ViewModelProvider(requireActivity()).get(MusicActivityIconsModel::class.java)
		placeholderCoverArt = iconsModel.placeholderCoverArt

		// redraw to catch any updated coverart
		viewModel.redrawListener.observe(viewLifecycleOwner, Observer {
			listQueue.adapter?.notifyDataSetChanged()
		})

		viewModel.queueMetadata.observe(viewLifecycleOwner, Observer { metadata ->
			redraw(metadata)
		})

		listQueue.setHasFixedSize(true)
		listQueue.layoutManager = LinearLayoutManager(this.context)
		listQueue.adapter = QueueAdapter(this.context!!, contents) { mediaEntry ->
			if (mediaEntry != null) {
				musicController.playQueue(mediaEntry)
				(activity as MusicPlayerActivity).showNowPlaying()
			}
		}

		listQueueRefresh.setOnRefreshListener {
			Handler(this.context?.mainLooper).postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listQueueRefresh)?.isRefreshing = false
			}, 1000)
		}

		txtQueueEmpty.text = getString(R.string.MUSIC_QUEUE_EMPTY)
	}

	fun redraw(metadata: QueueMetadata?) {
		queueTitle.text = metadata?.title
		queueSubtitle.text = metadata?.subtitle
		queueCoverArt.setImageBitmap(metadata?.coverArt ?: placeholderCoverArt)

		// The MusicMetadata objects may have their coverart filled in later
		// so we don't need to clear the list and readd them just to get new coverart
		// so we can avoid work by not doing this cover if the list is the same
		if (currentQueueMetadata?.title != metadata?.title || currentQueueMetadata?.songs?.size != metadata?.songs?.size) {
			contents.clear()
			contents.addAll(metadata?.songs ?: emptyList())

			listQueue.adapter?.notifyDataSetChanged()
			currentQueueMetadata = metadata
		}

		if (contents.isEmpty()) {
			txtQueueEmpty.text = getString(R.string.MUSIC_BROWSE_EMPTY)
		} else {
			txtQueueEmpty.text = ""
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

			if (item.queueId == musicController.getMetadata()?.queueId && !musicController.getPlaybackPosition().isPaused) {
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