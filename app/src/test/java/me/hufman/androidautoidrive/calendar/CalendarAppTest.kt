package me.hufman.androidautoidrive.calendar

import android.location.Address
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.carapp.calendar.RHMIDateUtils
import me.hufman.androidautoidrive.carapp.calendar.CalendarApp
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.*

class CalendarAppTest {
	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_calendar.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val calendarEvents = listOf(
		CalendarEvent("A", makeCalendar(2021, 11, 10, 9, 15), makeCalendar(2021, 11, 10, 16, 45), "", "", 0),
		CalendarEvent("B", makeCalendar(2021, 11, 11, 9, 15), makeCalendar(2021, 11, 11, 16, 45), "", "", 0),
		CalendarEvent("C", makeCalendar(2021, 11, 11, 18, 0), makeCalendar(2021, 11, 11, 20, 0), "Home", "Full Description", 0),
		CalendarEvent("Holiday", makeCalendar(2021, 11, 25, 0, 0), makeCalendar(2021, 11, 26, 0, 0), "", "", 0),
		CalendarEvent("Holiday", makeCalendar(2021, 12, 25, 0, 0), makeCalendar(2021, 12, 26, 0, 0), "", "", 0),
		CalendarEvent("Holiday", makeCalendar(2022, 1, 1, 0, 0), makeCalendar(2022, 1, 2, 0, 0), "", "", 0),
	)
	val calendarProvider = mock<CalendarProvider> {
		on {hasPermission()} doReturn true
		on {getEvents(any(), any(), isNull())} doAnswer { inv -> calendarEvents.filter { it.start[Calendar.YEAR] == inv.arguments[0] && it.start[Calendar.MONTH] + 1 == inv.arguments[1] } }
		on {getEvents(any(), any(), isNotNull())} doAnswer { inv -> calendarEvents.filter { it.start[Calendar.YEAR] == inv.arguments[0] && it.start[Calendar.MONTH] + 1 == inv.arguments[1] && it.start[Calendar.DAY_OF_MONTH] == inv.arguments[2] } }
	}
	val addressSearcher = mock<AddressSearcher> {
		on {search(any())} doReturn null
	}
	val navigationTrigger = mock<NavigationTrigger> ()

	@Before
	fun setUp() {
		Locale.setDefault(Locale.US)
	}

	private fun makeCalendar(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Calendar {
		return Calendar.getInstance().also {
			it[Calendar.YEAR] = year
			it[Calendar.MONTH] = month - 1
			it[Calendar.DAY_OF_MONTH] = day
			it[Calendar.HOUR_OF_DAY] = hour
			it[Calendar.MINUTE] = minute
			it[Calendar.SECOND] = 0
			it[Calendar.MILLISECOND] = 0
		}
	}

	@Test
	fun testMockCalendar() {
		val nov = calendarProvider.getEvents(2021, 11, null)
		assertEquals(4, nov.size)
		val dec = calendarProvider.getEvents(2021, 12, null)
		assertEquals(1, dec.size)

		val singleDay = calendarProvider.getEvents(2021, 11, 10)
		assertEquals(1, singleDay.size)
		val doubleDay = calendarProvider.getEvents(2021, 11, 11)
		assertEquals(2, doubleDay.size)
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)

		assertEquals("Richtext", app.viewEvent.descriptionList.getModel()?.modelType)
	}

	@Test
	fun testMonthPermission() {
		whenever(calendarProvider.hasPermission()) doReturn false

		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		app.viewMonth.selectedDate = makeCalendar(2021, 11, 1)
		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewMonth.state.id, 1, mapOf(4.toByte() to true))

		val focusEvent = app.carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
		assertNotNull(mockServer.triggeredEvents[focusEvent.id])
		assertEquals(app.viewPermission.state.id, mockServer.triggeredEvents[focusEvent.id]!![0.toByte()])
	}

	@Test
	fun testMonthView() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		app.viewMonth.selectedDate = makeCalendar(2021, 11, 1)
		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewMonth.state.id, 1, mapOf(4.toByte() to true))

		val model = mockServer.data[app.viewMonth.listModel.id] as BMWRemoting.RHMIDataTable
		assertEquals(1, model.numColumns)
		assertEquals(3, model.numRows)
		assertEquals(setOf(10, 11, 25), model.data.map { it[0] }.toSet())
	}

	@Test
	fun testMonthScroll() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		app.viewMonth.selectedDate = makeCalendar(2021, 11, 1)
		app.viewMonth.update()

		val nextMonth = RHMIDateUtils.convertToRhmiDate(makeCalendar(2021, 12, 1))
		IDriveConnection.mockRemotingClient?.rhmi_onActionEvent(0, "", app.viewMonth.state.asCalendarMonthState()!!.getChangeAction()!!.id, mapOf(0.toByte() to nextMonth))

		val model = mockServer.data[app.viewMonth.listModel.id] as BMWRemoting.RHMIDataTable
		assertEquals(1, model.numColumns)
		assertEquals(1, model.numRows)
		assertEquals(setOf(25), model.data.map { it[0] }.toSet())
	}

	@Test
	fun testMonthClick() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		val monthState = app.viewMonth.state.asCalendarMonthState()!!

		app.viewMonth.selectedDate = makeCalendar(2021, 11, 1)
		app.viewMonth.update()

		val clickedDay = RHMIDateUtils.convertToRhmiDate(makeCalendar(2021, 11, 11))
		IDriveConnection.mockRemotingClient?.rhmi_onActionEvent(0, "", monthState.getAction()!!.asRAAction()!!.id, mapOf(0.toByte() to clickedDay))

		assertEquals(clickedDay, mockServer.data[monthState.dateModel])
		assertEquals(app.viewDay.state.id, mockServer.data[monthState.getAction()!!.asHMIAction()!!.getTargetModel()?.id])
		assertEquals(makeCalendar(2021, 11, 11), app.viewDay.selectedDate)
	}

	@Test
	fun testDayView() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		app.viewDay.selectedDate = makeCalendar(2021, 11, 11)
		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewDay.state.id, 1, mapOf(4.toByte() to true))

		val date = RHMIDateUtils.convertToRhmiDate(makeCalendar(2021, 11, 11))
		assertEquals(date, mockServer.data[app.viewDay.dateModel.id])
		val data = mockServer.data[app.viewDay.listModel.id] as BMWRemoting.RHMIDataTable
		assertEquals(2, data.numRows)
		calendarProvider.getEvents(2021, 11, 11).zip(data.data).forEach {
			val event = it.first
			val carData = it.second
			assertEquals(RHMIDateUtils.convertToRhmiTime(event.start), carData[0])
			assertEquals(RHMIDateUtils.convertToRhmiTime(event.end), carData[1])
			assertEquals(event.title, carData[3])
		}
	}

	@Test
	fun testDayClick() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		app.viewDay.selectedDate = makeCalendar(2021, 11, 11)
		val dayComponent = app.viewDay.calendarDay

		app.viewDay.update()
		val data = mockServer.data[app.viewDay.listModel.id] as BMWRemoting.RHMIDataTable
		assertEquals(2, data.numRows)

		IDriveConnection.mockRemotingClient?.rhmi_onActionEvent(0, "", dayComponent.getAction()!!.asRAAction()!!.id, mapOf(0.toByte() to 2))

		assertEquals(app.viewEvent.state.id, mockServer.data[dayComponent.getAction()!!.asHMIAction()!!.getTargetModel()?.id])
		assertEquals(calendarProvider.getEvents(2021, 11, 11)[1], app.viewEvent.selectedEvent)
	}

	@Test
	fun testEventViewNull() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		val viewEvent = app.viewEvent

		// RHMIIdempotentApp thinks the data is "" and will ignore requests to set it to ""
		// so tell it something else
		app.viewEvent.titleLabel.getModel()?.asRaDataModel()?.value = "UNSET"
		mockServer.data.remove(viewEvent.titleLabel.model)

		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewEvent.state.id, 1, mapOf(4.toByte() to true))

		// check that data was explicitly cleared
		assertEquals("", mockServer.data[viewEvent.titleLabel.model])
		assertEquals(0, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(2, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals(0, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalColumns)
//		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.ENABLED.id])  // already disabled by ui_description
		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
		assertEquals(0, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals(false, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
	}

	@Test
	fun testEventView() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		val viewEvent = app.viewEvent
		viewEvent.selectedEvent = calendarProvider.getEvents(2021, 11, 11)[1]

		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewEvent.state.id, 1, mapOf(4.toByte() to true))

		// check that data was filled in
		assertEquals("C", mockServer.data[viewEvent.titleLabel.model])
		assertEquals(2, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(2, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Start", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[0][0])
		assertEquals("End", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[1][0])
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Full Description\n", (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).data[0][0])
//		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
		assertEquals(1, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Home\n", (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).data[0][0])
		assertEquals(false, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
	}

	@Test
	fun testEventViewAllDay() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		val viewEvent = app.viewEvent
		viewEvent.selectedEvent = calendarProvider.getEvents(2021, 11, 25)[0]

		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewEvent.state.id, 1, mapOf(4.toByte() to true))

		// check that data was filled in
		assertEquals("Holiday", mockServer.data[viewEvent.titleLabel.model])
		assertEquals(1, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(2, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Duration", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[0][0])
		assertEquals("All Day", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[0][1])
		assertEquals(0, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalColumns)
//		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
		assertEquals(0, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(false, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(false, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
	}

	@Test
	fun testEventAddress() {
		// address lookup works
		val address = mock<Address>()
		whenever(addressSearcher.search(any())) doReturn address

		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CalendarApp(iDriveConnectionStatus, securityAccess, carAppResources, calendarProvider, addressSearcher, navigationTrigger)
		val viewEvent = app.viewEvent
		viewEvent.selectedEvent = calendarProvider.getEvents(2021, 11, 11)[1]

		// check that the hmi event listener is set
		IDriveConnection.mockRemotingClient!!.rhmi_onHmiEvent(0, "", app.viewEvent.state.id, 1, mapOf(4.toByte() to true))

		// check that data was filled in
		assertEquals("C", mockServer.data[viewEvent.titleLabel.model])
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(1, (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Full Description\n", (mockServer.data[viewEvent.descriptionList.model] as BMWRemoting.RHMIDataTable).data[0][0])
//		assertEquals(false, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.ENABLED.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.descriptionList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])
		assertEquals(1, (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals("Home\n", (mockServer.data[viewEvent.locationList.model] as BMWRemoting.RHMIDataTable).data[0][0])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.ENABLED.id])      // address is clickable
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[viewEvent.locationList.id]!![RHMIProperty.PropertyId.SELECTABLE.id])

		// the Times list should include a new Navigate option
		assertEquals(3, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalRows)
		assertEquals(2, (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).totalColumns)
		assertEquals("Start", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[0][0])
		assertEquals("End", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[1][0])
		assertEquals("Navigate", (mockServer.data[viewEvent.timesList.model] as BMWRemoting.RHMIDataTable).data[2][0])
		// click the address
		IDriveConnection.mockRemotingClient?.rhmi_onActionEvent(0, "", viewEvent.locationList.getAction()!!.asRAAction()!!.id, mapOf(0.toByte() to 0))

		// navigation handler should be triggered
		verify(navigationTrigger).triggerNavigation(address)
	}
}