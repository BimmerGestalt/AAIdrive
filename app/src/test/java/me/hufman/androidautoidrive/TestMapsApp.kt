package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.media.ImageReader
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import me.hufman.androidautoidrive.carapp.maps.MapView
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import java.io.ByteArrayInputStream

class TestMapsApp {
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader.getResourceAsStream("ui_description_onlineservices_v2.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val mockImageReader = mock<ImageReader> {
		on { width } doReturn 1000
		on { height } doReturn 500
	}
	val mockController = mock<MapInteractionController> {

	}
	val mockMap = mock<VirtualDisplayScreenCapture> {
	}

	init {
		SecurityService.activeSecurityConnections["mock"] = mock {
			on { signChallenge(any(), any() )} doReturn ByteArray(512)
		}
	}

	fun setUp() {
		reset(carAppResources)
		reset(mockImageReader)
		reset(mockMap)
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MapView(carAppResources, mockController, mockMap)
		assertEquals(9, app.stateMenu.id)
		assertEquals(19, app.stateMap.id)
		assertEquals(132, app.viewFullMap.id)
		assertEquals(133, app.mapInputList.id)

		app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			assertEquals("Entry button goes to menu screen", app.stateMenu.id, it.getAction()?.asHMIAction()?.getTargetState()?.id)
		}
		assertNotNull("Scroll listener registered", app.mapInputList?.getSelectAction()?.asRAAction()?.rhmiActionCallback)
	}

	@Test
	fun testMapShow() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MapView(carAppResources, mockController, mockMap)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		val imageCallbackCapture = ArgumentCaptor.forClass(ImageReader.OnImageAvailableListener::class.java)
		verify(mockMap).registerImageListener(imageCallbackCapture.capture())
		val imageCallback = imageCallbackCapture.value

		// show the main screen
		mockClient.rhmi_onHmiEvent(1, "", app.stateMenu.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(400, 200)
		verify(mockController).showMap()

		mockClient.rhmi_onHmiEvent(1, "", app.stateMenu.id, 1, mapOf(4.toByte() to false))
		verify(mockController).pauseMap()

		reset(mockMap)
		reset(mockController)

		// show the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.stateMap.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(1000, 500)
		verify(mockController).showMap()

		// send a picture, but it's the wrong size so it shouldn't send to the car
		whenever(mockMap.getFrame()).then {
			mock<Bitmap>()
		}
		imageCallback.onImageAvailable(null)
		assertEquals("Didn't show map in full view", null, mockServer.data[app.viewFullMap.model])
		// Now send the right picture
		imageCallback.newFrame(ByteArray(5), 1000, 500)
		assertArrayEquals("Sent map to car", ByteArray(5), (mockServer.data[app.viewFullMap.model] as BMWRemoting.RHMIResourceData).data as ByteArray)

		// try changing the zoom
		mockClient.rhmi_onActionEvent(1, "", app.mapInputList.getSelectAction()?.asRAAction()?.id, mapOf(1.toByte() to 2))
		verify(mockController).zoomIn(1)
		assertEquals("Reset scroll back to neutral" , 3, mockServer.triggeredEvents[6]?.get(41))

		// hide the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.stateMap.id, 1, mapOf(4.toByte() to false))
		verify(mockController).pauseMap()
	}
}