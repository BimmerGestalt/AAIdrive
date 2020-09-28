package me.hufman.androidautoidrive.carapp.music.components

import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterInt
import me.hufman.idriveconnectionkit.rhmi.RHMIModel

interface ProgressGauge {
	/**
	 * Sets the percentage of progress, in [0..100]
	 */
	var value: Int
}

class ProgressGaugeToolbarState(val model: RHMIModelMultiSetterInt): ProgressGauge {
	override var value: Int
		get() = model.value
		set(value) { model.value = value }
}

class ProgressGaugeAudioState(val model: RHMIModel.RaDataModel): ProgressGauge {
	override var value: Int
		get() = (model.value.toDouble() * 100).toInt()
		set(value) { model.value = (value / 100.0).toString() }
}