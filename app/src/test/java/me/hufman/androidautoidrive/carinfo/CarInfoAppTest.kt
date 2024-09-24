package me.hufman.androidautoidrive.carinfo

import android.content.res.Resources
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import me.hufman.androidautoidrive.AppSettingsViewer
import me.hufman.androidautoidrive.MockBMWRemotingServer
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.carinfo.CarInfoApp
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream

class CarInfoAppTest {

	val iDriveConnectionStatus = mock<IDriveConnectionStatus>()
	val securityAccess = mock<SecurityAccess> {
		on { signChallenge(any(), any() )} doReturn ByteArray(512)
	}
	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_news.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}
	val resources = mock<Resources> {
		on { openRawResource(any()) } doThrow Resources.NotFoundException()
	}

	@Test
	fun testAppInit() {
		val mockServer = MockBMWRemotingServer()
		IDriveConnection.mockRemotingServer = mockServer
		val app = CarInfoApp(iDriveConnectionStatus, securityAccess, carAppResources, mock(), mock(), resources, AppSettingsViewer())

		val labelComponent = app.infoState.state.componentsList.filterIsInstance<RHMIComponent.Button>().first()
		Assert.assertEquals(L.CARINFO_TITLE, mockServer.data[labelComponent.model])
		val listComponent = app.infoState.state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		// won't show data until the screen is opened

		app.disconnect()
	}

}