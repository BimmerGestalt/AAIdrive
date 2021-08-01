package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.getThemeColor
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels
import me.hufman.androidautoidrive.utils.Utils
import kotlin.coroutines.CoroutineContext

class MusicSearchFragment : Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	val contents = ArrayList<MusicMetadata>()
	var searchJob: Job? = null

	val viewModel by activityViewModels<MusicActivityModel>()
	val iconsModel by activityViewModels<MusicActivityIconsModel>()
	lateinit var musicController: MusicController

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_searchpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		musicController = viewModel.musicController

		val listSearchResult = view.findViewById<RecyclerView>(R.id.listSearchResult)
		viewModel.redrawListener.observe(viewLifecycleOwner, {
			listSearchResult.adapter?.notifyDataSetChanged()
		})

		listSearchResult.setHasFixedSize(true)
		listSearchResult.layoutManager = LinearLayoutManager(this.context)
		listSearchResult.adapter = SearchResultsAdapter(this.requireContext(), iconsModel, contents) {
			if (it != null) {
				val musicPlayerController = (activity as MusicPlayerActivity).musicPlayerController
				if (it.browseable) {
					musicPlayerController.pushBrowse(it)
					musicPlayerController.showBrowse()
				} else if (it.playable) {
					musicController.playSong(it)
					musicPlayerController.showNowPlaying()
				}
			}
		}

		val searchBar = view.findViewById<SearchView>(R.id.searchBar)
		searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String?): Boolean {
				return true
			}

			override fun onQueryTextChange(newText: String?): Boolean {
				if (newText != null && newText.length > 1) {
					searchForQuery(newText)
				}
				return true
			}
		})
	}

	private fun searchForQuery(query: String) {
		if (searchJob != null) {
			searchJob?.cancel()
		}

		contents.clear()

		val listSearchResult = view?.findViewById<RecyclerView>(R.id.listSearchResult)
		listSearchResult?.adapter?.notifyDataSetChanged()
		val txtSearchResultsEmpty = view?.findViewById<TextView>(R.id.txtSearchResultsEmpty)
		txtSearchResultsEmpty?.text = getString(R.string.MUSIC_BROWSE_LOADING)

		searchJob = launch {
			val result = musicController.searchAsync(query)
			val contents = result.await() ?: emptyList()
			this@MusicSearchFragment.contents.clear()
			this@MusicSearchFragment.contents.addAll(contents)
			redraw()
		}
	}

	private fun redraw() {
		val txtSearchResultsEmpty = view?.findViewById<TextView>(R.id.txtSearchResultsEmpty)
		txtSearchResultsEmpty?.text = if (contents.isEmpty()) {
			getString(R.string.MUSIC_SEARCH_EMPTY)
		} else {
			""
		}
		val listSearchResult = view?.findViewById<RecyclerView>(R.id.listSearchResult)
		listSearchResult?.adapter?.notifyDataSetChanged()
	}

	class SearchResultsAdapter(val context: Context, val iconsModel: MusicActivityIconsModel, val contents: ArrayList<MusicMetadata>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
		inner class ViewHolder(val view: View, val searchResultCoverArtImageView: ImageView, val searchResultTitleTextView: TextView, val searchResultSubtitleTextView: TextView): RecyclerView.ViewHolder(view), View.OnClickListener {
			init {
				view.setOnClickListener(this)
			}

			override fun onClick(v: View?) {
				val entry = contents.getOrNull(adapterPosition)
				clickListener(entry)
			}
		}

		override fun getItemCount(): Int {
			return contents.size
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val layout = LayoutInflater.from(context).inflate(R.layout.music_search_listitem, parent, false)
		    return ViewHolder(layout, layout.findViewById(R.id.imgSearchResultCover), layout.findViewById(R.id.txtSearchResultTitle), layout.findViewById(R.id.txtSearchResultSubtitleTitle))
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = contents.getOrNull(position) ?: return

			holder.searchResultTitleTextView.text = item.title

			val subtitleString = if (item.subtitle == "Artist" || item.subtitle == "Episode") {
				item.subtitle
			} else {
				"${item.subtitle} - ${item.artist}"
			}
			holder.searchResultSubtitleTextView.text = subtitleString

			holder.searchResultCoverArtImageView.colorFilter = Utils.getIconMask(context.getThemeColor(android.R.attr.textColorSecondary))
			holder.searchResultCoverArtImageView.setImageBitmap(if (item.coverArt != null) {
				item.coverArt
			} else {
				iconsModel.songIcon
			})
		}
	}
}