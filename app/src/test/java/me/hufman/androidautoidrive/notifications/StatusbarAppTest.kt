package me.hufman.androidautoidrive.notifications

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.carapp.notifications.ID5StatusbarApp
import me.hufman.androidautoidrive.carapp.notifications.ShowNotificationController
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.CarAppWidgetAssetResources
import me.hufman.androidautoidrive.carapp.L
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StatusbarAppTest {

	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}

	val images: ByteArray = ByteArrayOutputStream().apply {
		ZipOutputStream(this).apply {
			putNextEntry(ZipEntry("100.png"))
			write("".toByteArray())
			putNextEntry(ZipEntry("150.png"))
			write("56aaKV".toByteArray())       // envelope icon CRC32 collision
			close()
		}
	}.toByteArray()
	val carAppResources = mock<CarAppWidgetAssetResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_bmwone.xml") }
		on { getImagesDB(any()) } doAnswer { ByteArrayInputStream(images) }
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}
	val graphicsHelpers = mock<GraphicsHelpers> {
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer {"Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer {"Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray()}
	}
	val showNotificationController = mock<ShowNotificationController>()

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources, graphicsHelpers)

		assertEquals(null, mockServer.resources[BMWRemoting.RHMIResourceType.TEXTDB])
		assertArrayEquals(carAppResources.getImagesDB("")?.readBytes(), mockServer.resources[BMWRemoting.RHMIResourceType.IMAGEDB])

		val infoComponent = app.infoState.componentsList.filterIsInstance<RHMIComponent.List>().first()
		val infoList = mockServer.data[infoComponent.model] as BMWRemoting.RHMIDataTable
		assertEquals(L.NOTIFICATION_CENTER_APP + "\n", infoList.data[0][0])

		// verify that the popup is set up right
		val visiblePopupComponents = app.popupView.state.componentsList.filter {
			mockServer.properties[it.id]?.get(RHMIProperty.PropertyId.VISIBLE.id) as? Boolean ?: true
		}
		assertEquals(2, visiblePopupComponents.size)
		assertTrue(visiblePopupComponents[0] is RHMIComponent.List)
		assertTrue(visiblePopupComponents[1] is RHMIComponent.Button)
		assertEquals(app.popupView.imageId, (mockServer.data[visiblePopupComponents[1].asButton()?.imageModel] as BMWRemoting.RHMIResourceIdentifier).id)

		app.disconnect()
	}

	@Test
	fun testAppEntrybutton() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources, graphicsHelpers)

		val button = app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first()
		button.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0.toByte() to 0))
		assertEquals(app.infoState.id, mockServer.triggeredEvents[app.focusTriggerController.focusEvent.id]!![0.toByte()])
	}

	@Test
	fun testNotificationCenterController() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources, graphicsHelpers)
		val controller = app.statusbarController
		val event = app.notificationEvent

		// adds a notification to the car's Notification Center
		val sbn = CarNotification("test", "test", "Test AppName", null, true, emptyList(),
				"Title", "line1\nline2 yay",
				null, null, null, null, null)
		controller.add(sbn)
		assertEquals(true, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(0, event.getIndexId()?.asRaIntModel()?.value)
		assertEquals(150, event.getImageModel()?.asImageIdModel()?.imageId)     // envelope icon
		assertEquals("Title", event.getTitleTextModel()?.asRaDataModel()?.value)
		assertEquals("line2 yay", event.getNotificationTextModel()?.asRaDataModel()?.value)

		// updates the notification to the car's Notification Center
		val sbnUpdate = CarNotification("test", "test", "Test AppName",null, true, emptyList(),
				"Title", "line1\nline2 yay\nline3",
				null, null, null, null, null)
		controller.add(sbnUpdate)
		assertEquals(true, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(0, event.getIndexId()?.asRaIntModel()?.value)
		assertEquals(150, event.getImageModel()?.asImageIdModel()?.imageId)     // envelope icon
		assertEquals("Title", event.getTitleTextModel()?.asRaDataModel()?.value)
		assertEquals("line3", event.getNotificationTextModel()?.asRaDataModel()?.value)

		// adds a new notification
		val sbnSecond = CarNotification("test", "test2", "Test AppName", null, true, emptyList(),
				"Title2", "more test",
				null, null, null, null, null)
		controller.add(sbnSecond)
		assertEquals(true, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(1, event.getIndexId()?.asRaIntModel()?.value)
		assertEquals(150, event.getImageModel()?.asImageIdModel()?.imageId)     // envelope icon
		assertEquals("Title2", event.getTitleTextModel()?.asRaDataModel()?.value)
		assertEquals("more test", event.getNotificationTextModel()?.asRaDataModel()?.value)

		// removes one notification
		controller.remove(sbnUpdate)
		assertEquals(false, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(0, event.getIndexId()?.asRaIntModel()?.value)

		// removes the rest of the notifications (just the one remaining)
		controller.clear()
		assertEquals(false, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(1, event.getIndexId()?.asRaIntModel()?.value)
	}

	@Test
	fun testID5Popup() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources, graphicsHelpers)
		app.showNotificationController = showNotificationController
		val popupState = app.carApp.states.values.filterIsInstance<RHMIState.PopupState>().first()
		val popupView = app.popupView

		val sbn = CarNotification("test package", "asdf", "Test App Name", mock(),
				true, emptyList(), "Title", "Body\nBody 2",
				null, null, null, null, null)
		popupView.showNotification(sbn)

		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(true, mockServer.triggeredEvents[1]!![0])
		assertEquals("Test App Name", mockServer.data[popupState.textModel])
		val list = mockServer.data[popupState.componentsList.first().asList()?.model] as BMWRemoting.RHMIDataTable
		assertEquals(1, list.totalRows)
		assertArrayEquals("Drawable{48x48}".toByteArray(), list.data[0][0] as ByteArray)
		assertEquals("Title", list.data[0][1])
		assertEquals("Body 2", list.data[0][2])

		// assert focus event when clicked
		val callback = popupState.componentsList.filterIsInstance<RHMIComponent.Button>().first().getAction()?.asRAAction()?.rhmiActionCallback
		callback?.onActionEvent(mapOf(0.toByte() to null))
		val focusEvent = app.focusTriggerController.focusEvent
		verify(showNotificationController).showFromFocusEvent(popupView.currentNotification, true)

		// assert hide
		popupView.hideNotification()
		assertNotNull(mockServer.triggeredEvents[1])    // triggers the popupEvent
		assertEquals(false, mockServer.triggeredEvents[1]!![0])
	}
}