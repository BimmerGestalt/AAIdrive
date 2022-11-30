package me.hufman.androidautoidrive.notifications

import android.app.Notification
import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.Person
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.carapp.notifications.*
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView
import me.hufman.androidautoidrive.utils.GraphicsHelpers

import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.carapp.L
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import java.io.ByteArrayInputStream
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NotificationAppTest {

	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_onlineservices_v1.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val phoneAppResources = mock<PhoneAppResources> {
		on { getAppName(any()) } doReturn "Test AppName"
		on { getAppIcon(any())} doReturn mock<Drawable>()
		on { getBitmapDrawable(any())} doReturn mock<Drawable>()
		on { getIconDrawable(any())} doReturn mock<Drawable>()
		on { getUriDrawable(any())} doReturn mock<Drawable>()
	}

	val graphicsHelpers = mock<GraphicsHelpers> {
		on { isDark(any()) } doReturn false
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer {"Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer {"Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
	}

	val carNotificationController = mock<CarNotificationController> {}
	val audioPlayer = mock<AudioPlayer>()
	val readoutController = mock<ReadoutController>()
	val appSettings = MockAppSettings()
	val notificationSettings = mock<NotificationSettings> {
		on { appSettings } doReturn appSettings
		on { getSettings() } doAnswer { listOf(
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP, AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER, AppSettings.KEYS.NOTIFICATIONS_SOUND,
				AppSettings.KEYS.NOTIFICATIONS_READOUT, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP, AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		)}
		on { quickReplies } doAnswer { listOf("\uD83D\uDE3B") }
		on { shouldPopup(any()) } doReturn true
		on { shouldPlaySound() } doReturn true
		on { shouldReadoutNotificationDetails() } doReturn true
		on { shouldReadoutNotificationPopup(any()) } doReturn true
	}

	/* A helper to set a constant value
	* From https://proandroiddev.com/build-version-in-unit-testing-4e963940dae7
	* */
	fun setFinalStatic(field: Field, newValue: Any) {
		field.setAccessible(true)

		val modifiersField = Field::class.java.getDeclaredField("modifiers")
		modifiersField.setAccessible(true)
		modifiersField.setInt(field, field.getModifiers() and Modifier.FINAL.inv())

		field.set(null, newValue)
	}

	@Before
	fun setUp() {
		NotificationsState.notifications.clear()
		NotificationsState.serviceConnected = true
		UnicodeCleaner._addPlaceholderEmoji("\u00A9", listOf("copyright"), "copyright")
		UnicodeCleaner._addPlaceholderEmoji("\u2714", listOf("heavy_check_mark"), "heavy_check_mark")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE00", listOf("grinning"), "grinning face")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC08", listOf("cat2"), "cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDE3B", listOf("heart_eyes_cat"), "heart_eyes_cat")
		UnicodeCleaner._addPlaceholderEmoji("\uD83D\uDC97", listOf("heartpulse"), "heartpulse")
	}

	@After
	fun tearDown() {
		setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 0)
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// test the AM button
		run {
			assertEquals(1, mockServer.amApps.size)
			assertEquals("androidautoidrive.notifications", mockServer.amApps[0])

			mockClient.am_onAppEvent(1, "1", mockServer.amApps[0], BMWRemoting.AMEvent.AM_APP_START)
			assertEquals(app.viewList.state.id, mockServer.triggeredEvents[app.focusTriggerController.focusEvent.id]?.get(0.toByte()))
		}
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
			assertEquals(4, visibleWidgets.size)
			assertTrue(visibleWidgets[0] is RHMIComponent.List)
			assertTrue(visibleWidgets[1] is RHMIComponent.List)
			assertTrue(visibleWidgets[2] is RHMIComponent.Separator)
			assertTrue(visibleWidgets[3] is RHMIComponent.List)
			assertEquals("Richtext", visibleWidgets[3].asList()?.getModel()?.modelType)
			assertEquals(true, mockServer.properties[visibleWidgets[3].id]?.get(RHMIProperty.PropertyId.SELECTABLE.id))
			val state = app.viewDetails.state as RHMIState.ToolbarState
			state.toolbarComponentsList.subList(1, 7).forEach {
				assertEquals(true, it.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
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
			assertEquals(true, mockServer.properties[visibleWidgets[0].id]?.get(RHMIProperty.PropertyId.BOOKMARKABLE.id))
			assertTrue(visibleWidgets[1] is RHMIComponent.Label)
			assertEquals(true, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as Boolean)
			assertEquals(false, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.SELECTABLE.id) as Boolean)
			assertEquals(false, mockServer.properties[visibleWidgets[1].id]?.get(RHMIProperty.PropertyId.ENABLED.id) as Boolean)
			assertTrue(visibleWidgets[2] is RHMIComponent.List)
			assertNotNull(visibleWidgets[2].asList()?.getAction()?.asRAAction()?.rhmiActionCallback)
		}
		// test statePermission setup
		run {
			val visibleWidgets = app.viewPermission.state.componentsList.filter {
				mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as? Boolean == true
			}
			assertEquals(1, visibleWidgets.size)
			val label = visibleWidgets[0].asLabel()!!
			assertFalse(mockServer.properties[label.id]?.get(RHMIProperty.PropertyId.ENABLED.id) as Boolean)
			assertEquals(L.NOTIFICATION_PERMISSION_NEEDED, label.getModel()?.asRaDataModel()?.value)
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

	@Suppress("DEPRECATION")
	fun createNotification(tickerText:String, title:String?, text: String?, summary:String, clearable: Boolean=false, packageName: String="me.hufman.androidautoidrive"): StatusBarNotification {
		val smallIconMock = mock<Icon>()
		val largeIconMock = mock<Icon>()
		val phoneNotification = mock<Notification> {
			on { getLargeIcon() } doReturn largeIconMock
			on { smallIcon } doReturn smallIconMock
		}
		phoneNotification.tickerText = tickerText
		phoneNotification.extras = mock<Bundle> {
			on { get(eq(Notification.EXTRA_TITLE)) } doReturn title
			on { getCharSequence(eq(Notification.EXTRA_TITLE)) } doReturn title
			on { getCharSequence(eq(Notification.EXTRA_TEXT)) } doReturn text
			on { getCharSequence(eq(Notification.EXTRA_SUMMARY_TEXT)) } doReturn summary
		}
		phoneNotification.sound = mock()
		phoneNotification.actions = arrayOf(mock(), mock(), mock())
		phoneNotification.actions[0].title = "Custom Action"
		phoneNotification.actions[0].actionIntent = mock()
		phoneNotification.actions[1].title = "      "       // don't include empty titles
		phoneNotification.actions[1].actionIntent = mock()
		phoneNotification.actions[2].title = null           // don't include null titles
		phoneNotification.actions[2].actionIntent = mock()

		return mock {
			on { key } doReturn "testKey"
			on { notification } doReturn phoneNotification
			on { getPackageName() } doReturn packageName
			on { isClearable } doReturn clearable
		}
	}

	@Test
	fun testSummary() {
		val notification = createNotification("Ticker Text", "Title", "Text \uD83D\uDE3B\nTwo\n", "Summary", true)
		val smallIconDrawable = mock<Drawable>()
		whenever(phoneAppResources.getIconDrawable(eq(notification.notification.smallIcon))) doReturn smallIconDrawable
		val notificationObject = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification)
		assertEquals("testKey", notificationObject.key)
		assertEquals("me.hufman.androidautoidrive", notificationObject.packageName)
		assertEquals("Title", notificationObject.title)
		assertEquals("Text :heart_eyes_cat:\nTwo", notificationObject.text)
		assertNull(notificationObject.picture)
		assertEquals(smallIconDrawable, notificationObject.icon)
		assertTrue(notificationObject.isClearable)

		assertEquals(1, notificationObject.actions.size)
		assertEquals("Custom Action", notificationObject.actions[0].name)

		// try parsing a picture
		val largeIcon = mock<Icon>()
		val picture = mock<Bitmap>()
		val largeIconDrawable = mock<Drawable>()
		val pictureDrawable = mock<Drawable>()
		whenever(notification.notification.extras.getParcelable<Bitmap>(eq(Notification.EXTRA_PICTURE))) doReturn picture
		whenever(notification.notification.extras.getParcelable<Icon>(eq(NotificationCompat.EXTRA_LARGE_ICON))) doReturn largeIcon
		whenever(notification.notification.extras.getParcelable<Icon>(eq(NotificationCompat.EXTRA_LARGE_ICON_BIG))) doReturn largeIcon
		whenever(phoneAppResources.getIconDrawable(eq(largeIcon))) doReturn largeIconDrawable
		whenever(phoneAppResources.getBitmapDrawable(eq(picture))) doReturn pictureDrawable
		val notificationImageObject = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification)
		assertEquals(largeIconDrawable, notificationImageObject.icon)
		assertEquals(pictureDrawable, notificationImageObject.picture)

		// make sure the dump method doesn't crash
		NotificationParser.dumpNotification("Title", notification, null)
		NotificationParser.dumpMessage("Title", notification.notification.extras)

		// try parsing a null icon
		val notification2 = createNotification("Ticker Text", "Title", "Text \uD83D\uDE3B\nTwo\n", "Summary", true)
		whenever(phoneAppResources.getIconDrawable(eq(notification2.notification.smallIcon))) doReturn null
		val notificationNullIcon = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification2)
		assertEquals("testKey", notificationNullIcon.key)
		assertEquals("me.hufman.androidautoidrive", notificationNullIcon.packageName)
		assertEquals("Title", notificationNullIcon.title)
		assertEquals("Text :heart_eyes_cat:\nTwo", notificationNullIcon.text)
		assertNull(notificationNullIcon.icon)
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
		val personIcon = mock<Icon>()
		val personIconDrawable = mock<Drawable>(name = "personIconDrawable")
		val person = mock<Person> {
			on { getIcon() } doReturn personIcon
		}
		val message3 = mock<Bundle> {
			on { getCharSequence(eq("sender")) } doReturn "Sender"
			on { getParcelable<Person>(eq("sender_person")) } doReturn person
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

		val smallIconDrawable = mock<Drawable>(name = "smallIconDrawable")
		val largeIconDrawable = mock<Drawable>(name = "largeIconDrawable")

		whenever(phoneAppResources.getIconDrawable(eq(personIcon))) doReturn personIconDrawable
		whenever(phoneAppResources.getIconDrawable(eq(notification.notification.smallIcon))) doReturn smallIconDrawable
		whenever(phoneAppResources.getIconDrawable(eq(notification.notification.getLargeIcon()))) doReturn largeIconDrawable

		run {       // API 26
			setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 26)
			val notificationObject = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification)
			assertEquals("Sender: Message\nSender: Message2\nSender: Message3", notificationObject.text)
			assertNotNull(notificationObject.pictureUri)
			assertEquals(smallIconDrawable, notificationObject.icon)     // don't load person icons from api < 28
		}
		run {       // API 28
			setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 28)
			val notificationObject = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification)
			assertEquals("Sender: Message\nSender: Message2\nSender: Message3", notificationObject.text)
			assertNotNull(notificationObject.pictureUri)
			assertEquals(personIconDrawable, notificationObject.icon)
		}
	}

	@Test
	fun testSummaryEmptyText() {
		val notification = createNotification("Ticker Text", "Title", null, "Summary", true)
		val notificationObject = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(notification)
		assertEquals("testKey", notificationObject.key)
		assertEquals("me.hufman.androidautoidrive", notificationObject.packageName)
		assertEquals("Title", notificationObject.title)
		assertEquals("Summary", notificationObject.text)
	}

	@Suppress("DEPRECATION")
	@Test
	fun testSummaryCustomView() {
		val phoneNotification = createNotification("Ticker Text", "Title", "Text", "Summary", false)
		val notification = phoneNotification.notification
		notification.bigContentView = mock()

		val label = mock<TextView> {
			on { isClickable } doReturn true
			on { text } doReturn "View Action"
		}
		val container = mock<ViewGroup> {
			on { childCount } doReturn 1
			on { getChildAt(any())} doReturn label
		}
		val parsed = NotificationParser(mock(), phoneAppResources, {container}).summarizeNotification(phoneNotification)

		assertEquals(1, parsed.actions.size)
		assertEquals("View Action", parsed.actions[0].name)
	}

	@Test
	fun testShouldShow() {
		val notification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldShowNotification(notification))
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(notification, null))

		val nullTitle = createNotification("Ticker Text", null, null, "Summary", true)
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldShowNotification(nullTitle))
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(nullTitle, null))

		val musicApp = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		whenever(musicApp.notification.extras.getString(eq(Notification.EXTRA_TEMPLATE))) doReturn "android.app.Notification\$MediaStyle"
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldShowNotification(musicApp))
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(musicApp, null))

		val groupNotification = createNotification("Ticker Text", "Title", "Text", "Summary", true)
		groupNotification.notification.flags = FLAG_GROUP_SUMMARY
		whenever(groupNotification.notification.group) doReturn "Yes"
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldShowNotification(groupNotification))
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(groupNotification, null))

		val spotifyNotification = createNotification("Ticker", "Spotify", "AndroidAutoIdrive is connecting", "", true, "com.spotify.music")
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldShowNotification(spotifyNotification))
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(spotifyNotification, null))

		// low priority notification handling
		setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 26)
		val highPriorityRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn true
		}
		assertTrue(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(notification, highPriorityRanking))

		val lowPriorityRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_LOW
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn true
		}
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(notification, lowPriorityRanking))

		val dndRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn false
			on { matchesInterruptionFilter() } doReturn false
		}
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(notification, dndRanking))

		val ambientRanking = mock<NotificationListenerService.Ranking> {
			on { importance } doReturn IMPORTANCE_HIGH
			on { isAmbient } doReturn true
			on { matchesInterruptionFilter() } doReturn true
		}
		assertFalse(NotificationParser(mock(), phoneAppResources, mock()).shouldPopupNotification(notification, ambientRanking))
	}

	@Test
	fun testShouldPopupHistory() {
		val history = PopupHistory()

		// show new notifications
		val notification = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(createNotification("Ticker Text", "Title", "Text", "Summary", true))
		assertFalse(history.contains(notification))

		// don't show the same notification twice
		history.add(notification)
		assertTrue(history.contains(notification))
		assertEquals(1, history.poppedNotifications.size)

		// identical notifications should be coalesced in the history
		val duplicate = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(createNotification("Ticker Text", "Title", "Text", "Summary", true))
		history.add(duplicate)
		assertEquals(1, history.poppedNotifications.size)

		// updated notification should still be shown
		val updated = NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(createNotification("Ticker Text", "Title", "Text\nLine2", "Summary", true))
		assertFalse(history.contains(updated))
		history.add(updated)
		assertEquals(2, history.poppedNotifications.size)

		// only should have 15 history entries
		(0..20).forEach { i ->
			val spam = createNotification("Ticker Text", "Title $i", "Text $i", "Summary", true)
			history.add(NotificationParser(mock(), phoneAppResources, mock()).summarizeNotification(spam))
		}
		assertEquals(15, history.poppedNotifications.size)

		// it should have flushed out the earlier notification
		assertFalse(history.contains(notification))
	}

	fun createNotificationObject(title:String, text:String, clearable:Boolean=false,
	                             sidePicture: Drawable? = null, picture: Drawable? = null, pictureUri: String? = null,
	                             actions: List<CarNotification.Action>? = null): CarNotification {
		val usedNotifications = actions ?: listOf(CarNotification.Action(
				"Custom Action \uD83D\uDC97", false, emptyList()
		))

		return CarNotification("me.hufman.androidautoidrive", "test$title", "Test AppName", mock(), clearable, usedNotifications,
				title, text, mock(), sidePicture, picture, pictureUri, mock())
	}

	@Test
	fun testPopupStatusbar() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController

		val bundle = createNotificationObject("Chat: Title", "Title: FirstLine\nTitle: Text")

		// show the statusbar icon when not popping
		whenever(notificationSettings.shouldPopup(any())) doReturn false
		app.notificationListener.onNotification(bundle)
		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent
		assertEquals(157, (mockServer.data[551] as BMWRemoting.RHMIResourceIdentifier).id)

		// plays the ringtone when showing statusbar icon
		verify(audioPlayer).playRingtone(any())

		// reads out the new notification
		verify(readoutController).readout(listOf("Chat: ", "Title: Text"))
		whenever(readoutController.isActive) doReturn true
		assertEquals(bundle, app.readoutInteractions.currentNotification)
	}

	@Test
	fun testPopupNewNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController

		val bundle = createNotificationObject("Chat: Title", "Title: FirstLine\nTitle: Text")

		// don't show the statusbar icon when popping
		app.notificationListener.onNotification(bundle)
		assertEquals(null, mockServer.triggeredEvents[4])       // does not trigger the notificationIconEvent

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		val expectedHeader = "Test AppName"
		val expectedLabel1 = "Chat: Title"
		val expectedLabel2 = "Title: Text"
		assertEquals(expectedHeader, mockServer.data[404])
		assertEquals(expectedLabel1, mockServer.data[405])
		assertEquals(expectedLabel2, mockServer.data[406])

		// plays the ringtone with the popup
		verify(audioPlayer).playRingtone(any())

		// reads out the popup
		verify(readoutController).readout(listOf("Chat: ", "Title: Text"))
		whenever(readoutController.isActive) doReturn true
		assertEquals(bundle, app.readoutInteractions.currentNotification)
	}

	/**
	 * Update the popup of the same notification
	 */
	@Test
	fun testPopupUpdatedNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		// posting the notification
		val bundle = createNotificationObject("Title", "Text")
		app.notificationListener.onNotification(bundle)
		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popup
		assertNull(mockServer.triggeredEvents[4])    // does not trigger the notificationIconEvent

		// system tells us that the screen is shown
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "114", "hmi.graphicalContext",
				"{\"graphicalContext\": { \"widgetType\": \"LT_Button_1Row_1CheckLeft_1IconLeft\" }}")

		// updating it should update the popup
		mockServer.triggeredEvents.clear()
		val bundle2 = createNotificationObject("Title", "Text\nLine2")
		app.notificationListener.onNotification(bundle2)
		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popup
		assertNull(mockServer.triggeredEvents[4])    // does not trigger the notificationIconEvent

		// system tells us that the screen is shown
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "114", "hmi.graphicalContext",
				"{\"graphicalContext\": { \"widgetType\": \"LT_Button_1Row_1CheckLeft_1IconLeft\" }}")

		// another notification should not update
		mockServer.triggeredEvents.clear()
		val bundle3 = createNotificationObject("A different", "Text")
		app.notificationListener.onNotification(bundle3)
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent

	}

	/**
	 * Don't popup if the phone was already showing the notification
	 */
	@Test
	fun testPopupExistingNotification() {
		val bundle = createNotificationObject("Title", "Text")
		NotificationsState.replaceNotifications(listOf(bundle))

		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		// it should not popup
		val bundle2 = createNotificationObject("Title", "Text")
		app.notificationListener.onNotification(bundle2)
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertNull(mockServer.triggeredEvents[4])    // did not trigger the statusbar icon
	}

	/**
	 * Don't popup if we are currently reading the relevant notification
	 */
	@Test
	fun testPopupReadingNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		val bundle = createNotificationObject("Title", "Text")

		// read the notification
		app.viewDetails.selectedNotification = bundle
		app.viewDetails.state.focusCallback?.onFocus(true)

		// opening the viewDetails hides the statusbar icon, so clear the event history
		mockServer.triggeredEvents.clear()

		// it should not popup, because it's the same conversation
		val bundle2 = createNotificationObject("Title", "Text\nNext Message")
		app.notificationListener.onNotification(bundle2)
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertNull(mockServer.triggeredEvents[4])    // did not trigger the statusbar icon

		// now hide the notification, and try to popup again
		// it should not popup because it's the same message
		app.viewDetails.state.focusCallback?.onFocus(false)
		app.notificationListener.onNotification(bundle2)
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup that we just read
		assertNull(mockServer.triggeredEvents[4])    // did not trigger the statusbar icon
	}

	/**
	 * Don't popup if we are currently inputting text
	 */
	@Test
	fun testPopupInputSuppress() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		val bundle = createNotificationObject("Title", "Text")

		// focus the Notification Reply input
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(app.rhmiHandle, "test", app.stateInput.id, 1, mapOf(4.toByte() to true))
		// new message
		app.notificationListener.onNotification(bundle)

		// it should not popup
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent

		// leave the Input
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(app.rhmiHandle, "test", app.stateInput.id, 1, mapOf(4.toByte() to false))

		// confirm that it does show afterward
		mockServer.triggeredEvents.clear()
		app.readHistory.retainAll(emptyList())
		app.notificationListener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popup
		assertNull(mockServer.triggeredEvents[4])    // does not trigger the notificationIconEvent

		// now try suppressing on system-wide input events
		mockServer.triggeredEvents.clear()
		app.readHistory.retainAll(emptyList())
		// user hides the popup
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(app.rhmiHandle, "test", app.carApp.events.values.filterIsInstance<RHMIEvent.PopupEvent>().first().target, 1, mapOf(4.toByte() to false))

		// a system-wide input
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "114", "hmi.graphicalContext",
		"{\"graphicalContext\": { \"widgetType\": \"LT_Speller_Normal\" }}")
		assertTrue(app.hmiContextChangedTime > 0)
		assertEquals("LT_Speller_Normal", app.hmiContextWidgetType)
		app.hmiContextChangedTime = 0       // don't rely on this to suppress, in this test

		// new message
		app.notificationListener.onNotification(bundle)

		// it should not popup
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent
	}

	/**
	 * Don't popup if we are currently interacting
	 */
	@Test
	fun testPopupInteractionSuppress() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		val bundle = createNotificationObject("Title", "Text")

		// a system-wide window update
		IDriveConnection.mockRemotingClient?.cds_onPropertyChangedEvent(1, "114", "hmi.graphicalContext",
				"{\"graphicalContext\": { \"widgetType\": \"LT_Button_1Row_1CheckLeft_1IconLeft\" }}")
		assertTrue(app.hmiContextChangedTime > 0)

		// new message
		app.notificationListener.onNotification(bundle)

		// it should not popup
		assertNull(mockServer.triggeredEvents[1])    // did not trigger the popup
		assertTrue(mockServer.triggeredEvents[4]?.get(0) as Boolean)    // triggers the notificationIconEvent

		// interaction times out
		app.hmiContextChangedTime -= 10000

		// try showing the notification now
		mockServer.triggeredEvents.clear()
		app.readHistory.retainAll(emptyList())
		app.notificationListener.onNotification(bundle)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popup
		assertNull(mockServer.triggeredEvents[4])    // does not trigger the notificationIconEvent
	}

	/**
	 * Close the popup if the notification disappears
	 */
	@Test
	fun testDismissPopup() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController

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

		// car has started speaking
		verify(readoutController).readout(any())
		whenever(readoutController.isActive) doReturn true

		// swipe it away
		NotificationsState.notifications.clear()
		app.notificationListener.onUpdatedList()
		assertEquals(false, mockServer.triggeredEvents[1]?.get(0))

		// make sure it cancels any readout
		verify(readoutController, times(1)).cancel()
	}

	/**
	 * Close the popup if the notification disappears
	 */
	@Test
	fun testPopupNotificationHistoryClearing() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

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
		app.notificationListener.onUpdatedList()
		assertEquals(0, app.readHistory.poppedNotifications.size)
		// it should trigger a popup now
		app.notificationListener.onNotification(bundle)
		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(true, mockServer.triggeredEvents[1]?.get(0))
	}

	/**
	 * Show a one-time popup about missing notification permissions
	 */
	@Test
	fun testViewNotificationsMissingPermission() {
		NotificationsState.serviceConnected = false

		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		NotificationsState.notifications.clear()

		// on viewing the state, it should skip to the permissions view
		app.viewList.state.focusCallback!!.onFocus(true)
		assertEquals(app.viewPermission.state.id, mockServer.triggeredEvents[app.focusTriggerController.focusEvent.id]?.get(0.toByte()))
		assertNull(mockServer.data[386])

		// doing it again should not skip through, and should draw the list like normal
		mockServer.triggeredEvents.clear()
		app.viewList.state.focusCallback?.onFocus(true)

		val list = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertNotNull(list)
		assertEquals(1, list.numRows)
		val row = list.data[0]
		assertEquals("", row[0])
		assertEquals("", row[1])
		assertEquals("No Notifications", row[2])
	}

	@Test
	fun testViewEmptyNotifications() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

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
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

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
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
			verify(notificationSettings).callback = any()
		}
	}

	@Test
	fun testListSettings() {
		val rhmiApp = RHMIApplicationConcrete()
		rhmiApp.loadFromXML(carAppResources.getUiDescription()!!.readBytes())
		val state = rhmiApp.states[8]!!
		val appSettings = MockAppSettings()

		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID4++", "tts" to "true"), mock(), appSettings)
			val id4Menu = NotificationListView(state, graphicsHelpers, settings, mock(), mock(), mock())
			id4Menu.initWidgets(mock(), mock())
			id4Menu.redrawNotificationList()
			id4Menu.redrawSettingsList()

			val label = rhmiApp.modelData[393]
			assertEquals("Options", label)
			assertEquals(true, rhmiApp.propertyData[id4Menu.settingsListView.id]!![RHMIProperty.PropertyId.VISIBLE.id])
			val menu = rhmiApp.modelData[394] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
		}

		rhmiApp.modelData.clear()
		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID5", "tts" to "true"), mock(), appSettings)
			val id5Menu = NotificationListView(state, graphicsHelpers, settings, mock(), mock(), mock())
			id5Menu.initWidgets(mock(), mock())
			id5Menu.redrawNotificationList()
			id5Menu.redrawSettingsList()

			val label = rhmiApp.modelData[393]
			assertEquals("Options", label)
			assertEquals(true, rhmiApp.propertyData[id5Menu.settingsListView.id]!![RHMIProperty.PropertyId.VISIBLE.id])
			val menu = rhmiApp.modelData[394] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER,
					L.NOTIFICATION_SOUND, L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
		}

		rhmiApp.modelData.clear()
		run {
			val settings = NotificationSettings(mapOf("hmi.type" to "MINI ID5", "tts" to "false"), mock(), appSettings)
			val id5Menu = NotificationListView(state, graphicsHelpers, settings, mock(), mock(), mock())
			id5Menu.initWidgets(mock(), mock())
			id5Menu.redrawNotificationList()
			id5Menu.redrawSettingsList()

			val label = rhmiApp.modelData[393]
			assertEquals("Options", label)
			assertEquals(true, rhmiApp.propertyData[id5Menu.settingsListView.id]!![RHMIProperty.PropertyId.VISIBLE.id])
			val menu = rhmiApp.modelData[394] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND), menu.data.map { it[2] })
		}
	}

	@Test
	fun testClickEntryButton() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController
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

		// showing the list without a reading-out notification cancels the readout
		// assert the times(1) so that we can assert times(2) next
		verify(readoutController, times(1)).cancel()

		// check whether it shortcuts to the reading-out notification
		app.readoutInteractions.currentNotification = statusbarNotification2
		whenever(readoutController.isActive) doReturn true
		mockClient.rhmi_onHmiEvent(0, "don't care", 8, 1, mapOf(4.toByte() to true))
		assertEquals(app.viewDetails.state.id, mockServer.triggeredEvents[5]?.get(0.toByte()))

		// check that it cancels the reading-out when pushing back
		mockServer.triggeredEvents.remove(5)
		app.viewList.entryButtonTimestamp -= 3000   // user didn't push Entrybutton recently, but the list becomes visible
		mockClient.rhmi_onHmiEvent(0, "don't care", 8, 1, mapOf(4.toByte() to true))
		assertNull(app.readoutInteractions.currentNotification)
		assertNull(mockServer.triggeredEvents[5])
		verify(readoutController, times(2)).cancel()

		// check that it does not shortcut through if the readout has finished
		app.readoutInteractions.currentNotification = statusbarNotification2
		whenever(readoutController.isActive) doReturn false
		mockClient.rhmi_onHmiEvent(0, "don't care", 8, 1, mapOf(4.toByte() to true))
		assertNull(mockServer.triggeredEvents[5])

	}

	@Test
	fun testClickNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController

		val notification = createNotificationObject("Title", "Text",false)
		val notification2 = createNotificationObject("Title2", "Title2: Text2\nTest3",true)
		NotificationsState.replaceNotifications(listOf(notification, notification2))

		// pretend that the car shows this window
		app.viewList.state.focusCallback?.onFocus(true)

		// verify that it redraws the list
		val notificationsList = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertEquals(2, notificationsList.numRows)

		// user clicks a list entry
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onActionEvent(1, "Dont care", 161, mapOf(1.toByte() to 1))

		assertEquals(app.viewDetails.state.id, mockServer.data[163])
		assertEquals(notification2, app.viewDetails.selectedNotification)

		// the car shows the view state
		callbacks.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to true))

		// it should set the focus to the first button
		assertEquals(app.viewDetails.state.asToolbarState()?.toolbarComponentsList!![1].id, mockServer.triggeredEvents[5]!![0.toByte()])

		// verify that the right information is shown
		val appTitleList = mockServer.data[519] as BMWRemoting.RHMIDataTable
		assertNotNull(appTitleList)
		assertEquals(1, appTitleList.numRows)
		assertEquals(3, appTitleList.numColumns)
		assertEquals("Test AppName", appTitleList.data[0][2])

		val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
		val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
		assertEquals(1, titleList.numRows)
		assertEquals(2, titleList.numColumns)
		assertEquals("Title2", titleList.data[0][1])
		assertEquals(1, bodyList.numRows)
		assertEquals(1, bodyList.numColumns)
		assertEquals("Title2: Text2\nTest3", bodyList.data[0][0])

		// verify the right buttons are enabled
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.ENABLED.id))  // clear this notification button
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.SELECTABLE.id))  // clear this notification button
		assertEquals(true, mockServer.properties[122]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Clear", mockServer.data[523])  // clear this notification button
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.ENABLED.id))
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.SELECTABLE.id))
		assertEquals(true, mockServer.properties[123]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Custom Action  ♥ ", mockServer.data[524])  // custom action button
		assertEquals(false, mockServer.properties[124]?.get(RHMIProperty.PropertyId.ENABLED.id))  // custom action button
		assertEquals(false, mockServer.properties[124]?.get(RHMIProperty.PropertyId.SELECTABLE.id))  // clear this notification button
		assertEquals(true, mockServer.properties[124]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals(null, mockServer.data[525])    // empty button

		// it should start reading out the notification
		verify(readoutController).readout(listOf("Title2: Text2", "Test3"))

		// it should not cancel readout if the list is updated
		reset(readoutController)
		whenever(readoutController.isActive) doReturn true
		app.notificationListener.onUpdatedList()
		verify(readoutController, never()).cancel()

		// now try clicking the custom action
		callbacks.rhmi_onActionEvent(1, "Dont care", 330, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).action(notification2.key, notification2.actions[0].name.toString())
		// clicking the clear action
		callbacks.rhmi_onActionEvent(1, "Dont care", 326, mapOf(0.toByte() to 1))
		verify(carNotificationController, times(1)).clear(notification2.key)
		assertEquals("Returns to main list", app.viewList.state.id, mockServer.data[328])

		// test clicking surprise notifications
		// which are added to the NotificationsState without showing in the UI
		val statusbarNotificationSurprise = createNotificationObject("SurpriseTitle", "SurpriseText")
		NotificationsState.notifications.add(0, statusbarNotificationSurprise)
		app.viewList.notificationListView.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))   // clicks the first one
		assertEquals(notification, app.viewDetails.selectedNotification)

		// check the notification picture
		val picture = mock<Drawable> {
			on { intrinsicWidth } doReturn 800
			on { intrinsicHeight } doReturn 600
		}
		assertEquals(false, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertNull(mockServer.data[510])
		NotificationsState.notifications[0] = createNotificationObject("Title", "Text", picture = picture)
		app.viewDetails.redraw()
		assertEquals(true, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Drawable{400x300}", String((mockServer.data[510] as BMWRemoting.RHMIResourceData).data as ByteArray))

		// check a notification pictureUri
		val drawable = mock<Drawable> {
			on { intrinsicWidth } doReturn 800
			on { intrinsicHeight } doReturn 600
		}
		whenever(phoneAppResources.getUriDrawable(any())) doReturn drawable
		mockServer.data.remove(510)
		NotificationsState.notifications[0]  = createNotificationObject("Title", "Text", pictureUri="content:///")
		app.viewDetails.redraw()
		assertEquals(true, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		assertEquals("Drawable{400x300}", String((mockServer.data[510] as BMWRemoting.RHMIResourceData).data as ByteArray))
	}

	@Test
	fun testBookmarkNotificationMenu() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val notification = createNotificationObject("Title", "Text", false)
		val notification2 = createNotificationObject("Title2", "Title2: Text2\nTest3", true)
		NotificationsState.replaceNotifications(listOf(notification2))

		// pretend that the car shows this menu, to generate the list
		app.viewList.state.focusCallback?.onFocus(true)

		val notificationsList = mockServer.data[386] as BMWRemoting.RHMIDataTable
		assertEquals(1, notificationsList.numRows)

		// click one of them
		callbacks.rhmi_onActionEvent(1, "Dont care", 161, mapOf(1.toByte() to 0))
		assertEquals(app.viewDetails.state.id, mockServer.data[163])
		assertEquals(notification2, app.viewDetails.selectedNotification)

		// shows the details view
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewList.state.id, 1, mapOf(4.toByte() to false))
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewDetails.state.id, 1, mapOf(4.toByte() to true))

		val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
		assertEquals("Title2", titleList.data[0][1])

		// user leaves
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewDetails.state.id, 1, mapOf(4.toByte() to false))
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewDetails.state.id, 11, mapOf(4.toByte() to false))

		// clears the display
		val emptyTitleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
		assertEquals(0, emptyTitleList.totalRows)

		// new notifications have shown up
		NotificationsState.replaceNotifications(listOf(notification, notification2))

		// user clicks bookmark into the list
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewList.state.id, 1, mapOf(4.toByte() to true))
		callbacks.rhmi_onActionEvent(1, "Dont care", 161, mapOf(1.toByte() to 0, 43.toByte() to 2))

		// it technically clicked the 0th item, but don't change the selectedNotification
		assertEquals(notification2, app.viewDetails.selectedNotification)

		// show the details view
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewList.state.id, 1, mapOf(4.toByte() to false))
		callbacks.rhmi_onHmiEvent(1, "unused", app.viewDetails.state.id, 1, mapOf(4.toByte() to true))

		val newTitleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
		assertEquals("Title2", newTitleList.data[0][1])
	}

	@Test
	fun testViewSidePicture() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		val picture = mock<Drawable> {
			on { intrinsicWidth } doReturn 400
			on { intrinsicHeight } doReturn 300
		}
		val notification = createNotificationObject("Title", "Text",true, sidePicture = picture)
		NotificationsState.notifications.add(0, notification)
		app.viewDetails.selectedNotification = notification
		app.viewDetails.visible = true
		app.viewDetails.show()

		val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
		val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
		assertEquals(1, titleList.numRows)
		assertEquals(2, titleList.numColumns)
		val sidePicture = titleList.data[0][0] as BMWRemoting.RHMIResourceData
		assertEquals("Drawable{128x96}", String(sidePicture.data as ByteArray))
		assertEquals("Title\n", titleList.data[0][1])
		assertEquals(1, bodyList.numRows)
		assertEquals(1, bodyList.numColumns)
		assertEquals("Text", bodyList.data[0][0])
	}

	@Test
	fun testViewReplyNotification() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		val actions = listOf(
			CarNotification.Action("Reply", true, listOf("Yes", "No")),
			CarNotification.Action("Mark Read", false, emptyList())
		)
		val notification = createNotificationObject("Title", "Text",true, actions = actions)
		NotificationsState.notifications.add(0, notification)
		app.viewDetails.selectedNotification = notification
		app.viewDetails.visible = true
		app.viewDetails.show()

		// verify the right buttons are enabled
		val buttons = app.viewDetails.state.asToolbarState()!!.toolbarComponentsList.subList(1, 7)
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)  // clear this notification button
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)  // clear this notification button
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 150, buttons[0].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Clear", buttons[0].getTooltipModel()?.asRaDataModel()?.value)  // clear this notification button
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 158, buttons[1].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Reply", buttons[1].getTooltipModel()?.asRaDataModel()?.value)  // reply button
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 158, buttons[2].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Mark Read", buttons[2].getTooltipModel()?.asRaDataModel()?.value)  // custom action button
		assertEquals(false, buttons[3].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)  // empty button
		assertEquals(false, buttons[3].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)
		assertEquals(true, buttons[3].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals("", buttons[3].getTooltipModel()?.asRaDataModel()?.value)    // empty button

		buttons[2].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.viewList.state.id, buttons[2].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.stateInput.id, buttons[1].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		buttons[0].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.viewList.state.id, buttons[0].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)

		val inputComponent = app.stateInput.componentsList[0] as RHMIComponent.Input

		// show the suggested replies
		app.stateInput.focusCallback?.onFocus(true) // shown to user
		assertEquals(listOf("Yes", "No", ":heart_eyes_cat:"), (mockServer.data[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(carNotificationController).reply(notification.key, notification.actions[0].name.toString(), "No")
		reset(carNotificationController)

		// the user goes back and accidentally clicks to send again, but it shouldn't send again
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
		verify(carNotificationController, never()).reply(any(), any(), any())

		// show the user's input
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "Spoken text"))
		assertEquals(listOf("Spoken text"), (mockServer.data[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		verify(carNotificationController).reply(notification.key, notification.actions[0].name.toString(), "Spoken text")

		// Add a colon, should show emoji suggestions
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "Spoken text :hea"))
		assertEquals(listOf("Spoken text :hea", "Spoken text :heavy_check_mark:", "Spoken text :heart_eyes_cat:", "Spoken text :heartpulse:"), (mockServer.data[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(carNotificationController).reply(notification.key, notification.actions[0].name.toString(), "Spoken text \uD83D\uDE3B")

		// erase, should show the suggestions again
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		inputComponent.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(8.toByte() to "delall"))
		assertEquals(listOf("Yes", "No", ":heart_eyes_cat:"), (mockServer.data[inputComponent.suggestModel] as BMWRemoting.RHMIDataTable).data.map { it[0] })
		inputComponent.getSuggestAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 0))
		verify(carNotificationController).reply(notification.key, notification.actions[0].name.toString(), "Yes")
	}

	@Test
	fun testActionReadout() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)
		app.readoutInteractions.readoutController = readoutController

		whenever(notificationSettings.shouldReadoutNotificationDetails()) doReturn false

		val actions = listOf(
				CarNotification.Action("Mark Read", false, emptyList())
		)
		val notification = createNotificationObject("Title", "Title: Text\nTest",true, actions = actions)
		NotificationsState.notifications.add(0, notification)
		app.viewDetails.selectedNotification = notification
		app.viewDetails.visible = true
		app.viewDetails.show()

		// verify the right buttons are enabled
		val buttons = app.viewDetails.state.asToolbarState()!!.toolbarComponentsList.subList(1, 7)
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)  // clear this notification button
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)  // clear this notification button
		assertEquals(true, buttons[0].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 150, buttons[0].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Clear", buttons[0].getTooltipModel()?.asRaDataModel()?.value)  // clear this notification button
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)  // clear this notification button
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)  // clear this notification button
		assertEquals(true, buttons[1].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 154, buttons[1].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Speak", buttons[1].getTooltipModel()?.asRaDataModel()?.value)  // clear this notification button
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)
		assertEquals(true, buttons[2].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals( 158, buttons[2].getImageModel()?.asImageIdModel()?.imageId)
		assertEquals("Mark Read", buttons[2].getTooltipModel()?.asRaDataModel()?.value)  // custom action button
		assertEquals(false, buttons[3].properties[RHMIProperty.PropertyId.ENABLED.id]?.value)  // empty button
		assertEquals(false, buttons[3].properties[RHMIProperty.PropertyId.SELECTABLE.id]?.value)
		assertEquals(true, buttons[3].properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
		assertEquals("", buttons[3].getTooltipModel()?.asRaDataModel()?.value)    // empty button

		buttons[2].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.viewList.state.id, buttons[2].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		buttons[1].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.viewDetails.state.id, buttons[1].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		buttons[0].getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(emptyMap<Int, Any>())
		assertEquals(app.viewList.state.id, buttons[0].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value)
		// it should have start reading out the notification
		verify(readoutController).readout(listOf("Title: Text", "Test"))
	}

	@Test
	fun testClickMenu() {
		val mockServer = spy(MockBMWRemotingServer())
		IDriveConnection.mockRemotingServer = mockServer

		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = "true"
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "false"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = "false"

		// the car shows the view state
		val callbacks = IDriveConnection.mockRemotingClient as BMWRemotingClient
		callbacks.rhmi_onHmiEvent(1, "unused", 8, 1, mapOf(4.toByte() to true))
		val appSettingsCallback = argumentCaptor<() -> Unit>()
		verify(notificationSettings).callback = appSettingsCallback.capture()

		// check the correct displayed entries
		run {
			val menu = mockServer.data[394] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
			val icon = menu.data[0][0] as BMWRemoting.RHMIResourceIdentifier
			assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon.type)
			assertEquals(150, icon.id)
			assertEquals("", menu.data[1][0])
		}

		// click a menu entry
		callbacks.rhmi_onActionEvent(1, "Dont care", 173, mapOf(1.toByte() to 1))
		verify(mockServer).rhmi_ackActionEvent(1, 173, 1, false)    // don't click to the next screen
		assertEquals("true", appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER])

		// the callback should trigger because of the changed setting
		appSettingsCallback.lastValue.invoke()

		// check that the entries were updated
		run {
			val menu = mockServer.data[394] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
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
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		NotificationsState.notifications.clear()
		val notification = createNotificationObject("Title", "Text", false)
		NotificationsState.notifications.add(notification)
		app.viewDetails.selectedNotification = notification

		// show the viewDetails
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to true))

		// verify that it shows the notification
		run {
			val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(1, titleList.numRows)
			assertEquals(2, titleList.numColumns)
			assertEquals("", titleList.data[0][0])
			assertEquals("Title", titleList.data[0][1])
			assertEquals(1, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
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
			val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(1, titleList.numRows)
			assertEquals(2, titleList.numColumns)
			assertEquals("Title", titleList.data[0][1])
			assertEquals(1, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
			assertEquals("Text", bodyList.data[0][0])
		}
		// it should trigger a transition to the main list
		assertEquals(app.viewList.state.id, mockServer.triggeredEvents[5]?.get(0.toByte()))
	}

	@Test
	fun testHideNotificationView() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = PhoneNotifications(iDriveConnectionStatus, securityAccess, carAppResources, phoneAppResources, graphicsHelpers, carNotificationController, audioPlayer, notificationSettings)

		NotificationsState.notifications.clear()
		val notification = createNotificationObject("Title", "Text", false)
		NotificationsState.notifications.add(notification)
		app.viewDetails.selectedNotification = notification

		// show the viewDetails
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to true))

		// verify that it shows the notification
		run {
			val appTitleList = mockServer.data[519] as BMWRemoting.RHMIDataTable
			val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(1, appTitleList.numRows)
			assertEquals(3, appTitleList.numColumns)
			assertEquals("Test AppName", appTitleList.data[0][2])
			assertEquals(1, titleList.numRows)
			assertEquals(2, titleList.numColumns)
			assertEquals("", titleList.data[0][0])
			assertEquals("Title", titleList.data[0][1])
			assertEquals(1, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
			assertEquals("Text", bodyList.data[0][0])
		}

		// hide the viewDetails
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(1, "unused", 20, 1, mapOf(4.toByte() to false))
		IDriveConnection.mockRemotingClient?.rhmi_onHmiEvent(1, "unused", 20, 11, mapOf(4.toByte() to false))

		// verify that it shows the notification
		run {
			val appTitleList = mockServer.data[519] as BMWRemoting.RHMIDataTable
			val titleList = mockServer.data[520] as BMWRemoting.RHMIDataTable
			val bodyList = mockServer.data[521] as BMWRemoting.RHMIDataTable
			assertEquals(0, appTitleList.numRows)
			assertEquals(1, appTitleList.numColumns)
			assertEquals(0, titleList.numRows)
			assertEquals(1, titleList.numColumns)
			assertEquals(0, bodyList.numRows)
			assertEquals(1, bodyList.numColumns)
			assertEquals(false, mockServer.properties[120]?.get(RHMIProperty.PropertyId.VISIBLE.id))
		}
	}
}
