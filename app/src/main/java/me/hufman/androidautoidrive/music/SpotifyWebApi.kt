package me.hufman.androidautoidrive.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adamratzman.spotify.SpotifyApiOptionsBuilder
import com.adamratzman.spotify.SpotifyClientAPI
import com.adamratzman.spotify.SpotifyException
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.spotifyClientPkceApi
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.authentication.AuthorizationActivity
import me.hufman.androidautoidrive.music.spotify.authentication.SpotifyAuthStateManager
import net.openid.appauth.*

/**
 * Handles the logic around the Spotify Web API. This class is a singleton to prevent issues of having
 * multiple [SpotifyClientAPI]s.
 */
class SpotifyWebApi private constructor(val context: Context) {
	companion object {
		const val TAG = "SpotifyWebApi"

		var isUsingSpotify = false
		var webApiInstance: SpotifyWebApi? = null

		/**
		 * Retrieves the current [SpotifyWebApi] instance, creating one if it there are no instances
		 * currently running.
		 */
		fun getInstance(context: Context): SpotifyWebApi {
			if (webApiInstance == null) {
				webApiInstance = SpotifyWebApi(context)
			}
			return webApiInstance as SpotifyWebApi
		}
	}

	// will be null if the user is not authenticated
	private var webApi: SpotifyClientAPI? = null

	private val authStateManager: SpotifyAuthStateManager

	init {
		isUsingSpotify = true
		authStateManager = SpotifyAuthStateManager.getInstance(context)
		initializeWebApi()
	}

	fun getUserId(): String? {
		return webApi?.userId
	}

	/**
	 * Initializes the [SpotifyClientAPI] instance and updates the [AuthState] with the token used.
	 */
	fun initializeWebApi() {
		webApi = createWebApiClient()
		if (webApi != null) {
			updateAuthStateWithAccessToken(webApi!!.token)
		}
	}

	/**
	 * Returns if the current [AuthState] is authorized.
	 */
	fun isAuthorized(): Boolean {
		return authStateManager.currentState.isAuthorized
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
					Log.d(TAG, "Token refreshed - updating AuthState")
					updateAuthStateWithAccessToken(it.token)
				})
		val currentState = authStateManager.currentState
		val accessToken = currentState.accessToken
		if (accessToken != null) {
			return try {
				val expiration = currentState.accessTokenExpirationTime
				val refreshToken = currentState.refreshToken
				val scopes = currentState.scope
				val token = Token(accessToken, "Bearer", expiration!!.toInt(), refreshToken, scopes)
				val apiBuilder = spotifyClientPkceApi(
						clientId,
						SpotifyAppController.REDIRECT_URI,
						token,
						AuthorizationActivity.CODE_VERIFIER,
						spotifyApiOptionsBuilder)
				apiBuilder.build()
			} catch (e: SpotifyException.AuthenticationException) {
				val authorizationException = AuthorizationException(-1, -1, "Authentication Error", "Authentication failed with the message: ${e.message}", null, null)
				authStateManager.updateAfterTokenResponse(null, authorizationException)
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an access token. Access token is invalid")
				createNotAuthorizedNotification()
				null
			}
		} else {
			val authorizationCode = authStateManager.currentState.lastAuthorizationResponse?.authorizationCode
			if (authorizationCode == null) {
				Log.e(SpotifyAppController.TAG, "Failed to create the Web API with an authorization code. Authorization code is invalid or not found")
				createNotAuthorizedNotification()
				return null
			}

			return try {
				val apiBuilder = spotifyClientPkceApi(
						clientId,
						SpotifyAppController.REDIRECT_URI,
						authorizationCode,
						AuthorizationActivity.CODE_VERIFIER,
						spotifyApiOptionsBuilder)
				apiBuilder.build()
			} catch (e: SpotifyException.AuthenticationException) {
				updateAuthStateWithAuthorizationCodeException()
				Log.e(SpotifyAppController.TAG, "Failed to create the web API with an authorization code. Authorization failed with the error: ${e.message}")
				createNotAuthorizedNotification()
				null
			}
		}
	}

	/**
	 * Updates the [AuthState] last authorization code response with an [AuthorizationException].
	 */
	private fun updateAuthStateWithAuthorizationCodeException() {
		val authorizationException = AuthorizationException(-1, -1, "Authorization Code Invalid", "Authorization code invalid", null, null)
		authStateManager.updateAfterAuthorization(null, authorizationException)
	}

	/**
	 * Updates the [AuthState] with the [Token] information provided.
	 */
	private fun updateAuthStateWithAccessToken(token: Token) {
		val tokenRequest = TokenRequest.Builder(authStateManager.currentState.authorizationServiceConfiguration!!, SpotifyAppController.getClientId(context))
				.setRefreshToken(token.refreshToken)
				.setGrantType(GrantTypeValues.REFRESH_TOKEN)
				.build()
		val tokenResponse = TokenResponse.Builder(tokenRequest)
				.setAccessToken(token.accessToken)
				.setRefreshToken(token.refreshToken)
				.setAccessTokenExpirationTime(token.expiresAt)
				.setScopes(token.scopes?.map { it.uri })
				.build()
		authStateManager.updateAfterTokenResponse(tokenResponse, null)
	}

	/**
	 * Creates and displays a notification stating that the web API needs to be authorized.
	 */
	private fun createNotAuthorizedNotification() {
		val notifyIntent = Intent(context, AuthorizationActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
		}
		val notification = NotificationCompat.Builder(context, SpotifyAppController.CHANNEL_ID)
				.setContentTitle(context.getString(R.string.notification_title))
				.setContentText(context.getString(R.string.txt_spotify_auth_notification))
				.setSmallIcon(R.drawable.ic_notify)
				.setContentIntent(PendingIntent.getActivity(context, SpotifyAppController.NOTIFICATION_REQ_ID, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT))
				.build()
		val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(SpotifyAppController.CHANNEL_ID, "androidAutoIDrive", NotificationManager.IMPORTANCE_HIGH)
			notificationManager.createNotificationChannel(channel)
		}
		notificationManager.notify(SpotifyAppController.NOTIFICATION_REQ_ID, notification)
	}

	/**
	 * Disconnect process for shutting down the [SpotifyClientAPI].
	 */
	fun disconnect() {
		Log.d(TAG, "Disconnecting Web API")
		webApi?.shutdown()
		isUsingSpotify = false
	}
}