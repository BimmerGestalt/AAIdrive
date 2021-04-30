package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.connections.CarConnectionDebugging

class ConnectionTip(text: Context.() -> String, drawable: Context.() -> Drawable?, val condition: CarConnectionDebugging.() -> Boolean): Tip(text, drawable)

class ConnectionTipsModel(val connection: CarConnectionDebugging): TipsModel() {
	class Factory(val appContext: Context) : ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val handler = Handler()
			var model: ConnectionTipsModel? = null
			val connection = CarConnectionDebugging(appContext) {
//				handler.post { model?.update() }
			}
			// can't unittest CarConnectionDebugging registration
			connection.register()
			model = ConnectionTipsModel(connection)
			model.update()
			return model as T
		}
	}

	val TIPS = listOf(
		ConnectionTip({getString(R.string.tip_usb_chargingmode)}, {ContextCompat.getDrawable(this, R.drawable.tip_phone_chargemode)}) {
			!isUsbAccessoryConnected
		},
		ConnectionTip({getString(R.string.tip_connassistant)}, {ContextCompat.getDrawable(this, R.drawable.tip_connassistant_bmw)}) {
			isBMWConnectedInstalled
		},
		ConnectionTip({getString(R.string.tip_connassistant)}, {ContextCompat.getDrawable(this, R.drawable.tip_connassistant_mini)}) {
			isMiniConnectedInstalled
		},
		ConnectionTip({getString(R.string.tip_btapp)}, {ContextCompat.getDrawable(this, R.drawable.tip_btapp_bmw)}) {
			val btApps = !isBTConnected || (isBTConnected && !isSPPAvailable)
			(isBMWConnectedInstalled || isBMWMineInstalled) && btApps
		},
		ConnectionTip({getString(R.string.tip_btapp)}, {ContextCompat.getDrawable(this, R.drawable.tip_btapp_mini)}) {
			val btApps = !isBTConnected || (isBTConnected && !isSPPAvailable)
			(isMiniConnectedInstalled || isMiniMineInstalled) && btApps
		},
		ConnectionTip({getString(R.string.tip_batterymode_mybmw)}, {ContextCompat.getDrawable(this, R.drawable.tip_batterymode_mybmw)}) {
			isBMWMineInstalled
		},
		ConnectionTip({getString(R.string.tip_batterymode_mymini)}, {ContextCompat.getDrawable(this, R.drawable.tip_batterymode_mymini)}) {
			isMiniMineInstalled
		}
	)

	override fun update() {
		currentTips.clear()
		currentTips.addAll(TIPS.filter { connection.run(it.condition) })
	}
}