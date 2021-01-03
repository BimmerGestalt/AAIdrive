package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.music_browsepage.*
import kotlinx.android.synthetic.main.music_queuepage.*
import kotlinx.android.synthetic.main.music_searchpage.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.getThemeColor
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import me.hufman.androidautoidrive.utils.Utils
import kotlin.coroutines.CoroutineContext

class MusicSearchFragment : Fragment(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.Main

	lateinit var musicController: MusicController
	val contents = ArrayList<MusicMetadata>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.music_searchpage, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		musicController = viewModel.musicController

		val iconsModel = ViewModelProvider(requireActivity()).get(MusicActivityIconsModel::class.java)

		viewModel.redrawListener.observe(viewLifecycleOwner, Observer {
			listSearchResult.adapter?.notifyDataSetChanged()
		})

		listSearchResult.setHasFixedSize(true)
		listSearchResult.layoutManager = LinearLayoutManager(this.context)
		listSearchResult.adapter = SearchResultsAdapter(this.requireContext(), iconsModel, contents) { }

		searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String?): Boolean {
				if (query != null && query.isNotBlank()) {
					searchForQuery(query)
				}
				return true
			}

			override fun onQueryTextChange(newText: String?): Boolean {
				return true
			}
		})
	}

	private fun searchForQuery(query: String) {
		searchBar.clearFocus()

		// hide the keyboard
		val inputManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		inputManager.hideSoftInputFromWindow(searchBar.windowToken, 0)

		txtSearchResultsEmpty.text = getString(R.string.MUSIC_BROWSE_LOADING)

		launch {
			val result = musicController.searchAsync(query)
			val contents = result.await() ?: emptyList()
			this@MusicSearchFragment.contents.clear()
			this@MusicSearchFragment.contents.addAll(contents)
			redraw()
		}
	}

	private fun redraw() {
		if (contents.isEmpty()) {
			txtSearchResultsEmpty.text = "No results found"
		} else {
			txtSearchResultsEmpty.text = ""
		}
		listSearchResult.adapter?.notifyDataSetChanged()
	}

	class SearchResultsAdapter(val context: Context, val iconsModel: MusicActivityIconsModel, val contents: ArrayList<MusicMetadata>, val clickListener: (MusicMetadata?) -> Unit): RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
		inner class ViewHolder(val view: View): RecyclerView.ViewHolder(view), View.OnClickListener {
			init {
				view.setOnClickListener(this)
			}

			override fun onClick(v: View?) {

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
			holder.view.findViewById<TextView>(R.id.txtBrowseEntrySubtitle).text = item.artist

			holder.view.findViewById<ImageView>(R.id.imgBrowseType).colorFilter = Utils.getIconMask(context.getThemeColor(android.R.attr.textColorSecondary))
			holder.view.findViewById<ImageView>(R.id.imgBrowseType).setImageBitmap(if (item.coverArt != null) {
				item.coverArt
			} else {
				iconsModel.songIcon
			})
		}
	}
}