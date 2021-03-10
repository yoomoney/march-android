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

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.only
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class LoggingChannelTest {
    @Test
    fun `offer should log element and send to origin`() {
        // given
        val expected = "expected"
        val origin = mock<Channel<String>>()
        val log = mock<Listener>()
        val loggingChannel = LoggingChannel(origin, log)

        // when
        loggingChannel.offer(expected)

        // then
        verify(log).invoke(expected)
        verify(origin, only()).offer(expected)
    }

    @Test
    fun `send should log element and send to origin`() = runBlockingTest {
        // given
        val expected = "expected"
        val origin = mock<Channel<String>>()
        val log = mock<Listener>()
        val loggingChannel = LoggingChannel(origin, log)

        // when
        loggingChannel.send(expected)

        // then
        verify(log).invoke(expected)
        verify(origin, only()).send(expected)
    }

    interface Listener : (String) -> Unit
}