package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.RemoteBMWRemotingServer
import de.bmw.idrive.ValueFactoryBMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIAction
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import me.hufman.androidautoidrive.carapp.RHMIApplicationEtchBackground
import org.apache.etch.bindings.java.msg.Message
import org.apache.etch.bindings.java.support.Mailbox
import org.apache.etch.util.CircularQueue
import org.apache.etch.util.core.Who
import org.awaitility.Awaitility.await
import org.junit.Assert.assertEquals
import org.junit.Test
import org.powermock.reflect.Whitebox

@ExperimentalCoroutinesApi
class RHMIApplicationEtchBackgroundTest {
	val pendingSetData = ArrayList<Mailbox>()
	val pendingTriggerEvent = ArrayList<Mailbox>()

	val asyncConnection = mock<RemoteBMWRemotingServer._Async> {
		on {_begin_rhmi_setData(any(), any(), any())} doAnswer {
			val pending = TestMailbox()
			println("_begin_rhmi_setData(${it.arguments[0]}, ${it.arguments[1]}, ${it.arguments[2]}")
			pendingSetData.add(pending)
			pending
		}
		on {_begin_rhmi_triggerEvent(any(), any(), any())} doAnswer {
			val pending = TestMailbox()
			pendingTriggerEvent.add(pending)
			pending
		}
	}
	val syncConnection = mock<RemoteBMWRemotingServer._Async> {
		on {_begin_rhmi_setData(any(), any(), any())} doAnswer {
			val pending = TestMailbox()
			pendingSetData.add(pending)
			pending
		}
		on {_end_rhmi_setData(any())} doAnswer {
			val msg = it.getArgument<Mailbox>(0).read(5000).msg
			val response = msg[ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData.responseField]
			if (response is Exception) throw response
		}
		on {_begin_rhmi_triggerEvent(any(), any(), any())} doAnswer {
			val pending = TestMailbox()
			pendingTriggerEvent.add(pending)
			pending
		}
		on {_end_rhmi_triggerEvent(any())} doAnswer {
			val msg = it.getArgument<Mailbox>(0).read(5000).msg
			val response = msg[ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent.responseField]
			if (response is Exception) throw response
		}
	}
	val connection = mock<RemoteBMWRemotingServer> {
		// async field is set in init below
		on {rhmi_setData(any(), any(), any())} doAnswer {
			syncConnection._end_rhmi_setData(syncConnection._begin_rhmi_setData(it.arguments[0] as Int, it.arguments[1] as Int, it.arguments[2]))
		}
		on {rhmi_triggerEvent(any(), any(), any())} doAnswer {
			syncConnection._end_rhmi_triggerEvent(syncConnection._begin_rhmi_triggerEvent(it.arguments[0] as Int, it.arguments[1] as Int, it.arguments[2] as Map<*, *>))
		}
	}
	val subject = RHMIApplicationEtchBackground(connection, 1)

	init {
		Whitebox.setInternalState(connection, "_async", asyncConnection)

		subject.models[35] = RHMIModel.RaDataModel(subject, 35)
		subject.models[6] = RHMIModel.RaIntModel(subject, 6)
		subject.models[7] = RHMIModel.RaListModel(subject, 7)
		subject.actions[4] = RHMIAction.RAAction(subject, 4)
		subject.actions[5] = RHMIAction.HMIAction(subject, 5).apply {
			targetModel = 6
		}
		subject.actions[3] = RHMIAction.CombinedAction(subject, 3, subject.actions[4] as RHMIAction.RAAction, subject.actions[5] as RHMIAction.HMIAction)
		subject.components[71] = RHMIComponent.List(subject, 71).apply {
			model = 7
		}
		subject.events[70] = RHMIEvent.FocusEvent(subject, 70)
	}

	@Test
	fun testRegularModel() = runBlockingTest {
		// should use async and return right away
		subject.setModel(35, "Name")
		verify(asyncConnection)._begin_rhmi_setData(1, 35, "Name")
		assertEquals(1, pendingSetData.size)
		pendingSetData[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting("")))
	}
	@Test
	fun testTargetModel() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// should use sync to set an HMIAction's targetModel and block until done
		val setJob = launch { subject.setModel(6, 9) }
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_setData(1, 6, 9) }
		assertEquals(1, pendingSetData.size)
		val response = Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting(""))
		pendingSetData[0].message(null, response)
		setJob.join()
	} }
	@Test
	fun testMultipleListModel() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// should async the first list update and fence the second list update
		val firstTable = BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 1, 1, 0, 1, 1)
		val setJob = launch { subject.setModel(7, firstTable) }
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_setData(1, 7, firstTable) }
		assertEquals(1, pendingSetData.size)

		// second set is fenced
		val secondTable = BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 4, 4, 0, 1, 1)
		val setJob2 = launch { subject.setModel(7, secondTable) }
		delay(200)
		assertEquals(1, pendingSetData.size)

		// other models are unaffected
		subject.setModel(35, "Name")
		verify(asyncConnection)._begin_rhmi_setData(1, 35, "Name")
		assertEquals(2, pendingSetData.size)
		subject.setModel(36, "Two")
		verify(asyncConnection)._begin_rhmi_setData(1, 36, "Two")
		assertEquals(3, pendingSetData.size)

		// resolve the first
		val response = Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting(""))
		pendingSetData[0].message(null, response)
		setJob.join()

		await().untilAsserted { verify(asyncConnection)._begin_rhmi_setData(1, 7, secondTable) }
		assertEquals(4, pendingSetData.size)
		pendingSetData[2].message(null, response)
		setJob2.join()
	} }

	@Test
	fun testEventFlushEmpty() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// no pending data for triggerEvent to wait for
		val triggerJob = launch { subject.triggerHMIEvent(1, emptyMap()) }
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_triggerEvent(1, 1, emptyMap<Any, Any>()) }
		assertEquals(1, pendingTriggerEvent.size)
		pendingTriggerEvent[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent, ValueFactoryBMWRemoting("")))
		triggerJob.join()
	} }

	@Test
	fun testEventFlushWait() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// wait for pending data to finish before the triggerEvent finishes
		subject.setModel(35, "Name")
		subject.setModel(7, BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 0, 0, 0, 1, 1))

		// try the trigger
		val triggerJob = launch { subject.triggerHMIEvent(1, emptyMap()) }
		delay(200)
		verify(asyncConnection, never())._begin_rhmi_triggerEvent(any(), any(), any())

		// resolve the first setData
		pendingSetData[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting("")))
		delay(200)
		verify(asyncConnection, never())._begin_rhmi_triggerEvent(any(), any(), any())

		// resolve the second setData
		pendingSetData[1].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting("")))

		// the trigger should continue
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_triggerEvent(1, 1, emptyMap<Any, Any>()) }
		pendingTriggerEvent[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent, ValueFactoryBMWRemoting("")))
		triggerJob.join()
	} }

	@Test
	fun testEventFlushListFocus() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// wait for pending data to finish before the triggerEvent finishes
		subject.setModel(35, "Name")
		subject.setModel(7, BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 0, 0, 0, 1, 1))

		// try the trigger
		val triggerJob = launch { subject.triggerHMIEvent(70, mapOf(0.toByte() to 71)) }
		delay(200)
		verify(connection, never()).rhmi_triggerEvent(any(), any(), any())

		// resolve the list setData
		pendingSetData[1].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting("")))

		// the trigger should continue, even though the text setModel isn't resolved
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_triggerEvent(1, 70, mapOf(0.toByte() to 71)) }
		pendingTriggerEvent[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent, ValueFactoryBMWRemoting("")))
		triggerJob.join()
	} }

	@Test
	fun testEventFlushMultipleListFocus() = runBlocking(Dispatchers.IO) { withTimeout(5000) {
		// wait for pending data to finish before the triggerEvent finishes
		// many little updates to the list shouldn't cause problems
		subject.setModel(35, "Name")
		subject.setModel(7, BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 1, 1, 0, 1, 1))
		subject.setModel(7, BMWRemoting.RHMIDataTable(emptyArray(), false, 0, 1, 5, 0, 1, 1))
		subject.setModel(7, BMWRemoting.RHMIDataTable(emptyArray(), false, 3, 2, 5, 0, 1, 1))

		// try the trigger
		val triggerJob = launch { subject.triggerHMIEvent(70, mapOf(0.toByte() to 71)) }
		delay(200)
		verify(connection, never()).rhmi_triggerEvent(any(), any(), any())

		// resolve the list setData
		pendingSetData[3].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData, ValueFactoryBMWRemoting("")))

		// the trigger should continue, even though the text setModel isn't resolved
		await().untilAsserted { verify(asyncConnection)._begin_rhmi_triggerEvent(1, 70, mapOf(0.toByte() to 71)) }
		pendingTriggerEvent[0].message(null, Message(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent, ValueFactoryBMWRemoting("")))
		triggerJob.join()
	} }
}

class TestMailbox: Mailbox {
	val contents = CircularQueue<Mailbox.Element>(1)
	var closed: Boolean = false

	var notify: Mailbox.Notify? = null
	var notifyState: Any? = null

	override fun getMessageId(): Long {
		TODO("Not yet implemented")
	}
	override fun message(p0: Who?, p1: Message?): Boolean {
		val success = contents.put(Mailbox.Element(p0, p1), -1)
		println("Setting message for id $notifyState, succeeded:$success")
		if (success) fireNotify()
		return success
	}

	override fun read(): Mailbox.Element? = contents.get()
	override fun read(p0: Int): Mailbox.Element? = contents.get(p0)

	override fun closeDelivery(): Boolean {
		if (contents.isClosed) return false
		contents.close()
		fireNotify()
		return true
	}
	override fun closeRead(): Boolean = closeDelivery()
		// and some other stuff about redelivery

	override fun registerNotify(p0: Mailbox.Notify?, p1: Any?, p2: Int) {
		println("Registering notification for id $p1")
		notify = p0
		notifyState = p1
		// timeout = p2
		if (!contents.isEmpty || contents.isClosed) {
			fireNotify()
		}
	}
	private fun fireNotify() {
		println("Firing notification for id $notifyState to $notify which is currently closed ${contents.isClosed} and full ${contents.isFull}")
		notify?.mailboxStatus(this, notifyState, contents.isClosed)
	}
	override fun unregisterNotify(p0: Mailbox.Notify?) {
		notify = null
		notifyState = null
	}

	override fun isEmpty(): Boolean = contents.isEmpty
	override fun isClosed(): Boolean = contents.isClosed
	override fun isFull(): Boolean = contents.isFull

}