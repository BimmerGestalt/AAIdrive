package me.hufman.androidautoidrive.music.spotify

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.adamratzman.spotify.SpotifyException
import net.openid.appauth.*
import org.json.JSONException

/**
 * Spotify manager for an [AuthState] instance that is created from and mutates a JSON shared preferences
 * file. If there are multiple instances of this class modifying the [AuthState], the current instance
 * of the [AuthState] must manually be refreshed.
 */
class SpotifyAuthStateManager(context: Context) {
	companion object {
		const val TAG = "AuthStateManager"
		const val STORE_NAME = "AuthState"
		const val KEY_STATE = "state"
	}

	private val prefs: SharedPreferences
	var currentState: AuthState

	init {
		prefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
		currentState = readState()
	}

	/**
	 * Refreshes the current [AuthState] instance from what is written in the shared preferences. WARNING:
	 * this will overwrite the current [AuthState] with the [AuthState] stored in the shared preferences.
	 */
	fun refreshCurrentState() {
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
	 * Updates the [AuthState] with the token response and authorization exception, replacing the old
	 * AuthState in the shared preferences file with the new one.
	 */
	fun updateTokenResponse(response: TokenResponse?, ex: AuthorizationException?) {
		currentState.update(response, ex)
		writeState(currentState)
	}

	/**
	 * Updates the [AuthState] last token response with an [AuthorizationException].
	 */
	fun addAccessTokenAuthorizationException(e: SpotifyException.AuthenticationException) {
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
	 * Writes the new [AuthState] to the shared preferences file, replacing the existing one with the
	 * new one.
	 */
	private fun writeState(state: AuthState) {
		Log.d(TAG, "Writing new AuthState to shared prefs")
		val editor = prefs.edit()
		editor.putString(KEY_STATE, state.jsonSerializeString())
		val commitResult = editor.commit()
		if (!commitResult) {
			Log.e(TAG, "Error writing new AuthState to shared prefs")
		}
	}

	/**
	 * Reads the existing [AuthState] from the shared preferences file. If the AuthState doesn't exist
	 * or can't be read then an empty unauthenticated AuthState is returned.
	 */
	private fun readState(): AuthState {
		Log.d(TAG, "Reading AuthState from shared prefs")
		val currentState = prefs.getString(KEY_STATE, null) ?: return AuthState()
		return try {
			AuthState.jsonDeserialize(currentState)
		} catch (ex: JSONException) {
			Log.d(TAG, "Failed to deserialize stored auth state - discarding")
			AuthState()
		}
	}
}