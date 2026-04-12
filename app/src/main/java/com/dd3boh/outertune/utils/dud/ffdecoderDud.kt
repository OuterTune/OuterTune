package io.github.anilbeesetti.nextlib.media3ext.ffdecoder

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Dud class. This should never be used.
 */
open class NextRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        throw NotImplementedError("Dud class. This should never be used.")
    }
}

class FfmpegLibrary() {
    companion object {
        fun isAvailable() = false
        fun getVersion(): String = "N/A"
    }
}
