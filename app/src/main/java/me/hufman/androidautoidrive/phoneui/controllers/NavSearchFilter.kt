package me.hufman.androidautoidrive.phoneui.controllers

import android.widget.Filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.phoneui.viewmodels.MapResultViewModel

open class NavSearchFilter(val api: MapPlaceSearch, val output: MutableList<MapResultViewModel>): Filter() {
	override fun performFiltering(constraint: CharSequence?): FilterResults {
		if (constraint?.isNotBlank() != true) {
			return FilterResults().also {
				it.count = 0
				it.values = ArrayList<MapResult>()
			}
		}
		return runBlocking {
			delay(1500)     // debounce input, will be cancelled by the FilterResults processing if new input arrives
			val results = api.searchLocationsAsync(constraint.toString()).await()
			FilterResults().also {
				it.count = results.size
				it.values = results
			}
		}
	}

	override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
		output.clear()
		val resultList = (results?.values as? List<*>)?.mapNotNull { it as? MapResult } ?: emptyList()
		val resultModelList = resultList.map {
			MapResultViewModel(CarInformation(),  it)
		}
		output.addAll(resultModelList)
	}
}