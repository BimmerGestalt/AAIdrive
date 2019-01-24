package me.hufman.androidautoidrive

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BaseBMWRemotingServer
import java.util.concurrent.CountDownLatch

class MockBMWRemotingServer: BaseBMWRemotingServer() {
	val waitForApp = CountDownLatch(1)
	val resources = HashMap<BMWRemoting.RHMIResourceType, ByteArray>()
	val addedActionHandler = HashMap<String, Boolean>()
	val addedEventHandler = HashMap<String, Boolean>()
	val properties = HashMap<Int, MutableMap<Int, Any>>()
	val data = HashMap<Int, Any>()
	val triggeredEvents = HashMap<Int, Map<*, *>>()

	override fun sas_certificate(data: ByteArray?): ByteArray {
		return ByteArray(16)
	}

	override fun sas_login(data: ByteArray?) {

	}

	override fun rhmi_create(token: String?, metaData: BMWRemoting.RHMIMetaData?): Int {
		return 1
	}

	override fun rhmi_initialize(handle: Int?) {
		waitForApp.countDown()
	}

	override fun rhmi_setResource(handle: Int?, data: ByteArray?, type: BMWRemoting.RHMIResourceType?) {
		if (type != null && data != null)
			resources[type] = data
	}

	override fun rhmi_addActionEventHandler(handle: Int?, ident: String?, actionId: Int?) {
		addedActionHandler[ident as String] = true
	}

	override fun rhmi_addHmiEventHandler(handle: Int?, ident: String?, componentId: Int?, eventId: Int?) {
		addedEventHandler[ident as String] = true
	}

	override fun rhmi_ackActionEvent(handle: Int?, actionId: Int?, confirmId: Int?, success: Boolean?) {

	}

	override fun rhmi_triggerEvent(handle: Int?, eventId: Int?, args: MutableMap<*, *>?) {
		triggeredEvents[eventId!!] = args!!
	}

	override fun rhmi_setProperty(handle: Int?, componentId: Int?, propertyId: Int?, values: MutableMap<*, *>?) {
		properties.putIfAbsent(componentId as Int, HashMap())
		properties[componentId]?.put(propertyId as Int, values?.get(0) as Any)
	}

	override fun rhmi_setData(handle: Int?, modelId: Int?, value: Any?) {
//		System.out.println("Updated data $modelId: $value")
		data[modelId!!] = value!!
	}

	override fun cds_create(): Int {
		return 1
	}

	override fun cds_addPropertyChangedEventHandler(handle: Int?, propertyName: String?, ident: String?, intervalLimit: Int?) {

	}

	override fun cds_getPropertyAsync(handle: Int?, ident: String?, propertyName: String?) {

	}
}