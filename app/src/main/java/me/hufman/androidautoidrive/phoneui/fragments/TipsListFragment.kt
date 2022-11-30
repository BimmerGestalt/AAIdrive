package me.hufman.androidautoidrive.phoneui.fragments

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.ViewHelpers.findParent
import me.hufman.androidautoidrive.phoneui.ViewHelpers.scrollTop
import me.hufman.androidautoidrive.phoneui.ViewHelpers.visible
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundListAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.CapabilitiesTipsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionTipsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.TipsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class TipsListFragment: Fragment() {
	var mode = "UNKNOWN"
	val viewModel by viewModels<TipsModel>
	{
		if (mode == "connection") {
			ConnectionTipsModel.Factory(requireContext().applicationContext)
		} else {
			CapabilitiesTipsModel.Factory()
		}
	}
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

		// show the offscreen pages
		pane.offscreenPageLimit = 2
		(pane.getChildAt(0) as? RecyclerView)?.apply {
			clipToPadding = false
		}

		// set up the tab bar
		val tabLayout = view.findViewById<TabLayout>(R.id.pgrTipsListTabs)
		TabLayoutMediator(tabLayout, pane) { _, _ -> }.attach()

		// automatic pager height measurement https://stackoverflow.com/a/67104270/169035
		val pageCallback = object: ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				super.onPageSelected(position)

				val recyclerView = (pane.getChildAt(0) as? RecyclerView)
				recyclerView?.getChildAt(position)?.let {
					updatePagerHeightForChild(it, pane)
				}
			}

			fun updatePagerHeightForChild(view: View, pager: ViewPager2) {
				view.post {
					val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
							view.width, View.MeasureSpec.EXACTLY
					)
					val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
							0, View.MeasureSpec.UNSPECIFIED
					)
					view.measure(widthMeasureSpec, heightMeasureSpec)
					if (pager.layoutParams.height < view.measuredHeight) {
						pager.layoutParams = (pager.layoutParams).also {
							it.height = view.measuredHeight
						}
					}
				}
			}
		}
		pane.registerOnPageChangeCallback(pageCallback)
		pageCallback.onPageSelected(0)

		// support toggling
		view.findViewById<View>(R.id.pane_tiplist_expand).setOnClickListener {
			val visible = !pane.visible
			pane.visible = visible
			tabLayout.visible = visible
			update()
			pane.postDelayed(200) {
				if (visible) {
					val position = pane.scrollTop
					(pane.findParent { it is ScrollView } as? ScrollView)?.smoothScrollTo(0, position)
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
		viewModel.mode = mode
		viewModel.update()
		view?.visible = viewModel.currentTips.isNotEmpty()
		adapter.notifyDataSetChanged()
		view?.invalidate()
	}
}