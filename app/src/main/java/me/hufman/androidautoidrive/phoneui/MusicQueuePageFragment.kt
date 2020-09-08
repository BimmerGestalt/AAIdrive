package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.spotify.protocol.types.ImageUri
import kotlinx.android.synthetic.main.music_queuepage.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
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
	val cachedCoverArtImages = LruCache<String?,Bitmap?>(50)

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
		listQueue.adapter = QueueAdapter(this.context!!, contents, cachedCoverArtImages) { mediaEntry ->
			if(mediaEntry != null) {
				musicController.playQueue(mediaEntry)
				(activity as MusicPlayerActivity).showNowPlaying()
			}
		}

		listQueueRefresh.setOnRefreshListener {
			queueDirectory()
			Handler(this.context?.mainLooper).postDelayed({
				listQueueRefresh.isRefreshing = false
			}, 1000)
		}

		queueDirectory()
	}

	fun queueDirectory() {
		txtQueueEmpty.text = getString(R.string.MUSIC_BROWSE_LOADING)

		if(loaderJob != null) {
			loaderJob?.cancel()
		}

		//reason why app doesn't start with the queue already loaded is due to async nature of getting the queue and it not being ready when getQueue is called
		//need to have a redraw method or something that periodically checks to see if the musicController.getQueue is not empty and if it is then display everything otherwise keep waiting
		loaderJob = launch {
			val result = musicController.getQueue()?.songs
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

			val coverArtImage = musicController.getQueue()?.coverArt
			if(coverArtImage != null) {
				playlistCoverArt.setImageBitmap(coverArtImage)
			}
			else {
				playlistCoverArt.setImageBitmap(placeholderCoverArt)
			}

			playlistName.setText(musicController.getQueue()?.title)
		}
	}

	inner class QueueAdapter(val context: Context, val contents: ArrayList<MusicMetadata>, val cachedCoverArtImages: LruCache<String?,Bitmap?>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<QueueAdapter.ViewHolder>() {
		inner class ViewHolder(val view: View, val coverArtImageView: ImageView): RecyclerView.ViewHolder(view), View.OnClickListener {
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
			//TODO change to own listitem
			val layout = LayoutInflater.from(context).inflate(R.layout.music_browse_listitem, parent, false)
			return ViewHolder(layout, layout.findViewById(R.id.imgBrowseType))
		}

//		inner class Test {
//			private val semaphore = Semaphore(1)
//			var counter = 0
//				private set
//
//			fun retrieveCoverArt(coverArtUri: String, mediaId: String, holder: ViewHolder) {
//				semaphore.acquire()
//				counter++
//				launch {
//					val coverArt = musicController.getSongCoverArtAsync(ImageUri(coverArtUri)).await()
//					cachedCoverArtImages.put(mediaId, coverArt)
//					holder.coverArtImageView.setImageBitmap(coverArt)
//					//delay(TimeUnit.SECONDS.toSeconds(10))
//					semaphore.release()
//				}
//			}
//
//			fun t() {
//				s.acquire()
//				launch {
//					delay(TimeUnit.SECONDS.toSeconds(5))
//					s.release()
//				}
//			}
//		}
//
//		val test = Test()
//
//		val s = Semaphore(1)

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = contents.getOrNull(position) ?: return

//			val cachedCoverArtImage = cachedCoverArtImages[item.mediaId]
//			holder.coverArtImageView.setImageBitmap(cachedCoverArtImage)
//
//			if(cachedCoverArtImage == null) {
//				launch {
//					val coverArt = musicController.getSongCoverArtAsync(ImageUri(item.coverArtUri)).await()
//					cachedCoverArtImages.put(item.mediaId, coverArt)
//					holder.coverArtImageView.setImageBitmap(coverArt)
//				}
			holder.coverArtImageView.setImageBitmap(item.coverArt)

				//test.retrieveCoverArt(item.coverArtUri!!, item.mediaId!!, holder)

//				launch {
//					//test.t()
//					//delay(15000)
//				}
//			}

			holder.view.findViewById<TextView>(R.id.txtBrowseEntryTitle).setText(item.title)
			holder.view.findViewById<TextView>(R.id.txtBrowseEntrySubtitle).setText(item.artist)
		}
	}
}