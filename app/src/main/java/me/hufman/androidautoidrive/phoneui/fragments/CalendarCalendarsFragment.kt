package me.hufman.androidautoidrive.phoneui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.calendar.PhoneCalendar
import me.hufman.androidautoidrive.phoneui.NestedListView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.CalendarEventsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class CalendarCalendarsFragment: Fragment() {
	val calendarEventsModel by activityViewModels<CalendarEventsModel> { CalendarEventsModel.Factory(requireContext().applicationContext) }
	val displayedCalendars = ArrayList<PhoneCalendar>()
	val calendarsAdapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.calendar_listitem, displayedCalendars, null)
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
		// update list of upcoming events
		viewLifecycleOwner.lifecycleScope.launch {
			// calendar events are supposed to only be queried from a background thread
			withContext(Dispatchers.IO) {
				calendarEventsModel.update()
			}
			// but the ListView needs to be updated on the UI thread
			displayedCalendars.clear()
			displayedCalendars.addAll(calendarEventsModel.calendars)
			calendarsAdapter.notifyDataSetChanged()
		}
	}
}