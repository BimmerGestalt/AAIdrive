package me.hufman.androidautoidrive

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A thin interface to allow replacing dispatchers during tests
 */
@Suppress("PropertyName")
interface DispatcherProvider {
	val Main: CoroutineDispatcher
		get() = Dispatchers.Main
	val Default: CoroutineDispatcher
		get() = Dispatchers.Default
	val IO: CoroutineDispatcher
		get() = Dispatchers.IO
	val Unconfined: CoroutineDispatcher
		get() = Dispatchers.Unconfined
}

class DefaultDispatcherProvider : DispatcherProvider