package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.RemoteBMWRemotingServer
import de.bmw.idrive.ValueFactoryBMWRemoting
import io.bimmergestalt.idriveconnectkit.rhmi.*
import org.apache.etch.bindings.java.support.Mailbox
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

/**
 * This variant of RHMIApplicationEtch uses the Apache Etch async methods
 * to set data and properties in parallel while ignoring the result
 *
 * It adds some extra RHMI-specific logic to ensure data dependencies are properly synchronous
 * For example:
 *  - Any HmiAction targetModels must be synchronous, so that the write finishes before the action handler returns to the car
 *  - Any Trigger Events wait for all pending setModels to flush, to allow any focusEvents to be successful
 */
class RHMIApplicationEtchBackground(val remoteServer: RemoteBMWRemotingServer, val rhmiHandle: Int): RHMIApplication() {

	/** Represents an application layout that is backed by a Car connection */
	override val models = HashMap<Int, RHMIModel>()
	override val actions = HashMap<Int, RHMIAction>()
	override val events = HashMap<Int, RHMIEvent>()
	override val states = HashMap<Int, RHMIState>()
	override val components = HashMap<Int, RHMIComponent>()

	private val pendingModels = HashMap<Int, Mailbox>()

	private val MailboxCloser = object: Mailbox.Notify {
		override fun mailboxStatus(p0: Mailbox?, p1: Any?, p2: Boolean) {
			if (p0?.isFull == true) {
				p0.closeDelivery()
				p0.unregisterNotify(this)
				if (p1 is Int) synchronized(this) {
					pendingModels.remove(p1)
				}

				val message = p0.read()?.msg
				val setDataResponse = message?.get(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setData.responseField)
				if (setDataResponse is Exception) {
					println("Exception setting data $p1: $setDataResponse")
				}
				val setPropertyResponse = message?.get(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_setProperty.responseField)
				if (setPropertyResponse is Exception) {
					println("Exception setting property on $p1: $setPropertyResponse")
				}
				val triggerEventResponse = message?.get(ValueFactoryBMWRemoting._mt_de_bmw_idrive_BMWRemoting__result_rhmi_triggerEvent.responseField)
				if (triggerEventResponse is Exception) {
					println("Exception triggering event $p1: $triggerEventResponse")
				}
			}
		}
	}

	@Throws(BMWRemoting.SecurityException::class, BMWRemoting.IllegalArgumentException::class, BMWRemoting.ServiceException::class)
	override fun setModel(modelId: Int, value: Any) {
		if (ignoreUpdates) return
		// wait for previous sets of this model to finish
		fence(synchronized(this) { pendingModels[modelId] })

		// start the call
		val mailbox = synchronized(remoteServer) {
			this.remoteServer._async._begin_rhmi_setData(this.rhmiHandle, modelId, value)
		}
		synchronized(this) {
			pendingModels[modelId] = mailbox
		}
		mailbox.registerNotify(MailboxCloser, modelId, 0)

		// wait for certain models to finish before returning
		if (isSynchronousModel(modelId)) {
			fence(mailbox, 100, 100)
		}
	}

	@Throws(BMWRemoting.SecurityException::class, BMWRemoting.IllegalArgumentException::class, BMWRemoting.ServiceException::class)
	override fun setProperty(componentId: Int, propertyId: Int, value: Any?) {
		if (ignoreUpdates) return
		val propertyValue = HashMap<Int, Any?>()
		propertyValue[0] = value
		val mailbox = synchronized(remoteServer) {
			this.remoteServer._async._begin_rhmi_setProperty(rhmiHandle, componentId, propertyId, propertyValue)
		}
		mailbox.registerNotify(MailboxCloser, componentId, 5000)
	}

	@Throws(BMWRemoting.SecurityException::class, BMWRemoting.IllegalArgumentException::class, BMWRemoting.ServiceException::class)
	override fun triggerHMIEvent(eventId: Int, args: Map<Any, Any?>) {
		fence()
		val mailbox = synchronized(remoteServer) {
			this.remoteServer._async._begin_rhmi_triggerEvent(rhmiHandle, eventId, args)
		}
		mailbox.registerNotify(MailboxCloser, mailbox, 5000)
		fence(mailbox, 100, 100)
	}

	/** Some models are used by non-RHMIApplication rpc calls, and must be synchronous */
	private fun isSynchronousModel(modelId: Int): Boolean {
		return models[modelId] is RHMIModel.RaIntModel &&
				actions.values.any {
					it is RHMIAction.HMIAction &&
					it.targetModel == modelId
				}
	}

	/** Wait for any pending models to be flushed to the car */
	private fun fence(tries: Int = 10, delay: Long = 100) {
		val currentPending = synchronized(this) { ArrayList(pendingModels.values) }
		fence(currentPending, tries, delay)
	}

	private fun fence(pendingCalls: List<Mailbox>, tries: Int = 10, delay: Long = 100) {
		for (i in 0..tries) {
			if (pendingCalls.any {!it.isClosed}) {
				Thread.sleep(delay)    // wait for any pending setData to reach the car
			}
		}
	}

	private fun fence(pendingCall: Mailbox?, tries: Int = 10, delay: Long = 100) {
		pendingCall ?: return
		for (i in 0..tries) {
			if (!pendingCall.isClosed) {
				Thread.sleep(delay)    // wait for any pending setData to reach the car
			}
		}
	}
}