package me.hufman.androidautoidrive.phoneui.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class MapTestingModel: ViewModel() {

	// autocomplete results
	val locationQuery = MutableLiveData("")
	val locationAutocompleteResults = ArrayList<MapResultViewModel>()
	val isSearchingLocation = MutableLiveData(false)
	val navigationQuery = MutableLiveData("")
	val navigationAutocompleteResults = ArrayList<MapResultViewModel>()
	val isSearchingNavigation = MutableLiveData(false)
}