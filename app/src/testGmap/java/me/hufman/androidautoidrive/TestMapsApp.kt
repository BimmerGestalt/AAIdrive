package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import me.hufman.androidautoidrive.carapp.maps.MapInteractionController
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import me.hufman.androidautoidrive.carapp.maps.MapApp
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.awaitility.Awaitility.await
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
		on { compressBitmap(any()) } doReturn ByteArray(4)
	}

	init {
		AppSettings.loadDefaultSettings()
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
		val app = MapApp(carAppResources, mockController, mockMap)
		assertEquals(9, app.menuView.state.id)
		assertEquals(19, app.mapView.state.id)
		assertEquals(132, app.mapView.mapImage.id)
		assertEquals(133, app.mapView.mapInputList.id)

		app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			assertEquals("Entry button goes to menu screen", app.menuView.state.id, it.getAction()?.asHMIAction()?.getTargetState()?.id)
		}
		assertNotNull("Scroll listener registered", app.mapView.mapInputList?.getSelectAction()?.asRAAction()?.rhmiActionCallback)
	}

	@Test
	fun testMapShow() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MapApp(carAppResources, mockController, mockMap)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient
		val mockHandlerRunnable = ArgumentCaptor.forClass(Runnable::class.java)
		val mockHandler = mock<Handler>()
		whenever(mockHandler.postDelayed(mockHandlerRunnable.capture(), any())).thenReturn(true)
		app.onCreate(mock(), mockHandler)

		val imageCallbackCapture = ArgumentCaptor.forClass(ImageReader.OnImageAvailableListener::class.java)
		await().untilAsserted { verify(mockMap).registerImageListener(imageCallbackCapture.capture()) }
		val imageCallback = imageCallbackCapture.value

		// show the main screen
		mockClient.rhmi_onHmiEvent(1, "", app.menuView.state.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(350, 90)
		verify(mockController).showMap()

		mockClient.rhmi_onHmiEvent(1, "", app.menuView.state.id, 1, mapOf(4.toByte() to false))
		verify(mockController).pauseMap()

		reset(mockMap)
		reset(mockController)

		// show the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.mapView.state.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(700, 400)
		verify(mockController).showMap()

		// send a picture, but it's the wrong size so it shouldn't send to the car's map
		reset(mockMap)
		whenever(mockMap.getFrame()).then {
			mock<Bitmap> {
				on { width } doReturn 400
				on { height } doReturn 90
			}
		}.thenReturn(null)
		whenever(mockMap.compressBitmap(any())).thenReturn(ByteArray(4))
		reset(mockHandler)
		imageCallback.onImageAvailable(null)
		verify(mockHandler).postDelayed(mockHandlerRunnable.capture(), any())
		mockHandlerRunnable.allValues.forEach { it.run() }
		await().untilAsserted { verify(mockMap, atLeast(1)).getFrame() }
		await().untilAsserted { verify(mockMap).compressBitmap(any()) } // it should compress the bitmap, and send it to menu map
		mockHandlerRunnable.allValues.forEach { it.run() }
		await().untilAsserted { verify(mockMap, atLeast(2)).getFrame() }    // wait until the frame updater checks for another frame
		assertArrayEquals("Updates the menu map", ByteArray(4), ((mockServer.data[app.menuView.menuList.model] as BMWRemoting.RHMIDataTable).data[0][0] as BMWRemoting.RHMIResourceData).data)
		assertEquals("Doesn't show the wrong-sized map in full view", null, mockServer.data[app.mapView.mapImage.model])

		// Now send the right picture
		reset(mockMap)
		whenever(mockMap.getFrame()).then {
			mock<Bitmap> {
				on { width } doReturn 700
				on { height } doReturn 400
			}
		}.thenReturn(null)
		whenever(mockMap.compressBitmap(any())).thenReturn(ByteArray(5))
		imageCallback.onImageAvailable(null)
		mockHandlerRunnable.allValues.forEach { it.run() }
		await().untilAsserted { verify(mockMap, atLeastOnce()).getFrame() }
		assertArrayEquals("Sent map to car", ByteArray(5), (mockServer.data[app.mapView.mapImage.model] as BMWRemoting.RHMIResourceData).data as ByteArray)

		// try changing the zoom
		mockClient.rhmi_onActionEvent(1, "", app.mapView.mapInputList.getSelectAction()?.asRAAction()?.id, mapOf(1.toByte() to 2))
		verify(mockController).zoomIn(1)
		assertEquals("Reset scroll back to neutral" , 3, mockServer.triggeredEvents[6]?.get(41))

		// hide the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.mapView.state.id, 1, mapOf(4.toByte() to false))
		verify(mockController).pauseMap()
	}

}