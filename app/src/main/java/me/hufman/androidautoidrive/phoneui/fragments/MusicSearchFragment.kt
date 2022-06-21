package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.phoneui.MusicPlayerActivity
import me.hufman.androidautoidrive.phoneui.UIState
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.*
import kotlin.coroutines.CoroutineContext

class MusicSearchFragment : Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	val contents = ArrayList<MusicPlayerItem>()
	var searchJob: Job? = null

	val viewModel by activityViewModels<MusicActivityModel> { MusicActivityModel.Factory(requireContext().applicationContext, UIState.selectedMusicApp) }
	val iconsModel by activityViewModels<MusicActivityIconsModel> { MusicActivityIconsModel.Factory(requireActivity()) }
	private lateinit var musicController: MusicController
	private lateinit var _iconsModel: MusicActivityIconsModel

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_searchpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		musicController = viewModel.musicController
		_iconsModel = iconsModel

		val listSearchResult = view.findViewById<RecyclerView>(R.id.listSearchResult)
		viewModel.redrawListener.observe(viewLifecycleOwner, {
			listSearchResult.adapter?.notifyDataSetChanged()
		})

		listSearchResult.setHasFixedSize(true)
		listSearchResult.layoutManager = LinearLayoutManager(this.context)
		listSearchResult.adapter = DataBoundListAdapter(contents, R.layout.music_browse_listitem, (activity as MusicPlayerActivity).musicPlayerController)

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
			this@MusicSearchFragment.contents.addAll(contents.map {
				MusicPlayerSearchItem(iconsModel, it)
			})
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
}