package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.ViewHelpers.scrollBottom
import me.hufman.androidautoidrive.phoneui.ViewHelpers.scrollTop
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionTipsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class ConnectionTipsListFragment: Fragment() {
	var mode = ""
	val viewModel by viewModels<ConnectionTipsModel> { ConnectionTipsModel.Factory(requireContext().applicationContext) }
	val adapter by lazy { DataBoundListAdapter(viewModel.currentTips, R.layout.fragment_tip, null) }

	override fun onInflate(context: Context, attrs: AttributeSet, savedInstanceState: Bundle?) {
		super.onInflate(context, attrs, savedInstanceState)
		val ta = context.obtainStyledAttributes(attrs, R.styleable.TipsListFragment_MembersInjector)
		if (ta.hasValue(R.styleable.TipsListFragment_MembersInjector_tipsMode)) {
			mode = ta.getString(R.styleable.TipsListFragment_MembersInjector_tipsMode) ?: ""
		}
		ta.recycle()
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val view = inflater.inflate(R.layout.fragment_tipslist, container, false)
		val pane = view.findViewById<ViewPager2>(R.id.pgrTipsList)
		pane.adapter = adapter
		pane.offscreenPageLimit = 1
		(pane.getChildAt(0) as? RecyclerView)?.apply {
			clipToPadding = false
		}
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
		return view
	}

	override fun onResume() {
		super.onResume()
		update()
	}

	fun update() {
		viewModel.update()
		view?.visible = viewModel.currentTips.isNotEmpty()
		adapter.notifyDataSetChanged()
		view?.invalidate()
	}
}