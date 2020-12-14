package me.hufman.androidautoidrive

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import me.hufman.androidautoidrive.carapp.assistant.AssistantApp
import me.hufman.androidautoidrive.carapp.assistant.AssistantAppInfo
import me.hufman.androidautoidrive.carapp.assistant.AssistantController
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AssistantAppTest {
	val assistantController = mock<AssistantController>()

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

	val phoneAppResources = mock<PhoneAppResources> {
		on { getAppName(any()) } doReturn "Test AppName"
		on { getAppIcon(any()) } doReturn mock<Drawable>()
		on { getIconDrawable(any()) } doReturn mock<Drawable>()
	}

	val graphicsHelpers = mock<GraphicsHelpers> {
		on { isDark(any()) } doReturn false
		on { compress(isA<Drawable>(), any(), any(), any(), any()) } doAnswer { "Drawable{${it.arguments[1]}x${it.arguments[2]}}".toByteArray() }
		on { compress(isA<Bitmap>(), any(), any(), any(), any()) } doAnswer { "Bitmap{${it.arguments[1]}x${it.arguments[2]}}".toByteArray() }
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer

		val app = AssistantApp(iDriveConnectionStatus, securityAccess, carAppResources, assistantController, graphicsHelpers)
		app.onCreate()

		// verify the right icons were added
		assertEquals(0, mockServer.amApps.size)

		// add an assistant and try again
		whenever(assistantController.getAssistants()) doReturn setOf(
				AssistantAppInfo("Test App", mock(), "me.test.app")
		)
		app.onCreate()

		assertEquals(1, mockServer.amApps.size)
		assertEquals("androidautoidrive.assistant.me.test.app", mockServer.amApps[0])
	}

	@Test
	fun testAppClick() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer

		val assistant = AssistantAppInfo("Test App", mock(), "me.test.app")
		whenever(assistantController.getAssistants()) doReturn setOf(assistant)

		val app = AssistantApp(iDriveConnectionStatus, securityAccess, carAppResources, assistantController, graphicsHelpers)
		app.onCreate()

		assertEquals(1, mockServer.amApps.size)
		val appName = mockServer.amApps[0]
		IDriveConnection.mockRemotingClient?.am_onAppEvent(0, "", appName, BMWRemoting.AMEvent.AM_APP_START)

		verify(assistantController).triggerAssistant(assistant)
	}
}