package me.hufman.androidautoidrive.music.spotify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adamratzman.spotify.*
import com.adamratzman.spotify.models.Token
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.phoneui.SpotifyAuthorizationActivity
import net.openid.appauth.*

/**
 * Handles the logic around the Spotify Web API. This class is a singleton to prevent issues of having
 * multiple [SpotifyClientAPI]s.
 */
class SpotifyWebApi private constructor(val context: Context, val appSettings: MutableAppSettings) {
	companion object {
		const val TAG = "SpotifyWebApi"
		const val NOTIFICATION_CHANNEL_ID = "SpotifyAuthorization"
		const val NOTIFICATION_REQ_ID = 56

		private var webApiInstance: SpotifyWebApi? = null

		/**
		 * Retrieves the current [SpotifyWebApi] instance creating one if it there are no instances
		 * currently running.
		 */
		fun getInstance(context: Context, appSettings: MutableAppSettings): SpotifyWebApi {
			if (webApiInstance == null) {
				webApiInstance = SpotifyWebApi(context, appSettings)
			}
			return webApiInstance as SpotifyWebApi
		}
	}

	// will be null if the user is not authenticated
	private var webApi: SpotifyClientAPI? = null

	private val authStateManager: SpotifyAuthStateManager
	private var getLikedSongsAttempted: Boolean = false
	private var spotifyAppControllerCaller: SpotifyAppController? = null
	var isUsingSpotify: Boolean = false

	init {
		Log.d(TAG, "Initializing for the first time")
		authStateManager = SpotifyAuthStateManager.getInstance(appSettings)
	}

	/**
	 * Sets the Liked Songs flag to false and sets the [SpotifyAppController] caller to null.
	 */
	fun clearGetLikedSongsAttemptedFlag() {
		getLikedSongsAttempted = false
		spotifyAppControllerCaller = null
	}

	fun getLikedSongs(spotifyAppController: SpotifyAppController): List<SpotifyMusicMetadata>?  {
		if (webApi == null) {
			getLikedSongsAttempted = true
			spotifyAppControllerCaller = spotifyAppController
			return emptyList()
		}
		try {
			val likedSongs = webApi?.library?.getSavedTracks(50)?.getAllItemsNotNull()?.complete()
			return likedSongs?.map {
				val track = it.track
				val mediaId = track.uri.uri
				val artists = track.artists.map { it.name }.joinToString(", ")
				val album = track.album
				val coverArtIndex = album.images.indexOfFirst { it.height == 300 }
				val imageUrl = album.images[coverArtIndex].url
				val coverArtCode = imageUrl.substring(imageUrl.lastIndexOf("/")+1)
				val coverArtUri = "spotify:image:$coverArtCode"
				SpotifyMusicMetadata(spotifyAppController, mediaId, mediaId.hashCode().toLong(), coverArtUri, artists, album.name, track.name)
			}
		} catch (e: SpotifyException.AuthenticationException) {
			Log.e(TAG, "Failed to get data from Liked Songs library due to authentication error with the message: ${e.message}")
			authStateManager.addAccessTokenAuthorizationException(e)
			createNotAuthorizedNotification()
		} catch (e: SpotifyException) {
			Log.e(TAG, "Exception occurred while getting Liked Songs library data with message: ${e.message}")
		}
		return null
	}

	/**
	 * Initializes the [SpotifyClientAPI] instance and updates the [AuthState] with the token used.
	 */
	fun initializeWebApi() {
		webApi = createWebApiClient()
		if (webApi != null) {
			updateAuthStateWithAccessToken(webApi!!.token)

			if (getLikedSongsAttempted) {
				getLikedSongsAttempted = false
				spotifyAppControllerCaller?.createLikedSongsQueueMetadata()
			}
		}
	}

	/**
	 * Returns if the current [AuthState] is authorized.
	 */
	fun isAuthorized(): Boolean {
		return authStateManager.isAuthorized()
	}

	/**
	 * Creates the Web API client using the PKCE authentication flow. If the [SpotifyClientAPI] creation
	 * and validation process encounters a authentication failure, a null instance is returned and the
	 * [AuthState] updated with the exception.
	 */
	private fun createWebApiClient(): SpotifyClientAPI? {
		val clientId = SpotifyAppController.getClientId(context)
		val spotifyApiOptionsBuilder = SpotifyApiOptionsBuilder(
				retryWhenRateLimited = false,
				onTokenRefresh = {
					Log.d(TAG, "Updating AuthState with refreshed token")
					updateAuthStateWithAccessToken(it.token)
				})
		val accessToken = authStateManager.getAccessToken()
		if (accessToken != null) {
			return try {
				val expirationIn = authStateManager.getAccessTokenExpirationIn()
				val refreshToken = authStateManager.getRefreshToken()
				val scopes = authStateManager.getScopeString()
				val token = Token(accessToken, "Bearer", expirationIn.toInt(), refreshToken, scopes)
				val apiBuilder = SpotifyClientApiBuilderHelper.createApiBuilderWithAccessToken(clientId, token, spotifyApiOptionsBuilder)
				apiBuilder.build()
			} catch (e: SpotifyException.AuthenticationException) {
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an access token. Access token is invalid")
				authStateManager.addAccessTokenAuthorizationException(e)
				createNotAuthorizedNotification()
				null
			}
		} else {
			val authorizationCode = authStateManager.getAuthorizationCode()
			if (authorizationCode == null) {
				Log.e(SpotifyAppController.TAG, "Failed to create the Web API with an authorization code. Authorization code is invalid or not found")
				createNotAuthorizedNotification()
				return null
			}

			return try {
				val apiBuilder = SpotifyClientApiBuilderHelper.createApiBuilderWithAuthorizationCode(clientId, authorizationCode, spotifyApiOptionsBuilder)
				apiBuilder.build()
			} catch (e: SpotifyException.AuthenticationException) {
				authStateManager.addAuthorizationCodeAuthorizationException(e)
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an authorization code. Authorization failed with the error: ${e.message}")
				createNotAuthorizedNotification()
				null
			}
		}
	}

	/**
	 * Updates the [AuthState] with the [Token] information provided.
	 */
	private fun updateAuthStateWithAccessToken(token: Token) {
		val tokenRequest = TokenRequest.Builder(authStateManager.getAuthorizationServiceConfiguration()!!, SpotifyAppController.getClientId(context))
				.setRefreshToken(token.refreshToken)
				.setGrantType(GrantTypeValues.REFRESH_TOKEN)
				.build()
		val tokenResponse = TokenResponse.Builder(tokenRequest)
				.setAccessToken(token.accessToken)
				.setRefreshToken(token.refreshToken)
				.setAccessTokenExpirationTime(token.expiresAt)
				.setScopes(token.scopes?.map { it.uri })
				.build()
		authStateManager.updateTokenResponse(tokenResponse, null)
	}

	/**
	 * Creates and displays a notification stating that the web API needs to be authorized only if the
	 * user has previously successfully authorized.
	 */
	private fun createNotAuthorizedNotification() {
		if (appSettings[AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION] == "false") {
			return
		}
		val notifyIntent = Intent(context, SpotifyAuthorizationActivity::class.java)
		notifyIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
				.setContentTitle(context.getString(R.string.notification_title))
				.setContentText(context.getString(R.string.txt_spotify_auth_notification))
				.setSmallIcon(R.drawable.ic_notify)
				.setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_REQ_ID, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.build()
		val notificationManager = context.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
					context.getString(R.string.notification_channel_spotify),
					NotificationManager.IMPORTANCE_HIGH)
			notificationManager.createNotificationChannel(channel)
		}
		notificationManager.notify(NOTIFICATION_REQ_ID, notification)
	}

	/**
	 * Disconnect process for shutting down the [SpotifyClientAPI].
	 */
	fun disconnect() {
		Log.d(TAG, "Disconnecting Web API")
		webApi?.shutdown()
		isUsingSpotify = false
		clearGetLikedSongsAttemptedFlag()
	}

	/**
	 * Helper class for creating [SpotifyClientApiBuilder]s through the PKCE authentication flow.
	 */
	class SpotifyClientApiBuilderHelper {
		companion object {
			/**
			 * Creates a [SpotifyClientApiBuilder] from an authorization code.
			 */
			fun createApiBuilderWithAuthorizationCode(clientId: String, authorizationCode: String, spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder): SpotifyClientApiBuilder {
				return spotifyClientPkceApi(
						clientId,
						SpotifyAppController.REDIRECT_URI,
						authorizationCode,
						SpotifyAuthorizationActivity.CODE_VERIFIER,
						spotifyApiOptionsBuilder)
			}

			/**
			 * Creates a [SpotifyClientApiBuilder] from an access token.
			 */
			fun createApiBuilderWithAccessToken(clientId: String, token: Token, spotifyApiOptionsBuilder: SpotifyApiOptionsBuilder): SpotifyClientApiBuilder {
				return spotifyClientPkceApi(
						clientId,
						SpotifyAppController.REDIRECT_URI,
						token,
						SpotifyAuthorizationActivity.CODE_VERIFIER,
						spotifyApiOptionsBuilder)
			}
		}
	}
}