package me.hufman.androidautoidrive.phoneui.fragments

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.music_browsepage.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.MusicActivityModel

class MusicBrowseFragment: Fragment() {
	var fragment: Fragment? = null
	var fm: FragmentManager? = null

	companion object {
		fun newInstance(fragment: Fragment): MusicBrowseFragment {
			val instance = MusicBrowseFragment()
			instance.fragment = fragment
			return instance
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		super.onCreateView(inflater, container, savedInstanceState)
		fm = childFragmentManager
		val view = inflater.inflate(R.layout.music_browse_container, container, false)
		val fragment = fragment
		if (fragment != null) {
			replaceFragment(fragment, false)
		}

		return view
	}

	fun onActive() {
		val viewModel = ViewModelProvider(requireActivity()).get(MusicActivityModel::class.java)
		viewModel.musicController?.listener = kotlinx.coroutines.Runnable {
			(this.context as Activity).listBrowse.adapter?.notifyDataSetChanged()
		}
	}

	fun replaceFragment(fragment: Fragment, addToBackstack: Boolean = true) {
		val fragmentManager = fm ?: return

		with(fragmentManager.beginTransaction()) {
			setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
					android.R.anim.slide_in_left, android.R.anim.slide_out_right)
			replace(R.id.frgContainer, fragment)
			if (addToBackstack) addToBackStack(null)
			commit()
		}
	}

	fun onBackPressed(): Boolean {
		val fragmentManager = fm ?: return false
		if (isVisible && fragmentManager.backStackEntryCount > 0) {
			fragmentManager.popBackStack()
			return true
		}
		return false
	}
}