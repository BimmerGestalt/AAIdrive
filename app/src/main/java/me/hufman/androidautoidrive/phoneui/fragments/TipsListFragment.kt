package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager.widget.ViewPager
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundPagerAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.TipsModel
import me.hufman.androidautoidrive.phoneui.visible

class TipsListFragment: Fragment() {
	var mode = ""
	val viewModel by viewModels<TipsModel>()
	val adapter by lazy { DataBoundPagerAdapter(parentFragmentManager, viewModel.currentTips, R.layout.fragment_tip, null) }

	override fun onInflate(context: Context, attrs: AttributeSet, savedInstanceState: Bundle?) {
		super.onInflate(context, attrs, savedInstanceState)
		val ta = context.obtainStyledAttributes(attrs, R.styleable.TipsListFragment_MembersInjector)
		if (ta.hasValue(R.styleable.TipsListFragment_MembersInjector_tipsMode)) {
			mode = ta.getString(R.styleable.TipsListFragment_MembersInjector_tipsMode) ?: ""
		}
		ta.recycle()
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		viewModel.mode = mode
		val view = inflater.inflate(R.layout.fragment_tipslist, container, false)
		view.findViewById<ViewPager>(R.id.pgrTipsList).adapter = adapter
		viewModel.hasCarConnnected.observe(viewLifecycleOwner) {
			view.visible = it
		}
		return view
	}

	override fun onResume() {
		super.onResume()
		viewModel.update()
		adapter.notifyDataSetChanged()
		view?.invalidate()
	}
}