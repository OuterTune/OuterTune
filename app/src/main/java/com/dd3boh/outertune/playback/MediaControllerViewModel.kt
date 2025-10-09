// https://github.com/FoedusProgramme/Gramophone/blob/94baf6aca52ce7ca97715325853cbda555e55ca7/app/src/main/java/org/akanework/gramophone/ui/MediaControllerViewModel.kt
package com.dd3boh.outertune.playback

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.dd3boh.outertune.App
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException

class MediaControllerViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver, MediaBrowser.Listener {

    private val context: App
        get() = getApplication()
    private var controllerLifecycle: LifecycleHost? = null
    private var controllerFuture: ListenableFuture<MediaBrowser>? = null
    private val customCommandListenersImpl = LifecycleCallbackListImpl<
                (MediaController, SessionCommand, Bundle) -> ListenableFuture<SessionResult>>()
    private val connectionListenersImpl =
        LifecycleCallbackListImpl<LifecycleCallbackListImpl.Disposable.(MediaBrowser, Lifecycle) -> Unit>()
    val customCommandListeners
        get() = customCommandListenersImpl.toBaseInterface()
    val connectionListeners
        get() = connectionListenersImpl.toBaseInterface()

    override fun onStart(owner: LifecycleOwner) {
        val sessionToken =
            SessionToken(context, ComponentName(context, MusicService::class.java))
        val lc = LifecycleHost()
        controllerLifecycle = lc
        controllerFuture =
            MediaBrowser
                .Builder(context, sessionToken)
                .setListener(this)
                .buildAsync()
                .apply {
                    addListener(
                        {
                            if (isCancelled) return@addListener
                            val instance = try {
                                get()
                            } catch (e: ExecutionException) {
                                if (e.cause !is SecurityException)
                                    throw e
                                if (e.cause!!.message != "Session rejected the connection request.")
                                    throw e
                                Log.w(
                                    "MediaControllerViewMdel", "Session rejected the connection" +
                                            " request. Maybe controller.release() was called before" +
                                            " connecting was done?"
                                )
                                null
                            }
                            if (this == controllerFuture && instance == null) {
                                controllerFuture = null
                                controllerLifecycle = null
                            }
                            if (this != controllerFuture || instance == null) {
                                // If there is a race condition that would cause this controller
                                // to leak, which can happen, just make sure we don't leak.
                                lc.destroy()
                                instance?.release()
                            } else {
                                lc.lifecycleRegistry.currentState = Lifecycle.State.CREATED
                                connectionListenersImpl.dispatch { it(instance, lc.lifecycle) }
                            }
                        }, ContextCompat.getMainExecutor(context)
                    )
                }
    }

    fun addControllerCallback(
        lifecycle: Lifecycle?,
        callback: LifecycleCallbackListImpl.Disposable.(MediaBrowser, Lifecycle) -> Unit
    ) {
        // TODO migrate this to kt flows or LiveData?
        val instance = get()
        var skip = false
        if (instance != null) {
            val ds = LifecycleCallbackListImpl.DisposableImpl()
            ds.callback(instance, controllerLifecycle!!.lifecycle)
            skip = ds.disposed
        }
        if (instance == null || !skip) {
            connectionListeners.addCallback(lifecycle, callback)
        }
    }

    fun addRecreationalPlayerListener(lifecycle: Lifecycle, callback: (Player) -> Player.Listener) {
        addControllerCallback(lifecycle) { controller, controllerLifecycle ->
            controller.registerLifecycleCallback(
                LifecycleIntersection(lifecycle, controllerLifecycle).lifecycle,
                callback(controller)
            )
        }
    }

    fun get(): MediaBrowser? {
        if (controllerFuture?.isDone == true && controllerFuture?.isCancelled == false) {
            return controllerFuture!!.get()
        }
        return null
    }

    override fun onDisconnected(controller: MediaController) {
        controllerLifecycle?.destroy()
        controllerLifecycle = null
        controllerFuture = null
    }

    // TODO reconsider whether onStop is a good place, as getting stopped is quite easy and
    //  predictive back makes it obvious that we are reconnecting
    override fun onStop(owner: LifecycleOwner) {
        if (controllerFuture?.isDone == true) {
            if (controllerFuture?.isCancelled == false) {
                controllerFuture?.get()?.release()
            } else {
                throw IllegalStateException("controllerFuture?.isCancelled != false")
            }
        } else {
            controllerFuture?.cancel(true)
            controllerLifecycle?.destroy()
            controllerLifecycle = null
            controllerFuture = null
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        customCommandListenersImpl.release()
        connectionListenersImpl.release()
    }

    override fun onCustomCommand(
        controller: MediaController,
        command: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        var future: ListenableFuture<SessionResult>? = null
        val listenerIterator = customCommandListenersImpl.iterator()
        while (listenerIterator.hasNext() && (future == null || (future.isDone &&
                    future.get().resultCode == SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        ) {
            future = listenerIterator.next()(controller, command, args)
        }
        return future ?: super.onCustomCommand(controller, command, args)
    }

    fun getService(): MusicService? {
        val mediaBrowser = get() ?: return null
        mediaBrowser.sendCustomCommand(
            SessionCommand(MusicService.COMMAND_GET_BINDER, Bundle.EMPTY),
            Bundle.EMPTY
        ).get().extras.run {
            return (getBinder("music_binder") as MusicService.MusicBinder).service
        }
    }

    private class LifecycleHost : LifecycleOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle
            get() = lifecycleRegistry

        fun destroy() {
            // you cannot set DESTROYED before setting CREATED
            // this would leak observers if the LifecycleHost is exposed to clients before ON_CREATE
            // but it's not and that is apparently what google wanted to achieve with this check
            if (lifecycle.currentState != Lifecycle.State.INITIALIZED)
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    class LifecycleIntersection(
        private val lifecycleOne: Lifecycle,
        private val lifecycleTwo: Lifecycle
    ) : LifecycleOwner, LifecycleEventObserver {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle
            get() = lifecycleRegistry

        init {
            lifecycleRegistry.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    lifecycleOne.removeObserver(this@LifecycleIntersection)
                    lifecycleTwo.removeObserver(this@LifecycleIntersection)
                }
            })
            lifecycleOne.addObserver(this)
            lifecycleTwo.addObserver(this)
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (lifecycleOne.currentState == Lifecycle.State.DESTROYED ||
                lifecycleTwo.currentState == Lifecycle.State.DESTROYED
            ) {
                // you cannot set DESTROYED before setting CREATED
                if (lifecycle.currentState == Lifecycle.State.INITIALIZED)
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                return
            }
            val target = lifecycleOne.currentState.coerceAtMost(lifecycleTwo.currentState)
            if (target == lifecycleRegistry.currentState) return
            lifecycleRegistry.currentState = target
        }
    }
}

fun Player.registerLifecycleCallback(lifecycle: Lifecycle, callback: Player.Listener) {
    addListener(callback)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            removeListener(callback)
        }
    })
}
