package me.hufman.androidautoidrive

import android.content.Context
import android.util.Log
import me.hufman.androidautoidrive.carapp.carprojection.ProjectionApp
import me.hufman.androidautoidrive.carapp.maps.*
import me.hufman.carprojection.AppDiscovery

class ProjectionService(val context: Context) {
	var threadProjection: CarThread? = null
	var projectionScreenCapture: VirtualDisplayScreenCapture? = null
	var projectionApp: ProjectionApp? = null

	fun start(): Boolean {

		System.setProperty("org.mockito.android.target", context.getDir("mockito", Context.MODE_PRIVATE).absolutePath)
		synchronized(this) {
			if (threadProjection == null) {
				threadProjection = CarThread("Projection") {
					Log.i(MainService.TAG, "Starting Projection")
					val projectionScreenCapture = VirtualDisplayScreenCapture.build(ProjectionApp.MAX_WIDTH, ProjectionApp.MAX_HEIGHT)
					this.projectionScreenCapture = projectionScreenCapture

					projectionApp = ProjectionApp(context, CarAppAssetManager(context, "smartthings"),
							AppDiscovery(context), GraphicsHelpersAndroid(), projectionScreenCapture)
					val handler = threadProjection?.handler
					if (handler != null) {
						projectionApp?.onCreate(handler)
					}
				}
				threadProjection?.start()
			}
		}
		return true
	}
	fun stop() {
		val threadProjection = threadProjection ?: return
		threadProjection.handler?.post {
			projectionScreenCapture?.onDestroy()
			projectionApp?.onDestroy()
			threadProjection.handler?.looper?.quitSafely()
			projectionScreenCapture = null
			projectionApp = null
		}
		this.threadProjection = null
	}
}