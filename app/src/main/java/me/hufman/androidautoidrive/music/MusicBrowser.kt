package me.hufman.androidautoidrive.music

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MusicBrowser(context: Context, val musicAppInfo: MusicAppInfo) {
	private var connecting = false
	private var connected = false   // whether we are still connected
	private val mediaBrowser: MediaBrowserCompat    // all interactions with MediaBrowserCompat must be from the same thread

	init {
		val component = ComponentName(musicAppInfo.packageName, musicAppInfo.className)

		// load the mediaBrowser on the UI thread
		mediaBrowser = runBlocking(Dispatchers.Main) {
			MediaBrowserCompat(context, component, object : MediaBrowserCompat.ConnectionCallback() {
				override fun onConnected() {
					connecting = false
					connected = true
				}

				override fun onConnectionSuspended() {
					connecting = false
					connected = false
				}

				override fun onConnectionFailed() {
					connecting = false
					connected = false
				}
			}, null)
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
		connecting = true
		mediaBrowser.connect()
		while (connecting) {
			delay(50)
		}
	}

	suspend fun browse(path: String?): List<MediaBrowserCompat.MediaItem> {
		val deferred = CompletableDeferred<List<MediaBrowserCompat.MediaItem>>()
		withContext(Dispatchers.Main) {
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
		withContext(Dispatchers.Main) {
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
}