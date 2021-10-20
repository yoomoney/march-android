/*
 * The MIT License (MIT)
 * Copyright © 2021 YooMoney Tech division by NBCO YooMoney LLC
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.yoomoney.sdk.march

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