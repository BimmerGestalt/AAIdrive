package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager.widget.ViewPager
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundPagerAdapter
import me.hufman.androidautoidrive.phoneui.scrollBottom
import me.hufman.androidautoidrive.phoneui.scrollTop
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
		val pane = view.findViewById<ViewPager>(R.id.pgrTipsList)
		pane.adapter = adapter
		view.findViewById<View>(R.id.pane_tiplist_expand).setOnClickListener {
			val visible = !pane.visible
			pane.visible = visible
			update()
			pane.post {
				if (visible) {
					val position = (pane.scrollTop + pane.scrollBottom) / 2
					requireActivity().findViewById<ScrollView>(R.id.pane_scrollView)?.smoothScrollTo(0, position)
				}
			}
		}
		viewModel.hasCarConnnected.observe(viewLifecycleOwner) {
			view.visible = it
		}
		return view
	}

	override fun onResume() {
		super.onResume()
		update()
	}

	fun update() {
		viewModel.update()
		adapter.notifyDataSetChanged()
		view?.invalidate()
	}
}