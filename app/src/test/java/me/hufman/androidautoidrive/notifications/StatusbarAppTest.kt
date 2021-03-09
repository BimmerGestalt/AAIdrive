package me.hufman.androidautoidrive.notifications

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.carapp.notifications.ID5StatusbarApp
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
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

	val images = ByteArrayOutputStream().apply {
		ZipOutputStream(this).apply {
			putNextEntry(ZipEntry("100.png"))
			write("".toByteArray())
			putNextEntry(ZipEntry("150.png"))
			write("56aaKV".toByteArray())       // envelope icon CRC32 collision
			close()
		}
	}.toByteArray()
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_bmwone.xml") }
		on { getImagesDB(any()) } doAnswer { ByteArrayInputStream(images) }
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources)

		assertEquals(null, mockServer.resources[BMWRemoting.RHMIResourceType.TEXTDB])
		assertArrayEquals(carAppResources.getImagesDB("")?.readBytes(), mockServer.resources[BMWRemoting.RHMIResourceType.IMAGEDB])

		val infoComponent = app.infoState.componentsList.filterIsInstance<RHMIComponent.List>().first()
		val infoList = mockServer.data[infoComponent.model] as BMWRemoting.RHMIDataTable
		assertEquals(L.NOTIFICATION_CENTER_APP + "\n", infoList.data[0][0])

		app.onDestroy()
	}

	@Test
	fun testAppEntrybutton() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources)

		val button = app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().first()
		button.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(0.toByte() to 0))
		assertEquals(app.infoState.id, mockServer.triggeredEvents[app.focusTriggerController.focusEvent.id]!![0.toByte()])
	}

	@Test
	fun testNotificationCenterController() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = ID5StatusbarApp(iDriveConnectionStatus, securityAccess, carAppResources)
		val controller = app.statusbarController
		val event = app.notificationEvent

		// adds a notification to the car's Notification Center
		val sbn = CarNotification("test", "test", null, true, emptyList(),
				"Title", "line1\nline2 yay",
				null, null, null, null, null)
		controller.add(sbn)
		assertEquals(true, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(0, event.getIndexId()?.asRaIntModel()?.value)
		assertEquals(150, event.getImageModel()?.asImageIdModel()?.imageId)     // envelope icon
		assertEquals("Title", event.getTitleTextModel()?.asRaDataModel()?.value)
		assertEquals("line2 yay", event.getNotificationTextModel()?.asRaDataModel()?.value)

		// updates the notification to the car's Notification Center
		val sbnUpdate = CarNotification("test", "test", null, true, emptyList(),
				"Title", "line1\nline2 yay\nline3",
				null, null, null, null, null)
		controller.add(sbnUpdate)
		assertEquals(true, mockServer.triggeredEvents[event.id]!![0.toByte()])
		assertEquals(0, event.getIndexId()?.asRaIntModel()?.value)
		assertEquals(150, event.getImageModel()?.asImageIdModel()?.imageId)     // envelope icon
		assertEquals("Title", event.getTitleTextModel()?.asRaDataModel()?.value)
		assertEquals("line3", event.getNotificationTextModel()?.asRaDataModel()?.value)

		// adds a new notification
		val sbnSecond = CarNotification("test", "test2", null, true, emptyList(),
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
}