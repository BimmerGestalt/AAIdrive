package me.hufman.androidautoidrive.notifications

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationConcrete
import io.bimmergestalt.idriveconnectkit.rhmi.deserialization.loadFromXML
import me.hufman.androidautoidrive.carapp.ReadoutCommandsRHMI
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.carapp.ReadoutState
import me.hufman.androidautoidrive.carapp.TTSState
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class ReadoutControllerTest {
	val rhmiDescription by lazy {
		this.javaClass.classLoader!!.getResourceAsStream("ui_description_news.xml")
	}
	val rhmiApp by lazy {
		RHMIApplicationConcrete().apply {
			loadFromXML(rhmiDescription.readBytes())
		}
	}

	/**
	 * Test that it parses some data from the car's CDS
	 */
	@Test
	fun testTTSCallback() {
		val jsonObject = JsonParser.parseString("{\"TTSState\": {\"state\": 0, \"type\": \"app\", \"currentblock\": 0}}") as JsonObject

		val controller = ReadoutController("app", mock())
		controller.onTTSEvent(Gson().fromJson(jsonObject["TTSState"], TTSState::class.java))

		assertEquals(ReadoutState.UNDEFINED, controller.currentState)
		assertEquals("app", controller.currentName)
		assertEquals(0, controller.currentBlock)
	}

	/**
	 * Test that the commands are sent to the car properly
	 */
	@Test
	fun testBuild() {
		val commands = ReadoutCommandsRHMI.build(rhmiApp)
		assertEquals(107, commands.speechEvent.id)
		assertEquals(108, commands.commandEvent.id)
		assertEquals(110, commands.speechList.id)
		assertEquals(111, commands.commandList.id)
	}

	/**
	 * Test that the commands are sent to the car properly
	 */
	@Test
	fun testTTSTrigger() {
		val commands = ReadoutCommandsRHMI.build(rhmiApp)
		val controller = ReadoutController("name", commands)
		controller.readout(listOf("Test Output"))
		assertEquals(1, commands.speechList.asRaListModel()?.value?.height)
		assertEquals(2, commands.speechList.asRaListModel()?.value?.width)
		assertEquals("Test Output", commands.speechList.asRaListModel()?.value?.get(0)?.get(0))
		assertEquals("name", commands.speechList.asRaListModel()?.value?.get(0)?.get(1))
		assertEquals(setOf(commands.speechEvent.id), rhmiApp.triggeredEvents.keys)
		assertEquals(mapOf(0 to null), rhmiApp.triggeredEvents[commands.speechEvent.id])

		// check that the controller updates state properly
		controller.onTTSEvent(TTSState(state = 3, currentblock = 0, blocks = 1, type="name", languageavailable = 0))
		assertTrue(controller.isActive)

		// cancel
		// continuing the previous test, because ReadoutController only sends cancel if it's still talking
		rhmiApp.modelData.clear()
		rhmiApp.triggeredEvents.clear()
		controller.cancel()
		assertNull(commands.speechList.asRaListModel()?.value)
		assertEquals(1, commands.commandList.asRaListModel()?.value?.height)
		assertEquals(2, commands.commandList.asRaListModel()?.value?.width)
		assertEquals("STR_READOUT_STOP", commands.commandList.asRaListModel()?.value?.get(0)?.get(0))
		assertEquals("name", commands.commandList.asRaListModel()?.value?.get(0)?.get(1))
		assertEquals(setOf(commands.commandEvent.id), rhmiApp.triggeredEvents.keys)
		assertEquals(mapOf(0 to null), rhmiApp.triggeredEvents[commands.commandEvent.id])

		// check state
		controller.onTTSEvent(TTSState(state = 1, currentblock = 0, blocks = 1, type="name", languageavailable = 0))
		assertFalse(controller.isActive)

		// cancel again
		// ReadoutController should skip, since it doesn't think it is talking
		rhmiApp.modelData.clear()
		rhmiApp.triggeredEvents.clear()
		controller.cancel()
		assertNull(commands.speechList.asRaListModel()?.value)
		assertNull(commands.commandList.asRaListModel()?.value)
		assertEquals(0, rhmiApp.triggeredEvents.size)
	}
}