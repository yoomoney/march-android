package ru.yoo.sdk.march

import kotlinx.coroutines.channels.Channel

/**
 * Proxy channel that log objects that sent to it.
 *
 * @param origin - original channel
 * @param log - logging function
 */
class LoggingChannel<E>(
    private val origin: Channel<E>,
    private val log: (E) -> Unit
) : Channel<E> by origin {
    override fun offer(element: E): Boolean {
        log(element)
        return origin.offer(element)
    }

    override suspend fun send(element: E) {
        log(element)
        origin.send(element)
    }
}

/**
 * Proxy function that log objects that sent to it.
 *
 * @param origin - original function
 * @param log - logging function
 * */
class LoggingFunction<T>(
    private val origin: (T) -> Unit,
    private val log: (T) -> Unit
) : (T) -> Unit {
    override fun invoke(p1: T) {
        log(p1)
        origin(p1)
    }
}