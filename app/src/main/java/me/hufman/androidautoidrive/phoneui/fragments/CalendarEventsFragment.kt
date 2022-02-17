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
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.BooleanLiveSetting
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.calendar.CalendarEvent
import me.hufman.androidautoidrive.phoneui.NestedListView
import me.hufman.androidautoidrive.phoneui.adapters.DataBoundArrayAdapter
import me.hufman.androidautoidrive.phoneui.viewmodels.CalendarEventsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.activityViewModels

class CalendarEventsFragment: Fragment() {
	val calendarEventsModel by activityViewModels<CalendarEventsModel> { CalendarEventsModel.Factory(requireContext().applicationContext) }
	val displayedEvents = ArrayList<CalendarEvent>()
	val eventsAdapter by lazy {
		DataBoundArrayAdapter(requireContext(), R.layout.calendarevent_listitem, displayedEvents, null)
	}
	val calendarDetailedEventsSetting by lazy {BooleanLiveSetting(requireContext().applicationContext, AppSettings.KEYS.CALENDAR_DETAILED_EVENTS)}
	val calendarIgnoreVisibilitySetting by lazy {BooleanLiveSetting(requireContext().applicationContext, AppSettings.KEYS.CALENDAR_IGNORE_VISIBILITY)}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return inflater.inflate(R.layout.fragment_calendar_events, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		view.findViewById<NestedListView>(R.id.listCalendarEvents).apply {
			emptyView = view.findViewById<TextView>(R.id.txtEmptyCalendarEvents)
			adapter = eventsAdapter
		}
		calendarDetailedEventsSetting.observe(viewLifecycleOwner) {
			redraw()
		}
		calendarIgnoreVisibilitySetting.observe(viewLifecycleOwner) {
			redraw()
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
			displayedEvents.clear()
			displayedEvents.addAll(calendarEventsModel.upcomingEvents)
			eventsAdapter.notifyDataSetChanged()
		}
	}
}