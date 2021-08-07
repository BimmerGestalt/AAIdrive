package me.hufman.androidautoidrive

import android.content.Context
import androidx.test.runner.screenshot.BasicScreenCaptureProcessor
import androidx.test.runner.screenshot.ScreenCapture
import java.io.File

class PrivateScreenshotProcessor(context: Context): BasicScreenCaptureProcessor() {
	init {
		this.mDefaultScreenshotPath = File(
			context.getExternalFilesDir(null)!!.absolutePath,
			"screenshots"
		)
	}

	override fun getFilename(prefix: String): String = prefix
	override fun process(capture: ScreenCapture?): String {
		val imageFolder = mDefaultScreenshotPath
		val filename = if (capture!!.name == null) defaultFilename else getFilename(capture.name)
		val suffix = "." + capture.format.toString().toLowerCase()
		val imageFile = File(imageFolder, filename + suffix)
		println("Saving to $imageFile")
		return super.process(capture)
	}
}