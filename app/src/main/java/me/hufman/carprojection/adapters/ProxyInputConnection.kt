package me.hufman.carprojection.adapters

import android.content.Context
import android.os.IBinder
import android.view.KeyEvent
import me.hufman.carprojection.Gearhead
import me.hufman.carprojection.adapters.impl.MagicProxyInputConnection

abstract class ProxyInputConnection(val context: Context, transport: IBinder) {
	val proxy = Gearhead.createInterface(context, "input.IProxyInputConnection\$Stub\$Proxy", transport)

	companion object {
		fun getInstance(context: Context, transport: IBinder): ProxyInputConnection {
			return MagicProxyInputConnection(context, transport)
		}
	}

	abstract fun commitText(text: CharSequence, newPosition: Int = 1): Boolean

	abstract fun sendKeyEvent(event: KeyEvent): Boolean
	abstract fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
}