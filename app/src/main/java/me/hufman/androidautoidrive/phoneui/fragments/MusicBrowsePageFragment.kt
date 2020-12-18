package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
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
import kotlin.coroutines.CoroutineContext
import kotlinx.android.synthetic.main.music_browsepage.*
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.utils.Utils
import me.hufman.androidautoidrive.phoneui.getThemeColor
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity

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

	lateinit var iconsModel: MusicActivityIconsModel
	lateinit var musicController: MusicController
	var loaderJob: Job? = null
	val contents = ArrayList<MusicMetadata>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_browsepage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		musicController = viewModel.musicController

		iconsModel = ViewModelProvider(requireActivity()).get(MusicActivityIconsModel::class.java)

		// redraw to catch any updated coverart
		viewModel.redrawListener.observe(viewLifecycleOwner, Observer {
			listBrowse.adapter?.notifyDataSetChanged()
		})

		listBrowse.setHasFixedSize(true)
		listBrowse.layoutManager = LinearLayoutManager(this.context)
		listBrowse.adapter = BrowseAdapter(this.requireContext(), iconsModel, contents) { mediaEntry ->
			if (mediaEntry != null) {
				if (mediaEntry.browseable) {
					(activity as MusicPlayerActivity).pushBrowse(mediaEntry)
				} else {
					musicController.playSong(mediaEntry)
					(activity as MusicPlayerActivity).showNowPlaying()
				}
			}
		}

		listBrowseRefresh.setOnRefreshListener {
			browseDirectory(arguments?.getString(ARG_MEDIA_ID))
			Handler(this.context?.mainLooper).postDelayed({
				this.view?.findViewById<SwipeRefreshLayout>(R.id.listBrowseRefresh)?.isRefreshing = false
			}, 1000)
		}

		val mediaId = arguments?.getString(ARG_MEDIA_ID)
		browseDirectory(mediaId)
	}

	private fun browseDirectory(mediaId: String?) {
		txtEmpty.text = getString(R.string.MUSIC_BROWSE_LOADING)

		if (loaderJob != null) {
			loaderJob?.cancel()
		}

		loaderJob = launch {
			val result = musicController.browseAsync(MusicMetadata(mediaId = mediaId))
			val contents = result.await()
			this@MusicBrowsePageFragment.contents.clear()
			this@MusicBrowsePageFragment.contents.addAll(contents)
			redraw()
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		if (isResumed) {
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

class BrowseAdapter(val context: Context, val iconsModel: MusicActivityIconsModel, val contents: ArrayList<MusicMetadata>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<BrowseAdapter.ViewHolder>() {
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
		holder.view.findViewById<TextView>(R.id.txtBrowseEntryTitle).text = item.title
		holder.view.findViewById<TextView>(R.id.txtBrowseEntrySubtitle).text = item.subtitle

		holder.view.findViewById<ImageView>(R.id.imgBrowseType).colorFilter = Utils.getIconMask(context.getThemeColor(android.R.attr.textColorSecondary))

		if(item.coverArt == null) {
			holder.view.findViewById<ImageView>(R.id.imgBrowseType).setImageBitmap(
				if (item.browseable)
					iconsModel.folderIcon
				else
					iconsModel.songIcon
			)
		} else {
			holder.view.findViewById<ImageView>(R.id.imgBrowseType).setImageBitmap(item.coverArt)
		}
	}
}