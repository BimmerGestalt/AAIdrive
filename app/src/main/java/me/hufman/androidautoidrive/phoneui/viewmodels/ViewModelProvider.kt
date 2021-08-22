package me.hufman.androidautoidrive.phoneui.viewmodels

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import kotlin.reflect.KClass

/** Helpers to fetch View Models, which might be optionally mocked out */

val mockedViewModels = HashMap<Class<*>, ViewModel>()

@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.viewModels(
		noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> {
	// the production producer
	val factoryPromise = factoryProducer ?: {
		defaultViewModelProviderFactory
	}
	return createMockedViewModelLazy(VM::class, { viewModelStore }, factoryPromise)
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
		noinline ownerProducer: () -> ViewModelStoreOwner = { this },
		noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
) = createViewModelLazy(VM::class, { ownerProducer().viewModelStore }, factoryProducer)

@MainThread
inline fun <reified VM : ViewModel> Fragment.activityViewModels(
		noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
) = createViewModelLazy(VM::class, { requireActivity().viewModelStore },
		factoryProducer ?: { requireActivity().defaultViewModelProviderFactory })

@MainThread
fun <VM : ViewModel> Fragment.createViewModelLazy(
		viewModelClass: KClass<VM>,
		storeProducer: () -> ViewModelStore,
		factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> {
	val factoryPromise = factoryProducer ?: {
		defaultViewModelProviderFactory
	}
	return createMockedViewModelLazy(viewModelClass, storeProducer, factoryPromise)
}

/**
 * Wraps the default factoryPromise with one that looks in the mockedViewModels map
 */
fun <VM : ViewModel> createMockedViewModelLazy(
		viewModelClass: KClass<VM>,
		storeProducer: () -> ViewModelStore,
		factoryPromise: () -> ViewModelProvider.Factory
): Lazy<VM> {
	// the mock producer
	val mockedFactoryPromise: () -> ViewModelProvider.Factory = {
		// if there are any mocked ViewModels, return a Factory that fetches them
		if (mockedViewModels.isNotEmpty()) {
			object: ViewModelProvider.Factory {
				override fun <T : ViewModel?> create(modelClass: Class<T>): T {
					return mockedViewModels[modelClass] as T
							?: factoryPromise().create(modelClass)  // return the normal one if no mock found
				}
			}
		} else {
			// if no mocks, call the normal factoryPromise directly
			factoryPromise()
		}
	}

	return ViewModelLazy(viewModelClass, storeProducer, mockedFactoryPromise)
}