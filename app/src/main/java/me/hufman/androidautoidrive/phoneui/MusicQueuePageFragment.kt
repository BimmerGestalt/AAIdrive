package me.hufman.androidautoidrive.phoneui

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
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
import me.hufman.androidautoidrive.music.controllers.CombinedMusicAppController
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import java.lang.Exception
import java.lang.ref.WeakReference
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
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
	var loaderJob: Job? = null
	val contents = ArrayList<MusicMetadata>()
	val cachedCoverArtImages: HashMap<String?,Bitmap?> = HashMap()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_queuepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProviders.of(requireActivity()).get(MusicActivityModel::class.java)
		val musicController = viewModel.musicController ?: return
		this.musicController = musicController

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
		}
	}

	inner class QueueAdapter(val context: Context, val contents: ArrayList<MusicMetadata>, val cachedCoverArtImages: HashMap<String?,Bitmap?>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

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

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = contents.getOrNull(position) ?: return

//			val cachedCoverArtImage = cachedCoverArtImages[item.mediaId]
			//holder.coverArtImageView.setImageBitmap(cachedCoverArtImage); //TODO: working version, currently disabled due to experimentation

//			if(cachedCoverArtImage == null) {
//				doInBackground({
//					musicController.getSongCoverArtAsync(ImageUri(item.coverArtUri))
//				}) { fragment, result ->
//					result.onSuccess { value ->
//						launch {
//							value.await()
//							cachedCoverArtImages[item.mediaId] = value.getCompleted()
//							holder.coverArtImageView.setImageBitmap(value.getCompleted())
//						}
//					}
//				}
//			}

			holder.view.findViewById<TextView>(R.id.txtBrowseEntryTitle).setText(item.title)
			holder.view.findViewById<TextView>(R.id.txtBrowseEntrySubtitle).setText(item.artist)
		}
	}
}

class BackgroundTask<A: Fragment, R>(activity: A, val task: () -> R, val resultHandler: (Activity: A?, result: Result<R>) -> Result<Deferred<Bitmap?>>): AsyncTask<Unit,Unit,R?>() {
	val activityReference = WeakReference<A>(activity)
	var value: R? = null
	var exception: Exception? = null

	override fun doInBackground(vararg params: Unit?): R? {
		try {
			value = task()
		} catch(exception: Exception) {
			this.exception = exception
		}
		return value
	}

	override fun onPostExecute(result: R?) {
		resultHandler(activityReference.get(), if(exception == null) {
			Result.success(value!!)
		} else {
			Result.failure(exception!!)
		})
	}
}

fun <A: Fragment, R> A.doInBackground(task: () -> R, resultHandler: (Activity: A?, result: Result<R>) -> Result<Deferred<Bitmap?>>) {
	BackgroundTask(this, task, resultHandler).execute()
}