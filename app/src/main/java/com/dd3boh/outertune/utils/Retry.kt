package com.dd3boh.outertune.utils

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.random.Random

suspend fun <T> retry(
    times: Int,
    initialDelay: Long,
    maxDelay: Long,
    factor: Double,
    jitterRange: Long,
    functionName: String,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            val nextDelay = currentDelay + Random.nextLong(0, jitterRange)
            Timber.tag("Retry").d("$functionName attempt ${attempt + 1} of $times failed, retrying in $nextDelay ms")
            delay(nextDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    // Last attempt
    return block()
}

suspend fun <T> retryResult(
    times: Int,
    initialDelay: Long,
    maxDelay: Long,
    factor: Double,
    jitterRange: Long,
    functionName: String,
    block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    repeat(times - 1) { attempt ->

        val result = block()
        if (result.isSuccess){
            return result
        }

        val nextDelay = currentDelay + Random.nextLong(0, jitterRange)
        Timber.tag("Retry").d("$functionName attempt ${attempt + 1} of $times failed, retrying in $nextDelay ms")
        delay(nextDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    // Last attempt
    return block()
}