package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import io.bimmergestalt.idriveconnectkit.GenericRHMIDimensions
import me.hufman.androidautoidrive.carapp.*
import me.hufman.androidautoidrive.carapp.maps.MapAppMode
import me.hufman.androidautoidrive.utils.removeFirst
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationConcrete
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class FullImageViewTest {
	private lateinit var fullImageState: RHMIState
	private lateinit var carApp: RHMIApplicationConcrete

	@Before
	fun setUp() {
		val widgetStream = this.javaClass.classLoader!!.getResourceAsStream("ui_description_onlineservices_v2.xml")
		this.carApp = RHMIApplicationConcrete()
		this.carApp.loadFromXML(widgetStream?.readBytes() as ByteArray)
		val unclaimedStates = LinkedList(carApp.states.values)
		this.fullImageState = unclaimedStates.removeFirst { FullImageView.fits(it) }
	}

	@Test
	fun testInitialize() {
		val appSettings = MockAppSettings()
		appSettings[AppSettings.KEYS.MAP_WIDESCREEN] = "false"
		val fullImageConfig = MapAppMode.build(GenericRHMIDimensions(1280, 480), appSettings, CDSDataProvider(), MusicAppMode.TRANSPORT_PORTS.USB)
		val fullImageView = FullImageView(this.fullImageState, "Map", fullImageConfig, mock(), mock())
		fullImageView.initWidgets()
		assertEquals(743, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.WIDTH.id]?.value)
		assertEquals(480, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.HEIGHT.id]?.value)

		appSettings[AppSettings.KEYS.MAP_WIDESCREEN] = "true"
		fullImageView.initWidgets()
		assertEquals(1211, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.WIDTH.id]?.value)
		assertEquals(480, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.HEIGHT.id]?.value)
	}

	@Test
	fun testInteraction() {
		val appSettings = MockAppSettings()
		appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL] = "false"
		val fullImageConfig = MapAppMode.build(GenericRHMIDimensions(1280, 480), appSettings, CDSDataProvider(), MusicAppMode.TRANSPORT_PORTS.USB)
		val mockInteraction = mock<FullImageInteraction> {
			on { getClickState() } doReturn RHMIState.PlainState(carApp, 99)
		}

		val fullImageView = FullImageView(this.fullImageState, "Map", fullImageConfig, mockInteraction, mock())
		fullImageView.initWidgets()

		// handle a bookmark click
		fullImageView.inputList.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 3, 43.toByte() to 2))
		assertEquals(fullImageState.id, carApp.triggeredEvents[6]?.get(0))
		verify(mockInteraction, never()).click()

		// navigate up
		fullImageView.inputList.getSelectAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(mockInteraction).navigateUp()
		// it should reset the focus back to the middle of the list
		assertEquals("Reset scroll back to neutral" , 3, carApp.triggeredEvents[6]?.get(41))
		assertEquals("Reset scroll back to neutral" , fullImageView.inputList.id, carApp.triggeredEvents[6]?.get(0))
		// navigate down
		fullImageView.inputList.getSelectAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 4))
		verify(mockInteraction).navigateUp()
		// it should reset the focus back to the middle of the list
		assertEquals("Reset scroll back to neutral" , 3, carApp.triggeredEvents[6]?.get(41))
		assertEquals("Reset scroll back to neutral" , fullImageView.inputList.id, carApp.triggeredEvents[6]?.get(0))

		// try inverted mode
		appSettings[AppSettings.KEYS.MAP_INVERT_SCROLL] = "true"
		fullImageView.inputList.getSelectAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 2))
		verify(mockInteraction, times(2)).navigateDown()      // inverted

		// try to click
		fullImageView.inputList.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 3))
		assertEquals(99, carApp.triggeredEvents[6]?.get(0))
		verify(mockInteraction).getClickState()
		verify(mockInteraction).click()
	}
}