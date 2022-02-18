package me.hufman.androidautoidrive

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.media.ImageReader
import android.os.Bundle
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import me.hufman.androidautoidrive.carapp.maps.StaticScreenCaptureConfig
import me.hufman.androidautoidrive.carapp.maps.VirtualDisplayScreenCapture
import org.awaitility.Awaitility.await

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTestVirtualDisplay {

	val frameListener = mock<ImageReader.OnImageAvailableListener> {  }

	@Test
	fun testLifecycle() {
		/** Test that the VirtualDisplay can get created and destroyed */
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		Looper.prepare()
		val screenCaptureConfig = StaticScreenCaptureConfig(1000, 400)
		val testCapture = VirtualDisplayScreenCapture.build(screenCaptureConfig)
		testCapture.registerImageListener(frameListener)
		val virtualDisplay = VirtualDisplayScreenCapture.createVirtualDisplay(appContext, testCapture.imageCapture)
		val projection = MockProjection(appContext, virtualDisplay.display)
		projection.show()

		await().untilAsserted {
			verify(frameListener).onImageAvailable(any())
			projection.changeColor()
		}

		testCapture.onDestroy()
		virtualDisplay.release()
	}
}

class MockProjection(parentContext: Context, display: Display): Presentation(parentContext, display) {

	val colors = ArrayList<Int>()
	var colorIndex = 0
	val view = ImageView(context)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		colors.add(Color.CYAN)
		colors.add(Color.BLUE)
		colors.add(Color.RED)

		window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)

		view.setBackgroundColor(colors[colorIndex])
		setContentView(view)
	}

	fun changeColor() {
		colorIndex = (colorIndex + 1) % colors.size
		view.setBackgroundColor(colors[colorIndex])
	}
}