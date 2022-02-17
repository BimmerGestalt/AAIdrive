package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.rhmi.*
import me.hufman.androidautoidrive.carapp.L
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.SettingsToggleList
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class SettingsToggleListTest {

	val carAppResources = mock<CarAppResources> {
		on { getAppCertificate() } doReturn ByteArrayInputStream(ByteArray(0))
		on { getUiDescription() } doAnswer { this.javaClass.classLoader!!.getResourceAsStream("ui_description_onlineservices_v1.xml") }
		on { getImagesDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
		on { getTextsDB(any()) } doReturn ByteArrayInputStream(ByteArray(0))
	}
	val appSettings = MockAppSettings()
	val settings = ArrayList<AppSettings.KEYS>()
	val CHECKMARK_ID = 150

	val carApp: RHMIApplicationConcrete
	val state: RHMIState
	val component: RHMIComponent.List
	val list: SettingsToggleList

	init {
		carApp = RHMIApplicationConcrete()
		carApp.loadFromXML(carAppResources.getUiDescription()?.readBytes() as ByteArray)
		state = carApp.states.values.first { NotificationListView.fits(it) }
		component = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
		list = SettingsToggleList(component, appSettings, settings, CHECKMARK_ID)
		list.initWidgets()
	}

	@Test
	fun testView() {
		settings.addAll(listOf(
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
				AppSettings.KEYS.NOTIFICATIONS_SOUND,
				AppSettings.KEYS.NOTIFICATIONS_READOUT,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		))
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = "true"
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "false"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = "false"

		list.redraw()

		// check the correct displayed entries
		run {
			val menu = carApp.modelData[component.model] as BMWRemoting.RHMIDataTable
			Assert.assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
			val icon = menu.data[0][0] as BMWRemoting.RHMIResourceIdentifier
			Assert.assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon.type)
			Assert.assertEquals(150, icon.id)
			Assert.assertEquals("", menu.data[1][0])
		}

	}

	fun testClick() {
		settings.addAll(listOf(
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP,
				AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER,
				AppSettings.KEYS.NOTIFICATIONS_SOUND,
				AppSettings.KEYS.NOTIFICATIONS_READOUT,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP,
				AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER
		))
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = "true"
		appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = "false"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP] = "true"
		appSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER] = "false"

		list.redraw()

		// click a menu entry
		try {
			component.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 1))
			fail("Didn't abort the RHMI Action")
		} catch (e: RHMIActionAbort) {}
		assertEquals("true", appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER])

		// check that it changed
		run {
			val menu = carApp.modelData[component.model] as BMWRemoting.RHMIDataTable
			assertEquals(listOf(L.NOTIFICATION_POPUPS, L.NOTIFICATION_POPUPS_PASSENGER, L.NOTIFICATION_SOUND,
					L.NOTIFICATION_READOUT, L.NOTIFICATION_READOUT_POPUP, L.NOTIFICATION_READOUT_POPUP_PASSENGER), menu.data.map { it[2] })
			val icon = menu.data[1][0] as BMWRemoting.RHMIResourceIdentifier
			assertEquals(BMWRemoting.RHMIResourceType.IMAGEID, icon.type)
			assertEquals(150, icon.id)
		}
	}
}