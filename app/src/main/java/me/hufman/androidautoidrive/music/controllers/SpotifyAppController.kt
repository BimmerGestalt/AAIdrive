package me.hufman.androidautoidrive.music.controllers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.Log
import android.util.LruCache
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import kotlinx.coroutines.CompletableDeferred
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.Observable
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.*
import me.hufman.androidautoidrive.music.PlaybackPosition
import java.lang.Exception
import java.util.*

class SpotifyAppController(context: Context, val remote: SpotifyAppRemote): MusicAppController {
	companion object {
		const val TAG = "SpotifyAppController"
		const val REDIRECT_URI = "me.hufman.androidautoidrive://spotify_callback"
		
		fun hasSupport(context: Context): Boolean {
			val CLIENT_ID = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.spotify.music.API_KEY", "unavailable")
			return CLIENT_ID != "unavailable" && CLIENT_ID != ""
		}

		fun MusicMetadata.Companion.fromSpotify(track: Track, coverArt: Bitmap? = null): MusicMetadata {
			// general track metadata
			return MusicMetadata(mediaId = track.uri, queueId = track.uri.hashCode().toLong(),
					title = track.name, album = track.album.name, artist = track.artist.name, coverArt = coverArt)
		}

		fun MusicMetadata.Companion.fromSpotifyQueueListItem(listItem: ListItem): MusicMetadata {
			return MusicMetadata(mediaId = listItem.uri, queueId = listItem.uri.hashCode().toLong(),
					title = listItem.title, artist = listItem.subtitle,
					playable = listItem.playable, browseable = listItem.hasChildren,
					coverArtUri = listItem.imageUri?.raw)
		}

		fun MusicMetadata.Companion.fromSpotify(listItem: ListItem, coverArt: Bitmap? = null): MusicMetadata {
			// browse result
			return MusicMetadata(mediaId = listItem.uri, queueId = listItem.uri.hashCode().toLong(),
					title = listItem.title, subtitle = listItem.subtitle,
					playable = listItem.playable, browseable = listItem.hasChildren, coverArt = coverArt)
		}
		fun MusicMetadata.toListItem(): ListItem {
			return ListItem(this.mediaId, this.mediaId, null, this.title, this.subtitle,
					this.playable, this.browseable)
		}

		fun CustomAction.Companion.fromSpotify(name: String, icon: Drawable? = null): CustomAction {
			return formatCustomActionDisplay(CustomAction("com.spotify.music", name, name, icon, null))
		}
	}

	class Connector(val context: Context): MusicAppController.Connector {
		var lastError: Throwable? = null

		override fun connect(appInfo: MusicAppInfo): Observable<SpotifyAppController> {
			val pendingController = MutableObservable<SpotifyAppController>()
			if (appInfo.packageName != "com.spotify.music") {
				pendingController.value = null
				return pendingController
			}

			Log.w(TAG, "Attempting to connect to Spotify Remote")

			val CLIENT_ID = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
					.metaData.getString("com.spotify.music.API_KEY", "unavailable")
			val params = ConnectionParams.Builder(CLIENT_ID)
					.setRedirectUri(REDIRECT_URI)
					.showAuthView(true)
					.build()


			val remoteListener = object: com.spotify.android.appremote.api.Connector.ConnectionListener {
				override fun onFailure(e: Throwable?) {
					Log.e(TAG, "Failed to connect to Spotify Remote: $e")
					if (hasSupport(context)) {
						// show an error to the UI, unless we don't have an API key
						this@Connector.lastError = e
					}
					// disconnect an existing session, if any
					pendingController.value?.disconnect()
					if (pendingController.pending) {
						pendingController.value = null
					}
				}

				override fun onConnected(remote: SpotifyAppRemote?) {
					if (remote != null) {
						Log.i(TAG, "Successfully connected to Spotify Remote")
						pendingController.value = SpotifyAppController(context, remote)

						// if app discovery says we aren't able to connect, discover again
						if (!appInfo.connectable) {
							MusicAppDiscovery(context, Handler()).probeApp(appInfo)
						}
					} else {
						Log.e(TAG, "Connected to a null Spotify Remote?")
						pendingController.value = null
					}
				}
			}

			SpotifyAppRemote.connect(context, params, remoteListener)
			return pendingController
		}
	}

	// create the Custom Actions during client creation, so that the language is loaded
	val CUSTOM_ACTION_TURN_REPEAT_ALL_ON = CustomAction.fromSpotify("TURN_REPEAT_ALL_ON",
			context.getDrawable(R.drawable.spotify_repeat_off))
	val CUSTOM_ACTION_TURN_REPEAT_ONE_ON = CustomAction.fromSpotify("TURN_REPEAT_ONE_ON",
			context.getDrawable(R.drawable.spotify_repeat_on))
	val CUSTOM_ACTION_TURN_REPEAT_ONE_OFF = CustomAction.fromSpotify("TURN_REPEAT_ONE_OFF",
			context.getDrawable(R.drawable.spotify_repeat_one))
	val CUSTOM_ACTION_ADD_TO_COLLECTION = CustomAction.fromSpotify("ADD_TO_COLLECTION",
			context.getDrawable(R.drawable.spotify_add_library))
	val CUSTOM_ACTION_REMOVE_FROM_COLLECTION = CustomAction.fromSpotify("REMOVE_FROM_COLLECTION",
			context.getDrawable(R.drawable.spotify_added_library))
	val CUSTOM_ACTION_START_RADIO = CustomAction.fromSpotify("START_RADIO",
			context.getDrawable(R.drawable.spotify_start_radio))

	var connected = true

	// Spotify is very asynchronous, save any subscription state for the getters
	var callback: ((MusicAppController) -> Unit)? = null    // UI listener
	val spotifySubscription: Subscription<PlayerState> = remote.playerApi.subscribeToPlayerState()
	val playlistSubscription: Subscription<PlayerContext> = remote.playerApi.subscribeToPlayerContext()
	var playerActions: PlayerRestrictions? = null
	var playerOptions: PlayerOptions? = null
	var currentTrack: MusicMetadata? = null
	var currentSongCoverArtCache = LruCache<ImageUri, Bitmap>(4)
	var position: PlaybackPosition = PlaybackPosition(true, 0, 0, -1)
	var currentTrackLibrary: Boolean? = null
	var queueUri: String? = null
	var queueItems: List<MusicMetadata> = LinkedList()
	var queueMetadata: QueueMetadata? = null
	val coverArtCache = LruCache<ImageUri, Bitmap>(50)

	init {
		spotifySubscription.setEventCallback { playerState ->
			Log.d(TAG, "Heard an update from Spotify")

			// update the available actions
			playerActions = playerState.playbackRestrictions
			playerOptions = playerState.playbackOptions

			// update the current track info
			val track = playerState.track
			if (track != null) {
				val cachedCoverArt = currentSongCoverArtCache[track.imageUri]
				currentTrack = MusicMetadata.fromSpotify(track, coverArt = cachedCoverArt)
				val loadingTrack = currentTrack
				if (cachedCoverArt == null) {
					// try to load the coverart
					val coverArtLoader = remote.imagesApi.getImage(track.imageUri)
					coverArtLoader.setResultCallback { coverArt ->
						currentSongCoverArtCache.put(track.imageUri, coverArt)
						if (loadingTrack?.mediaId == currentTrack?.mediaId) {   // still playing the same song
							currentTrack = MusicMetadata.fromSpotify(track, coverArt = coverArt)
							callback?.invoke(this)
						}
					}
				}
				currentTrackLibrary = null
				remote.userApi.getLibraryState(track.uri).setResultCallback {
					currentTrackLibrary = it.isAdded
				}
			} else {
				currentTrack = null
			}

			// update a progress bar
			position = PlaybackPosition(playerState.isPaused, lastPosition = playerState.playbackPosition, maximumPosition = playerState.track.duration)

			callback?.invoke(this)
		}

		playlistSubscription.setEventCallback { playerContext ->
			Log.d(TAG, "Heard an update from Spotify queue: ${playerContext.uri}")
			// update the current queue
			val uri = playerContext.uri
			if (queueUri != uri) {
				queueUri = uri
				queueItems = emptyList()
				if (uri != null) {
					val listItem = ListItem(uri, uri, null, playerContext.title, playerContext.subtitle, false, true)
					loadPaginatedItems(listItem, { queueUri == playerContext.uri }) {
						queueItems = it

						// shuffle play button somehow gets returned with the rest of the tracks when loading an album
						if (queueItems.isNotEmpty() && queueItems[0].artist == "" && queueItems[0].title == "Shuffle Play") {
							queueItems = queueItems.drop(1)
						}

						buildQueueMetadata()
						callback?.invoke(this)
					}
				}
			}

			callback?.invoke(this)
		}
	}

	/**
	 * Retrieves the cover art Bitmap for the provided ImageUri. If it is present in the coverArtCache
	 * then the Bitmap is returned otherwise the imagesApi is called to asynchronously add the cover
	 * art Bitmap to the cache and returns null in the meantime.
	 */
	fun getCoverArt(imageUri: ImageUri): Bitmap? {
		val coverArt = coverArtCache.get(imageUri)
		if (coverArt != null) {
			return coverArt
		} else {
			remote.imagesApi.getImage(imageUri, Image.Dimension.THUMBNAIL).setResultCallback { coverArtResponse ->
				coverArtCache.put(imageUri, coverArtResponse)
			}
		}
		return null
	}

	/**
	 * Creates the QueueMetadata for the current queue containing the title, subtitle, queue songs,
	 * and queue cover art.
	 */
	private fun buildQueueMetadata() {
		val recentlyPlayedUri = "com.spotify.recently-played"
		val li = ListItem(recentlyPlayedUri, recentlyPlayedUri, null, null, null, false, true)
		remote.contentApi.getChildrenOfItem(li, 1, 0).setResultCallback { recentlyPlayed ->
			val item = recentlyPlayed?.items?.get(0)
			if (item != null) {
				remote.imagesApi.getImage(item.imageUri, Image.Dimension.THUMBNAIL).setResultCallback { coverArt ->
					queueMetadata = QueueMetadata(item.title,item.subtitle,SpotifyMusicMetadata.createSpotifyMusicMetadataList(this, queueItems),coverArt)
				}
			}
		}
	}

	/**
	 * Loads the complete children contents of the given parentItem
	 * checking if stillValid at each step
	 * and then passing it to onComplete at the end
	 */
	private fun loadPaginatedItems(parentItem: ListItem,
	                               stillValid: () -> Boolean, onComplete: (List<MusicMetadata>) -> Unit) {
		loadPaginatedItems(LinkedList(), parentItem, stillValid, onComplete)
	}

	/**
	 * Loads the complete children contents of the given parentItem
	 * into the destination list
	 * checking if stillValid at each step
	 * and passing the destination list to onComplete at the end
	 */
	private fun loadPaginatedItems(destination: MutableList<MusicMetadata>, parentItem: ListItem,
	                               stillValid: () -> Boolean, onComplete: (List<MusicMetadata>) -> Unit) {
		remote.contentApi.getChildrenOfItem(parentItem, 200, destination.size).setResultCallback { items ->
			if (items != null && stillValid()) {    // this is still a valid request
				destination.addAll(items.items.filterNotNull().map { listItem: ListItem ->
					MusicMetadata.fromSpotifyQueueListItem(listItem)
				})
				if (destination.size < items.total && items.items.isNotEmpty()) {   // more tracks to load
					loadPaginatedItems(destination, parentItem, stillValid, onComplete)
				} else {
					onComplete(destination)
				}
			}
		}.setErrorCallback {
			Log.w(TAG, "Unable to fetch Spotify content", it)
			onComplete(destination)
		}
	}

	override fun play() {
		remote.connectApi.connectSwitchToLocalDevice()
		remote.playerApi.resume()
	}

	override fun pause() {
		remote.playerApi.pause()
	}

	override fun skipToPrevious() {
		remote.playerApi.skipPrevious()
	}

	override fun skipToNext() {
		remote.playerApi.skipNext()
	}

	override fun seekTo(newPos: Long) {
		remote.playerApi.seekTo(newPos)
	}

	override fun playSong(song: MusicMetadata) {
		remote.playerApi.play(song.mediaId)
	}

	override fun playQueue(song: MusicMetadata) {
		// queue item equality is based on queueId, which we are storing as the uri hashCode
		// we don't know the index to jump to
		// so, we have to iterate through the queue to find the user's selected queueId
		val queueUri = this.queueUri
		if (song.queueId != null) {
			queueItems.forEachIndexed { index, it ->
				if (it.queueId == song.queueId) {
					remote.playerApi.skipToIndex(queueUri, index)
				}
			}
		}
	}

	override fun playFromSearch(search: String) {
	}

	override fun customAction(action: CustomAction) {
		when (action) {
			CUSTOM_ACTION_TURN_REPEAT_ALL_ON -> remote.playerApi.setRepeat(Repeat.ALL)
			CUSTOM_ACTION_TURN_REPEAT_ONE_ON -> remote.playerApi.setRepeat(Repeat.ONE)
			CUSTOM_ACTION_TURN_REPEAT_ONE_OFF -> remote.playerApi.setRepeat(Repeat.OFF)
			CUSTOM_ACTION_ADD_TO_COLLECTION -> currentTrack?.mediaId?.let { remote.userApi.addToLibrary(it) }
			CUSTOM_ACTION_REMOVE_FROM_COLLECTION -> currentTrack?.mediaId?.let { remote.userApi.removeFromLibrary(it) }
		}
	}

	override fun getQueue(): QueueMetadata? {
		// unreliable per https://github.com/spotify/android-sdk/issues/10
		return queueMetadata
	}

	override fun getMetadata(): MusicMetadata? {
		return currentTrack
	}

	override fun getPlaybackPosition(): PlaybackPosition {
		return position
	}

	override fun isSupportedAction(action: MusicAction): Boolean {
		val playerActions = playerActions
		return when (action) {
			MusicAction.SKIP_TO_PREVIOUS -> playerActions?.canSkipPrev == true
			MusicAction.SKIP_TO_NEXT -> playerActions?.canSkipNext == true
			MusicAction.PLAY -> true
			MusicAction.PAUSE -> true
			MusicAction.SEEK_TO -> playerActions?.canSeek == true
			MusicAction.SKIP_TO_QUEUE_ITEM -> false
			MusicAction.SET_SHUFFLE_MODE -> playerActions?.canToggleShuffle == true
			MusicAction.SET_REPEAT_MODE -> playerActions?.canRepeatContext == true || playerActions?.canRepeatTrack == true
			// figure out search
			else -> false
		}
	}

	override fun getCustomActions(): List<CustomAction> {
		val actions = LinkedList<CustomAction>()

		// Needed for when the repeat button isn't supported by the hmiState
		if (getRepeatMode() == RepeatMode.ALL) {
			if (playerActions?.canRepeatTrack == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_ON)
			}
			else {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_OFF)
			}
		} else if (getRepeatMode() == RepeatMode.ONE) {
			actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_OFF)
		}
		else {
			if (playerActions?.canRepeatContext == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ALL_ON)
			}
			else if (playerActions?.canRepeatTrack == true) {
				actions.add(CUSTOM_ACTION_TURN_REPEAT_ONE_ON)
			}
		}

		if (currentTrackLibrary == false) actions.add(CUSTOM_ACTION_ADD_TO_COLLECTION)
		if (currentTrackLibrary == true) actions.add(CUSTOM_ACTION_REMOVE_FROM_COLLECTION)

		// START_RADIO is blocked on https://github.com/spotify/android-sdk/issues/66

		return actions
	}

	override fun toggleShuffle() {
		val shuffling = isShuffling()
		remote.playerApi.setShuffle(!shuffling)
	}

	override fun toggleRepeat() {
		if (playerOptions?.repeatMode == Repeat.OFF) {
			if (playerActions?.canRepeatContext == true) {
				remote.playerApi.setRepeat(Repeat.ALL)
			} else if (playerActions?.canRepeatTrack == true) {
				remote.playerApi.setRepeat(Repeat.ONE)
			}
		} else if (playerOptions?.repeatMode == Repeat.ALL) {
			if (playerActions?.canRepeatTrack == true) {
				remote.playerApi.setRepeat(Repeat.ONE)
			} else {
				remote.playerApi.setRepeat(Repeat.OFF)
			}
		} else {
			remote.playerApi.setRepeat(Repeat.OFF)
		}
	}

	override fun isShuffling(): Boolean {
		return playerOptions?.isShuffling == true
	}

	override fun getRepeatMode(): RepeatMode {
		return when(playerOptions?.repeatMode) {
			Repeat.ALL -> RepeatMode.ALL
			Repeat.ONE -> RepeatMode.ONE
			Repeat.OFF -> RepeatMode.OFF
			else -> RepeatMode.OFF
		}
	}

	override suspend fun browse(directory: MusicMetadata?): List<MusicMetadata> {
		val deferred = CompletableDeferred<List<MusicMetadata>>()
		if (directory?.mediaId == null) {
			remote.contentApi.getRecommendedContentItems("default-cars").setResultCallback { results ->
				deferred.complete(results?.items?.map {
					MusicMetadata.fromSpotify(it)
				} ?: LinkedList())
			}.setErrorCallback {
				deferred.complete(LinkedList())
			}
		} else {
			loadPaginatedItems(directory.toListItem(), { !deferred.isCancelled }) {
				deferred.complete(it)
			}
		}
		return deferred.await()
	}

	override suspend fun search(query: String): List<MusicMetadata>? {
		// requires a Web API call, not sure how to get the access token
		return null
	}

	override fun subscribe(callback: (MusicAppController) -> Unit) {
		this.callback = callback
	}

	override fun isConnected(): Boolean {
		return this.connected
	}

	override fun disconnect() {
		Log.d(TAG, "Disconnecting from Spotify")
		this.connected = false
		this.callback = null
		try {
			spotifySubscription.cancel()
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}
		try {
			playlistSubscription.cancel()
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}
		try {
			SpotifyAppRemote.disconnect(remote)
		} catch (e: Exception) {
			Log.w(TAG, "Exception while disconnecting from Spotify: $e")
		}
	}

	override fun toString(): String {
		return "SpotifyAppController"
	}
}