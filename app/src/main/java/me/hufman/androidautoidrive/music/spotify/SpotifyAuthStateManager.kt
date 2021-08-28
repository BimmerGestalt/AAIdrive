package me.hufman.androidautoidrive.music.spotify

import android.util.Log
import com.adamratzman.spotify.SpotifyException
import com.adamratzman.spotify.models.Token
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import net.openid.appauth.*
import org.json.JSONException
import java.lang.Exception

/**
 * Spotify manager for an [AuthState] instance that is created from and mutates a JSON stored [AuthState]
 * in [AppSettings]. This class is a singleton.
 */
class SpotifyAuthStateManager private constructor(val appSettings: MutableAppSettings) {
	companion object {
		const val TAG = "AuthStateManager"

		private var authStateManagerInstance: SpotifyAuthStateManager? = null

		/**
		 * Retrieves the current [SpotifyAuthStateManager] instance, creating one if it there are no
		 * current instances.
		 */
		fun getInstance(appSettings: MutableAppSettings): SpotifyAuthStateManager {
			if (authStateManagerInstance == null) {
				authStateManagerInstance = SpotifyAuthStateManager(appSettings)
			}
			return authStateManagerInstance as SpotifyAuthStateManager
		}
	}

	var currentState: AuthState

	init {
		currentState = readState()
	}

	/**
	 * Returns the authorization code last saved to the [AuthState].
	 */
	fun getAuthorizationCode(): String? {
		return currentState.lastAuthorizationResponse?.authorizationCode
	}

	/**
	 * Returns the access token last saved to the [AuthState].
	 */
	fun getAccessToken(): String? {
		return currentState.accessToken
	}

	/**
	 * Returns the access token time remaining before it is expired from what was last saved to the
	 * [AuthState].
	 */
	fun getAccessTokenExpirationIn(): Long {
		return currentState.accessTokenExpirationTime?.minus(System.currentTimeMillis())?.div(1000) ?: 0
	}

	/**
	 * Returns the refresh token last saved to the [AuthState].
	 */
	fun getRefreshToken(): String? {
		return currentState.refreshToken
	}

	/**
	 * Returns a consolidated string of all the scopes authorized in the previous authorization
	 * response saved to the [AuthState].
	 */
	fun getScopeString(): String? {
		return currentState.scope
	}

	/**
	 * Returns the last [AuthorizationServiceConfiguration] saved to the [AuthState].
	 */
	fun getAuthorizationServiceConfiguration(): AuthorizationServiceConfiguration? {
		return currentState.authorizationServiceConfiguration
	}

	/**
	 * Returns if the current [AuthState] is authorized.
	 */
	fun isAuthorized(): Boolean {
		return currentState.isAuthorized
	}

	/**
	 * Updates the [AuthState] with the authorization response and authorization exception, replacing
	 * the old AuthState in the shared preferences file with the new one.
	 */
	fun updateAuthorizationResponse(response: AuthorizationResponse?, ex: AuthorizationException?) {
		currentState.update(response, ex)
		writeState(currentState)
	}

	/**
	 * Updates the [AuthState] with the provided [Token], replacing the old AuthState with the new one.
	 */
	fun updateTokenResponseWithToken(token: Token, clientId: String) {
		val tokenRequest = TokenRequest.Builder(getAuthorizationServiceConfiguration()!!, clientId)
				.setRefreshToken(token.refreshToken)
				.setGrantType(GrantTypeValues.REFRESH_TOKEN)
				.build()
		val tokenResponse = TokenResponse.Builder(tokenRequest)
				.setAccessToken(token.accessToken)
				.setRefreshToken(token.refreshToken)
				.setAccessTokenExpirationTime(token.expiresAt)
				.setScopes(token.scopes?.map { it.uri })
				.build()
		updateTokenResponse(tokenResponse, null)
	}

	/**
	 * Updates the [AuthState] last token response with an [AuthorizationException].
	 */
	fun addAccessTokenAuthorizationException(e: Exception) {
		val authorizationException = AuthorizationException(AuthorizationException.TYPE_OAUTH_TOKEN_ERROR, -1, "Access Token Authentication Error", "Authentication failed with the message: ${e.message}", null, e)
		updateTokenResponse(null, authorizationException)
	}

	/**
	 * Updates the [AuthState] last authorization code response with an [AuthorizationException].
	 */
	fun addAuthorizationCodeAuthorizationException(e: SpotifyException.AuthenticationException) {
		val authorizationException = AuthorizationException(AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR, -1, "Authorization Code Authentication Error", "Authentication failed with the message: ${e.message}", null, e)
		updateAuthorizationResponse(null, authorizationException)
	}

	/**
	 * Replaces the current [AuthState] with the provided [AuthState].
	 */
	fun replaceAuthState(authState: AuthState) {
		currentState = authState
		writeState(authState)
	}

	/**
	 * Forget an existing auth session
	 */
	fun clear() {
		currentState = AuthState()
		writeState(currentState)
		// suppress the notification popup for the future
		appSettings[AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION] = "false"
	}

	/**
	 * Updates the [AuthState] with the token response and authorization exception, replacing the old
	 * AuthState with the new one.
	 */
	private fun updateTokenResponse(response: TokenResponse?, ex: AuthorizationException?) {
		currentState.update(response, ex)
		writeState(currentState)
	}

	/**
	 * Writes the new [AuthState] to [AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION],
	 * replacing the existing [AuthState] with the new one.
	 */
	private fun writeState(state: AuthState) {
		Log.d(TAG, "Writing new AuthState to shared prefs")
		appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON] = state.jsonSerializeString()
	}

	/**
	 * Reads the existing [AuthState] from [AppSettings.KEYS.SPOTIFY_SHOW_UNAUTHENTICATED_NOTIFICATION].
	 * If the [AuthState] doesn't exist or can't be read then an empty unauthenticated [AuthState] is
	 * returned.
	 */
	private fun readState(): AuthState {
		Log.d(TAG, "Reading AuthState from shared prefs")
		val strJson = appSettings[AppSettings.KEYS.SPOTIFY_AUTH_STATE_JSON]
		if (strJson == "") {
			return AuthState()
		}
		return try {
			AuthState.jsonDeserialize(strJson)
		} catch (ex: JSONException) {
			Log.d(TAG, "Failed to deserialize stored auth state - discarding")
			AuthState()
		}
	}
}