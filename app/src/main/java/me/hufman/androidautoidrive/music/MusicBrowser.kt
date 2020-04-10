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
	private var mediaBrowser: MediaBrowserCompat? = null    // all interactions with MediaBrowserCompat must be from the same thread
	var mediaController: MediaControllerCompat? = null  // after the connection, this will become valid
		private set

	var listener: Runnable? = null
		set(value) { field = value; if (connected) value?.run() }

	init {
		if (musicAppInfo.className == null) {
			Log.i(TAG, "Skipping connection to ${musicAppInfo.name}, no className found")
		} else {
			val component = ComponentName(musicAppInfo.packageName, musicAppInfo.className)
			Log.i(TAG, "Opening connection to ${musicAppInfo.name}")
			// load the mediaBrowser on the UI thread
			handler.post {
				Log.i(TAG, "Connecting to the app ${musicAppInfo.name}")
				connecting = true
				mediaBrowser = MediaBrowserCompat(context, component, ConnectionCallback(), null)
				mediaBrowser?.connect()
			}
		}
	}

	private inner class ConnectionCallback: MediaBrowserCompat.ConnectionCallback() {
		override fun onConnected() {
			Log.i(TAG, "Connected to ${musicAppInfo.name}, triggering listener $listener")
			synchronized(this@MusicBrowser) {
				connecting = false
				connected = true
			}

			// load up the controller
			val browser = mediaBrowser
			if (browser != null && browser.sessionToken != null) {
				mediaController = MediaControllerCompat(context, browser.sessionToken)
			}

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
			"com.spotify.music" -> "com.google.android.projection.gearhead---spotify_media_browser_root_android_auto"   // the Android Auto root for Spotify
			"com.aspiro.tidal" -> "__ROOT_LOGGED_IN__"   // Tidal Music
			"com.apple.android.music" -> "__AUTO_ROOT__"     // Apple Music
			"com.radio.fmradio" -> "__ROOT__"   // Radio FM
			else -> mediaBrowser?.root ?: "disconnected root"
		}
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

	/** Reconnect to this app manually */
	fun reconnect() {
		handler.post {
			if (connected) {
				Log.i(TAG, "Reconnecting to existing app ${mediaBrowser?.serviceComponent?.packageName}")
			}
			synchronized(this@MusicBrowser) {
				mediaController = null
				connected = false
				connecting = true
			}
			mediaBrowser?.disconnect()

			if (musicAppInfo.className != null) {
				Log.i(TAG, "Connecting to the app ${musicAppInfo.name}")
				val component = ComponentName(musicAppInfo.packageName, musicAppInfo.className)
				mediaBrowser = MediaBrowserCompat(context, component, ConnectionCallback(), null)
				mediaBrowser?.connect()
			}
		}
	}

	fun disconnect() {
		mediaController = null
		handler.post {
			connected = false
			mediaBrowser?.disconnect()
		}
	}

	suspend fun browse(path: String?, timeout: Long = 5000): List<MediaBrowserCompat.MediaItem> {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>>()
		withContext(handler.asCoroutineDispatcher()) {
			waitForConnect()
			if (connected) {
				val browsePath = path ?: getRoot()
				mediaBrowser?.subscribe(browsePath, object : MediaBrowserCompat.SubscriptionCallback() {
					override fun onError(parentId: String) {
						mediaBrowser?.unsubscribe(browsePath)
						deferred.complete(LinkedList())
					}

					override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
						mediaBrowser?.unsubscribe(browsePath)
						deferred.complete(children)
					}

					override fun onError(parentId: String, options: Bundle) {
						onError(parentId)
					}

					override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>, options: Bundle) {
						onChildrenLoaded(parentId, children)
					}
				})

				// now we wait for the results
				try {
					withTimeout(timeout) {
						while (!deferred.isCompleted) {
							delay(100)
						}
					}
				} catch (e: CancellationException) {
					// timeout expired
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
				mediaBrowser?.search(query, null, object : MediaBrowserCompat.SearchCallback() {
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
			try {
				withTimeout(timeout) {
					while (!deferred.isCompleted) {
						delay(100)
					}
				}
			} catch (e: CancellationException) {
				// timeout expired
			}
			if (!deferred.isCompleted) {
				deferred.complete(null)
			}
		}
		return deferred.await()
	}
}