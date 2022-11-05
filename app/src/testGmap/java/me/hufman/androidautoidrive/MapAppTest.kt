package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import io.bimmergestalt.idriveconnectkit.GenericRHMIDimensions
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.maps.*
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.maps.CarLocationProvider
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.awaitility.Awaitility.await
import java.io.ByteArrayInputStream

class MapAppTest {
	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_onlineservices_v2.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}

	val appSettings = MockAppSettings()
	val mapAppMode = MapAppMode.build(GenericRHMIDimensions(1280, 480), appSettings, CDSDataProvider(), MusicAppMode.TRANSPORT_PORTS.USB)
	val locationProvider = mock<CarLocationProvider>()
	val mockImageReader = mock<ImageReader> {
		on { width } doReturn 1000
		on { height } doReturn 500
	}
	val mockController = mock<MapInteractionController> {

	}
	val mockPlaceSearch = mock<MapPlaceSearch> {

	}
	val mockMap = mock<VirtualDisplayScreenCapture> {
		on { compressBitmap(any()) } doReturn ByteArray(4)
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
		val app = MapApp(iDriveConnectionStatus, securityAccess, carAppResources, mapAppMode, locationProvider, mockController, mockPlaceSearch, mockMap)
		assertEquals(9, app.menuView.state.id)
		assertEquals(19, app.fullImageView.state.id)
		assertEquals(132, app.fullImageView.imageComponent.id)
		assertEquals(133, app.fullImageView.inputList.id)

		app.carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			assertEquals("Entry button goes to menu screen", app.menuView.state.id, it.getAction()?.asHMIAction()?.getTargetState()?.id)
		}
		assertNotNull("Scroll listener registered", app.fullImageView.inputList.getSelectAction()?.asRAAction()?.rhmiActionCallback)

		assertEquals(true, mockServer.properties[app.menuView.menuMap.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[app.menuView.menuList.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[app.menuView.labelSettings.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(true, mockServer.properties[app.menuView.labelSettings.id]!![RHMIProperty.PropertyId.VISIBLE.id])
		assertEquals(L.MAP_OPTIONS, mockServer.data[app.menuView.labelSettings.model])
	}

	@Test
	fun testMenuMap() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MapApp(iDriveConnectionStatus, securityAccess, carAppResources, mapAppMode, locationProvider, mockController, mockPlaceSearch, mockMap)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient
		val mockHandlerRunnable = ArgumentCaptor.forClass(Runnable::class.java)
		val mockHandler = mock<Handler>()
		whenever(mockHandler.postDelayed(mockHandlerRunnable.capture(), any())).thenReturn(true)
		app.onCreate(mockHandler)

		val imageCallbackCapture = ArgumentCaptor.forClass(ImageReader.OnImageAvailableListener::class.java)
		await().untilAsserted { verify(mockMap).registerImageListener(imageCallbackCapture.capture()) }
		val imageCallback = imageCallbackCapture.value

		// show the menu screen
		mockClient.rhmi_onHmiEvent(1, "", app.menuView.state.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(350, 90)
		verify(mockController).showMap()

		// send a picture
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
		assertArrayEquals("Updates the menu map", ByteArray(4), ((mockServer.data[app.menuView.menuMap.model] as BMWRemoting.RHMIDataTable).data[0][0] as BMWRemoting.RHMIResourceData).data)
		assertEquals("Doesn't show the full map", null, mockServer.data[app.fullImageView.imageComponent.model])

	}

	@Test
	fun testMapShow() {
		appSettings[AppSettings.KEYS.MAP_WIDESCREEN] = "false"
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = MapApp(iDriveConnectionStatus, securityAccess, carAppResources, mapAppMode, locationProvider, mockController, mockPlaceSearch, mockMap)
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient
		val mockHandlerRunnable = ArgumentCaptor.forClass(Runnable::class.java)
		val mockHandler = mock<Handler>()
		whenever(mockHandler.postDelayed(mockHandlerRunnable.capture(), any())).thenReturn(true)
		app.onCreate(mockHandler)

		val imageCallbackCapture = ArgumentCaptor.forClass(ImageReader.OnImageAvailableListener::class.java)
		await().untilAsserted { verify(mockMap).registerImageListener(imageCallbackCapture.capture()) }
		val imageCallback = imageCallbackCapture.value

		// show the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.fullImageView.state.id, 1, mapOf(4.toByte() to true))
		verify(mockMap).changeImageSize(743, 480)
		verify(mockController).showMap()

		// Send the fullsize map
		reset(mockMap)
		whenever(mockMap.getFrame()).then {
			mock<Bitmap> {
				on { width } doReturn 743
				on { height } doReturn 480
			}
		}.thenReturn(null)
		whenever(mockMap.compressBitmap(any())).thenReturn(ByteArray(5))
		imageCallback.onImageAvailable(null)
		mockHandlerRunnable.allValues.forEach { it.run() }
		await().untilAsserted { verify(mockMap, atLeastOnce()).getFrame() }
		assertArrayEquals("Sent map to car", ByteArray(5), (mockServer.data[app.fullImageView.imageComponent.model] as BMWRemoting.RHMIResourceData).data as ByteArray)

		// try changing the zoom
		mockClient.rhmi_onActionEvent(1, "", app.fullImageView.inputList.getSelectAction()?.asRAAction()?.id, mapOf(1.toByte() to 2))
		verify(mockController).zoomIn(1)
		assertEquals("Reset scroll back to neutral" , 3, mockServer.triggeredEvents[6]?.get(41))

		// hide the map screen
		mockClient.rhmi_onHmiEvent(1, "", app.fullImageView.state.id, 1, mapOf(4.toByte() to false))
		verify(mockController).pauseMap()
	}

}