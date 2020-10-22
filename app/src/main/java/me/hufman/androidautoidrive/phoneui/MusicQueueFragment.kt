package me.hufman.androidautoidrive.phoneui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.hufman.androidautoidrive.R

class MusicQueueFragment: Fragment() {
	var fragment: Fragment? = null
	var fm: FragmentManager? = null

	companion object {
		fun newInstance(fragment: Fragment): MusicQueueFragment {
			val instance = MusicQueueFragment()
			instance.fragment = fragment
			return instance
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		super.onCreateView(inflater, container, savedInstanceState)
		fm = childFragmentManager
		val view = inflater.inflate(R.layout.music_queue_container, container, false)
		val fragment = fragment
		if (fragment != null) {
			replaceFragment(fragment, false)
		}

		return view
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
		if (userVisibleHint && fragmentManager.backStackEntryCount > 0) {
			fragmentManager.popBackStack()
			return true
		}
		return false
	}
}