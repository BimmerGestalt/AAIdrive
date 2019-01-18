package me.hufman.androidautoidrive

import android.app.Notification
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.notifications.*

import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import org.junit.Assert.*
import org.junit.Test

import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayInputStream

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class TestNotificationApp {

	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader.getResourceAsStream("ui_description_onlineservices_v1.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val phoneAppResources = mock<PhoneAppResources> {
		on { getAppName(any()) } doReturn "Test AppName"
		on { getAppIcon(any())} doReturn mock<Drawable>()
		on { getIconDrawable(any())} doReturn mock<Drawable>()
		on { getBitmap(any(), any(), any()) } doReturn ByteArray(0)
	}

	val carNotificationController = mock<CarNotificationController> {
	}

	init {
		SecurityService.activeSecurityConnections["mock"] = mock {
			on { signChallenge(any(), any() )} doReturn ByteArray(512)
		}
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		// test entry button
		run {
			val buttons = app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>()
			buttons.forEach {
				assertEquals(app.stateList.id, it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
			}
		}
		// test stateView setup
		run {
			val visibleWidgets = app.stateView.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIComponent.Property.VISIBLE.propertyId) as Boolean
			}
			assertEquals(1, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
		}
		// test stateList setup
		run {
			val visibleWidgets = app.stateList.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIComponent.Property.VISIBLE.propertyId) as Boolean
			}
			assertEquals(1, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asCombinedAction()?.raAction?.rhmiActionCallback)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asRAAction()?.rhmiActionCallback)
		}
	}

	fun createNotification(tickerText:String, title:String, text:String, summary:String, clearable:Boolean=false): StatusBarNotification {

		val phoneNotification = mock<Notification> {
			on { getLargeIcon() } doReturn mock<Icon>()
			on { smallIcon } doReturn mock<Icon>()
		}
		phoneNotification.tickerText = tickerText
		phoneNotification.extras = mock<Bundle> {
			on { getString(eq(Notification.EXTRA_TITLE)) } doReturn title
			on { getString(eq(Notification.EXTRA_TEXT)) } doReturn text
			on { getString(eq(Notification.EXTRA_SUMMARY_TEXT)) } doReturn summary
		}
		phoneNotification.actions = arrayOf(mock<Notification.Action> {
		})
		phoneNotification.actions[0].title = "Custom Action"
		phoneNotification.actions[0].actionIntent = mock()
		val statusbarNotification = mock<StatusBarNotification> {
			on { key } doReturn "testKey"
			on { notification } doReturn phoneNotification
			on { packageName } doReturn "me.hufman.androidautoidrive"
			on { isClearable } doReturn clearable
		}
		return statusbarNotification
	}

	@Test
	fun testSummary() {
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		val notificationObject = NotificationListenerServiceImpl.summarizeNotification(notification)
		assertEquals("testKey", notificationObject.key)
		assertEquals("me.hufman.androidautoidrive", notificationObject.packageName)
		assertEquals("Title", notificationObject.title)
		assertEquals("Text", notificationObject.text)
		assertEquals("Summary", notificationObject.summary)
		assertEquals(notification.notification.smallIcon, notificationObject.icon)
		assertTrue(notificationObject.isClearable)

		assertEquals(1, notificationObject.actions.size)
		assertEquals("Custom Action", notificationObject.actions[0].title)
	}

	fun createNotificationObject(title:String, text:String, summary:String, clearable:Boolean=false): CarNotification {

		val actions = arrayOf(mock<Notification.Action> {
		})
		actions[0].title = "Custom Action"
		actions[0].actionIntent = mock()

		return CarNotification("me.hufman.androidautoidrive", "test", mock<Icon>(), clearable, actions,
				title, summary, text)
	}

	@Test
	fun testNewNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		val bundle = createNotificationObject("Title", "Text", "Summary")

		app.notificationListener.listener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		val expectedLabel = "Test AppName\n" +
				"Title\n" +
				"Summary"
		assertEquals(expectedLabel, mockServer.data[405])
	}

	@Test
	fun testViewEmptyNotifications() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		NotificationsState.notifications.clear()
		app.updateNotificationList()

		val list = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertNotNull(list)
		assertEquals(1, list.numRows)
		val row = list.data[0]
		assertEquals("", row[0])
		assertEquals("", row[1])
		assertEquals("No Notifications", row[2])
	}

	@Test
	fun testViewNotifications() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		NotificationsState.notifications.clear()
		val statusbarNotification = createNotificationObject("Title", "Text", "Summary")
		NotificationsState.notifications.add(statusbarNotification)
		val statusbarNotification2 = createNotificationObject("Title2", "Text2", "Summary2")
		NotificationsState.notifications.add(statusbarNotification2)
		app.updateNotificationList()

		val list = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertNotNull(list)
		assertEquals(2, list.numRows)
		val row = list.data[0]
		assertArrayEquals(ByteArray(0), row[0] as? ByteArray)
		assertEquals("", row[1])
		assertEquals("Title\nSummary", row[2])
		val row2 = list.data[1]
		assertArrayEquals(ByteArray(0), row2[0] as? ByteArray)
		assertEquals("", row2[1])
		assertEquals("Title2\nSummary2", row2[2])
	}

	@Test
	fun testClickEntryButton() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		NotificationsState.notifications.clear()
		val statusbarNotification = createNotificationObject("Title", "Text", "Summary")
		NotificationsState.notifications.add(statusbarNotification)
		val statusbarNotification2 = createNotificationObject("Title2", "Text2", "Summary2")
		NotificationsState.notifications.add(statusbarNotification2)

		// test that we don't needlessly update the list until we actually view it
		run {
			val list = mockServer.data[386]
			assertNull(list)
		}

		// now click the entry button
		mockClient.rhmi_onActionEvent(0, "don't care", 255, mapOf(0 to true))

		// check that there are contents now
		run {
			val list = mockServer.data[386] as BMWRemoting.RHMIDataTable
			assertNotNull(list)
			assertEquals(2, list.numRows)
		}
	}

	@Test
	fun testClickNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		NotificationsState.notifications.clear()
		val notification = createNotificationObject("Title", "Text", "Summary", false)
		NotificationsState.notifications.add(notification)
		val notification2 = createNotificationObject("Title2", "Text2", "Summary2", true)
		NotificationsState.notifications.add(notification2)
		app.updateNotificationList()

		val notificationsList = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertNotNull(notificationsList)
		assertEquals(2, notificationsList.numRows)

		// user clicks a list entry
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onActionEvent(1, "Dont care", 161, mapOf(1.toByte() to 1))

		assertEquals(app.stateView.id, mockServer.data[163])
		assertEquals(notification2, NotificationsState.selectedNotification)

		// verify that the right information is shown
		val list = mockServer.data[519] as BMWRemoting.RHMIDataTable
		assertNotNull(list)
		assertEquals(3, list.numRows)
		assertEquals("Test AppName", list.data[0][2])
		assertEquals("Title2", list.data[1][2])
		assertEquals("Text2", list.data[2][2])
		assertEquals(true, mockServer.properties[122]?.get(RHMIComponent.Property.ENABLED.propertyId))  // clear this notification button
		assertEquals(true, mockServer.properties[122]?.get(RHMIComponent.Property.SELECTABLE.propertyId))  // clear this notification button
		assertEquals("Clear", mockServer.data[523])
		assertEquals(true, mockServer.properties[123]?.get(RHMIComponent.Property.ENABLED.propertyId))  // custom action button
		assertEquals(true, mockServer.properties[123]?.get(RHMIComponent.Property.SELECTABLE.propertyId))  // clear this notification button
		assertEquals("Custom Action", mockServer.data[524])

		// now try clicking the custom action
		callbacks.rhmi_onActionEvent(1, "Dont care", 330, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).action(notification2, notification2.actions[0]?.title.toString())
		callbacks.rhmi_onActionEvent(1, "Dont care", 326, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).clear(notification2)
		assertEquals("Returns to main list", app.stateList.id, mockServer.data[328])
	}
}
