package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.NestedListView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.CalendarEventsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.viewModels

class CalendarCalendarsFragment: Fragment() {
	val calendarEventsModel by viewModels<CalendarEventsModel> { CalendarEventsModel.Factory(requireContext().applicationContext) }
	val calendarsAdapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.calendar_listitem, calendarEventsModel.calendars, null)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_calendar_calendars, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		view.findViewById<NestedListView>(R.id.listCalendars).apply {
			emptyView = view.findViewById<TextView>(R.id.txtEmptyCalendars)
			adapter = calendarsAdapter
		}
	}

	override fun onResume() {
		super.onResume()
		redraw()
	}

	fun redraw() {
		// update list of calendars
		calendarEventsModel.update()
		calendarsAdapter.notifyDataSetChanged()
	}
}