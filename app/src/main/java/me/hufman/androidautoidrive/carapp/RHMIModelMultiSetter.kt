package me.hufman.androidautoidrive.carapp

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel

class RHMIModelMultiSetterData(val members: Iterable<RHMIModel.RaDataModel?>) {
	var value: String
		get() {
			val member = members.filterNotNull().firstOrNull()
			return member?.value ?: ""
		}
		set(value) {
			for (member in members) {
				member?.value = value
			}
		}
}


class RHMIModelMultiSetterInt(val members: Iterable<RHMIModel.RaIntModel?>) {
	var value: Int
		get() {
			val member = members.filterNotNull().firstOrNull()
			return member?.value ?: 0
		}
		set(value) {
			for (member in members) {
				member?.value = value
			}
		}
}