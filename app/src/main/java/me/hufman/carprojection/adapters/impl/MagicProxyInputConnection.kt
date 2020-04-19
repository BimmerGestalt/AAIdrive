package me.hufman.carprojection.adapters.impl

import android.content.Context
import android.os.IBinder
import android.view.KeyEvent
import me.hufman.carprojection.adapters.ProxyInputConnection

class MagicProxyInputConnection(context: Context, transport: IBinder) : ProxyInputConnection(context, transport) {
	override fun commitText(text: CharSequence, newPosition: Int): Boolean {
		val commitText = proxy::class.java.methods.filter {
			if (it?.parameterTypes == null) { false } else {
				it.parameterTypes.contentEquals(arrayOf(CharSequence::class.java, Int::class.javaPrimitiveType))
			}
		}[1]
		return commitText.invoke(proxy, text, newPosition) as Boolean
	}

	override fun sendKeyEvent(event: KeyEvent): Boolean {
		val sendKeyEvent = proxy::class.java.methods.first {
			it?.parameterTypes?.contentEquals(arrayOf(KeyEvent::class.java)) ?: false
		}
		return sendKeyEvent(proxy, event) as Boolean
	}

	override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
		// as of version 5.1, it's the 3rd range function (c(int, int))
		val deleteSurroundingText = proxy::class.java.methods.filter {
			if (it?.parameterTypes == null) { false } else {
				it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.javaPrimitiveType))
			}
		}[2]
		return deleteSurroundingText.invoke(proxy, beforeLength, afterLength) as Boolean
	}
}