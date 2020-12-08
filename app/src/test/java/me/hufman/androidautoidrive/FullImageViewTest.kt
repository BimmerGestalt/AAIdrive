package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import me.hufman.androidautoidrive.carapp.FullImageInteraction
import me.hufman.androidautoidrive.carapp.FullImageView
import me.hufman.androidautoidrive.utils.removeFirst
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import me.hufman.idriveconnectionkit.rhmi.RHMIState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class FullImageViewTest {
	private lateinit var fullImageState: RHMIState
	private lateinit var carApp: RHMIApplicationConcrete

	@Before
	fun setUp() {
		val widgetStream = this.javaClass.classLoader.getResourceAsStream("ui_description_onlineservices_v2.xml")
		this.carApp = RHMIApplicationConcrete()
		this.carApp.loadFromXML(widgetStream?.readBytes() as ByteArray)
		val unclaimedStates = LinkedList(carApp.states.values)
		this.fullImageState = unclaimedStates.removeFirst { FullImageView.fits(it) }
	}

	@Test
	fun testInitialize() {
		val fullImageView = FullImageView(this.fullImageState, "Map", mock(), mock(), { 700 }, { 400 })
		fullImageView.initWidgets()
		assertEquals(700, fullImageView.getWidth())
		assertEquals(400, fullImageView.getHeight())
		assertEquals(700, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.WIDTH.id]?.value)
		assertEquals(400, fullImageView.imageComponent.properties[RHMIProperty.PropertyId.HEIGHT.id]?.value)
	}

	@Test
	fun testInteraction() {
		val mockInteraction = mock<FullImageInteraction> {
			on { getClickState() } doReturn RHMIState.PlainState(carApp, 99)
		}

		val fullImageView = FullImageView(this.fullImageState, "Map", mockInteraction, mock(), { 700 }, { 400 })
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

		// try to click
		fullImageView.inputList.getAction()?.asRAAction()?.rhmiActionCallback?.onActionEvent(mapOf(1.toByte() to 3))
		assertEquals(99, carApp.triggeredEvents[6]?.get(0))
		verify(mockInteraction).getClickState()
		verify(mockInteraction).click()
	}
}