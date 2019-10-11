package me.hufman.androidautoidrive

import android.app.Notification
import android.app.Notification.FLAG_GROUP_SUMMARY
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
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import org.junit.Assert.*
import org.junit.Before
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
		on { getBitmap(isA<Drawable>(), any(), any(), any()) } doReturn ByteArray(0)
	}

	val carNotificationController = mock<CarNotificationController> {
	}

	init {
		AppSettings.loadDefaultSettings()
		SecurityService.activeSecurityConnections["mock"] = mock {
			on { signChallenge(any(), any() )} doReturn ByteArray(512)
		}
	}

	@Before
	fun setUp() {
		NotificationsState.notifications.clear()
		NotificationsState.poppedNotifications.clear()
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
				mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean
			}
			assertEquals(2, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertTrue(visibleWidgets[1] is RHMIComponent.List)
			assertEquals("Richtext", visibleWidgets[1].asList()?.getModel()?.modelType)
			assertEquals( 150, app.stateView.toolbarComponentsList[1].getImageModel()?.asImageIdModel()?.imageId)
			app.stateView.toolbarComponentsList.subList(2, 7).forEach {
				assertEquals(158, it.getImageModel()?.asImageIdModel()?.imageId)
			}
		}
		// test stateList setup
		run {
			val visibleWidgets = app.stateList.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean
			}
			assertEquals(1, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asCombinedAction()?.raAction?.rhmiActionCallback)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asRAAction()?.rhmiActionCallback)
		}
	}

	fun createNotification(tickerText:String, title:String?, text: String?, summary:String, clearable:Boolean=false): StatusBarNotification {
		val phoneNotification = mock<Notification> {
			on { getLargeIcon() } doReturn mock<Icon>()
			on { smallIcon } doReturn mock<Icon>()
		}
		phoneNotification.tickerText = tickerText
		phoneNotification.extras = mock<Bundle> {
			on { getCharSequence(eq(Notification.EXTRA_TITLE)) } doReturn title
			on { getCharSequence(eq(Notification.EXTRA_TEXT)) } doReturn text
			on { getCharSequence(eq(Notification.EXTRA_SUMMARY_TEXT)) } doReturn summary
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

	@Test
	fun testSummaryMessaging() {
		val message = mock<Bundle> {
			on { getCharSequence(eq("sender")) } doReturn "Sender"
			on { getCharSequence(eq("text")) } doReturn "Message"
		}
		val message2 = mock<Bundle> {
			on { getCharSequence(eq("sender")) } doReturn "Sender"
			on { getCharSequence(eq("text")) } doReturn "Message2"
		}
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		whenever(notification.notification.extras.getString(eq(Notification.EXTRA_TEMPLATE))) doReturn "android.app.Notification\$MessagingStyle"
		whenever(notification.notification.extras.getParcelableArray(eq(Notification.EXTRA_HISTORIC_MESSAGES))) doAnswer { null }
		whenever(notification.notification.extras.getParcelableArray(eq(Notification.EXTRA_MESSAGES))) doReturn arrayOf(
				message, message2
		)

		val notificationObject = NotificationListenerServiceImpl.summarizeNotification(notification)
		assertEquals("Sender: Message\nSender: Message2", notificationObject.text)
	}

	@Test
	fun testShouldShow() {
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		assertTrue(NotificationListenerServiceImpl.shouldShowNotification(notification))
		assertTrue(NotificationListenerServiceImpl.shouldPopupNotification(notification))

		val nullTitle = createNotification("Ticker Text", null, null, "Summary", true)
		assertFalse(NotificationListenerServiceImpl.shouldShowNotification(nullTitle))
		assertTrue(NotificationListenerServiceImpl.shouldPopupNotification(nullTitle))

		val musicApp = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		whenever(musicApp.notification.extras.getString(eq(Notification.EXTRA_TEMPLATE))) doReturn "android.app.Notification\$MediaStyle"
		assertTrue(NotificationListenerServiceImpl.shouldShowNotification(musicApp))
		assertFalse(NotificationListenerServiceImpl.shouldPopupNotification(musicApp))

		val groupNotification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		groupNotification.notification.flags = FLAG_GROUP_SUMMARY
		whenever(groupNotification.notification.group) doReturn "Yes"
		assertFalse(NotificationListenerServiceImpl.shouldShowNotification(groupNotification))
		assertFalse(NotificationListenerServiceImpl.shouldPopupNotification(groupNotification))
	}

	@Test
	fun testShouldPopupHistory() {
		// show new notifications
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		assertTrue(NotificationListenerServiceImpl.shouldPopupNotification(notification))

		// don't show the same notification twice
		NotificationsState.poppedNotifications.add(NotificationListenerServiceImpl.summarizeNotification(notification))
		assertFalse(NotificationListenerServiceImpl.shouldPopupNotification(notification))
		assertEquals(1, NotificationsState.poppedNotifications.size)

		// identical notifications should be coalesced in the history
		val duplicate = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		NotificationsState.poppedNotifications.add(NotificationListenerServiceImpl.summarizeNotification(duplicate))
		assertEquals(1, NotificationsState.poppedNotifications.size)

		// only should have 15 history entries
		(0..20).forEach { i ->
			val spam = createNotification("Ticker Text", "Title $i", "Text $i", "Summary", true)
			NotificationsState.poppedNotifications.add(NotificationListenerServiceImpl.summarizeNotification(spam))
		}
		assertEquals(15, NotificationsState.poppedNotifications.size)

		// it should have flushed out the earlier notification
		assertTrue(NotificationListenerServiceImpl.shouldPopupNotification(notification))
	}

	fun createNotificationObject(title:String, text:String, summary:String, clearable:Boolean=false): CarNotification {
		val actions = arrayOf(mock<Notification.Action> {
		})
		actions[0].title = "Custom Action"
		actions[0].actionIntent = mock()

		return CarNotification("me.hufman.androidautoidrive", "test$title", mock<Icon>(), clearable, actions,
				title, summary, text)
	}

	@Test
	fun testNewNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(carAppResources, phoneAppResources, carNotificationController)

		val bundle = createNotificationObject("Title", "Text", "Summary")

		app.notificationListener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		val expectedHeader = "Test AppName"
		val expectedLabel1 = "Title"
		val expectedLabel2 = "Text"
		assertEquals(expectedHeader, mockServer.data[404])
		assertEquals(expectedLabel1, mockServer.data[405])
		assertEquals(expectedLabel2, mockServer.data[406])
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
		mockClient.rhmi_onActionEvent(0, "don't care", 255, mapOf(0.toByte() to true))
		// and show the main list
		mockClient.rhmi_onHmiEvent(0, "don't care", 8, 1, mapOf(4.toByte() to true))

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
		val appTitleList = mockServer.data[519] as BMWRemoting.RHMIDataTable
		assertNotNull(appTitleList)
		assertEquals(1, appTitleList.numRows)
		assertEquals(3, appTitleList.numColumns)
		assertEquals("Test AppName", appTitleList.data[0][2])

		val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
		assertEquals(1, bodyList.numRows)
		assertEquals(1, bodyList.numColumns)
		assertEquals("Title2\nText2", bodyList.data[0][0])

		// verify the right buttons are enabled
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.ENABLED.id))  // clear this notification button
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.SELECTABLE.id))  // clear this notification button
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Clear", mockServer.data[523])  // clear this notification button
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.ENABLED.id))
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.SELECTABLE.id))
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Custom Action", mockServer.data[524])  // custom action button
		assertEquals(false, mockServer.properties[124]?.get(RHMIProperty.PropertyId.ENABLED.id))  // custom action button
		assertEquals(false, mockServer.properties[124]?.get(RHMIProperty.PropertyId.SELECTABLE.id))  // clear this notification button
		assertEquals(true, mockServer.properties[124]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals(null, mockServer.data[525])    // empty button

		// now try clicking the custom action
		callbacks.rhmi_onActionEvent(1, "Dont care", 330, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).action(notification2, notification2.actions[0].title.toString())
		// clicking the clear action
		callbacks.rhmi_onActionEvent(1, "Dont care", 326, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).clear(notification2)
		assertEquals("Returns to main list", app.stateList.id, mockServer.data[328])

		// test clicking surprise notifications
		// which are added to the NotificationsState without showing in the UI
		val statusbarNotificationSurprise = createNotificationObject("SurpriseTitle", "SurpriseText", "SurpriseSummary")
		NotificationsState.notifications.add(0, statusbarNotificationSurprise)
		app.notificationListView.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))   // clicks the first one
		assertEquals(notification, NotificationsState.selectedNotification)

	}
}
