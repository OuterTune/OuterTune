package com.zionhuang.innertube.models.response

import kotlinx.serialization.Serializable

/** YouTube timed text API (fmt=json3) response model. */
@Serializable
data class TimedText3Response(
    val events: List<Event>? = null,
) {
    @Serializable
    data class Event(
        val tStartMs: Long,
        val aAppend: Int? = null,
        val segs: List<Seg>? = null,
    ) {
        val isAppend: Boolean get() = aAppend == 1

        @Serializable
        data class Seg(
            val utf8: String? = null,
        )
    }
}
