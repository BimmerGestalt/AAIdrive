package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.*

class MusicBrowser(val context: Context, val handler: Handler, val musicAppInfo: MusicAppInfo) {
	private val TAG = "MusicBrowser"
	private var connecting = false
	var connected = false   // whether we are still connected
		private set
	private val mediaBrowser: MediaBrowserCompat    // all interactions with MediaBrowserCompat must be from the same thread
	var mediaController: MediaControllerCompat? = null  // after the connection, this will become valid
		private set

	var listener: Runnable? = null
		set(value) { field = value; if (connected) value?.run() }

	/**
	 * runBlocking, but only if we need to switch to the handler thread
	 */
	private inline fun <T> runBlockingIfNecessary(crossinline task: () -> T): T {
		return if (Looper.myLooper() == handler.looper) {
			task()
		} else {
			runBlocking(handler.asCoroutineDispatcher()) {
				task()
			}
		}
	}
	init {
		val component = ComponentName(musicAppInfo.packageName, musicAppInfo.className)

		// load the mediaBrowser on the UI thread
		mediaBrowser = runBlockingIfNecessary {
			Log.i(TAG, "Opening connection to ${musicAppInfo.name}")
			MediaBrowserCompat(context, component, ConnectionCallback(), null).apply {
				Log.i(TAG, "Connecting to the app ${musicAppInfo.name}")
				connect()
			}
		}
		Log.i(TAG, "Connecting state ${musicAppInfo.name}: connecting:$connecting connected:$connected")
	}

	private inner class ConnectionCallback: MediaBrowserCompat.ConnectionCallback() {
		override fun onConnected() {
			Log.i(TAG, "Connected to ${musicAppInfo.name}, triggering listener $listener")
			synchronized(this@MusicBrowser) {
				connecting = false
				connected = true
			}

			// load up the controller
			mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken)

			// trigger any listener callbacks
			listener?.run()
		}

		override fun onConnectionSuspended() {
			Log.i(TAG, "Disconnected from ${musicAppInfo.name}")
			synchronized(this@MusicBrowser) {
				connecting = false
				connected = false
			}
		}

		override fun onConnectionFailed() {
			Log.i(TAG, "Failed connecting to ${musicAppInfo.name}")
			synchronized(this@MusicBrowser) {
				connecting = false
				connected = false
			}
		}
	}

	private fun getRoot(): String {
		if (!connected) return "disconnected root"
		return when (musicAppInfo.packageName) {
			"com.spotify.music" -> "com.google.android.projection.gearhead---spotify_media_browser_root_android_auto"   // the Android Auto root
			"com.aspiro.tidal" -> "__ROOT_LOGGED_IN__"   // Tidal Music
			else -> mediaBrowser.root
		}
	}

	fun connect() {
		Log.i(TAG, "Starting to connect ${musicAppInfo.name}")
		mediaBrowser.connect()
	}

	suspend fun waitForConnect() {
		(0..500).forEach { _ ->
			delay(50)
			synchronized(this) {
				if (!connecting) {
					return
				}
			}
		}
	}

	fun disconnect() {
		mediaController = null
		if (Looper.myLooper() == handler.looper) {
			mediaBrowser.disconnect()
		} else {
			handler.post {
				mediaBrowser.disconnect()
			}
		}
	}

	suspend fun browse(path: String?, timeout: Long = 5000): List<MediaBrowserCompat.MediaItem> {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>>()
		withContext(handler.asCoroutineDispatcher()) {
			waitForConnect()
			if (connected) {
				val browsePath = path ?: getRoot()
				mediaBrowser.subscribe(browsePath, object : MediaBrowserCompat.SubscriptionCallback() {
					override fun onError(parentId: String) {
						mediaBrowser.unsubscribe(browsePath)
						deferred.complete(LinkedList())
					}

					override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
						mediaBrowser.unsubscribe(browsePath)
						deferred.complete(children)
					}
				})
				// now we wait for the results
				withTimeout(timeout) {
					while (!deferred.isCompleted) {
						delay(100)
					}
				}
				if (!deferred.isCompleted) {
					deferred.complete(LinkedList())
				}
				true    // requires a boolean for this branch?
			} else {
				deferred.complete(LinkedList())
			}
		}
		return deferred.await()
	}

	suspend fun search(query: String, timeout: Long = 5000): List<MediaBrowserCompat.MediaItem>? {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>?>()
		withContext(handler.asCoroutineDispatcher()) {
			waitForConnect()
			if (connected) {
				mediaBrowser.search(query, null, object : MediaBrowserCompat.SearchCallback() {
					override fun onError(query: String, extras: Bundle?) {
						deferred.complete(null)
					}

					override fun onSearchResult(query: String, extras: Bundle?, items: MutableList<MediaBrowserCompat.MediaItem>) {
						deferred.complete(items)
					}
				})
			} else {
				deferred.complete(null)
			}
			// now we wait for the results
			withTimeout(timeout) {
				while (!deferred.isCompleted) {
					delay(100)
				}
			}
			if (!deferred.isCompleted) {
				deferred.complete(null)
			}
		}
		return deferred.await()
	}
}