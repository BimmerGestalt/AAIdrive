package me.hufman.androidautoidrive.phoneui.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import me.hufman.androidautoidrive.BR

class DataBoundPagerAdapter<I, C>(fragmentManager: FragmentManager,
                                  val data: List<I>, val layoutId: Int, val callback: C?): FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
	override fun getCount(): Int {
		return data.count()
	}

	override fun getItem(position: Int): Fragment {
		val item = data[position]
		val fragment = DataBoundFragment(item, layoutId, callback)
		return fragment
	}
}

class DataBoundFragment<I, C>(val item: I, val layoutId: Int, val callback: C?): Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val binding = DataBindingUtil.inflate<ViewDataBinding>(layoutInflater, layoutId, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.setVariable(BR.data, item)
		if (callback != null) {
			binding.setVariable(BR.callback, callback)
		}
		binding.executePendingBindings()
		binding.invalidateAll()
		return binding.root
	}
}