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

import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class LaunchBusinessLogicResultDeliveryTest {

    private val dispatcher = TestCoroutineDispatcher()

    @Test
    fun `should send result to receivers`() = dispatcher.runBlockingTest {
        // given
        val input: Channel<Triple<State, Command<*, Action>?, Effect?>> = Channel()
        val sendState: TestListener = mock()
        val effects: SendChannel<Effect> = mock()
        val commands: SendChannel<Command<*, Action>?> = mock()
        val job = launch { BusinessLogicResultDeliveryStrategyV1(dispatcher, input, sendState, effects, commands) }
        val command: Command<Any, Action> = mock()

        // when
        input.send(Triple(State, command, Effect))

        // then
        verify(sendState).invoke(State)
        verify(effects).send(Effect)
        verify(commands).send(command)

        job.cancelAndJoin()
    }

    @Test
    fun `should not send null effects`() = dispatcher.runBlockingTest {
        // given
        val input: Channel<Triple<State, Command<*, Action>?, Effect?>> = Channel()
        val sendState: TestListener = mock()
        val effects: SendChannel<Effect> = mock()
        val commands: SendChannel<Command<*, Action>?> = mock()
        val job = launch { BusinessLogicResultDeliveryStrategyV1(dispatcher, input, sendState, effects, commands) }

        // when
        input.send(just(State))

        // then
        verifyZeroInteractions(effects)

        job.cancelAndJoin()
    }

    @Test
    fun `should send null commands`() = dispatcher.runBlockingTest {
        // given
        val input: Channel<Triple<State, Command<*, Action>?, Effect?>> = Channel()
        val sendState: TestListener = mock()
        val effects: SendChannel<Effect> = mock()
        val commands: SendChannel<Command<*, Action>?> = mock()
        val job = launch { BusinessLogicResultDeliveryStrategyV1(dispatcher, input, sendState, effects, commands) }

        // when
        input.send(just(State))

        // then
        verify(commands).send(isNull())

        job.cancelAndJoin()
    }

    @Test
    fun `should not send same state twice`() = dispatcher.runBlockingTest {
        // given
        val input: Channel<Triple<State, Command<*, Action>?, Effect?>> = Channel()
        val sendState: TestListener = mock()
        val effects: SendChannel<Effect> = mock()
        val commands: SendChannel<Command<*, Action>?> = mock()
        val job = launch { BusinessLogicResultDeliveryStrategyV1(dispatcher, input, sendState, effects, commands) }

        // when
        input.send(just(State))
        input.send(just(State))

        // then
        verify(sendState).invoke(State)

        job.cancelAndJoin()
    }

    object State
    object Action
    object Effect

    interface TestListener : (State) -> Unit
}