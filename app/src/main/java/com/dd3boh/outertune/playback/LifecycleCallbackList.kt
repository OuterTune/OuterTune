// https://github.com/FoedusProgramme/Gramophone/blob/94baf6aca52ce7ca97715325853cbda555e55ca7/app/src/main/java/org/akanework/gramophone/logic/utils/LifecycleCallbackList.kt
package com.dd3boh.outertune.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

interface LifecycleCallbackList<T> {
    fun addCallback(lifecycle: Lifecycle?, callback: T)
    fun removeCallback(callback: T)
}

class LifecycleCallbackListImpl<T>(lifecycle: Lifecycle? = null) : LifecycleCallbackList<T>,
    DefaultLifecycleObserver {
    private val list = hashMapOf<T, CallbackLifecycleObserver?>()

    init {
        lifecycle?.addObserver(this)
    }

    fun toBaseInterface(): LifecycleCallbackList<T> {
        return this
    }

    override fun addCallback(lifecycle: Lifecycle?, callback: T) {
        if (list.containsKey(callback)) throw IllegalArgumentException("cannot add same callback twice")
        list[callback] = lifecycle?.let { CallbackLifecycleObserver(it, callback) }
    }

    override fun removeCallback(callback: T) {
        list.remove(callback)?.release()
    }

    fun dispatch(callback: Disposable.(T) -> Unit) {
        val ds = DisposableImpl()
        list.toList().forEach {
            ds.disposed = false
            ds.callback(it.first)
            if (ds.disposed) removeCallback(it.first)
        }
    }

    fun release() {
        dispatch { dispose() }
    }

    fun iterator(): Iterator<T> {
        return list.keys.iterator()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    interface Disposable {
        fun dispose()
    }

    class DisposableImpl : Disposable {
        var disposed = false
        override fun dispose() {
            if (disposed) throw IllegalStateException("already disposed")
            disposed = true
        }
    }

    private inner class CallbackLifecycleObserver(
        private val lifecycle: Lifecycle,
        private val callback: T
    ) : DefaultLifecycleObserver {

        init {
            lifecycle.addObserver(this)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            removeCallback(callback)
        }

        fun release() {
            lifecycle.removeObserver(this)
        }
    }
}