package me.hufman.androidautoidrive.music.spotify.authentication

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import org.json.JSONException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Spotify persistence manager for an [AuthState] instance for the Spotify Web API that stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
class SpotifyAuthStateManager private constructor(context: Context) {
	companion object {
		private val INSTANCE_REF: AtomicReference<WeakReference<SpotifyAuthStateManager?>> = AtomicReference<WeakReference<SpotifyAuthStateManager?>>(WeakReference(null))
		private const val TAG = "AuthStateManager"
		const val STORE_NAME = "AuthState"
		private const val KEY_STATE = "state"

		fun getInstance(context: Context): SpotifyAuthStateManager {
			var manager = INSTANCE_REF.get().get()
			if (manager == null) {
				manager = SpotifyAuthStateManager(context.applicationContext)
				INSTANCE_REF.set(WeakReference(manager))
			}
			return manager
		}
	}

	private val prefs: SharedPreferences
	private val prefsLock: ReentrantLock
	private val currentAuthState: AtomicReference<AuthState>

	init {
		prefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
		prefsLock = ReentrantLock()
		currentAuthState = AtomicReference()
	}

	val currentState: AuthState
		get() {
			if (currentAuthState.get() != null) {
				return currentAuthState.get()
			}
			val state = readState()
			return if (currentAuthState.compareAndSet(null, state)) {
				state
			} else {
				currentAuthState.get()
			}
		}

	/**
	 * Replaces the [AuthState] in the shared preferences file with the new AuthState.
	 */
	fun replaceState(state: AuthState?): AuthState? {
		writeState(state)
		currentAuthState.set(state)
		return state
	}

	/**
	 * Updates the [AuthState] from the authorization response, replacing the old AuthState in the shared preferences file with the new one.
	 */
	fun updateAfterAuthorization(response: AuthorizationResponse?, ex: AuthorizationException?): AuthState? {
		currentState.update(response, ex)
		return replaceState(currentState)
	}

	/**
	 * Updates the [AuthState] from the token response, replacing the old AuthState in the shared preferences file with the new one.
	 */
	fun updateAfterTokenResponse(response: TokenResponse?, ex: AuthorizationException?): AuthState? {
		currentState.update(response, ex)
		return replaceState(currentState)
	}

	fun isAuthorized(): Boolean {
		return currentState.isAuthorized
	}

	/**
	 * Reads the existing [AuthState] from the shared preferences file. If the AuthState doesn't exist
	 * or can't be read then an empty unauthenticated AuthState is returned.
	 */
	private fun readState(): AuthState {
		prefsLock.lock()
		return try {
			val currentState = prefs.getString(KEY_STATE, null)
					?: return AuthState()
			try {
				AuthState.jsonDeserialize(currentState)
			} catch (ex: JSONException) {
				Log.d(TAG, "Failed to deserialize stored auth state - discarding")
				AuthState()
			}
		} finally {
			prefsLock.unlock()
		}
	}

	/**
	 * Writes the new [AuthState] to the shared preferences file.
	 */
	private fun writeState(state: AuthState?) {
		prefsLock.lock()
		try {
			val editor = prefs.edit()
			if (state == null) {
				editor.remove(KEY_STATE)
			} else {
				editor.putString(KEY_STATE, state.jsonSerializeString())
			}
			check(editor.commit()) { "Failed to write state to shared prefs" }
		} finally {
			prefsLock.unlock()
		}
	}
}