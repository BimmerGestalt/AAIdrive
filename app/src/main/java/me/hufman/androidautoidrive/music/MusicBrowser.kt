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
	private var connected = false   // whether we are still connected
	private val mediaBrowser: MediaBrowserCompat    // all interactions with MediaBrowserCompat must be from the same thread
	var listener: Runnable? = null
		set(value) { field = value; if (connected) value?.run() }

	init {
		val component = ComponentName(musicAppInfo.packageName, musicAppInfo.className)

		// load the mediaBrowser on the UI thread
		mediaBrowser = runBlocking(handler.asCoroutineDispatcher()) {
			Log.i(TAG, "Opening connection to ${musicAppInfo.name}")
			MediaBrowserCompat(context, component, object : MediaBrowserCompat.ConnectionCallback() {
				override fun onConnected() {
					Log.i(TAG, "Connected to ${musicAppInfo.name}, triggering listener $listener")
					connecting = false
					connected = true

					listener?.run()
				}

				override fun onConnectionSuspended() {
					Log.i(TAG, "Disconnected from ${musicAppInfo.name}")
					connecting = false
					connected = false
				}

				override fun onConnectionFailed() {
					Log.i(TAG, "Failed connecting to ${musicAppInfo.name}")
					connecting = false
					connected = false
				}
			}, null).apply {
				Log.i(TAG, "Connecting to the app ${musicAppInfo.name}")
				connecting = true
				connect()
			}
		}
	}


	private fun getRoot(): String {
		if (!connected) return "disconnected root"
		return when (musicAppInfo.packageName) {
			"com.spotify.music" -> "com.google.android.projection.gearhead---spotify_media_browser_root_android_auto"   // the Android Auto root
			else -> mediaBrowser.root
		}
	}

	suspend fun connect() {
		if (connected) return
		if (!connecting) {
			connecting = true
			mediaBrowser.connect()
		}
		while (connecting) {
			delay(50)
		}
	}

	fun disconnect() {
		if (Looper.myLooper() == handler.looper) {
			mediaBrowser.disconnect()
		} else {
			runBlocking(handler.asCoroutineDispatcher()) {
				mediaBrowser.disconnect()
			}
		}
	}

	suspend fun browse(path: String?): List<MediaBrowserCompat.MediaItem> {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>>()
		withContext(handler.asCoroutineDispatcher()) {
			connect()
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
			} else {
				deferred.complete(LinkedList())
			}
		}
		return deferred.await()
	}

	suspend fun search(query: String): List<MediaBrowserCompat.MediaItem>? {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>?>()
		withContext(handler.asCoroutineDispatcher()) {
			connect()
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
		}
		return deferred.await()
	}

	fun getController(): MediaControllerCompat {
		if (Looper.myLooper() != handler.looper) {
			Log.w(TAG, "Fetching controller from a different thread, might cause problems")
		}
		val token = mediaBrowser.sessionToken
		return MediaControllerCompat(context, token)
	}
}