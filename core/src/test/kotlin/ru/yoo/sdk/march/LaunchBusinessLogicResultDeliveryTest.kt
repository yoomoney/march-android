package ru.yoo.sdk.march

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