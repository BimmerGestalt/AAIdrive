package me.hufman.androidautoidrive

import android.app.Notification
import android.app.Notification.FLAG_GROUP_SUMMARY
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationManagerCompat.IMPORTANCE_LOW
import android.support.v4.app.NotificationManagerCompat.IMPORTANCE_HIGH
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.notifications.*
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView

import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.io.ByteArrayInputStream

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestNotificationApp {

	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
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
		on { getUriDrawable(any())} doReturn mock<Drawable>()
	}

	val graphicsHelpers = mock<GraphicsHelpers> {
		on { isDark(any()) } doReturn false
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer {"Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer {"Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
	}

	val carNotificationController = mock<CarNotificationController> {
	}
	val appSettings = mock<MutableAppSettings>()

	init {
		AppSettings.loadDefaultSettings()
	}

	@Before
	fun setUp() {
		NotificationsState.notifications.clear()
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat")
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		// test entry button
		run {
			val buttons = app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>()
			buttons.forEach {
				assertEquals(app.viewList.state.id, it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
			}
		}
		// test stateView setup
		run {
			assertEquals(3, mockServer.properties[app.viewDetails.state.id]?.get(24))
			assertNull("Hasn't cleared Speedlock yet", mockServer.properties[app.viewDetails.state.id]?.get(36))
			val visibleWidgets = app.viewDetails.state.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean
			}
			assertEquals(3, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertTrue(visibleWidgets[1] is RHMIComponent.Label)
			assertTrue(visibleWidgets[2] is RHMIComponent.List)
			assertEquals("Richtext", visibleWidgets[2].asList()?.getModel()?.modelType)
			assertEquals(true, mockServer.properties[visibleWidgets[2].id]?.get(RHMIProperty.PropertyId.SELECTABLE.id))
			val state = app.viewDetails.state as RHMIState.ToolbarState
			assertEquals( 150, state.toolbarComponentsList[1].getImageModel()?.asImageIdModel()?.imageId)
			state.toolbarComponentsList.subList(2, 7).forEach {
				assertEquals(158, it.getImageModel()?.asImageIdModel()?.imageId)
			}
		}
		// test stateList setup
		run {
			assertEquals(3, mockServer.properties[app.viewList.state.id]?.get(24))
			val visibleWidgets = app.viewList.state.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean
			}
			assertEquals(3, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asCombinedAction()?.raAction?.rhmiActionCallback)
			assertNotNull(visibleWidgets[0].asList()?.getAction()?.asRAAction()?.rhmiActionCallback)
			assertTrue(visibleWidgets[1] is RHMIComponent.Label)
			assertEquals(true, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean)
			assertEquals(false, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.SELECTABLE.id) as Boolean)
			assertEquals(false, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.ENABLED.id) as Boolean)
			assertTrue(visibleWidgets[2] is RHMIComponent.List)
			assertNotNull(visibleWidgets[2].asList()?.getAction()?.asRAAction()?.rhmiActionCallback)
		}
		// test speedlock
		run {
			// parking gear
			mockServer.properties[app.viewDetails.state.id]?.remove(36)
			app.carappListener.cds_onPropertyChangedEvent(-1, "37", "driving.gear", """{"gear":3}""")
			assertEquals(false, mockServer.properties[app.viewDetails.state.id]?.get(36))
			// parking brake
			mockServer.properties[app.viewDetails.state.id]?.remove(36)
			app.carappListener.cds_onPropertyChangedEvent(-1, "40", "driving.parkingBrake", """{"parkingBrake":2}""")
			assertEquals(false, mockServer.properties[app.viewDetails.state.id]?.get(36))
		}
	}

	fun createNotification(tickerText:String, title:String?, text: String?, summary:String, clearable: Boolean=false, packageName: String="me.hufman.androidautoidrive"): StatusBarNotification {
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
			on { getPackageName() } doReturn packageName
			on { isClearable } doReturn clearable
		}
		return statusbarNotification
	}

	@Test
	fun testSummary() {
		val notification = createNotification("Ticker Text", "Title", "Text \uD83D\uDE3B\nTwo\n", "Summary", true)
		val notificationObject = ParseNotification.summarizeNotification(notification)
		assertEquals("testKey", notificationObject.key)
		assertEquals("me.hufman.androidautoidrive", notificationObject.packageName)
		assertEquals("Title", notificationObject.title)
		assertEquals("Text :heart_eyes_cat:\nTwo", notificationObject.text)
		assertNull(notificationObject.picture)
		assertEquals(notification.notification.smallIcon, notificationObject.icon)
		assertTrue(notificationObject.isClearable)

		assertEquals(1, notificationObject.actions.size)
		assertEquals("Custom Action", notificationObject.actions[0].title)

		whenever(notification.notification.extras.getParcelable<Bitmap>(eq(Notification.EXTRA_PICTURE))) doReturn mock<Bitmap>()
		val notificationImageObject = ParseNotification.summarizeNotification(notification)
		assertNotNull(notificationImageObject.picture)
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
		val message3 = mock<Bundle> {
			on { getCharSequence(eq("sender")) } doReturn "Sender"
			on { getCharSequence(eq("text")) } doReturn "Message3"
			on { getCharSequence(eq("type")) } doReturn "image/"
			on { getParcelable<Uri>(eq("uri")) } doReturn mock<Uri>()
		}
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		whenever(notification.notification.extras.getString(eq(Notification.EXTRA_TEMPLATE))) doReturn "android.app.Notification\$MessagingStyle"
		whenever(notification.notification.extras.getParcelableArray(eq(Notification.EXTRA_HISTORIC_MESSAGES))) doAnswer { null }
		whenever(notification.notification.extras.getParcelableArray(eq(Notification.EXTRA_MESSAGES))) doReturn arrayOf(
				message, message2, message3
		)

		val notificationObject = ParseNotification.summarizeNotification(notification)
		assertEquals("Sender: Message\nSender: Message2\nSender: Message3", notificationObject.text)
		assertNotNull(notificationObject.pictureUri)
	}

	@Test
	fun testSummaryEmptyText() {
		val notification = createNotification("Ticker Text", "Title", null, "Summary", true)
		val notificationObject = ParseNotification.summarizeNotification(notification)
		assertEquals("testKey", notificationObject.key)
		assertEquals("me.hufman.androidautoidrive", notificationObject.packageName)
		assertEquals("Title", notificationObject.title)
		assertEquals("Summary", notificationObject.text)
	}

	@Test
	fun testShouldShow() {
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		assertTrue(ParseNotification.shouldShowNotification(notification))
		assertTrue(ParseNotification.shouldPopupNotification(notification, null))

		val nullTitle = createNotification("Ticker Text", null, null, "Summary", true)
		assertFalse(ParseNotification.shouldShowNotification(nullTitle))
		assertTrue(ParseNotification.shouldPopupNotification(nullTitle, null))

		val musicApp = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		whenever(musicApp.notification.extras.getString(eq(Notification.EXTRA_TEMPLATE))) doReturn "android.app.Notification\$MediaStyle"
		assertTrue(ParseNotification.shouldShowNotification(musicApp))
		assertFalse(ParseNotification.shouldPopupNotification(musicApp, null))

		val groupNotification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		groupNotification.notification.flags = FLAG_GROUP_SUMMARY
		whenever(groupNotification.notification.group) doReturn "Yes"
		assertFalse(ParseNotification.shouldShowNotification(groupNotification))
		assertFalse(ParseNotification.shouldPopupNotification(groupNotification, null))

		val spotifyNotification = createNotification("Ticker", "Spotify", "AndroidAutoIdrive is connecting", "", true, "com.spotify.music")
		assertTrue(ParseNotification.shouldShowNotification(spotifyNotification))
		assertFalse(ParseNotification.shouldPopupNotification(spotifyNotification, null))

		// low priority notification handling
		val highPriorityRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn true
		}
		assertTrue(ParseNotification.shouldPopupNotification(notification, highPriorityRanking))

		val lowPriorityRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_LOW
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn true
		}
		assertFalse(ParseNotification.shouldPopupNotification(notification, lowPriorityRanking))

		val dndRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn false
		}
		assertFalse(ParseNotification.shouldPopupNotification(notification, dndRanking))

		val ambientRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn true
			on { matchesInterruptionFilter() } doReturn true
		}
		assertFalse(ParseNotification.shouldPopupNotification(notification, ambientRanking))
	}

	@Test
	fun testShouldPopupHistory() {
		val history = PopupHistory()

		// show new notifications
		val notification = ParseNotification.summarizeNotification(createNotification("Ticker Text", "Title", "Text", "Summary", true))
		assertFalse(history.contains(notification))

		// don't show the same notification twice
		history.add(notification)
		assertTrue(history.contains(notification))
		assertEquals(1, history.poppedNotifications.size)

		// identical notifications should be coalesced in the history
		val duplicate = ParseNotification.summarizeNotification(createNotification("Ticker Text", "Title", "Text", "Summary", true))
		history.add(duplicate)
		assertEquals(1, history.poppedNotifications.size)

		// updated notification should still be shown
		val updated = ParseNotification.summarizeNotification(createNotification("Ticker Text", "Title", "Text\nLine2", "Summary", true))
		assertFalse(history.contains(updated))
		history.add(updated)
		assertEquals(2, history.poppedNotifications.size)

		// only should have 15 history entries
		(0..20).forEach { i ->
			val spam = createNotification("Ticker Text", "Title $i", "Text $i", "Summary", true)
			history.add(ParseNotification.summarizeNotification(spam))
		}
		assertEquals(15, history.poppedNotifications.size)

		// it should have flushed out the earlier notification
		assertFalse(history.contains(notification))
	}

	fun createNotificationObject(title:String, text:String, clearable:Boolean=false,
	                             picture: Bitmap? = null, pictureUri: String? = null): CarNotification {
		val actions = arrayOf(mock<Notification.Action> {
		})
		actions[0].title = "Custom Action"
		actions[0].actionIntent = mock()

		return CarNotification("me.hufman.androidautoidrive", "test$title", mock<Icon>(), clearable, actions,
				title, text, picture, pictureUri)
	}

	@Test
	fun testPopupNewNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		val bundle = createNotificationObject("Title", "FirstLine\nText")

		app.notificationListener.onNotification(bundle)

		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent
		assertEquals(157, (mockServer.data[551] as BMWRemoting.RHMIResourceIdentifier).id)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		val expectedHeader = "Test AppName"
		val expectedLabel1 = "Title"
		val expectedLabel2 = "Text"
		assertEquals(expectedHeader, mockServer.data[404])
		assertEquals(expectedLabel1, mockServer.data[405])
		assertEquals(expectedLabel2, mockServer.data[406])
	}

	/**
	 * Don't popup if we are currently reading the relevant notification
	 */
	@Test
	fun testPopupExistingNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		val bundle = createNotificationObject("Title", "Text")

		// read the notification
		app.viewDetails.selectedNotification = bundle

		// it should not popup
		val bundle2 = createNotificationObject("Title", "Text\nNext Message")
		app.notificationListener.onNotification(bundle2)
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup

		// now hide the notification, and try to popup again
		app.viewDetails.state.focusCallback?.onFocus(false)
		app.notificationListener.onNotification(bundle2)
		assertNotNull(mockServer.triggeredEvents[1])    // did not trigger the popup
	}

	/**
	 * Close the popup if the notification disappears
	 */
	@Test
	fun testDismissPopup() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		val bundle = createNotificationObject("Title", "Text")

		NotificationsState.replaceNotifications(listOf(bundle))
		app.notificationListener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(true, mockServer.triggeredEvents[1]?.get(0))
		val expectedHeader = "Test AppName"
		val expectedLabel1 = "Title"
		val expectedLabel2 = "Text"
		assertEquals(expectedHeader, mockServer.data[404])
		assertEquals(expectedLabel1, mockServer.data[405])
		assertEquals(expectedLabel2, mockServer.data[406])

		// swipe it away
		NotificationsState.notifications.clear()
		app.notificationListener.updateNotificationList()
		assertEquals(false, mockServer.triggeredEvents[1]?.get(0))
	}

	/**
	 * Close the popup if the notification disappears
	 */
	@Test
	fun testPopupNotificationHistoryClearing() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		val bundle = createNotificationObject("Title", "Text")

		NotificationsState.notifications.add(bundle)
		app.notificationListener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(true, mockServer.triggeredEvents[1]?.get(0))
		val expectedHeader = "Test AppName"
		val expectedLabel1 = "Title"
		val expectedLabel2 = "Text"
		assertEquals(expectedHeader, mockServer.data[404])
		assertEquals(expectedLabel1, mockServer.data[405])
		assertEquals(expectedLabel2, mockServer.data[406])

		// verify that a second onNotification doesn't trigger
		mockServer.triggeredEvents.remove(1)
		app.notificationListener.onNotification(bundle)
		assertNull(mockServer.triggeredEvents[1])

		// verify that clearing the notification resets the history
		NotificationsState.notifications.clear()
		app.notificationListener.updateNotificationList()
		assertEquals(0, app.viewPopup.popupHistory.poppedNotifications.size)
		// it should trigger a popup now
		app.notificationListener.onNotification(bundle)
		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(true, mockServer.triggeredEvents[1]?.get(0))
	}

	@Test
	fun testViewEmptyNotifications() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		NotificationsState.notifications.clear()
		app.viewList.redrawNotificationList()

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
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)
		app.viewList.initWidgets(app.viewDetails)

		val item1 = createNotificationObject("Title", "Text")
		val item2 = createNotificationObject("Title2", "Text2\nLine2")
		val item3 = createNotificationObject("Title3", "")
		NotificationsState.replaceNotifications(listOf(item1, item2, item3))
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onHmiEvent(1, "unused", 8, 1, mapOf(4.toByte() to true))

		run {
			val list = mockServer.data[386] as BMWRemoting.RHMIDataTable
			assertNotNull(list)
			assertEquals(3, list.numRows)
			val row = list.data[0]
			assertArrayEquals("Drawable{48x48}".toByteArray(), row[0] as? ByteArray)
			assertEquals("", row[1])
			assertEquals("Title\nText", row[2])
			val row2 = list.data[1]
			assertArrayEquals("Drawable{48x48}".toByteArray(), row2[0] as? ByteArray)
			assertEquals("", row2[1])
			assertEquals("Title2\nLine2", row2[2])
			val row3 = list.data[2]
			assertArrayEquals("Drawable{48x48}".toByteArray(), row3[0] as? ByteArray)
			assertEquals("", row3[1])
			assertEquals("Title3\n", row3[2])
		}

		run {
			val label = mockServer.data[393]
			assertEquals("Options", label)
			val menu = mockServer.data[394] as BMWRemoting.RHMIDataTable
			assertEquals(2, menu.numRows)
			assertEquals(listOf("Notification Popups", "Popups with passenger"), menu.data.map { it[2] })
			verify(appSettings).callback = any()
		}
	}

	@Test
	fun testListSettings() {
		val rhmiApp = RHMIApplicationConcrete()
		rhmiApp.loadFromXML(carAppResources.getUiDescription()!!.readBytes())
		val state = rhmiApp.states[8]!!

		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++"), appSettings)
			val id4Menu = NotificationListView(state, phoneAppResources, graphicsHelpers, settings)
			id4Menu.initWidgets(mock())
			id4Menu.redrawNotificationList()

			val label = rhmiApp.modelData[393]
			assertEquals("Options", label)
			val menu = rhmiApp.modelData[394] as BMWRemoting.RHMIDataTable
			assertEquals(2, menu.numRows)
			assertEquals(listOf("Notification Popups", "Popups with passenger"), menu.data.map { it[2] })
		}

		rhmiApp.modelData.clear()
		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID5"), appSettings)
			val id5Menu = NotificationListView(state, phoneAppResources, graphicsHelpers, settings)
			id5Menu.initWidgets(mock())
			id5Menu.redrawNotificationList()

			val label = rhmiApp.modelData[393]
			assertEquals(null, label)   // never sets the
			val menu = rhmiApp.modelData[394] as BMWRemoting.RHMIDataTable
			assertEquals(0, menu.numRows)
		}
	}

	@Test
	fun testClickEntryButton() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val statusbarNotification = createNotificationObject("Title", "Text")
		val statusbarNotification2 = createNotificationObject("Title2", "Text2")
		NotificationsState.replaceNotifications(listOf(statusbarNotification, statusbarNotification2))

		// test that we don't needlessly update the list until we actually view it
		run {
			val list = mockServer.data[386]
			assertNull(list)
		}

		// now click the entry button
		mockClient.rhmi_onActionEvent(0, "don't care", 255, mapOf(0.toByte() to true))
		// and show the main list
		mockClient.rhmi_onHmiEvent(0, "don't care", 8, 1, mapOf(4.toByte() to true))

		// check that any statusbar icon is hidden
		assertFalse(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent

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
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		val notification = createNotificationObject("Title", "Text",false)
		val notification2 = createNotificationObject("Title2", "Text2\nTest3",true)
		NotificationsState.replaceNotifications(listOf(notification, notification2))

		// pretend that the car shows this window
		app.viewList.state.focusCallback?.onFocus(true)

		// verify that it redraws the list
		val notificationsList = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertNotNull(notificationsList)
		assertEquals(2, notificationsList.numRows)

		// user clicks a list entry
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onActionEvent(1, "Dont care", 161, mapOf(1.toByte() to 1))

		assertEquals(app.viewDetails.state.id, mockServer.data[163])
		assertEquals(notification2, app.viewDetails.selectedNotification)

		// the car shows the view state
		callbacks.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to true))

		// it should set the focus to the first button
		assertEquals(app.viewDetails.state.asToolbarState()?.toolbarComponentsList!![1].id, mockServer.triggeredEvents[5]!![0])

		// verify that the right information is shown
		val appTitleList = mockServer.data[519] as BMWRemoting.RHMIDataTable
		assertNotNull(appTitleList)
		assertEquals(1, appTitleList.numRows)
		assertEquals(3, appTitleList.numColumns)
		assertEquals("Test AppName", appTitleList.data[0][2])

		val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
		assertEquals(1, bodyList.numRows)
		assertEquals(1, bodyList.numColumns)
		assertEquals("Title2", mockServer.data[517])
		assertEquals("Text2\nTest3", bodyList.data[0][0])

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
		assertEquals("Returns to main list", app.viewList.state.id, mockServer.data[328])

		// test clicking surprise notifications
		// which are added to the NotificationsState without showing in the UI
		val statusbarNotificationSurprise = createNotificationObject("SurpriseTitle", "SurpriseText")
		NotificationsState.notifications.add(0, statusbarNotificationSurprise)
		app.viewList.notificationListView.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))   // clicks the first one
		assertEquals(notification, app.viewDetails.selectedNotification)

		// check the notification picture
		assertEquals(false, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertNull(mockServer.data[510])
		NotificationsState.notifications[0] = createNotificationObject("Title", "Text", picture = mock())
		app.viewDetails.redraw()
		assertEquals(true, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertArrayEquals("Bitmap{400x300}".toByteArray(), (mockServer.data[510] as BMWRemoting.RHMIResourceData).data as ByteArray)
		mockServer.data.remove(510)
		NotificationsState.notifications[0]  = createNotificationObject("Title", "Text", pictureUri="content:///")
		app.viewDetails.redraw()
		assertEquals(true, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertArrayEquals("Drawable{400x300}".toByteArray(), (mockServer.data[510] as BMWRemoting.RHMIResourceData).data as ByteArray)
	}

	@Test
	fun testClickMenu() {
		val mockServer = spy(MockBMWRemotingServer())
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)
		app.viewList.initWidgets(app.viewDetails)
		whenever(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP]) doReturn "true"
		whenever(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER]) doReturn "false"
		val appSettingsCallback = argumentCaptor<() -> Unit>()

		// the car shows the view state
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onHmiEvent(1, "unused", 8, 1, mapOf(4.toByte() to true))
		verify(appSettings).callback = appSettingsCallback.capture()

		// check the correct displayed entries
		run {
			val menu = mockServer.data[394] as BMWRemoting.RHMIDataTable
			assertEquals(2, menu.numRows)
			assertEquals(listOf("Notification Popups", "Popups with passenger"), menu.data.map { it[2] })
			val icon = menu.data[0][0] as BMWRemoting.RHMIResourceIdentifier
			assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon.type)
			assertEquals(150, icon.id)
			assertEquals("", menu.data[1][0])
		}

		// click a menu entry
		callbacks.rhmi_onActionEvent(1, "Dont care", 173, mapOf(1.toByte() to 1))
		verify(mockServer).rhmi_ackActionEvent(1, 173, 1, false)    // don't click to the next screen
		verify(appSettings)[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "true"
		whenever(appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER]) doReturn "true"

		// the callback should trigger because of the changed setting
		appSettingsCallback.lastValue.invoke()

		// check that the entries were updated
		run {
			val menu = mockServer.data[394] as BMWRemoting.RHMIDataTable
			assertEquals(2, menu.numRows)
			assertEquals(listOf("Notification Popups", "Popups with passenger"), menu.data.map { it[2] })
			val icon1 = menu.data[0][0] as BMWRemoting.RHMIResourceIdentifier
			assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon1.type)
			assertEquals(150, icon1.id)
			val icon2 = menu.data[1][0] as BMWRemoting.RHMIResourceIdentifier
			assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon2.type)
			assertEquals(150, icon2.id)
		}
	}

	@Test
	fun testViewEmptyNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, appSettings)

		NotificationsState.notifications.clear()
		val notification = createNotificationObject("Title", "Text", false)
		NotificationsState.notifications.add(notification)
		app.viewDetails.selectedNotification = notification

		// show the viewDetails
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to true))

		// verify that it shows the notification
		run {
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(1, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
			assertEquals("Title", mockServer.data[517])
			assertEquals("Text", bodyList.data[0][0])
		}

		// swap out the notification
		NotificationsState.notifications.clear()
		val notification2 = createNotificationObject("Title2", "Text", false)
		NotificationsState.notifications.add(notification2)
		app.viewDetails.selectedNotification = notification  // still viewing the old notification

		// now redraw the view
		app.viewDetails.redraw()
		// verify that it didn't change any labels
		run {
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(1, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
			assertEquals("Title", mockServer.data[517])
			assertEquals("Text", bodyList.data[0][0])
		}
		// it should trigger a transition to the main list
		assertEquals(app.viewList.state.id, mockServer.triggeredEvents[5]?.get(0))
	}
}
