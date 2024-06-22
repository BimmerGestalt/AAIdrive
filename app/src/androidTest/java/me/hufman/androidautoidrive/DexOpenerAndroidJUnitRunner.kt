package me.hufman.androidautoidrive

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import com.github.tmurakami.dexopener.DexOpener


class DexOpenerAndroidJUnitRunner: AndroidJUnitRunner() {
	@Throws(ClassNotFoundException::class, IllegalAccessException::class, InstantiationException::class)
	override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application? {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
			DexOpener.install(this) // Call me first!
		}
		return super.newApplication(cl, className, context)
	}
}