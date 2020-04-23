package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.android.synthetic.main.music_browsepage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.getThemeColor
import kotlin.coroutines.CoroutineContext

class MusicBrowsePageFragment: Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	companion object {
		const val ARG_MEDIA_ID = "me.hufman.androidautoidrive.BROWSE_MEDIA_ID"
		const val FOLDER_ID = "155.png"
		const val SONG_ID = "152.png"

		fun newInstance(mediaEntry: MusicMetadata?): MusicBrowsePageFragment {
			val fragment = MusicBrowsePageFragment()
			fragment.arguments = Bundle().apply {
				putString(ARG_MEDIA_ID, mediaEntry?.mediaId)
			}
			return fragment
		}
	}

	lateinit var musicController: MusicController
	var loaderJob: Job? = null
	val contents = ArrayList<MusicMetadata>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_browsepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProviders.of(requireActivity()).get(MusicActivityModel::class.java)
		val musicController = viewModel.musicController ?: return
		this.musicController = musicController

		listBrowse.setHasFixedSize(true)
		listBrowse.layoutManager = LinearLayoutManager(this.context)
		listBrowse.adapter = BrowseAdapter(this.context!!, viewModel.icons, contents) { mediaEntry ->
			if (mediaEntry != null) {
				if (mediaEntry.browseable) {
					(activity as MusicActivity).pushBrowse(mediaEntry)
				} else {
					musicController.playSong(mediaEntry)
					(activity as MusicActivity).showNowPlaying()
				}
			}
		}

		listBrowseRefresh.setOnRefreshListener {
			browseDirectory(arguments?.getString(ARG_MEDIA_ID))
			Handler(this.context?.mainLooper).postDelayed({
				listBrowseRefresh.isRefreshing = false
			}, 1000)
		}

		val mediaId = arguments?.getString(ARG_MEDIA_ID)
		browseDirectory(mediaId)
	}

	fun browseDirectory(mediaId: String?) {
		txtEmpty.text = getString(R.string.MUSIC_BROWSE_LOADING)

		if (loaderJob != null) {
			loaderJob?.cancel()
		}

		loaderJob = launch {
			val result = musicController.browseAsync(MusicMetadata(mediaId = mediaId))
			val contents = result.await()
			this@MusicBrowsePageFragment.contents.clear()
			this@MusicBrowsePageFragment.contents.addAll(contents)
			if (isVisible) {
				if (contents.isEmpty()) {
					txtEmpty.text = getString(R.string.MUSIC_BROWSE_EMPTY)
				} else {
					txtEmpty.text = ""
				}

				listBrowse.removeAllViews()
				listBrowse.adapter?.notifyDataSetChanged()
			}
		}
	}
}

class BrowseAdapter(val context: Context, val icons: Map<String, Bitmap>, val contents: ArrayList<MusicMetadata>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<BrowseAdapter.ViewHolder>() {
	inner class ViewHolder(val view: View): RecyclerView.ViewHolder(view), View.OnClickListener {
		init {
			view.setOnClickListener(this)
		}
		override fun onClick(view: View?) {
			val mediaEntry = contents.getOrNull(adapterPosition)
			clickListener(mediaEntry)
		}
	}

	override fun getItemCount(): Int {
		return contents.size
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val layout = LayoutInflater.from(context).inflate(R.layout.music_browse_listitem, parent, false)
		return ViewHolder(layout)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = contents.getOrNull(position) ?: return
		holder.view.findViewById<TextView>(R.id.txtBrowseEntryTitle).setText(item.title)
		holder.view.findViewById<TextView>(R.id.txtBrowseEntrySubtitle).setText(item.subtitle)

		holder.view.findViewById<ImageView>(R.id.imgBrowseType).colorFilter = Utils.getIconMask(context.getThemeColor(android.R.attr.textColorSecondary))
		if (item.browseable) {
			holder.view.findViewById<ImageView>(R.id.imgBrowseType).setImageBitmap(icons[MusicBrowsePageFragment.FOLDER_ID])
		} else {
			holder.view.findViewById<ImageView>(R.id.imgBrowseType).setImageBitmap(icons[MusicBrowsePageFragment.SONG_ID])
		}
	}
}