package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.carapp.RHMIApplicationSwappable
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIEvent
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RHMIApplicationSwappableTest {
	/** No models are loaded, so no memoizing happens, but it shouldn't crash */
	@Test
	fun testPassthrough() {
		val backing = RHMIApplicationConcrete()
		val swap = RHMIApplicationSwappable(backing)
		swap.setModel(40, 50)
		swap.setProperty(100, 1, true)
		swap.triggerHMIEvent(200, emptyMap())
		assertEquals(50, backing.modelData[40])
		assertEquals(true, backing.propertyData[100]!![1])
		assertEquals(emptyMap<Any, Any?>(), backing.triggeredEvents[200])
	}

	/** Confirm that the disconnect switch works */
	@Test
	fun testDisconnect() {
		val backing = RHMIApplicationConcrete()
		val swap = RHMIApplicationSwappable(backing)
		// should not pass through
		swap.isConnected = false
		swap.setModel(40, 50)
		swap.setProperty(100, 1, true)
		swap.triggerHMIEvent(200, emptyMap())
		assertNull(backing.modelData[40])
		assertNull(backing.propertyData[100])
		assertNull(backing.triggeredEvents[200])

		// and it starts working
		swap.isConnected = true
		swap.setModel(40, 60)
		swap.setProperty(100, 1, true)
		swap.triggerHMIEvent(200, emptyMap())
		assertEquals(60, backing.modelData[40])
		assertEquals(true, backing.propertyData[100]!![1])
		assertEquals(emptyMap<Any, Any?>(), backing.triggeredEvents[200])
	}

	@Test
	fun testReplay() {
		val backing = RHMIApplicationConcrete()
		val swap = RHMIApplicationSwappable(backing)
		val model = RHMIModel.RaIntModel(swap, 40)
		val component = RHMIComponent.Label(swap, 100)
		val event = RHMIEvent.FocusEvent(swap, 200)
		swap.models[model.id] = model
		swap.components[component.id] = component
		swap.events[event.id] = event

		model.value = 50
		component.setProperty(1, true)
		event.triggerEvent(emptyMap())
		assertEquals(50, backing.modelData[40])
		assertEquals(true, backing.propertyData[100]!![1])
		assertEquals(emptyMap<Any, Any?>(), backing.triggeredEvents[200])

		val new = RHMIApplicationConcrete()
		swap.isConnected = false
		swap.app = new
		swap.isConnected = true
		assertEquals(50, new.modelData[40])
		assertEquals(true, new.propertyData[100]!![1])
		assertNull("Doesn't replay events", new.triggeredEvents[200])
	}
}