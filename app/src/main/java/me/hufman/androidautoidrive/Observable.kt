package me.hufman.androidautoidrive

/**
 * Represents a value that can be changed
 * A callback can be registered to be notified
 */
abstract class Observable<T> {
    /**
     * The current value
     */
    abstract var value: T?
        protected set

    /**
     * When pending is true, no value has been set yet
     */
    var pending: Boolean = true
        protected set

    /**
     * The callback to be notified right before the value changes
     * Setting the callback after the Observable has been set will trigger the callback
     * The passed value has the updated value
     */
    var callback: ((T?) -> Unit)? = null
        set(value) {
            field = value
            if (!pending) callback?.invoke(this.value)
        }

    /**
     * A convenience function to set the callback
     */
    fun subscribe(callback: (T?) -> Unit) {
        this.callback = callback
    }
}

/**
 * A subclass of Observable that can be modified
 */
class MutableObservable<T>(initial: T? = null) : Observable<T>() {
    override var value: T? = initial
        get() = field
        public set(value) {
            pending = false
            field = value
            callback?.invoke(value)
        }
}
